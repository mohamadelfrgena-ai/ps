package com.addonstation.mobile.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.addonstation.mobile.R
import com.addonstation.mobile.data.assetUrl
import com.addonstation.mobile.ui.theme.*

/** Press feedback like `:active{transform:scale(.97)}` in the CSS */
@Composable
fun Modifier.press(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this.scale(if (pressed) 0.97f else 1f).clickable(src, null, onClick = onClick)
}

enum class BtnStyle { Green, Dark, Gold, Blue, Outline, Danger }

@Composable
fun MButton(
    text: String, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Green,
    height: Dp = 46.dp, enabled: Boolean = true, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(13.dp)
    val (bg, fg, border) = when (style) {
        BtnStyle.Green -> Triple(GreenGradient, Color(0xFF04210F), Color.Transparent)
        BtnStyle.Dark -> Triple(SolidColor(C.elevated2), C.text, C.border2)
        BtnStyle.Gold -> Triple(Brush.linearGradient(listOf(Color(0xFFFFDC8C), Color(0xFFE8B04A))), Color(0xFF2A1A00), Color.Transparent)
        BtnStyle.Blue -> Triple(Brush.linearGradient(listOf(Color(0xFF8CC6FF), Color(0xFF3F86E0))), Color(0xFF071526), Color.Transparent)
        BtnStyle.Outline -> Triple(SolidColor(Color.Transparent), C.text, C.border2)
        BtnStyle.Danger -> Triple(SolidColor(Color(0xFF331519)), Color(0xFFFF8A8A), Color(0xFF7A2A30))
    }
    val glow = if (style == BtnStyle.Green) Modifier.shadow(14.dp, shape, ambientColor = C.green, spotColor = C.green) else Modifier
    Box(
        modifier.height(height).then(glow).clip(shape).background(bg).border(1.dp, border, shape)
            .then(if (enabled) Modifier.press(onClick) else Modifier.alpha(.6f)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center) }
}


@Composable
fun MField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, trailing: (@Composable () -> Unit)? = null,
    bg: Color = C.surface, height: Dp = 46.dp,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(height).clip(shape).background(bg).border(1.dp, C.border2, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = C.dim, fontSize = 14.sp)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = C.text, fontSize = 14.sp),
                cursorBrush = SolidColor(C.green),
                visualTransformation = if (password) PasswordVisualTransformation('•') else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, icon: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Text(icon, fontSize = 13.sp, color = C.blue); Spacer(Modifier.width(6.dp)) }
        Text(text, style = mc(14).copy(letterSpacing = .5.sp), modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = C.blue, fontSize = 12.sp, modifier = Modifier.clickable { onAction() })
    }
}

/** Remote/asset image with a local placeholder */
@Composable
fun Img(url: String?, fallback: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val u = assetUrl(url)
    if (u == null) Image(painterResource(fallback), null, modifier, contentScale = scale)
    else AsyncImage(u, null, modifier, contentScale = scale, placeholder = painterResource(fallback), error = painterResource(fallback))
}

@Composable
fun PlanBadge(plan: String?) {
    val p = (plan ?: "free").lowercase()
    val (label, fg, bg) = when (p) {
        "legendary" -> Triple("Legendary 👑", C.gold, Color(0x33FFD479))
        "epic" -> Triple("Epic 💎", C.purple, Color(0x33B98CFF))
        "free" -> Triple("Free", C.muted, C.elevated2)
        else -> Triple(plan!!.replaceFirstChar { it.uppercase() } + " ✦", C.blue, Color(0xFF1F3048))
    }
    Text(
        label, color = fg, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
fun EmptyState(icon: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 42.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.text)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = C.muted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = C.red, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
fun LogoImage(modifier: Modifier) = Image(painterResource(R.drawable.logo), null, modifier, contentScale = ContentScale.Crop)
