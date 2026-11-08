package com.addonstation.mobile.ui.admin

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.repo.BulkArchiveInspection
import com.addonstation.mobile.data.repo.BulkImportError
import com.addonstation.mobile.data.repo.BulkImportException
import com.addonstation.mobile.data.repo.BulkImportOptions
import com.addonstation.mobile.data.repo.BulkImportProgress
import com.addonstation.mobile.data.repo.BulkImportResult
import com.addonstation.mobile.data.repo.BulkImportStage
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.LocalHomePalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Admin-only streaming importer: ZIP folder → cover + addon/world → atomic batch. */
@Composable
fun AdminBulkContentImportScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalHomePalette.current

    var contentType by remember { mutableStateOf("addon") }
    var category by remember { mutableStateOf("other") }
    var plan by remember { mutableStateOf("free") }
    var acPrice by remember { mutableStateOf("0") }
    var publish by remember { mutableStateOf(false) }
    var inspection by remember { mutableStateOf<BulkArchiveInspection?>(null) }
    var result by remember { mutableStateOf<BulkImportResult?>(null) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var working by remember { mutableStateOf(false) }
    var workJob by remember { mutableStateOf<Job?>(null) }
    val progressFlow = remember { MutableStateFlow<BulkImportProgress?>(null) }
    val progress by progressFlow.collectAsStateWithLifecycle()
    val registrationUncertain = (failure as? BulkImportException)?.reason == BulkImportError.REGISTRATION_UNCERTAIN

    val latestInspection by rememberUpdatedState(inspection)
    val latestWorking by rememberUpdatedState(working)
    DisposableEffect(Unit) {
        onDispose {
            if (!latestWorking) latestInspection?.cleanup()
        }
    }

    fun resetArchive() {
        if (working) return
        inspection?.cleanup()
        inspection = null
        result = null
        failure = null
        progressFlow.value = null
    }

    fun inspect(uri: android.net.Uri) {
        if (working) return
        resetArchive()
        working = true
        workJob = scope.launch {
            try {
                container.admin.inspectBulkContentArchive(
                    context = context,
                    uri = uri,
                    contentType = contentType,
                    onProgress = { progressFlow.value = it },
                ).onSuccess {
                    inspection = it
                    failure = null
                }.onFailure {
                    failure = it
                }
            } catch (cancelled: CancellationException) {
                progressFlow.value = null
            } finally {
                working = false
                workJob = null
            }
        }
    }

    fun startImport() {
        val ready = inspection ?: return
        if (working) return
        working = true
        result = null
        failure = null
        workJob = scope.launch {
            try {
                container.admin.importBulkContentArchive(
                    inspection = ready,
                    options = BulkImportOptions(
                        category = category,
                        plan = plan,
                        acPrice = acPrice.toIntOrNull() ?: 0,
                        publish = publish,
                    ),
                    onProgress = { progressFlow.value = it },
                ).onSuccess {
                    result = it
                }.onFailure {
                    failure = it
                }
            } catch (cancelled: CancellationException) {
                progressFlow.value = null
            } finally {
                working = false
                workJob = null
            }
        }
    }

    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspect(uri)
    }

    BackHandler(enabled = working) {
        if (progress?.stage != BulkImportStage.REGISTERING_ITEMS) workJob?.cancel()
    }

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_content),
            title = stringResource(R.string.admin_bulk_import_title),
            onBack = {
                if (!working) {
                    inspection?.cleanup()
                    onBack()
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BulkInfoCard()

            Text(stringResource(R.string.creator_field_content_types), style = AppText.tiny, color = palette.textMuted)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AdminSegmentTab(
                    label = stringResource(R.string.store_tab_addons),
                    selected = contentType == "addon",
                    onClick = {
                        if (contentType != "addon") {
                            resetArchive()
                            contentType = "addon"
                        }
                    },
                    enabled = !working && !registrationUncertain,
                    modifier = Modifier.weight(1f),
                )
                AdminSegmentTab(
                    label = stringResource(R.string.store_tab_worlds),
                    selected = contentType == "world",
                    onClick = {
                        if (contentType != "world") {
                            resetArchive()
                            contentType = "world"
                        }
                    },
                    enabled = !working && !registrationUncertain,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(stringResource(R.string.admin_room_category), style = AppText.tiny, color = palette.textMuted)
            listOf(
                "adventure" to R.string.creator_category_adventure,
                "survival" to R.string.creator_category_survival,
                "pvp" to R.string.creator_category_pvp,
                "building" to R.string.creator_category_building,
                "skins" to R.string.creator_category_skins,
                "other" to R.string.creator_category_other,
            ).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (value, label) ->
                        AdminFilterChip(
                            label = stringResource(label),
                            selected = category == value,
                            onClick = { category = value },
                            enabled = !working && !registrationUncertain,
                        )
                    }
                }
            }

            Text(stringResource(R.string.admin_required_plan), style = AppText.tiny, color = palette.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "free" to R.string.plan_free_name,
                    "epic" to R.string.plan_epic_name,
                    "legendary" to R.string.plan_legendary_name,
                ).forEach { (key, label) ->
                    AdminFilterChip(
                        label = stringResource(label),
                        selected = plan == key,
                        onClick = { plan = key },
                        enabled = !working && !registrationUncertain,
                    )
                }
            }

            OutlinedTextField(
                value = acPrice,
                onValueChange = { if (!working && !registrationUncertain) acPrice = it.filter(Char::isDigit).take(8) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                label = { Text(stringResource(R.string.admin_ac_price_label)) },
                singleLine = true,
                enabled = !working && !registrationUncertain,
                modifier = Modifier.fillMaxWidth(),
            )

            BulkPublishToggle(
                checked = publish,
                enabled = !working && !registrationUncertain,
                onCheckedChange = { publish = it },
            )

            BulkArchivePicker(
                archiveName = inspection?.archiveName,
                enabled = !working && !registrationUncertain,
                onClick = {
                    archivePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
            )

            progress?.let { BulkProgressCard(it, workJob, working) }
            inspection?.let {
                BulkInspectionCard(
                    inspection = it,
                    progress = progress,
                    failure = failure,
                    imported = result != null,
                    working = working,
                )
            }
            failure?.let { BulkFailureCard(it) }
            result?.let { BulkSuccessCard(it) }

            if (inspection != null && result == null) {
                AdminPrimaryButton(
                    text = stringResource(
                        when {
                            working -> R.string.admin_bulk_import_working
                            registrationUncertain -> R.string.admin_bulk_retry_registration
                            else -> R.string.admin_bulk_import_action
                        }
                    ),
                    onClick = ::startImport,
                    palette = palette,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                )
            }
            if (working && progress?.stage != BulkImportStage.REGISTERING_ITEMS) {
                AdminOutlineButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { workJob?.cancel() },
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                )
            }
            if (result != null) {
                AdminPrimaryButton(
                    text = stringResource(R.string.admin_bulk_done),
                    onClick = onBack,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    height = 48.dp,
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun BulkInfoCard() {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(palette.surface)
            .border(1.dp, palette.border, shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(palette.blue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Archive, contentDescription = null, tint = palette.blue, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.admin_bulk_import_subtitle), style = AppText.bodySmall, color = palette.text)
            Text(stringResource(R.string.admin_bulk_import_structure), style = AppText.tiny, color = palette.textMuted)
            Text(stringResource(R.string.admin_bulk_import_limits), style = AppText.tiny, color = palette.textMuted)
        }
    }
}

@Composable
private fun BulkPublishToggle(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(palette.surface).border(1.dp, palette.border, shape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.admin_bulk_publish_title), style = AppText.bodySmall, color = palette.text)
            Text(stringResource(R.string.admin_bulk_publish_note), style = AppText.tiny, color = palette.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun BulkArchivePicker(archiveName: String?, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(palette.surface)
            .border(1.dp, palette.border, shape).clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            if (archiveName == null) Icons.Filled.UploadFile else Icons.Filled.FolderZip,
            contentDescription = null,
            tint = palette.blue,
            modifier = Modifier.size(30.dp),
        )
        Text(
            archiveName ?: stringResource(R.string.admin_bulk_choose_zip),
            style = AppText.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(stringResource(R.string.admin_bulk_choose_zip_note), style = AppText.tiny, color = palette.textMuted)
    }
}

@Composable
private fun BulkProgressCard(progress: BulkImportProgress, job: Job?, working: Boolean) {
    if (!working && progress.stage != BulkImportStage.COMPLETE) return
    val context = LocalContext.current
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(palette.surface)
            .border(1.dp, palette.border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bulkStageText(progress.stage), style = AppText.bodySmall, color = palette.text, modifier = Modifier.weight(1f))
            progress.fraction?.let {
                Text("${(it * 100f).toInt().coerceIn(0, 100)}%", style = AppText.tiny, color = palette.blue)
            }
        }
        if (progress.fraction != null) {
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = palette.blue,
                trackColor = palette.border,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = palette.blue,
                trackColor = palette.border,
            )
        }
        progress.itemTitle?.let { title ->
            Text(
                stringResource(R.string.admin_bulk_current_item, progress.itemIndex, progress.itemCount, title),
                style = AppText.tiny,
                color = palette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (progress.bytesTotal > 0L) {
            Text(
                stringResource(
                    R.string.admin_bulk_bytes_progress,
                    Formatter.formatShortFileSize(context, progress.bytesDone),
                    Formatter.formatShortFileSize(context, progress.bytesTotal),
                ),
                style = AppText.tiny,
                color = palette.textMuted,
            )
        }
        if (job?.isCancelled == true) {
            Text(stringResource(R.string.common_cancel), style = AppText.tiny, color = AppColors.Red)
        }
    }
}

