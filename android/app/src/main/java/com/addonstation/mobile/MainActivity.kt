package com.addonstation.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.addonstation.mobile.ui.nav.AppRoot
import com.addonstation.mobile.util.AppPrefs
import com.addonstation.mobile.util.CrashDiagnostics
import com.addonstation.mobile.util.LocaleManager

/**
 * النشاط الوحيد في التطبيق (بورتريه، singleTask):
 * يقرأ رابط بريد Supabase (تأكيد التسجيل أو الاستعادة) ويمرّره إلى الواجهة،
 * ويطبّق لغة المستخدم على الـ Context قبل بناء أي شاشة.
 *
 * الرابط **حالة قابلة للملاحظة** لا متغيّراً عادياً: قد يصل الرابط
 * والتطبيق مفتوح أصلاً (المستخدم يفتح بريده من نفس الهاتف) فيأتي عبر
 * `onNewIntent` لا `onCreate` — وحينها لا تُعاد كتابة setContent، فلا يصل
 * الرابط إلى الواجهة أبداً ويبدو أن «الرابط لا يعمل».
 */
class MainActivity : ComponentActivity() {

    private val authLink: MutableState<String?> = mutableStateOf(null)
    private val appLocaleCode: MutableState<String> = mutableStateOf("en")

    /** تطبيق اللغة فوراً داخل Compose دون إعادة إنشاء النشاط أو إعادة تشغيل جلسة الحساب. */
    fun applyAppLocale(languageCode: String) {
        val code = LocaleManager.byCode(languageCode).code
        AppPrefs.writeLocale(applicationContext, code)
        appLocaleCode.value = code
    }

    /**
     * الوجهة التي طلبها إشعار FCM (مثل `chat:العربية` أو `item:<id>`).
     * حالة قابلة للملاحظة مثل رابط الاستعادة: قد يصل الإشعار والتطبيق مفتوح
     * فيأتي عبر onNewIntent لا onCreate.
     */
    private val pushRoute: MutableState<String?> = mutableStateOf(null)

    /** أي رابط addonstation:// يصلنا (تأكيد التسجيل أو استعادة كلمة المرور). */
    private fun linkOf(intent: android.content.Intent?): String? =
        intent?.data?.toString()?.takeIf { it.startsWith("addonstation://") }

