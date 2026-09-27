package com.addonstation.mobile.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.*

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Settings("settings", "Settings", Icons.Outlined.Settings),
    Community("community", "Community", Icons.Outlined.Forum),
    Home("home", "Home", Icons.Outlined.Home),
    Store("store", "Store", Icons.Outlined.ShoppingCart),
    Profile("profile", "Profile", Icons.Outlined.Person),
}

@Composable
fun TopBar(title: String, icon: ImageVector, balance: Long?, onLogo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.bg).drawBehind {
            drawLine(C.border, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        }.height(60.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            LogoImage(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF101722))
                    .border(1.dp, Color(0x4D6FB8FF), RoundedCornerShape(11.dp)).padding(2.dp)
                    .clip(RoundedCornerShape(8.dp)).clickable { onLogo() }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = C.blue, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.height(36.dp).widthIn(min = 78.dp).clip(shape).background(Color(0xB8473510)).border(1.dp, Color(0x85F7CB43), shape)
                    .padding(horizontal = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.coin), null, Modifier.size(19.dp))
                Spacer(Modifier.width(5.dp))
                Text(balance?.toString() ?: "—", color = Color(0xFFFFE38A), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text("AC", color = Color(0xFFFFF0B0), fontWeight = FontWeight.Black, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun BottomNav(current: Tab?, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().height(80.dp)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(62.dp)
                .background(Brush.verticalGradient(listOf(Color(0xD110161E), Color(0xF70A0E14))))
                .drawBehind { drawLine(C.border, Offset(0f, 0f), Offset(size.width, 0f), 1f) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                if (t == Tab.Home) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Text("Home", color = C.text, fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                } else NavItem(t, current == t, Modifier.weight(1f)) { onSelect(t) }
            }
        }
        // Raised centre Home button (.m-nav-home)
        val shape = RoundedCornerShape(17.dp)
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 2.dp).size(54.dp)
                .shadow(16.dp, shape, ambientColor = C.green, spotColor = C.green)
                .clip(shape).background(Brush.linearGradient(listOf(Color(0xFF3ED989), Color(0xFF218A56))))
                .border(1.dp, Color(0xB83DDC84), shape).press { onSelect(Tab.Home) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Home, null, tint = Color(0xFF061A10), modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun NavItem(t: Tab, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (active) C.green else C.dim
    val bar by animateDpAsState(if (active) 30.dp else 0.dp, label = "bar")
    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(bar).height(3.dp).clip(RoundedCornerShape(3.dp)).background(C.green))
        Spacer(Modifier.height(15.dp))
        Icon(t.icon, null, tint = color.copy(alpha = if (active) 1f else .72f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(t.label, color = color, fontSize = 9.sp, letterSpacing = .3.sp)
    }
}
