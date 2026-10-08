package com.addonstation.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
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
 * يقرأ رابط الاستعادة addonstation://recovery ويمرّره إلى الواجهة،
 * ويطبّق لغة المستخدم على الـ Context قبل بناء أي شاشة.
 *
 * الرابط **حالة قابلة للملاحظة** لا متغيّراً عادياً: قد يصل الرابط
 * والتطبيق مفتوح أصلاً (المستخدم يفتح بريده من نفس الهاتف) فيأتي عبر
 * `onNewIntent` لا `onCreate` — وحينها لا تُعاد كتابة setContent، فلا يصل
 * الرابط إلى الواجهة أبداً ويبدو أن «الرابط لا يعمل».
 */
class MainActivity : ComponentActivity() {

    private val recoveryLink: MutableState<String?> = mutableStateOf(null)
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

    /** أي رابط addonstation:// يصلنا (استعادة كلمة المرور وغيرها). */
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
        recoveryLink.value = linkOf(intent)
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
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
                LocalLayoutDirection provides layoutDirection,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        recoveryLink = recoveryLink.value,
                        onRecoveryConsumed = { recoveryLink.value = null },
                        pushRoute = pushRoute.value,
                        onPushConsumed = { pushRoute.value = null },
                    )
                }
            }
        }
    }

    /** A minimal native view keeps the report accessible even if Compose startup crashed. */
    private fun showCrashReport(report: String) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = getString(R.string.crash_report_title)
            textSize = 22f
            setTextColor(Color.rgb(25, 35, 48))
        }
        val explanation = TextView(this).apply {
            text = getString(R.string.crash_report_body)
            textSize = 15f
            setTextColor(Color.rgb(70, 78, 90))
            setPadding(0, dp(12), 0, dp(12))
        }
        val details = TextView(this).apply {
            text = report
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(45, 52, 62))
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val scroll = ScrollView(this).apply {
            addView(details, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        val copy = Button(this).apply {
            text = getString(R.string.crash_report_copy)
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_report_title), report))
                Toast.makeText(this@MainActivity, R.string.crash_report_copied, Toast.LENGTH_LONG).show()
            }
        }
        val retry = Button(this).apply {
            text = getString(R.string.crash_report_continue)
            setOnClickListener {
                CrashDiagnostics.clear(this@MainActivity)
                recreate()
            }
        }
        root.addView(title)
        root.addView(explanation)
        root.addView(scroll)
        root.addView(copy)
        root.addView(retry)
        setContentView(root)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // التطبيق كان مفتوحاً ⇒ نحدّث الحالة فتلتقطها الواجهة فوراً
        recoveryLink.value = linkOf(intent)
        pushRoute.value = routeOf(intent)
    }
}