    /** الوجهة المطلوبة من إشعار (يضعها PendingIntent في الملحق). */
    private fun routeOf(intent: android.content.Intent?): String? =
        intent?.getStringExtra(com.addonstation.mobile.push.PushNotifications.EXTRA_ROUTE)
            ?.takeIf { it.isNotBlank() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.read(this)?.let { report ->
            showCrashReport(report)
            return
        }
        enableEdgeToEdge()
        authLink.value = linkOf(intent)
        pushRoute.value = routeOf(intent)
        appLocaleCode.value = LocaleManager.byCode(AppPrefs.readLocale(this)).code

        setContent {
            val languageCode = appLocaleCode.value
            val baseConfiguration = LocalConfiguration.current
            val localizedContext = remember(languageCode, baseConfiguration) {
                LocaleManager.applyLocale(this@MainActivity, languageCode)
            }
            val localizedConfiguration = remember(languageCode, baseConfiguration) {
                android.content.res.Configuration(localizedContext.resources.configuration)
            }
            val layoutDirection = if (LocaleManager.isRtl(languageCode)) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(
                // createConfigurationContext يبدّل LocalContext إلى Context غير Activity؛
                // نمرّر مالك ActivityResult صراحةً كي تعمل rememberLauncherForActivityResult.
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalLayoutDirection provides layoutDirection,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        authLink = authLink.value,
                        onAuthLinkConsumed = { authLink.value = null },
                        pushRoute = pushRoute.value,
                        onPushConsumed = { pushRoute.value = null },
                    )
                }
            }
        }
    }

    /** Native first-launch report view remains usable even if Compose startup crashed. */
    private fun showCrashReport(report: String) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = AppPrefs.readDark(this)
        val backgroundColor = if (dark) Color.rgb(10, 15, 28) else Color.rgb(244, 247, 252)
        val surface = if (dark) Color.rgb(18, 26, 43) else Color.WHITE
        val textColor = if (dark) Color.rgb(234, 240, 250) else Color.rgb(25, 35, 48)
        val muted = if (dark) Color.rgb(157, 176, 207) else Color.rgb(91, 105, 125)
        val blue = Color.rgb(46, 123, 239)
        val border = if (dark) Color.rgb(34, 48, 73) else Color.rgb(215, 223, 234)

        fun rounded(fill: Int, stroke: Int? = null, radius: Int = dp(14)): GradientDrawable =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = radius.toFloat()
                if (stroke != null) setStroke(dp(1), stroke)
            }

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        @Suppress("DEPRECATION")
        run {
            window.decorView.systemUiVisibility = if (dark) 0 else
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }

        val root = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(60), dp(20), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        root.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))

        val bug = TextView(this).apply {
            text = "🐛"
            textSize = 48f
            gravity = Gravity.CENTER
            setBackground(rounded(if (dark) Color.rgb(42, 24, 34) else Color.rgb(255, 232, 236), radius = dp(52)))
        }
        content.addView(bug, LinearLayout.LayoutParams(dp(104), dp(104)))

        val title = TextView(this).apply {
            text = getString(R.string.crash_report_title)
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(textColor)
            gravity = Gravity.CENTER
        }
        content.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(22) })

        val explanation = TextView(this).apply {
            text = getString(R.string.crash_report_body)
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1.2f)
        }
        content.addView(explanation, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })

        val logCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(6, 9, 15), border)
            clipToOutline = true
        }
        content.addView(logCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(20) })

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(8), dp(4))
        }
        val logLabel = TextView(this).apply {
            text = "LOG"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.rgb(185, 198, 220))
            gravity = Gravity.CENTER_VERTICAL
        }
        logHeader.addView(logLabel, LinearLayout.LayoutParams(0, dp(34), 1f))
        val smallCopy = TextView(this).apply {
            text = "⧉"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(185, 198, 220))
            contentDescription = getString(R.string.crash_report_copy)
            setOnClickListener { copyCrashReport(report) }
        }
        logHeader.addView(smallCopy, LinearLayout.LayoutParams(dp(40), dp(36)))
        logCard.addView(logHeader)
        val separator = View(this).apply { setBackgroundColor(Color.rgb(34, 48, 73)) }
        logCard.addView(separator, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1),
        ))

        val logScroll = ScrollView(this).apply {
            isFillViewport = false
            setBackgroundColor(Color.rgb(6, 9, 15))
        }
        val details = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(185, 198, 220))
            setTextIsSelectable(true)
            textDirection = View.TEXT_DIRECTION_LTR
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        logScroll.addView(details, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
        logCard.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(160),
        ))

        val copyButton = TextView(this).apply {
            text = getString(R.string.crash_report_copy)
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, Color.rgb(76, 154, 255)),
            ).apply { cornerRadius = dp(16).toFloat() }
            setOnClickListener { copyCrashReport(report) }
        }
        content.addView(copyButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56),
        ).apply { topMargin = dp(18) })

        val continueButton = TextView(this).apply {
            text = getString(R.string.crash_report_continue)
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (dark) textColor else blue)
            background = rounded(surface, border, dp(14))
            setOnClickListener {
                CrashDiagnostics.clear(this@MainActivity)
                recreate()
            }
        }
        content.addView(continueButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52),
        ).apply { topMargin = dp(10) })

        setContentView(root)
    }

    private fun copyCrashReport(report: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_report_title), report))
        Toast.makeText(this, R.string.crash_report_copied, Toast.LENGTH_LONG).show()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // التطبيق كان مفتوحاً ⇒ نحدّث الحالة فتلتقطها الواجهة فوراً
        authLink.value = linkOf(intent)
        pushRoute.value = routeOf(intent)
    }
}
