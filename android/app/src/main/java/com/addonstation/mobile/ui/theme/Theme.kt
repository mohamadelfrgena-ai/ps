package com.addonstation.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R

/** Design tokens copied 1:1 from css/mobile.css (:root --m-*) */
object C {
    val bg = Color(0xFF0B0F14)
    val surface = Color(0xFF131A24)
    val elevated = Color(0xFF1A2330)
    val elevated2 = Color(0xFF212D3D)
    val border = Color(0xFF263240)
    val border2 = Color(0xFF334152)
    val text = Color(0xFFE8EEF5)
    val muted = Color(0xFF8EA0B3)
    val dim = Color(0xFF5C6F82)
    val green = Color(0xFF3DDC84)
    val green2 = Color(0xFF2FAE67)
    val gold = Color(0xFFFFD479)
    val blue = Color(0xFF6FB8FF)
    val red = Color(0xFFFF5A5A)
    val purple = Color(0xFFB98CFF)
}

val MinecraftFont = FontFamily(
    Font(R.font.minecraft_regular, FontWeight.Normal),
    Font(R.font.minecraft_bold, FontWeight.Bold),
)
val MonocraftFont = FontFamily(Font(R.font.monocraft))

fun mc(size: Int, color: Color = C.text) = TextStyle(fontFamily = MinecraftFont, fontSize = size.sp, color = color)

val Radius = RoundedCornerShape(16.dp)
val RadiusLg = RoundedCornerShape(22.dp)

val GreenGradient = Brush.linearGradient(listOf(Color(0xFF43E68C), Color(0xFF27A05E)))

/** A card like .m-card: surface bg, 1px border, 16-22dp radius */
fun Modifier.card(
    shape: Shape = RadiusLg,
    bg: Brush = Brush.linearGradient(listOf(C.surface, C.surface)),
    border: Color = C.border,
    width: Dp = 1.dp,
) = this.background(bg, shape).border(width, border, shape)

@Composable
fun AddonStationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.surface, primary = C.green,
            onPrimary = Color(0xFF06140C), onBackground = C.text, onSurface = C.text,
        ),
        typography = Typography(bodyMedium = TextStyle(color = C.text, fontSize = 14.sp)),
        content = content,
    )
}
