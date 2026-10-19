package com.addonstation.mobile.data.repo

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.util.shrinkImageBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

const val BULK_IMPORT_MAX_ARCHIVE_BYTES: Long = 1_073_741_824L
const val BULK_IMPORT_MAX_CONTENT_BYTES: Long = 536_870_912L
private const val BULK_IMPORT_MAX_COVER_BYTES: Long = 20L * 1024L * 1024L
private const val BULK_IMPORT_MAX_TOTAL_UNCOMPRESSED: Long = 8L * 1024L * 1024L * 1024L
private const val BULK_IMPORT_MAX_ENTRIES = 5_000
private const val BULK_IMPORT_MAX_ITEMS = 200
private const val BULK_IMPORT_MAX_METADATA_BYTES = 64 * 1024
private const val BULK_IMPORT_CACHE_TTL_MS = 24L * 60L * 60L * 1_000L

/** Stable machine-readable reasons; the UI maps each one to localized text. */
enum class BulkImportError {
    ARCHIVE_NOT_ZIP,
    ARCHIVE_TOO_LARGE,
    ARCHIVE_EMPTY,
    ARCHIVE_CORRUPT,
    ARCHIVE_UNSAFE_PATH,
    ARCHIVE_TOO_MANY_ENTRIES,
    ARCHIVE_EXPANDS_TOO_LARGE,
    ARCHIVE_SUSPICIOUS_COMPRESSION,
    NOT_ENOUGH_SPACE,
    NO_ITEMS,
    TOO_MANY_ITEMS,
    WRONG_CONTENT_TYPE,
    CONTENT_MISSING,
    MULTIPLE_CONTENT_FILES,
    COVER_MISSING,
    MULTIPLE_COVERS,
    COVER_TOO_LARGE,
    CONTENT_TOO_LARGE,
    METADATA_INVALID,
    IMAGE_INVALID,
    CONTENT_ARCHIVE_INVALID,
    CACHE_FILE_MISSING,
    SERVER_REJECTED,
    REGISTRATION_UNCERTAIN,
}

class BulkImportException(
    val reason: BulkImportError,
    val detail: String? = null,
    cause: Throwable? = null,
) : IOException(listOfNotNull(reason.name.lowercase(Locale.ROOT), detail).joinToString(":"), cause)

enum class BulkImportStage {
    COPYING_ARCHIVE,
    SCANNING_ARCHIVE,
    EXTRACTING_ITEM,
    UPLOADING_COVER,
    UPLOADING_CONTENT,
    REGISTERING_ITEMS,
    CLEANING_UP,
    COMPLETE,
}

data class BulkImportProgress(
    val stage: BulkImportStage,
    val fraction: Float? = null,
    val itemIndex: Int = 0,
    val itemCount: Int = 0,
    val itemTitle: String? = null,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
)

data class BulkArchiveItem(
    val sourceFolder: String,
    val remoteKey: String,
    val title: String,
    val description: String?,
    val version: String?,
    val coverEntry: String,
    val contentEntry: String,
    val contentFileName: String,
    val contentSize: Long,
)

data class BulkArchiveInspection(
    val cacheFile: File,
    val batchId: String,
    val archiveName: String,
    val archiveSize: Long,
    val archiveSha256: String,
    val contentType: String,
    val items: List<BulkArchiveItem>,
    val ignoredFiles: Int,
    val totalContentBytes: Long,
) {
    /** Kept after an ambiguous RPC response so Retry confirms without re-uploading. */
    @Volatile
    internal var pendingRegistration: List<UploadedBulkItem>? = null

    fun cleanup() {
        if (cacheFile.exists()) cacheFile.delete()
    }
}

data class BulkImportOptions(
    val category: String,
    val plan: String,
    val acPrice: Int,
    val publish: Boolean,
)

data class BulkImportResult(
    val batchId: String,
    val importedCount: Int,
)

private data class ArchiveEntryInfo(
    val entry: ZipEntry,
    val name: String,
    val folder: String,
    val extension: String,
)

