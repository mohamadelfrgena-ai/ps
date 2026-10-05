package com.addonstation.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.addonstation.mobile.ui.nav.AppRoot
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

    /** أي رابط addonstation:// يصلنا (استعادة كلمة المرور وغيرها). */
    private fun linkOf(intent: android.content.Intent?): String? =
        intent?.data?.toString()?.takeIf { it.startsWith("addonstation://") }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recoveryLink.value = linkOf(intent)

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppRoot(
                    recoveryLink = recoveryLink.value,
                    onRecoveryConsumed = { recoveryLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // التطبيق كان مفتوحاً ⇒ نحدّث الحالة فتلتقطها الواجهة فوراً
        recoveryLink.value = linkOf(intent)
    }
}
