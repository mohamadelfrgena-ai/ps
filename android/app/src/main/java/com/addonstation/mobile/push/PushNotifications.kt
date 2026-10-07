package com.addonstation.mobile.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.addonstation.mobile.MainActivity
import com.addonstation.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * الإشعارات الفورية — الجانب الظاهر للمستخدم.
 *
 * المبدأ: **خمس قنوات مستقلّة** (وهذا ما يجعل الإشعارات «مرتّبة» لا مزعجة):
 *   • رسائل الشات   as_chat      — أهمية عالية (صوت + اهتزاز): محادثة جارية
 *   • محتوى جديد    as_content   — أهمية عادية: إضافة/عالم نزل
 *   • اجتماعي       as_social    — أهمية عادية: طلب صداقة أو إشارة
 *   • الحساب        as_account   — أهمية منخفضة بلا صوت: الاشتراك والدفع
 *   • تذكير         as_reminders — أهمية منخفضة بلا صوت: «اشتقنا لك»
 *
 * فائدة القنوات (أندرويد 8+): يستطيع المستخدم **كتم قسم واحد** من إعدادات
 * النظام بلا أن يفقد البقية، ونحن نحترم ذلك تلقائياً لأن كل إشعار يُوجَّه
 * إلى قناته. وأسماء القنوات تأتي من نصوص التطبيق ⇒ تظهر بلغة المستخدم.
 *
 * أيقونة الشريط `ic_push_notification` ملف متجهيّ أبيض (أندرويد يرسم أيقونة
 * الإشعار كظلّ أحاديّ اللون — أي صورة ملوّنة تظهر مربّعاً أبيض).
 *
 * ملاحظة مهمة: الإشعار يبنيه **التطبيق** لا الخادم (رسائل data-only)، فيظهر
 * النص بلغة الجهاز ويُفتح على الشاشة الصحيحة (route) عند الضغط.
 *
 * الصور: إن وصل مع الإشعار حقل `image`:
 *   • image_kind = cover  ⇒ **صورة كبيرة** داخل الإشعار (غلاف الإضافة/العالم).
 *   • image_kind = avatar ⇒ **أيكونة دائرية** بجانب النص (صورة المرسل في الشات).
 * والتنزيل في الخلفية بمهلة قصيرة: إن تأخّر الاتصال يظهر الإشعار نصّاً
 * عادياً بلا صورة (لا نُفقد الإشعار أبداً بسبب صورة).
 */
object PushNotifications {

    /** نطاق التنزيل في الخلفية (خدمة FCM قصيرة العمر ⇒ لا نُطيل عملها). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** أقصى ما نُنزّله للصورة الواحدة (حماية من غلاف ضخم). */
    private const val MAX_IMAGE_BYTES = 900_000

    const val CHAT = "as_chat"
    const val CONTENT = "as_content"
    const val SOCIAL = "as_social"
    const val ACCOUNT = "as_account"
    const val REMINDERS = "as_reminders"

    /** إضافة الإشعار إلى الشاشة الصحيحة عند الضغط. */
    const val EXTRA_ROUTE = "push_route"

    /** هل ملف google-services.json موجود فعلاً (أي أن FCM مُهيّأة في هذا البناء)؟ */
    fun configured(context: Context): Boolean =
        context.resources.getIdentifier("google_app_id", "string", context.packageName) != 0

    /** هل منح المستخدم إذن الإشعارات؟ (أندرويد 13+ يطلبه صراحةً) */
    fun permissionGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * إنشاء القنوات الخمس. تُنادى عند بدء التطبيق (رخيصة وآمنة التكرار:
     * إعادة التسمية بنفس المعرّف لا تُلغي تفضيلات المستخدم في النظام).
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        fun channel(id: String, nameRes: Int, descRes: Int, importance: Int, sound: Boolean, vibrate: Boolean) {
            val ch = NotificationChannel(id, context.getString(nameRes), importance).apply {
                description = context.getString(descRes)
                enableVibration(vibrate)
                if (!sound) setSound(null, null)
                // Chat: بلا شارة تفاعل ثقيل — النص هو المهم
            }
            manager.createNotificationChannel(ch)
        }