@Composable
private fun BulkInspectionCard(
    inspection: BulkArchiveInspection,
    progress: BulkImportProgress?,
    failure: Throwable?,
    imported: Boolean,
    working: Boolean,
) {
    val context = LocalContext.current
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    val uncertain = (failure as? BulkImportException)?.reason == BulkImportError.REGISTRATION_UNCERTAIN
    Column(
        Modifier.fillMaxWidth().clip(shape).background(palette.surface)
            .border(1.dp, AppColors.Green.copy(alpha = 0.45f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Green, modifier = Modifier.size(19.dp))
            Text(stringResource(R.string.admin_bulk_ready, inspection.items.size), style = AppText.bodySmall, color = palette.text)
        }
        Text(
            stringResource(
                R.string.admin_bulk_archive_summary,
                Formatter.formatShortFileSize(context, inspection.archiveSize),
                Formatter.formatShortFileSize(context, inspection.totalContentBytes),
                inspection.ignoredFiles,
            ),
            style = AppText.tiny,
            color = palette.textMuted,
        )
        Text(
            stringResource(R.string.admin_bulk_hash, inspection.archiveSha256.take(16)),
            style = AppText.tiny,
            color = palette.textMuted,
        )
        inspection.items.forEachIndexed { index, item ->
            val position = index + 1
            val status = when {
                imported -> BulkItemUiStatus.IMPORTED
                failure != null && !uncertain -> BulkItemUiStatus.FAILED
                progress?.stage == BulkImportStage.REGISTERING_ITEMS || uncertain -> BulkItemUiStatus.UPLOADED
                working && progress != null && progress.itemIndex > 0 && position < progress.itemIndex -> BulkItemUiStatus.UPLOADED
                working && progress != null && progress.itemIndex == position -> BulkItemUiStatus.PROCESSING
                working -> BulkItemUiStatus.WAITING
                else -> BulkItemUiStatus.READY
            }
            val statusColor = when (status) {
                BulkItemUiStatus.IMPORTED -> AppColors.Green
                BulkItemUiStatus.FAILED -> AppColors.Red
                BulkItemUiStatus.PROCESSING, BulkItemUiStatus.UPLOADED -> palette.blue
                BulkItemUiStatus.READY, BulkItemUiStatus.WAITING -> palette.textMuted
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.surfaceHigh).padding(9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$position.", style = AppText.tiny, color = palette.blue)
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = AppText.tiny.copy(fontWeight = FontWeight.Bold), color = palette.text, maxLines = 1)
                    Text(
                        "${item.contentFileName} · ${Formatter.formatShortFileSize(context, item.contentSize)}",
                        style = AppText.tiny.copy(fontSize = 10.sp),
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    bulkItemStatusText(status),
                    style = AppText.tiny.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = statusColor,
                )
            }
        }
    }
}