internal data class UploadedBulkItem(
    val sourceFolder: String,
    val title: String,
    val description: String?,
    val version: String?,
    val coverPath: String,
    val filePath: String,
    val fileSize: Long,
    val fileSha256: String,
)

/**
 * ZIP importer used only behind AdminGate. It never extracts using archive
 * paths: every selected entry is copied into a generated cache file, which is
 * the core Zip-Slip defence. The outer archive is streamed to disk so 1 GiB
 * packages never become 1 GiB ByteArrays.
 */
internal class BulkContentImporter(private val supabase: SupabaseClient) {

    private val metadataJson = Json { ignoreUnknownKeys = true; isLenient = false }

    suspend fun inspect(
        context: Context,
        uri: Uri,
        contentType: String,
        onProgress: (BulkImportProgress) -> Unit,
    ): BulkArchiveInspection = withContext(Dispatchers.IO) {
        require(contentType == "addon" || contentType == "world")
        val metadata = documentMetadata(context, uri)
        val archiveName = metadata.first
        val declaredSize = metadata.second
        if (!archiveName.lowercase(Locale.ROOT).endsWith(".zip")) {
            throw BulkImportException(BulkImportError.ARCHIVE_NOT_ZIP, archiveName)
        }
        if (declaredSize != null && declaredSize > BULK_IMPORT_MAX_ARCHIVE_BYTES) {
            throw BulkImportException(BulkImportError.ARCHIVE_TOO_LARGE, declaredSize.toString())
        }

        val cacheDir = File(context.cacheDir, "bulk-content-import").apply { mkdirs() }
        pruneStaleCache(cacheDir)
        if (declaredSize != null) {
            requireFreeSpace(cacheDir, declaredSize + 32L * 1024L * 1024L)
        } else {
            requireFreeSpace(cacheDir, 64L * 1024L * 1024L)
        }
        val cached = File.createTempFile("archive-", ".zip", cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var nextUnknownSizeSpaceCheck = 16L * 1024L * 1024L
            context.contentResolver.openInputStream(uri)?.buffered(256 * 1024)?.use { input ->
                cached.outputStream().buffered(256 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read
                        if (copied > BULK_IMPORT_MAX_ARCHIVE_BYTES) {
                            throw BulkImportException(BulkImportError.ARCHIVE_TOO_LARGE, copied.toString())
                        }
                        if (declaredSize == null && copied >= nextUnknownSizeSpaceCheck) {
                            requireFreeSpace(cacheDir, 32L * 1024L * 1024L)
                            nextUnknownSizeSpaceCheck += 16L * 1024L * 1024L
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        onProgress(
                            BulkImportProgress(
                                stage = BulkImportStage.COPYING_ARCHIVE,
                                fraction = declaredSize?.takeIf { it > 0L }
                                    ?.let { (copied.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() },
                                bytesDone = copied,
                                bytesTotal = declaredSize ?: 0L,
                            )
                        )
                    }
                }
            } ?: throw BulkImportException(BulkImportError.ARCHIVE_EMPTY)
            if (copied <= 0L) throw BulkImportException(BulkImportError.ARCHIVE_EMPTY)
            if (declaredSize != null && declaredSize > 0L && copied != declaredSize) {
                throw BulkImportException(BulkImportError.ARCHIVE_CORRUPT, "$copied/$declaredSize")
            }

            onProgress(BulkImportProgress(BulkImportStage.SCANNING_ARCHIVE))
            val inspection = scanArchive(
                cached = cached,
                batchId = UUID.randomUUID().toString(),
                archiveName = archiveName.take(255),
                archiveSize = copied,
                archiveSha256 = digest.digest().toHex(),
                contentType = contentType,
            )
            val extractionReserve = inspection.items.maxOf { it.contentSize } + 32L * 1024L * 1024L
            requireFreeSpace(cacheDir, extractionReserve)
            inspection
        } catch (cancelled: CancellationException) {
            cached.delete()
            throw cancelled
        } catch (failure: BulkImportException) {
            cached.delete()
            throw failure
        } catch (failure: ZipException) {
            cached.delete()
            throw BulkImportException(BulkImportError.ARCHIVE_CORRUPT, failure.message, failure)
        } catch (failure: Throwable) {
            cached.delete()
            throw BulkImportException(BulkImportError.ARCHIVE_CORRUPT, failure.message, failure)
        }
    }

    suspend fun import(
        inspection: BulkArchiveInspection,
        options: BulkImportOptions,
        onProgress: (BulkImportProgress) -> Unit,
    ): BulkImportResult = withContext(Dispatchers.IO) {
        if (!inspection.cacheFile.isFile || inspection.cacheFile.length() != inspection.archiveSize) {
            throw BulkImportException(BulkImportError.CACHE_FILE_MISSING)
        }
        require(options.category in VALID_CATEGORIES)
        require(options.plan in VALID_PLANS)
        require(options.acPrice >= 0)

        val uploadedCovers = mutableListOf<String>()
        val uploadedContent = mutableListOf<String>()
        val payload = mutableListOf<UploadedBulkItem>()
        val workDir = File(inspection.cacheFile.parentFile, "work-${inspection.batchId}").apply { mkdirs() }
        val workTotal = (inspection.totalContentBytes * 2L).coerceAtLeast(1L)
        var completedWork = 0L
        var preserveUploads = false
        val pendingPayload = inspection.pendingRegistration
        if (pendingPayload != null) {
            payload += pendingPayload
            // A previous registration response was ambiguous. Never delete or
            // overwrite those objects before the idempotent RPC confirms state.
            preserveUploads = true
        }

        try {
            if (pendingPayload == null) {
                ZipFile(inspection.cacheFile).use { zip ->
                inspection.items.forEachIndexed { index, item ->
                    currentCoroutineContext().ensureActive()
                    val itemNumber = index + 1
                    val baseProgress = completedWork
                    onProgress(
                        BulkImportProgress(
                            stage = BulkImportStage.EXTRACTING_ITEM,
                            fraction = baseProgress.toFloat() / workTotal.toFloat(),
                            itemIndex = itemNumber,
                            itemCount = inspection.items.size,
                            itemTitle = item.title,
                        )
                    )

                    val coverEntry = zip.getEntry(item.coverEntry)
                        ?: throw BulkImportException(BulkImportError.COVER_MISSING, item.sourceFolder)
                    val coverBytes = readEntryLimited(zip, coverEntry, BULK_IMPORT_MAX_COVER_BYTES)
                    val preparedCover = shrinkImageBytes(
                        bytes = coverBytes,
                        maxSide = 1280,
                        jpegQuality = 86,
                        forceJpeg = true,
                    ) ?: throw BulkImportException(BulkImportError.IMAGE_INVALID, item.sourceFolder)
                    if (preparedCover.bytes.size > 5 * 1024 * 1024) {
                        throw BulkImportException(BulkImportError.COVER_TOO_LARGE, item.sourceFolder)
                    }
                    val coverPath = "items/bulk/${inspection.batchId}/${item.remoteKey}/cover.jpg"
                    onProgress(
                        BulkImportProgress(
                            stage = BulkImportStage.UPLOADING_COVER,
                            fraction = baseProgress.toFloat() / workTotal.toFloat(),
                            itemIndex = itemNumber,
                            itemCount = inspection.items.size,
                            itemTitle = item.title,
                        )
                    )
                    supabase.upload("site-assets", coverPath, preparedCover.bytes, preparedCover.mime, upsert = true)
                    uploadedCovers += coverPath

                    val extension = item.contentFileName.substringAfterLast('.', "bin").lowercase(Locale.ROOT)
                    val localContent = File.createTempFile("content-", ".$extension", workDir)
                    try {
                        val contentEntry = zip.getEntry(item.contentEntry)
                            ?: throw BulkImportException(BulkImportError.CONTENT_MISSING, item.sourceFolder)
                        val fileDigest = MessageDigest.getInstance("SHA-256")
                        var extracted = 0L
                        zip.getInputStream(contentEntry).buffered(256 * 1024).use { input ->
                            localContent.outputStream().buffered(256 * 1024).use { output ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    extracted += read
                                    if (extracted > BULK_IMPORT_MAX_CONTENT_BYTES || extracted > item.contentSize) {
                                        throw BulkImportException(BulkImportError.CONTENT_TOO_LARGE, item.sourceFolder)
                                    }
                                    output.write(buffer, 0, read)
                                    fileDigest.update(buffer, 0, read)
                                    onProgress(
                                        BulkImportProgress(
                                            stage = BulkImportStage.EXTRACTING_ITEM,
                                            fraction = ((baseProgress + extracted).toDouble() / workTotal.toDouble())
                                                .coerceIn(0.0, 0.98).toFloat(),
                                            itemIndex = itemNumber,
                                            itemCount = inspection.items.size,
                                            itemTitle = item.title,
                                            bytesDone = extracted,
                                            bytesTotal = item.contentSize,
                                        )
                                    )
                                }
                            }
                        }
                        if (extracted != item.contentSize || localContent.length() != item.contentSize) {
                            throw BulkImportException(
                                BulkImportError.ARCHIVE_CORRUPT,
                                "${item.sourceFolder}:$extracted/${item.contentSize}",
                            )
                        }
                        validateNestedContentArchive(localContent, item.sourceFolder)
                        val fileSha256 = fileDigest.digest().toHex()
                        completedWork += extracted

                        val remoteFileName = safeRemoteFileName(item.contentFileName, item.remoteKey)
                        val filePath = "bulk/${inspection.batchId}/${item.remoteKey}/$remoteFileName"
                        onProgress(
                            BulkImportProgress(
                                stage = BulkImportStage.UPLOADING_CONTENT,
                                fraction = (completedWork.toDouble() / workTotal.toDouble()).coerceIn(0.0, 0.98).toFloat(),
                                itemIndex = itemNumber,
                                itemCount = inspection.items.size,
                                itemTitle = item.title,
                                bytesTotal = item.contentSize,
                            )
                        )
                        val uploadBase = completedWork
                        supabase.uploadFile(
                            bucket = "content-files",
                            path = filePath,
                            file = localContent,
                            contentType = "application/zip",
                            upsert = true,
                        ) { sent, total ->
                            onProgress(
                                BulkImportProgress(
                                    stage = BulkImportStage.UPLOADING_CONTENT,
                                    fraction = ((uploadBase + sent).toDouble() / workTotal.toDouble())
                                        .coerceIn(0.0, 0.98).toFloat(),
                                    itemIndex = itemNumber,
                                    itemCount = inspection.items.size,
                                    itemTitle = item.title,
                                    bytesDone = sent,
                                    bytesTotal = total,
                                )
                            )
                        }
                        uploadedContent += filePath
                        completedWork += extracted
                        payload += UploadedBulkItem(
                            sourceFolder = item.sourceFolder,
                            title = item.title,
                            description = item.description,
                            version = item.version,
                            coverPath = coverPath,
                            filePath = filePath,
                            fileSize = extracted,
                            fileSha256 = fileSha256,
                        )
                    } finally {
                        localContent.delete()
                    }
                }
            }
            }

            inspection.pendingRegistration = payload.toList()
            preserveUploads = true
            onProgress(
                BulkImportProgress(
                    stage = BulkImportStage.REGISTERING_ITEMS,
                    fraction = 0.99f,
                    itemCount = inspection.items.size,
                )
            )
            // A transport failure after this point is ambiguous: the transaction
            // may have committed while its response was lost. Preserve objects
            // and the cached archive so pressing Retry with the same batch id is safe.
            val rpcItems = JsonArray(payload.map { item ->
                buildJsonObject {
                    put("source_folder", item.sourceFolder)
                    put("title", item.title)
                    item.description?.let { put("description", it) }
                    item.version?.let { put("version", it) }
                    put("cover_path", item.coverPath)
                    put("file_path", item.filePath)
                    put("file_size", item.fileSize)
                    put("file_sha256", item.fileSha256)
                }
            })
            val response = try {
                supabase.rpc(
                    function = "admin_bulk_import_content",
                    params = mapOf(
                        "p_batch_id" to JsonPrimitive(inspection.batchId),
                        "p_archive_name" to JsonPrimitive(inspection.archiveName),
                        "p_archive_size" to JsonPrimitive(inspection.archiveSize),
                        "p_archive_sha256" to JsonPrimitive(inspection.archiveSha256),
                        "p_content_type" to JsonPrimitive(inspection.contentType),
                        "p_category" to JsonPrimitive(options.category),
                        "p_plan" to JsonPrimitive(options.plan),
                        "p_ac_price" to JsonPrimitive(options.acPrice),
                        "p_is_published" to JsonPrimitive(options.publish),
                        "p_items" to rpcItems,
                    ),
                    signedOnly = true,
                )
            } catch (failure: Throwable) {
                throw BulkImportException(BulkImportError.REGISTRATION_UNCERTAIN, failure.message, failure)
            }
            val outcome = response.asOutcome()
            if (!outcome.ok) {
                // A structured false response confirms that no atomic batch was
                // committed, so its objects are safe to remove.
                preserveUploads = false
                inspection.pendingRegistration = null
                throw BulkImportException(BulkImportError.SERVER_REJECTED, outcome.message)
            }
            val imported = outcome.data["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: payload.size
            inspection.pendingRegistration = null
            inspection.cleanup()
            onProgress(BulkImportProgress(BulkImportStage.COMPLETE, 1f, itemCount = imported))
            BulkImportResult(inspection.batchId, imported)
        } catch (cancelled: CancellationException) {
            if (!preserveUploads) {
                cleanupUploaded(
                    covers = (uploadedCovers + payload.map { it.coverPath }).distinct(),
                    content = (uploadedContent + payload.map { it.filePath }).distinct(),
                    onProgress = onProgress,
                )
                inspection.pendingRegistration = null
            }
            throw cancelled
        } catch (failure: Throwable) {
            if (!preserveUploads) {
                cleanupUploaded(
                    covers = (uploadedCovers + payload.map { it.coverPath }).distinct(),
                    content = (uploadedContent + payload.map { it.filePath }).distinct(),
                    onProgress = onProgress,
                )
                inspection.pendingRegistration = null
            }
            throw failure
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun scanArchive(
        cached: File,
        batchId: String,
        archiveName: String,
        archiveSize: Long,
        archiveSha256: String,
        contentType: String,
    ): BulkArchiveInspection {
        val allFiles = mutableListOf<ArchiveEntryInfo>()
        val seenPaths = mutableSetOf<String>()
        var totalUncompressed = 0L

        ZipFile(cached).use { zip ->
            val entries = zip.entries()
            var entryCount = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount += 1
                if (entryCount > BULK_IMPORT_MAX_ENTRIES) {
                    throw BulkImportException(BulkImportError.ARCHIVE_TOO_MANY_ENTRIES, entryCount.toString())
                }
                val cleanName = validateArchivePath(entry.name)
                val pathKey = cleanName.lowercase(Locale.ROOT)
                if (!seenPaths.add(pathKey)) {
                    throw BulkImportException(BulkImportError.ARCHIVE_UNSAFE_PATH, "duplicate:$cleanName")
                }
                if (entry.isDirectory) continue
                if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
                    throw BulkImportException(BulkImportError.ARCHIVE_CORRUPT, "method:${entry.method}")
                }
                if (entry.size < 0L || entry.compressedSize < 0L) {
                    throw BulkImportException(BulkImportError.ARCHIVE_CORRUPT, "size:$cleanName")
                }
                totalUncompressed = safeAdd(totalUncompressed, entry.size)
                if (totalUncompressed > BULK_IMPORT_MAX_TOTAL_UNCOMPRESSED) {
                    throw BulkImportException(BulkImportError.ARCHIVE_EXPANDS_TOO_LARGE, totalUncompressed.toString())
                }
                if (entry.size > 20L * 1024L * 1024L && entry.compressedSize > 0L &&
                    entry.size / entry.compressedSize > 250L
                ) {
                    throw BulkImportException(BulkImportError.ARCHIVE_SUSPICIOUS_COMPRESSION, cleanName)
                }
                if (isNoiseEntry(cleanName)) continue
                val folder = cleanName.substringBeforeLast('/', "")
                val extension = cleanName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                allFiles += ArchiveEntryInfo(entry, cleanName, folder, extension)
            }

            if (allFiles.isEmpty()) throw BulkImportException(BulkImportError.ARCHIVE_EMPTY)
            val wantedExtensions = if (contentType == "addon") ADDON_EXTENSIONS else WORLD_EXTENSIONS
            val wrongExtensions = if (contentType == "addon") setOf("mcworld") else setOf("mcaddon", "mcpack")
            allFiles.firstOrNull { it.extension in wrongExtensions }?.let {
                throw BulkImportException(BulkImportError.WRONG_CONTENT_TYPE, it.name)
            }

            val contentCandidates = allFiles.filter { it.extension in wantedExtensions }
            if (contentCandidates.isEmpty()) throw BulkImportException(BulkImportError.NO_ITEMS)
            val byFolder = contentCandidates.groupBy { it.folder }
            if (byFolder.size > BULK_IMPORT_MAX_ITEMS) {
                throw BulkImportException(BulkImportError.TOO_MANY_ITEMS, byFolder.size.toString())
            }

            val selectedNames = mutableSetOf<String>()
            val items = byFolder.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (folder, contents) ->
                if (folder.isBlank()) throw BulkImportException(BulkImportError.CONTENT_MISSING, "root")
                if (contents.size != 1) {
                    throw BulkImportException(BulkImportError.MULTIPLE_CONTENT_FILES, folder)
                }
                val content = contents.single()
                if (content.entry.size <= 0L || content.entry.size > BULK_IMPORT_MAX_CONTENT_BYTES) {
                    throw BulkImportException(BulkImportError.CONTENT_TOO_LARGE, folder)
                }
                val images = allFiles.filter { it.folder == folder && it.extension in IMAGE_EXTENSIONS }
                val cover = selectCover(folder, images)
                if (cover.entry.size <= 0L || cover.entry.size > BULK_IMPORT_MAX_COVER_BYTES) {
                    throw BulkImportException(BulkImportError.COVER_TOO_LARGE, folder)
                }
                val metadataEntries = allFiles.filter {
                    it.folder == folder && it.name.substringAfterLast('/').lowercase(Locale.ROOT) in METADATA_NAMES
                }
                if (metadataEntries.size > 1) {
                    throw BulkImportException(BulkImportError.METADATA_INVALID, folder)
                }
                val metadata = metadataEntries.singleOrNull()?.let { info ->
                    if (info.entry.size > BULK_IMPORT_MAX_METADATA_BYTES) {
                        throw BulkImportException(BulkImportError.METADATA_INVALID, folder)
                    }
                    parseMetadata(zip, info.entry, folder)
                }
                selectedNames += content.name
                selectedNames += cover.name
                metadataEntries.singleOrNull()?.let { selectedNames += it.name }

                val fallbackTitle = folder.substringAfterLast('/').replace('_', ' ').replace('-', ' ')
                    .replace(Regex("\\s+"), " ").trim().take(120)
                val title = (metadata?.get("title") as? JsonPrimitive)?.contentOrNull
                    ?.trim()?.take(120)?.takeIf(String::isNotBlank)
                    ?: fallbackTitle.takeIf(String::isNotBlank)
                    ?: throw BulkImportException(BulkImportError.METADATA_INVALID, folder)
                val description = (metadata?.get("description") as? JsonPrimitive)?.contentOrNull
                    ?.trim()?.take(5_000)?.takeIf(String::isNotBlank)
                val version = (metadata?.get("version") as? JsonPrimitive)?.contentOrNull
                    ?.trim()?.take(40)?.takeIf(String::isNotBlank)
                BulkArchiveItem(
                    sourceFolder = folder,
                    remoteKey = remoteKey(folder),
                    title = title,
                    description = description,
                    version = version,
                    coverEntry = cover.name,
                    contentEntry = content.name,
                    contentFileName = content.name.substringAfterLast('/'),
                    contentSize = content.entry.size,
                )
            }
            if (items.isEmpty()) throw BulkImportException(BulkImportError.NO_ITEMS)
            val ignored = allFiles.count { it.name !in selectedNames }
            return BulkArchiveInspection(
                cacheFile = cached,
                batchId = batchId,
                archiveName = archiveName,
                archiveSize = archiveSize,
                archiveSha256 = archiveSha256,
                contentType = contentType,
                items = items,
                ignoredFiles = ignored,
                totalContentBytes = items.sumOf { it.contentSize },
            )
        }
    }

