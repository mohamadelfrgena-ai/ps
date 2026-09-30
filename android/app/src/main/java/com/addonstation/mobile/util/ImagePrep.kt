package com.addonstation.mobile.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** صورة جاهزة للرفع: بايتات + نوع MIME صحيح + اسم ملف بامتداد مطابق. */
data class PreparedImage(val bytes: ByteArray, val mime: String, val fileName: String)

/**
 * يقرأ الصورة المختارة من الجهاز، يصغّرها ويعيد ترميزها إلى JPEG/PNG مدعوم.
 *
 * يعالج ثلاث مشاكل كانت تمنع ظهور الصورة بعد اختيارها:
 *  1) صور الكاميرا (3–12 MB) أكبر من حد المخزن (site-assets = 5 MB) فيرفضها الرفع.
 *  2) صيغ غير مدرجة في المخزن (HEIC/HEIF من آيفون وأندرويد الحديث) فيرفضها الرفع.
 *  3) أسماء ملفات بلا امتداد صحيح أو بنوع MIME = null.
 *
 * يعيد null إذا لم يكن الملف صورة قابلة للفك (مثل PDF) — عندها يرفع المستدعي الملف كما هو.
 */
suspend fun prepareImageForUpload(
    context: Context,
    uri: Uri,
    maxSide: Int = 1280,
    jpegQuality: Int = 88,
): PreparedImage? = withContext(Dispatchers.IO) {
    runCatching {
        val originalName = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "image"

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return@runCatching null

        val scaled = scaleDown(decoded, maxSide)
        val hasAlpha = scaled.hasAlpha()
        val out = ByteArrayOutputStream()
        val mime: String
        val ext: String
        if (hasAlpha) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            mime = "image/png"
            ext = "png"
        } else {
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            mime = "image/jpeg"
            ext = "jpg"
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        val base = originalName.substringBeforeLast('.').ifBlank { "image" }
        PreparedImage(out.toByteArray(), mime, "$base-${System.currentTimeMillis()}.$ext")
    }.getOrNull()
}

/** اسم الملف الأصلي كما يراه المستخدم في معرض الصور. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/** أقرب مضاعف لقوى العدد 2 يقلّل الأبعاد إلى الحد المطلوب (يوفّر الذاكرة قبل الفك). */
private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    var w = width
    var h = height
    while (w / 2 >= maxSide && h / 2 >= maxSide) {
        w /= 2
        h /= 2
        sample *= 2
    }
    return sample
}

/** تصغير دقيق (اختياري) بعد الفك إن بقيت الأبعاد أكبر من الحد. */
private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
    val longest = maxOf(bitmap.width, bitmap.height)
    if (longest <= maxSide) return bitmap
    val ratio = maxSide.toFloat() / longest.toFloat()
    val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}
