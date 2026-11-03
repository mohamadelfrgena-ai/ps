package com.addonstation.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * بيانات فيديو مستخرجة من الجهاز قبل رفعه: **إطار مصغّر** + المدة.
 *
 * لماذا؟ بطاقة الفيديو في الشات كانت تظهر كصندوق رمادي فيه 🎬 فقط — لا صورة
 * ولا مدّة (الطلب: «الفيديو خليه يطلع … مثل التلجرام»). الآن نستخرج إطاراً
 * من الفيديو على الجهاز ونعرضه كصورة مصغّرة، ونقرأ المدّة الحقيقية.
 */
data class VideoMeta(
    /** ملف الصورة المصغّرة في ذاكرة التطبيق (يُحمّل محلياً فوراً). */
    val thumbPath: String,
    /** المدة بالثواني (0 إن تعذّر قراءتها). */
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
)

/**
 * يستخرج إطاراً (بعد ثانية من البداية) ومدّة الفيديو من [uri]، ويحفظ الإطار
 * كملف JPEG في ذاكرة التطبيق ليُحمّل بـ Coil بلا شبكة.
 *
 * يُعيد null إن لم يكن الملف فيديو قابلاً للفك — عندها يُرسل الفيديو بلا
 * صورة مصغّرة (تبقى بطاقته تعمل) ولا يفشل الإرسال.
 */
suspend fun extractVideoMeta(
    context: Context,
    uri: Uri,
    maxSide: Int = 720,
    jpegQuality: Int = 82,
): VideoMeta? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    runCatching {
        retriever.setDataSource(context, uri)
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return@runCatching null
        val scaled = if (maxOf(frame.width, frame.height) > maxSide) {
            val ratio = maxSide.toFloat() / maxOf(frame.width, frame.height).toFloat()
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * ratio).toInt().coerceAtLeast(1),
                (frame.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            frame
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        if (scaled !== frame) scaled.recycle()
        frame.recycle()
        val file = File(context.cacheDir, "chat-video-thumb-${System.currentTimeMillis()}.jpg")
        file.writeBytes(out.toByteArray())
        VideoMeta(
            thumbPath = file.absolutePath,
            durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(0),
            width = frame.width,
            height = frame.height,
        )
    }.getOrNull().also {
        runCatching { retriever.release() }
    }
}

/** يقرأ ملفاً من المعرض إلى بايتات مع سقف حجم يمنع رفع ملف ضخم بالخطأ. */
suspend fun readBytes(context: Context, uri: Uri, maxBytes: Long = Long.MAX_VALUE): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return@runCatching null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

/** يحفظ نسخة محلية من صورة/إطار ليُعرض فوراً في مربع الإرسال وفي الرسالة. */
suspend fun cachePreviewFile(context: Context, bytes: ByteArray, extension: String = "jpg"): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "chat-preview-${System.currentTimeMillis()}.$extension")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }
