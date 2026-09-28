package com.addonstation.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.addonstation.mobile.ui.nav.AppRoot
import com.addonstation.mobile.util.LocaleManager

/**
 * النشاط الوحيد في التطبيق (بورتريه، singleTask):
 * يقرأ رابط الاستعادة addonstation://recovery ويمرّره إلى الواجهة،
 * ويطبّق لغة المستخدم على الـ Context قبل بناء أي شاشة.
 */
class MainActivity : ComponentActivity() {

    private var recoveryLink: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recoveryLink = intent?.data?.toString()?.takeIf { it.startsWith("addonstation://recovery") }

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppRoot(
                    recoveryLink = recoveryLink,
                    onRecoveryConsumed = { recoveryLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
