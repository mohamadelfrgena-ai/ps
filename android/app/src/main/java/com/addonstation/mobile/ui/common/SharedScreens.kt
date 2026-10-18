package com.addonstation.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.ui.components.ContentColumn
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import java.util.Locale

/** شاشة عامة لقائمة محتوى (مفضلتي / تحميلاتي / الحصريات / محتوى مبدع…). */
@Composable
fun ContentListScreen(
    title: String,
    emptyText: String,
    loader: suspend () -> List<ContentItem>,
    onBack: () -> Unit,
    onOpen: (ContentItem) -> Unit,
) {
    val container = rememberContainer()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }

    LaunchedEffect(title) {
        loading = true
        items = runCatching { loader() }.getOrElse { emptyList() }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(title = title, onLogoClick = onBack, showWallet = false)
        ContentColumn(Modifier.weight(1f)) {
            when {
                loading -> LoadingBox(text = stringResource(R.string.common_loading))
                items.isEmpty() -> EmptyState(emptyText)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        ContentCard(
                            title = item.title,
                            coverUrl = container.siteAssetUrl(item.coverUrl),
                            planLabel = planLabelOf(item.plan),
                            metaLine = metaOf(item),
                            onClick = { onOpen(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun planLabelOf(plan: String): String = when (plan) {
    "legendary" -> stringResource(R.string.plan_legendary_name)
    "epic" -> stringResource(R.string.plan_epic_name)
    else -> stringResource(R.string.plan_free_name)
}

/**
 * Compact legacy metadata for surfaces that have not adopted the visual stat row.
 * Emoji are deliberately excluded; Store and item screens use the supplied
 * vector stat icons so their appearance is identical on every phone.
 */
fun metaOf(item: ContentItem): String =
    "${item.downloadsCount} · ${item.viewsCount}" + if (item.rating > 0) " · %.1f".format(Locale.US, item.rating) else ""

/** عارض صورة بملء الشاشة مع تكبير/تحريك وزر إغلاق. */
@Composable
fun FullScreenImageViewer(url: String, onClose: () -> Unit) {
    // الرجوع (إيماءة النظام أو زرّ الرجوع) يُغلق العارض أولاً — لا الشاشة التي تحته
    androidx.activity.compose.BackHandler(onBack = onClose)
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Elevated2)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = AppColors.Text)
        }
    }
}

@Composable
fun InfoDialog(title: String, text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.Text,
        textContentColor = AppColors.Muted,
        title = { Text(title, style = AppText.sectionTitle) },
        // النصوص الطويلة (سياسة الخصوصية · شروط وأحكام الاستخدام) تُمرَّر داخل النافذة
        // فتبقى كل الفقرات مقروءة ولا يخرج زر «حسناً» خارج الشاشة.
        text = {
            Column(
                modifier = Modifier
                    .heightIn(
                        max = com.addonstation.mobile.ui.theme.screenHeightFraction(
                            fraction = 0.62f, minDp = 280, maxDp = 520,
                        ),
                    )
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text, style = AppText.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.common_ok), color = AppColors.Green)
            }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** لون زر التأكيد — أحمر افتراضياً (إجراء حسّاس)، وأخضر للمنح. */
    confirmColor: Color = AppColors.Red,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.Text,
        textContentColor = AppColors.Muted,
        title = { Text(title, style = AppText.sectionTitle) },
        text = { Text(text, style = AppText.bodySmall) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = confirmColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = AppColors.Muted) }
        },
    )
}

/**
 * شاشة الإعداد الأول: تُعرض فقط إذا لم تُضبط مفاتيح Supabase في gradle.properties.
 * تُخزَّن القيم محلياً (المفتاح العام anon فقط — لا مفاتيح خدمة).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val container = rememberContainer()
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errMsg = stringResource(R.string.setup_error)
    val secretWarn = stringResource(R.string.setup_secret_warning)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.setup_title), style = AppText.pageTitle, color = AppColors.Text)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.setup_text), style = AppText.bodySmall, color = AppColors.Muted)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            label = { Text(stringResource(R.string.setup_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            label = { Text(stringResource(R.string.setup_anon)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.setup_hint), style = AppText.tiny, color = AppColors.Dim)
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, style = AppText.bodySmall, color = AppColors.Red)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GradientButton(
                text = stringResource(R.string.setup_connect),
                onClick = {
                    val cleanUrl = url.trim()
                    val cleanKey = key.trim()
                    if (cleanKey.startsWith("sb_secret_") || cleanKey.startsWith("service_role")) {
                        // حماية: منع لصق المفتاح السري داخل التطبيق
                        error = secretWarn
                    } else if (!cleanUrl.startsWith("http") || cleanKey.length < 20) {
                        error = errMsg
                    } else {
                        container.saveConfig(cleanUrl, cleanKey)
                        onDone()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDone,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