private enum class BulkItemUiStatus { READY, WAITING, PROCESSING, UPLOADED, FAILED, IMPORTED }

@Composable
private fun bulkItemStatusText(status: BulkItemUiStatus): String = stringResource(
    when (status) {
        BulkItemUiStatus.READY -> R.string.admin_bulk_item_ready
        BulkItemUiStatus.WAITING -> R.string.admin_bulk_item_waiting
        BulkItemUiStatus.PROCESSING -> R.string.admin_bulk_item_processing
        BulkItemUiStatus.UPLOADED -> R.string.admin_bulk_item_uploaded
        BulkItemUiStatus.FAILED -> R.string.admin_bulk_item_failed
        BulkItemUiStatus.IMPORTED -> R.string.admin_bulk_item_imported
    }
)

@Composable
private fun BulkFailureCard(failure: Throwable) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Red.copy(alpha = 0.08f))
            .border(1.dp, AppColors.Red.copy(alpha = 0.5f), shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = AppColors.Red, modifier = Modifier.size(20.dp))
        Text(bulkFailureText(failure), style = AppText.bodySmall, color = palette.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BulkSuccessCard(result: BulkImportResult) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.Green.copy(alpha = 0.1f))
            .border(1.dp, AppColors.Green.copy(alpha = 0.5f), shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Green, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.admin_bulk_success, result.importedCount), style = AppText.bodySmall, color = palette.text)
            Text(stringResource(R.string.admin_bulk_batch_id, result.batchId.take(12)), style = AppText.tiny, color = palette.textMuted)
        }
    }
}

