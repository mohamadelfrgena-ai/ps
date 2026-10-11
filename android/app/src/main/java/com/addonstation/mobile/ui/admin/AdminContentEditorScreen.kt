package com.addonstation.mobile.ui.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.ConfirmDialog
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.util.prepareImageForUpload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Admin-only form backed by the existing admin_upsert_content/storage RPCs. */
@Composable
fun AdminContentEditorScreen(
    itemId: String? = null,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = itemId != null
    val titleRequiredText = stringResource(R.string.admin_content_title_required)
    val fileRequiredText = stringResource(R.string.admin_content_file_required)
    val notFoundText = stringResource(R.string.admin_content_not_found)
    val genericErrorText = stringResource(R.string.msg_generic_error)
    val noneText = stringResource(R.string.common_none)

    var currentItem by remember(itemId) { mutableStateOf<ContentItem?>(null) }
    var loading by remember(itemId) { mutableStateOf(itemId != null) }
    var loadFailed by remember(itemId) { mutableStateOf(false) }
    var reloadKey by remember(itemId) { mutableStateOf(0) }
    var title by remember(itemId) { mutableStateOf("") }
    var description by remember(itemId) { mutableStateOf("") }
    var contentType by remember(itemId) { mutableStateOf("addon") }
    var category by remember(itemId) { mutableStateOf("other") }
    var plan by remember(itemId) { mutableStateOf("free") }
    var version by remember(itemId) { mutableStateOf("") }
    var acPrice by remember(itemId) { mutableStateOf("0") }
    var coverUrl by remember(itemId) { mutableStateOf<String?>(null) }
    var filePath by remember(itemId) { mutableStateOf<String?>(null) }
    var fileSize by remember(itemId) { mutableStateOf<Long?>(null) }
    var published by remember(itemId) { mutableStateOf(false) }
    var featured by remember(itemId) { mutableStateOf(false) }
    var exclusive by remember(itemId) { mutableStateOf(false) }
    var coverUri by remember(itemId) { mutableStateOf<Uri?>(null) }
    var fileUri by remember(itemId) { mutableStateOf<Uri?>(null) }
    var coverName by remember(itemId) { mutableStateOf("") }
    var selectedFileName by remember(itemId) { mutableStateOf("") }
    var message by remember(itemId) { mutableStateOf<String?>(null) }
    var messageIsError by remember(itemId) { mutableStateOf(false) }
    var saving by remember(itemId) { mutableStateOf(false) }
    var confirmDelete by remember(itemId) { mutableStateOf(false) }

    LaunchedEffect(itemId, reloadKey) {
        if (itemId == null) {
            loading = false
            loadFailed = false
            return@LaunchedEffect
        }
        loading = true
        loadFailed = false
        val item = container.admin.contentById(itemId)
        if (item == null) {
            loadFailed = true
        } else {
            currentItem = item
            title = item.title
            description = item.description.orEmpty()
            contentType = item.contentType
            category = item.category ?: "other"
            plan = item.plan
            version = item.version.orEmpty()
            acPrice = item.acPrice.toString()
            coverUrl = item.coverUrl
            filePath = item.filePath
            fileSize = item.fileSize
            published = item.isPublished
            featured = item.isFeatured
            exclusive = item.isExclusive
            selectedFileName = item.filePath?.substringAfterLast('/')?.takeIf(String::isNotBlank).orEmpty()
            coverName = item.coverUrl?.substringAfterLast('/')?.takeIf(String::isNotBlank).orEmpty()
        }
        loading = false
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            coverUri = uri
            coverName = contentUriDisplayName(context, uri)
            message = null
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            fileUri = uri
            selectedFileName = contentUriDisplayName(context, uri)
            message = null
        }
    }

    fun saveContent() {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            message = titleRequiredText
            messageIsError = true
            return
        }
        if (published && filePath.isNullOrBlank() && fileUri == null) {
            message = fileRequiredText
            messageIsError = true
            return
        }
        if (isEditing && currentItem == null) {
            message = notFoundText
            messageIsError = true
            return
        }

        scope.launch {
            saving = true
            message = null
            messageIsError = false
            try {
                var nextFilePath = filePath
                var nextFileSize = fileSize
                var nextCoverUrl = coverUrl

                fileUri?.let { uri ->
                    val bytes = readContentUriBytes(context, uri)
                        ?: throw IllegalStateException(genericErrorText)
                    val uploadName = safeContentUploadName(selectedFileName.ifBlank { "content.bin" })
                    nextFilePath = container.admin.uploadContentFile(
                        bytes = bytes,
                        mime = context.contentResolver.getType(uri) ?: "application/octet-stream",
                        fileName = uploadName,
                        isCover = false,
                    ).getOrThrow()
                    nextFileSize = bytes.size.toLong()
                }

                coverUri?.let { uri ->
                    val prepared = prepareImageForUpload(context, uri)
                    val bytes = prepared?.bytes
                        ?: readContentUriBytes(context, uri)
                        ?: throw IllegalStateException(genericErrorText)
                    nextCoverUrl = container.admin.uploadContentFile(
                        bytes = bytes,
                        mime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/png",
                        fileName = safeContentUploadName(
                            prepared?.fileName ?: coverName.ifBlank { "content-cover.png" },
                        ),
                        isCover = true,
                    ).getOrThrow()
                }

                container.admin.upsertContent(
                    id = currentItem?.id,
                    title = cleanTitle,
                    description = description.trim().takeIf(String::isNotEmpty),
                    contentType = contentType,
                    category = category,
                    plan = plan,
                    version = version.trim().takeIf(String::isNotEmpty),
                    coverUrl = nextCoverUrl,
                    filePath = nextFilePath,
                    fileSize = nextFileSize,
                    acPrice = acPrice.toIntOrNull() ?: 0,
                    isPublished = published,
                    isFeatured = featured,
                    isExclusive = exclusive,
                ).onSuccess {
                    onBack()
                }.onFailure { failure ->
                    message = failure.message ?: genericErrorText
                    messageIsError = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                message = failure.message ?: genericErrorText
                messageIsError = true
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_content),
            title = stringResource(if (isEditing) R.string.common_edit else R.string.admin_add_content_plain),
            onBack = onBack,
        )
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingBox() }
            loadFailed -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(notFoundText)
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    text = stringResource(R.string.common_retry),
                    onClick = { reloadKey += 1 },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    label = { Text(stringResource(R.string.creator_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.creator_field_content_types), style = AppText.tiny, color = AppColors.Muted)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LocalHomePalette.current.surface)
                        .border(1.dp, LocalHomePalette.current.border, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AdminSegmentTab(
                        label = stringResource(R.string.store_tab_addons),
                        selected = contentType == "addon",
                        onClick = { contentType = "addon" },
                        modifier = Modifier.weight(1f),
                    )
                    AdminSegmentTab(
                        label = stringResource(R.string.store_tab_worlds),
                        selected = contentType == "world",
                        onClick = { contentType = "world" },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.admin_room_category), style = AppText.tiny, color = AppColors.Muted)
                listOf("adventure", "survival", "pvp", "building", "skins", "other")
                    .chunked(3)
                    .forEach { categoryRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categoryRow.forEach { value ->
                                AdminFilterChip(
                                    label = value,
                                    selected = category == value,
                                    onClick = { category = value },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(5_000) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    label = { Text(stringResource(R.string.admin_content_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it.take(40) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    label = { Text(stringResource(R.string.creator_field_version)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.admin_required_plan), style = AppText.tiny, color = AppColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "free" to R.string.plan_free_name,
                        "epic" to R.string.plan_epic_name,
                        "legendary" to R.string.plan_legendary_name,
                    ).forEach { (key, labelRes) ->
                        AdminFilterChip(
                            label = stringResource(labelRes),
                            selected = plan == key,
                            onClick = { plan = key },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = acPrice,
                    onValueChange = { acPrice = it.filter(Char::isDigit).take(8) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    label = { Text(stringResource(R.string.admin_ac_price_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UploadZone(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.admin_cover_label),
                        detail = coverName.ifBlank { noneText },
                        icon = Icons.Filled.Image,
                        onClick = { coverPicker.launch("image/*") },
                    )
                    UploadZone(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.admin_file_label),
                        detail = selectedFileName.ifBlank { noneText },
                        icon = Icons.Filled.InsertDriveFile,
                        onClick = { filePicker.launch("*/*") },
                    )
                }
                if (coverUri != null || !coverUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    com.addonstation.mobile.ui.components.CoverImage(
                        url = assetUrl(container, coverUrl),
                        model = coverUri,
                        radius = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        animate = false,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ContentToggle(
                    label = stringResource(R.string.admin_publish_content),
                    checked = published,
                    onCheckedChange = { published = it },
                )
                ContentToggle(
                    label = stringResource(R.string.admin_featured_content),
                    checked = featured,
                    onCheckedChange = { featured = it },
                )
                ContentToggle(
                    label = stringResource(R.string.admin_exclusive_content),
                    checked = exclusive,
                    onCheckedChange = { exclusive = it },
                )

                message?.let { text ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text,
                        style = AppText.bodySmall,
                        color = if (messageIsError) AppColors.Red else AppColors.Green,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (isEditing) {
                    GhostButton(
                        text = stringResource(R.string.admin_delete_content_label),
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        borderColor = AppColors.Red.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                GradientButton(
                    text = stringResource(if (saving) R.string.common_loading else R.string.admin_save),
                    onClick = { saveContent() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                )
                Spacer(Modifier.height(96.dp))
            }
        }
    }

    if (confirmDelete && currentItem != null) {
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_content_label),
            text = currentItem?.title.orEmpty(),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                confirmDelete = false
                val target = currentItem ?: return@ConfirmDialog
                scope.launch {
                    saving = true
                    container.admin.deleteContent(target.id)
                        .onSuccess { onBack() }
                        .onFailure { failure ->
                            message = failure.message ?: genericErrorText
                            messageIsError = true
                        }
                    saving = false
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun UploadZone(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(86.dp)
            .clip(shape)
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = AppColors.Blue,
            modifier = Modifier.size(18.dp),
        )
        Text(title, style = AppText.bodySmall, color = AppColors.Text, maxLines = 1)
        Text(detail, style = AppText.tiny, color = AppColors.Muted, maxLines = 1)
    }
}

@Composable
private fun ContentToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppText.bodySmall, color = AppColors.Text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private suspend fun readContentUriBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
}

private fun contentUriDisplayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf(String::isNotBlank)
    ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
    ?: "selected-file"

private fun safeContentUploadName(name: String): String = name
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .replace(Regex("[\\r\\n]"), "_")
    .takeLast(128)
    .ifBlank { "content-file" }
