@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.addonstation.mobile.ui.chat

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import java.io.File

/**
 * مشغّل فيديو بملء الشاشة (ExoPlayer/Media3) — يُفتح بضغطة على بطاقة الفيديو
 * في الشات، كما تُفتح الصورة بكامل حجمها.
 *
 * الرابط **موقّع** (صالح دقائق) من مخزن chat-media الخاص، فيُمرَّر كما هو.
 */
@Composable
fun FullScreenVideoPlayer(url: String, onClose: () -> Unit) {
    // الرجوع يُغلق العارض أولاً (إيماءة أو زرّ) بدل مغادرة الغرفة
    androidx.activity.compose.BackHandler(onBack = onClose)
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) {
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
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
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = AppColors.Text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * بطاقة فيديو داخل الرسالة (بأسلوب تليجرام): صورة مصغّرة مأخوذة من الفيديو +
 * زر تشغيل دائري في الوسط + المدّة في الزاوية.
 *
 * الضغطة تشغّل الفيديو، والضغطة المطوّلة تفتح قائمة الرسالة (رد · نسخ ·
 * إبلاغ · حذف) كما في بقية الأنواع.
 */
@Composable
fun VideoTile(
    /** رابط الصورة المصغّرة الموقّع (null إن لم تُرفع أو فشل توقيعه). */
    thumbUrl: String?,
    /** ملف الصورة المصغّرة على الجهاز (قبل الرفع) — يُعرض فوراً بلا شبكة. */
    localPreview: String? = null,
    durationSeconds: Int? = null,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        CoverImage(
            url = thumbUrl,
            model = localPreview?.let { File(it) },
            radius = 14.dp,
            modifier = Modifier.fillMaxSize(),
        )
        // تعتيم خفيف يجعل زر التشغيل والمدّة مقروءين فوق أي صورة
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x33000000)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.chat_play_video),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        if (durationSeconds != null && durationSeconds > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = shortDurationLabel(durationSeconds),
                    style = AppText.tiny,
                    color = Color.White,
                )
            }
        }
    }
}

/** 95 ⇒ "1:35" — تسمية مدّة قصيرة تظهر على بطاقة الفيديو. */
private fun shortDurationLabel(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
