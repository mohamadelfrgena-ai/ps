package com.addonstation.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.app.data.ContentItem
import com.addonstation.app.data.Creator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun AddonImage(
    path: String?,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { decodeImage(context, path) }
    }
    if (bitmap != null) {
        Image(bitmap!!, contentDescription = null, modifier = modifier, contentScale = scale)
    } else {
        Box(modifier.background(palette.elevated))
    }
}

private fun decodeImage(context: Context, path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val stream = if (path.startsWith("http")) URL(path).openStream() else context.assets.open(path.removePrefix("/"))
        stream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun GreenButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.green, contentColor = palette.greenInk)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.elevated, contentColor = palette.text)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun RankChip(plan: String, role: String = "user") {
    val palette = LocalPalette.current
    val (label, color) = when {
        role == "admin" -> "TheKing" to palette.gold
        role == "creator" -> "Creator" to palette.green
        plan == "legendary" -> "Legendary" to palette.gold
        plan == "epic" -> "Epic" to palette.blue
        else -> "Free" to palette.green
    }
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (action != null) Text(action, color = palette.green, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
fun ContentCard(item: ContentItem, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            AddonImage(item.coverUrl, Modifier.fillMaxWidth().height(108.dp))
            Box(Modifier.padding(8.dp)) { RankChip(item.plan) }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.title, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("★ ${"%.1f".format(item.rating)}  ·  ↓ ${item.downloads}", color = palette.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun CreatorCard(creator: Creator, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        AddonImage(creator.bannerUrl, Modifier.fillMaxWidth().height(72.dp))
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddonImage(creator.avatarUrl, Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(creator.name, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(creator.badge, color = palette.green, fontSize = 10.sp)
                }
            }
            Text(creator.specialty, color = palette.muted, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✦", color = palette.gold, fontSize = 28.sp)
        Text(title, color = palette.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(body, color = palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun PageHeading(kicker: String, title: String, subtitle: String) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(kicker, color = palette.blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(title, color = palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = palette.muted, fontSize = 13.sp)
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(title, color = palette.text, fontWeight = FontWeight.Bold)
        if (body.isNotBlank()) Text(body, color = palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Avatar(path: String, size: Int = 44) {
    AddonImage(path, Modifier.size(size.dp).clip(CircleShape).border(2.dp, LocalPalette.current.green, CircleShape))
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("…", color = LocalPalette.current.green, fontSize = 28.sp)
    }
}

fun stickerPacks(context: Context): List<Pair<String, List<String>>> {
    val folders = context.assets.list("stickers")?.filter { !it.contains('.') } ?: return emptyList()
    return folders.mapNotNull { folder ->
        val files = context.assets.list("stickers/$folder")
            ?.filter { it.endsWith(".webp") || it.endsWith(".png") || it.endsWith(".gif") }
            ?.sorted()
            .orEmpty()
        if (files.isEmpty()) null else folder to files.map { "stickers/$folder/$it" }
    }
}

fun channelIcon(slug: String): String {
    val known = setOf("arabic", "chinese", "creator", "english", "espanol", "francais", "general", "minecraft", "portuguese", "russian", "social-media-makers", "support")
    val key = when (slug) {
        "creators" -> "creator"
        else -> slug
    }
    return if (key in known) "/icons/chat-rooms/$key.png" else "/icons/icon-192.png"
}