@Composable
private fun bulkStageText(stage: BulkImportStage): String = stringResource(
    when (stage) {
        BulkImportStage.COPYING_ARCHIVE -> R.string.admin_bulk_stage_copying
        BulkImportStage.SCANNING_ARCHIVE -> R.string.admin_bulk_stage_scanning
        BulkImportStage.EXTRACTING_ITEM -> R.string.admin_bulk_stage_extracting
        BulkImportStage.UPLOADING_COVER -> R.string.admin_bulk_stage_cover
        BulkImportStage.UPLOADING_CONTENT -> R.string.admin_bulk_stage_content
        BulkImportStage.REGISTERING_ITEMS -> R.string.admin_bulk_stage_registering
        BulkImportStage.CLEANING_UP -> R.string.admin_bulk_stage_cleaning
        BulkImportStage.COMPLETE -> R.string.admin_bulk_stage_complete
    }
)

@Composable
private fun bulkFailureText(failure: Throwable): String {
    val bulk = failure as? BulkImportException
    val detail = bulk?.detail?.take(140).orEmpty()
    return when (bulk?.reason) {
        BulkImportError.ARCHIVE_NOT_ZIP -> stringResource(R.string.admin_bulk_error_not_zip)
        BulkImportError.ARCHIVE_TOO_LARGE -> stringResource(R.string.admin_bulk_error_too_large)
        BulkImportError.NOT_ENOUGH_SPACE -> stringResource(R.string.admin_bulk_error_space)
        BulkImportError.NO_ITEMS -> stringResource(R.string.admin_bulk_error_no_items)
        BulkImportError.TOO_MANY_ITEMS -> stringResource(R.string.admin_bulk_error_too_many_items)
        BulkImportError.WRONG_CONTENT_TYPE -> stringResource(R.string.admin_bulk_error_wrong_type, detail)
        BulkImportError.CONTENT_MISSING,
        BulkImportError.MULTIPLE_CONTENT_FILES,
        BulkImportError.COVER_MISSING,
        BulkImportError.MULTIPLE_COVERS -> stringResource(R.string.admin_bulk_error_folder, detail)
        BulkImportError.COVER_TOO_LARGE -> stringResource(R.string.admin_bulk_error_cover_size, detail)
        BulkImportError.CONTENT_TOO_LARGE -> stringResource(R.string.admin_bulk_error_content_size, detail)
        BulkImportError.METADATA_INVALID -> stringResource(R.string.admin_bulk_error_metadata, detail)
        BulkImportError.IMAGE_INVALID -> stringResource(R.string.admin_bulk_error_image, detail)
        BulkImportError.CONTENT_ARCHIVE_INVALID -> stringResource(R.string.admin_bulk_error_content_archive, detail)
        BulkImportError.ARCHIVE_UNSAFE_PATH -> stringResource(R.string.admin_bulk_error_unsafe)
        BulkImportError.ARCHIVE_TOO_MANY_ENTRIES,
        BulkImportError.ARCHIVE_EXPANDS_TOO_LARGE,
        BulkImportError.ARCHIVE_SUSPICIOUS_COMPRESSION -> stringResource(R.string.admin_bulk_error_zip_bomb)
        BulkImportError.REGISTRATION_UNCERTAIN -> stringResource(R.string.admin_bulk_error_uncertain)
        BulkImportError.SERVER_REJECTED -> stringResource(R.string.admin_bulk_error_server, detail)
        BulkImportError.ARCHIVE_EMPTY,
        BulkImportError.ARCHIVE_CORRUPT,
        BulkImportError.CACHE_FILE_MISSING -> stringResource(R.string.admin_bulk_error_corrupt)
        null -> failure.message?.take(180) ?: stringResource(R.string.msg_generic_error)
    }
}