        channel(
            CHAT, R.string.notif_ch_chat, R.string.notif_ch_chat_desc,
            NotificationManager.IMPORTANCE_HIGH, sound = true, vibrate = true,
        )
        channel(
            CONTENT, R.string.notif_ch_content, R.string.notif_ch_content_desc,
            NotificationManager.IMPORTANCE_DEFAULT, sound = true, vibrate = false,
        )
        channel(
            SOCIAL, R.string.notif_ch_social, R.string.notif_ch_social_desc,
            NotificationManager.IMPORTANCE_DEFAULT, sound = true, vibrate = false,
        )
        channel(
            ACCOUNT, R.string.notif_ch_account, R.string.notif_ch_account_desc,
            NotificationManager.IMPORTANCE_LOW, sound = false, vibrate = false,
        )
        channel(
            REMINDERS, R.string.notif_ch_reminders, R.string.notif_ch_reminders_desc,
            NotificationManager.IMPORTANCE_LOW, sound = false, vibrate = false,
        )
    }

    /** قناة الإشعار بحسب القسم الوارد من الخادم. */
    private fun channelFor(category: String?): String = when (category) {
        "chat" -> CHAT
        "content" -> CONTENT
        "social" -> SOCIAL
        "reminders" -> REMINDERS
        else -> ACCOUNT
    }

    /** نيّة الفتح: التطبيق يفتح (أو يُستأنف) على الوجهة المطلوبة. */
    private fun contentIntent(context: Context, route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!route.isNullOrBlank()) putExtra(EXTRA_ROUTE, route)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        // رقم الطلب يميّز كل وجهة عن غيرها (وإلا فتحت جميع الإشعارات نفس الشاشة)
        return PendingIntent.getActivity(context, (route ?: "app").hashCode(), intent, flags)
    }

    /**
     * إظهار إشعار من حمولة الخادم (data-only):
     *   title · body · category · route · queue (رقم صف الطابور)
     * رقم الطابور هو معرّف الإشعار ⇒ إشعار جديد بنفس الرقم **يستبدل** القديم
     * (نفس سلوك التجميع الذي يضمنه collapse_key على الخادم).
     */
    fun show(context: Context, data: Map<String, String>) {
        val app = context.applicationContext
        scope.launch {
            val kind = data["image_kind"]
            // الصورة قد تكون مساراً داخل المخزن العام (site-assets) أو رابطاً كاملاً
            val url = resolveImageUrl(app, data["image"])
            val bitmap = when {
                url == null || kind == null -> null
                kind == "avatar" -> loadBitmap(url, maxWidth = 144)?.let { circle(it) }
                else -> loadBitmap(url, maxWidth = 1080)
            }
            runCatching { post(app, data, kind, bitmap) }
        }
    }

    /**
     * يحوّل ما وصل من الخادم إلى رابط قابل للتنزيل:
     * رابط كامل ⇒ كما هو · مسار مثل `items/covers/x.jpg` ⇒ رابط المخزن العام.
     */
    private fun resolveImageUrl(context: Context, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.startsWith("data:")) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        val container = AppContainerHolder.get(context) ?: return null
        return container.siteAssetUrl(value)
    }

    /**
     * تنزيل الصورة مع سقف حجم وتصغير أثناء فك الضغط (لا نُحمّل صورة بحجم الشاشة
     * إلى الذاكرة). أي فشل ⇒ null ⇒ الإشعار يظهر نصّاً بلا صورة.
     */
    private fun loadBitmap(url: String, maxWidth: Int): Bitmap? = runCatching {
        val bytes = withTimeoutOrNull(8_000L) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 6_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
            }
            try {
                if (conn.responseCode !in 200..299) return@withTimeoutOrNull null
                conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) break
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        } ?: return null

        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    /** صورة المرسل دائرية كما تظهر في التطبيق (لا مربّع). */
    private fun circle(source: Bitmap): Bitmap = runCatching {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, Rect(0, 0, source.width, source.height), Rect(0, 0, size, size), paint)
        output
    }.getOrDefault(source)

    /** بناء الإشعار وإظهاره (بعد تجهيز الصورة إن وُجدت). */
    private fun post(context: Context, data: Map<String, String>, kind: String?, bitmap: Bitmap?) {
        val title = data["title"].orEmpty().ifBlank { "AddonStation" }
        val body = data["body"].orEmpty()
        val category = data["category"]
        val route = data["route"]
        // رقم الإشعار: رقم صف الطابور (ثابت لكل إشعار) وإلا تجزئة النص
        val id = data["queue"]?.toIntOrNull() ?: (route + title).hashCode()

        if (!permissionGranted(context)) return

        val builder = NotificationCompat.Builder(context, channelFor(category))
            .setSmallIcon(R.drawable.ic_push_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, route))
            .setPriority(
                if (category == "chat") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setGroup("com.addonstation.mobile")

        when {
            // غلاف إضافة/عالم: صورة كبيرة داخل الإشعار (أوضح ما يراه المستخدم)
            bitmap != null && kind != "avatar" -> builder
                .setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setBigContentTitle(title)
                        .setSummaryText(body)
                )
            // صورة المرسل: أيكونة دائرية صغيرة بجانب النص
            bitmap != null -> builder
                .setLargeIcon(bitmap)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            else -> builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
}