    private fun parseMetadata(zip: ZipFile, entry: ZipEntry, folder: String): JsonObject = try {
        val bytes = readEntryLimited(zip, entry, BULK_IMPORT_MAX_METADATA_BYTES.toLong())
        metadataJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
            ?: throw BulkImportException(BulkImportError.METADATA_INVALID, folder)
    } catch (failure: BulkImportException) {
        throw failure
    } catch (failure: Throwable) {
        throw BulkImportException(BulkImportError.METADATA_INVALID, folder, failure)
    }

    private fun selectCover(folder: String, candidates: List<ArchiveEntryInfo>): ArchiveEntryInfo {
        if (candidates.isEmpty()) throw BulkImportException(BulkImportError.COVER_MISSING, folder)
        if (candidates.size == 1) return candidates.single()
        val preferred = candidates.filter {
            it.name.substringAfterLast('/').substringBeforeLast('.').lowercase(Locale.ROOT) in COVER_STEMS
        }
        if (preferred.size == 1) return preferred.single()
        throw BulkImportException(BulkImportError.MULTIPLE_COVERS, folder)
    }

    private fun validateNestedContentArchive(file: File, sourceFolder: String) {
        try {
            ZipFile(file).use { nested ->
                val entries = nested.entries()
                var count = 0
                var files = 0
                var expanded = 0L
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count += 1
                    if (count > 10_000) {
                        throw BulkImportException(BulkImportError.CONTENT_ARCHIVE_INVALID, sourceFolder)
                    }
                    validateArchivePath(entry.name)
                    if (!entry.isDirectory) {
                        files += 1
                        if (entry.size < 0L || entry.compressedSize < 0L) {
                            throw BulkImportException(BulkImportError.CONTENT_ARCHIVE_INVALID, sourceFolder)
                        }
                        expanded = safeAdd(expanded, entry.size)
                        if (expanded > BULK_IMPORT_MAX_TOTAL_UNCOMPRESSED) {
                            throw BulkImportException(BulkImportError.CONTENT_ARCHIVE_INVALID, sourceFolder)
                        }
                        if (entry.size > 20L * 1024L * 1024L && entry.compressedSize > 0L &&
                            entry.size / entry.compressedSize > 500L
                        ) {
                            throw BulkImportException(BulkImportError.CONTENT_ARCHIVE_INVALID, sourceFolder)
                        }
                    }
                }
                if (files == 0) throw BulkImportException(BulkImportError.CONTENT_ARCHIVE_INVALID, sourceFolder)
            }
        } catch (failure: BulkImportException) {
            throw failure
        } catch (failure: Throwable) {
            throw BulkImportException(BulkImportError.CONTENT_ARCHIVE_INVALID, sourceFolder, failure)
        }
    }

    private suspend fun cleanupUploaded(
        covers: List<String>,
        content: List<String>,
        onProgress: (BulkImportProgress) -> Unit,
    ) = withContext(NonCancellable + Dispatchers.IO) {
        if (covers.isEmpty() && content.isEmpty()) return@withContext
        onProgress(BulkImportProgress(BulkImportStage.CLEANING_UP))
        runCatching { supabase.removeObjects("site-assets", covers) }
        runCatching { supabase.removeObjects("content-files", content) }
    }

    private fun readEntryLimited(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray {
        if (entry.size < 0L || entry.size > limit || entry.size > Int.MAX_VALUE) {
            throw BulkImportException(BulkImportError.ARCHIVE_EXPANDS_TOO_LARGE, entry.name)
        }
        val output = ByteArrayOutputStream(entry.size.toInt().coerceAtLeast(32))
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > limit) throw BulkImportException(BulkImportError.ARCHIVE_EXPANDS_TOO_LARGE, entry.name)
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun documentMetadata(context: Context, uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                }
            }
        }
        val safeName = name?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "content-batch.zip"
        return safeName.take(255) to size
    }

    private fun requireFreeSpace(directory: File, wanted: Long) {
        val available = runCatching { StatFs(directory.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < wanted) {
            throw BulkImportException(BulkImportError.NOT_ENOUGH_SPACE, "$available/$wanted")
        }
    }

    private fun pruneStaleCache(directory: File) {
        val cutoff = System.currentTimeMillis() - BULK_IMPORT_CACHE_TTL_MS
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.deleteRecursively()
        }
    }

    private fun validateArchivePath(rawName: String): String {
        if (rawName.isBlank() || rawName.length > 512 || '\u0000' in rawName || '\\' in rawName ||
            rawName.startsWith('/') || DRIVE_PREFIX.matches(rawName)
        ) {
            throw BulkImportException(BulkImportError.ARCHIVE_UNSAFE_PATH, rawName.take(120))
        }
        val parts = rawName.split('/')
        if (parts.withIndex().any { (index, part) ->
                part == "." || part == ".." || (part.isBlank() && index != parts.lastIndex)
            }
        ) {
            throw BulkImportException(BulkImportError.ARCHIVE_UNSAFE_PATH, rawName.take(120))
        }
        return rawName.trimEnd('/')
    }

    private fun isNoiseEntry(name: String): Boolean {
        val parts = name.split('/')
        val fileName = parts.last().lowercase(Locale.ROOT)
        return parts.any { it.equals("__MACOSX", true) } || fileName == ".ds_store" || fileName.startsWith("._")
    }

    private fun remoteKey(folder: String): String {
        val base = folder.substringAfterLast('/').lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "item" }
        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(folder.toByteArray(Charsets.UTF_8)).toHex().take(10)
        return "$base-$suffix"
    }

    private fun safeRemoteFileName(original: String, fallback: String): String {
        val extension = original.substringAfterLast('.', "bin").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "").take(12).ifBlank { "bin" }
        val base = original.substringBeforeLast('.', fallback).lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifBlank { fallback }
        return "$base.$extension"
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right < 0L || left > Long.MAX_VALUE - right) {
            throw BulkImportException(BulkImportError.ARCHIVE_EXPANDS_TOO_LARGE)
        }
        return left + right
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }

    private companion object {
        val ADDON_EXTENSIONS = setOf("mcaddon", "mcpack", "zip")
        val WORLD_EXTENSIONS = setOf("mcworld", "zip")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val METADATA_NAMES = setOf("metadata.json", "info.json")
        val COVER_STEMS = setOf("cover", "thumbnail", "preview", "icon")
        val VALID_CATEGORIES = setOf("adventure", "survival", "pvp", "building", "skins", "other")
        val VALID_PLANS = setOf("free", "epic", "legendary")
        val DRIVE_PREFIX = Regex("^[A-Za-z]:.*")
    }
}
