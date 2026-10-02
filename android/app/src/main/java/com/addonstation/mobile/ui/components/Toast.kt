package com.addonstation.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * نخب (Toast) عام للتطبيق — ما تطلبه المواصفة في «النخب/الرسائل»:
 * «أُضيف إلى المفضلة ⭐» · «أُزيل من المفضلة» · «سجّل الدخول أولًا 🔐» ·
 * «جارٍ التحميل… 📥» · «تعذّر التحميل» · «لا تملك صلاحية تحميل هذا المحتوى» ·
 * «ملف التنزيل غير مرفوع بعد».
 *
 * الاستخدام من أي شاشة:   Toasts.show(stringResource(R.string.item_added_fav))
 * تُركَّب مرّة واحدة في `AppNav` عبر [ToastHost] فتظهر فوق كل الشاشات.
 */
data class ToastMessage(val text: String, val id: Long)

object Toasts {
    private val _messages = MutableStateFlow<ToastMessage?>(null)
    val messages: StateFlow<ToastMessage?> = _messages

    private var counter = 0L

    /** يعرض رسالة، ويخفيها تلقائياً بعد مدّة قصيرة. */
    fun show(text: String) {
        counter += 1
        _messages.value = ToastMessage(text, counter)
    }

    fun clear() {
        _messages.value = null
    }
}

/** بطاقة النخب: تظهر أسفل الشاشة فوق أشرطة النظام ثم تختفي وحدها. */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val current by Toasts.messages.collectAsState()
    var visible by remember { mutableStateOf(false) }
    var shownId by remember { mutableStateOf(0L) }

    LaunchedEffect(current?.id) {
        val msg = current ?: return@LaunchedEffect
        shownId = msg.id
        visible = true
        delay(2400L)
        visible = false
        delay(260L)
        if (Toasts.messages.value?.id == shownId) Toasts.clear()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val msg = current ?: return@AnimatedVisibility
            Box(
                modifier = Modifier
                    .padding(bottom = 78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEE131C27))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = msg.text,
                    style = AppText.bodySmall,
                    color = AppColors.Text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
