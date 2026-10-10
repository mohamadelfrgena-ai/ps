package com.addonstation.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.parseIsoMillis
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.util.TimeFormat
import java.util.Locale

/**
 * عناصر شاشات الملف الشخصي الثلاث — منفَّذة حرفياً على عقد `SPEC_PROFILES.md`
 * (حقيبة AddonStation_profiles).
 *
 * القاعدة: **لا لون ثابت ولا مقاس مخترع هنا**؛ كل قيمة إمّا رمز في [Spec]
 * (نُقلت من جدول الوثيقة §1) أو مأخوذة من أعمدتها (§2) كما هي بالحرف.
 *
 * الشاشات الثلاث: A «ملفي» (تبويب البروفايل) · B «ملف عضو» (من الشات) ·
 * C «ملفي كما يراه الآخرون». هذه المكوّنات يشترك فيها الثلاثة.
 */

/** حالة الاتصال المعروضة في نقطة الأفاتار وسطر الهوية. */
enum class SpecStatus { ONLINE, AWAY, OFFLINE, HIDDEN }

/**
 * حالة العضو من بياناته الحقيقية:
 *   • صاحب الملف أخفى حالته (`show_online_status = false`) ⇒ لا نقطة ولا نصّ
 *     (الوثيقة: «show no dot and no "Online" text»).
 *   • أنا صاحب الملف والمفتوح الآن ⇒ متصل.
 *   • غيره: نقيس من `last_seen_at` (تُحدَّثها نبضة الحضور) — أقلّ من 5 دقائق
 *     متصل · أقلّ من ساعة بعيد (كهرماني) · وإلا غير متصل (رمادي بلا توهّج).
 */
fun specStatusOf(profile: Profile?, isMe: Boolean): SpecStatus {
    if (profile == null) return SpecStatus.HIDDEN
    if (!profile.showOnlineStatus) return SpecStatus.HIDDEN
    if (isMe) return SpecStatus.ONLINE
    val seen = parseIsoMillis(profile.lastSeenAt) ?: return SpecStatus.OFFLINE
    val minutes = (System.currentTimeMillis() - seen) / 60_000L
    return when {
        minutes < 5 -> SpecStatus.ONLINE
        minutes < 60 -> SpecStatus.AWAY
        else -> SpecStatus.OFFLINE
    }
}

/** لون النصّ المرافق للحالة (يتبع الحالة كما في الوثيقة §2.3). */
private fun statusTextColor(status: SpecStatus): Color = when (status) {
    SpecStatus.ONLINE -> Spec.onlineText
    SpecStatus.AWAY -> Spec.awayAmber
    else -> Spec.textMuted
}

/** اسم الحالة المعروض. */
@Composable
private fun statusLabel(status: SpecStatus): String = when (status) {
    SpecStatus.ONLINE -> stringResource(R.string.profile_status_online)
    SpecStatus.AWAY -> stringResource(R.string.profile_status_away)
    SpecStatus.OFFLINE -> stringResource(R.string.profile_status_offline)
    SpecStatus.HIDDEN -> ""
}

// ───────────────────────────────────────────────────────────── بطاقة عامة (§1)

/** بطاقة: خلفية `surface` · حدّ 1dp `border` · نصف قطر 16 · حشو 14 · هامش علوي 12. */
@Composable
fun SpecCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spec.profileCardRadius))
            .background(Spec.surface)
            .border(1.dp, Spec.border, RoundedCornerShape(Spec.profileCardRadius))
            .padding(padding),
    ) { content() }
}

/** بطاقة بإطار متقطّع (شاشة C: المحفظة المخفيّة). */
@Composable
fun SpecDashedCard(
    modifier: Modifier = Modifier,
    color: Color = Spec.dashedBorder,
    padding: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spec.profileCardRadius))
            .background(Spec.surface)
            .specDashedBorder(color, Spec.profileCardRadius)
            .padding(padding),
    ) { content() }
}

/** إطار متقطّع 1dp حول عنصر بزوايا مستديرة. */
fun Modifier.specDashedBorder(color: Color, radius: Dp, dash: Float = 7f, gap: Float = 5f): Modifier =
    drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f)),
        )
    }

/** تسمية قسم صغيرة (بكسل · 11 · ذهبي · تباعد 1). */
@Composable
fun SpecSmallCaps(text: String, color: Color = Spec.gold, modifier: Modifier = Modifier) {
    Text(text, style = Spec.smallCaps, color = color, modifier = modifier)
}

// ─────────────────────────────────────────────────────────────── 2.1 البانر

/**
 * بانر الملف: ارتفاع 150 · عرض كامل · الصورة تُملأ (crop) · تدرّج داكن شفاف 40%
 * ثم `bg` بشفافية 90% في الأسفل. البانر الافتراضي عند غياب صورة العضو.
 * زرّا فوقه (44×44 دائري · `bg` بشفافية 60% · أيقونة بيضاء 22 · 10dp من الحافة):
 * الرجوع (يسار · كل الشاشات) و«المزيد» (يمين · شاشة B وحدها).
 */
@Composable
fun SpecProfileBanner(
    bannerUrl: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    more: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth().height(Spec.bannerH)) {
        val context = LocalContext.current
        if (bannerUrl.isNullOrBlank()) {
            // بلا بانر مخصّص ⇒ بانر التصميم الافتراضي من الحقيبة
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(R.drawable.profile_banner_default)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // نفس مسار بقية أغلفة التطبيق: لقطة خفيفة تظهر فوراً ثم الحركة
            // (GIF) فوقها — فلا يتجمّد بانر متحرّك ولا يثقل نموذج الصورة.
            CoverImage(
                url = bannerUrl,
                radius = 0.dp,
                animate = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // تدرّج القراءة: شفاف حتى 40% ⇒ خلفية الشاشة بشفافية 90% في الأسفل
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.40f to Color.Transparent,
                        1f to Spec.bg.copy(alpha = 0.90f),
                    )
                )
        )
        onBack?.let {
            SpecBannerIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.common_back),
                onClick = it,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            )
        }
        more?.let {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { it() }
        }
    }
}

/** زر دائري فوق البانر (44×44 · `bg` بشفافية 60% · أيقونة بيضاء 22dp). */
@Composable
fun SpecBannerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(Spec.touch)
            .clip(CircleShape)
            .background(Spec.bg.copy(alpha = 0.60f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

// ────────────────────────────────────────────────── 2.2 الأفاتار + نقطة الحالة

/**
 * أفاتار 96×96 دائري · حدّ 5dp بلون الخلفية · ونقطة حالة 30×30 في الرُّكن
 * السفلي الأيمن (ملتصقة · إزاحة 0) بحدّ داخلي رفيع 2dp وتدرّج شعاعي أخضر
 * من أعلى اليسار مع توهّج ناعم (حلقة 2dp بشفافية 35% + ضباب 14dp بشفافية 65%).
 * «بعيد» كهرماني · «غير متصل» رمادي بلا توهّج · ومخفيّ الحالة: بلا نقطة.
 */
@Composable
fun SpecProfileAvatar(
    avatarUrl: String?,
    status: SpecStatus,
    modifier: Modifier = Modifier,
    size: Dp = Spec.avatarLg,
    borderWidth: Dp = 5.dp,
) {
    // لا تدع ارتفاع صفّ التداخل (50dp) يسحق صورة 96dp إلى شكل بيضاوي.
    Box(modifier = modifier.requiredSize(size)) {
        val context = LocalContext.current
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Spec.surfaceHigh)
                .border(borderWidth, Spec.bg, CircleShape),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl ?: R.drawable.ic_default_profile)
                    .error(R.drawable.ic_default_profile)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (status != SpecStatus.HIDDEN) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(Spec.dotSize)
                    .specStatusDot(status),
            )
        }
    }
}

/** رسم نقطة الحالة: توهّج + حلقة + قرص متدرّج + حدّ داخلي بلون الخلفية. */
private fun Modifier.specStatusDot(status: SpecStatus): Modifier = drawBehind {
    val r = size.minDimension / 2f
    val light = when (status) {
        SpecStatus.ONLINE -> Spec.dotStart
        SpecStatus.AWAY -> Spec.awayAmber
        else -> Spec.offlineGrey
    }
    val dark = when (status) {
        SpecStatus.ONLINE -> Spec.dotEnd
        SpecStatus.AWAY -> Spec.gold
        else -> Spec.offlineGrey
    }
    if (status == SpecStatus.ONLINE) {
        // توهّج: ضباب 14dp بشفافية 65% + حلقة 2dp بشفافية 35%
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Spec.dotStart.copy(alpha = 0.65f), Color.Transparent),
                center = center,
                radius = r + 14.dp.toPx(),
            ),
            radius = r + 14.dp.toPx(),
        )
        drawCircle(
            color = Spec.dotStart.copy(alpha = 0.35f),
            radius = r + 2.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
    // القرص المتدرّج (الضوء من أعلى اليسار)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(light, dark),
            center = Offset(size.width * 0.28f, size.height * 0.24f),
            radius = size.minDimension * 1.05f,
        ),
        radius = r,
    )
    // حدّ رفيع 2dp بلون الخلفية؛ يبقي النقطة واضحة من دون حلقة سوداء سميكة.
    drawCircle(
        color = Spec.bg,
        radius = r - 1.dp.toPx(),
        style = Stroke(width = 2.dp.toPx()),
    )
}

// ────────────────────────────────────────────────────── 2.3 بطاقة الهوية

/**
 * بطاقة الهوية: الاسم 22/800 · سطر `@username` 13/700 مصمت + ` · الحالة`
 * (يُخفى السطر كلّه عند إخفاء الحالة) · شرائح الشارات (التفاف · فراغ 6 ·
 * هامش علوي 10 · 11/800 · نصف قطر 8 · حشو 3×9) · صندوق الحالة المخصّصة
 * (`barSurface` · نصف قطر 12 · حشو 10×12 · 13sp) ويُخفى إن كان فارغاً.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecIdentityCard(
    profile: Profile,
    status: SpecStatus,
    level: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            profile.displayName ?: profile.username ?: "",
            style = Spec.profileName,
            color = Spec.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("@${profile.username ?: ""}", style = Spec.profileHandle, color = Spec.textMuted)
            if (status != SpecStatus.HIDDEN) {
                Text(" · ", style = Spec.profileHandle, color = Spec.textMuted)
                Text(statusLabel(status), style = Spec.profileHandle, color = statusTextColor(status))
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (profile.effectivePlan != "free") {
                SpecBadgeChip(stringResource(R.string.badge_premium), Spec.gold, Spec.goldDeepBg)
            }
            if (profile.role == "creator") {
                SpecBadgeChip(stringResource(R.string.badge_creator), Spec.blue, Color.White)
            }
            SpecBadgeChip(stringResource(R.string.badge_level, level), Spec.surfaceHigh, Spec.chipLabel)
            if (profile.role == "admin") {
                SpecBadgeChip(stringResource(R.string.badge_admin), Spec.gold, Spec.goldDeepBg)
            } else if (profile.role == "moderator") {
                SpecBadgeChip(stringResource(R.string.badge_mod), Spec.blue, Color.White)
            }
        }

        profile.customStatus?.takeIf { it.isNotBlank() }?.let { state ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Spec.barSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(state, style = Spec.statusBox, color = Spec.chipLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** شريحة شارة (11/800 · نصف قطر 8 · حشو 3×9). */
@Composable
fun SpecBadgeChip(text: String, background: Color, contentColor: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Spec.badgeRadius))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = Spec.badgeChip, color = contentColor, maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────── 2.4 شريط الإحصاءات

/** شريط الإحصاءات: ثلاثة أعمدة متساوية تفصلها فواصل 1dp · حشو رأسي 12. */
@Composable
fun SpecStatsStrip(friends: Int, downloads: Int, favorites: Int, modifier: Modifier = Modifier) {
    SpecCard(modifier = modifier, padding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.CenterVertically) {
            SpecStatColumn(friends.toString(), stringResource(R.string.profile_stats_friends), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(46.dp).background(Spec.border))
            SpecStatColumn(downloads.toString(), stringResource(R.string.profile_stats_downloads), Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(46.dp).background(Spec.border))
            SpecStatColumn(favorites.toString(), stringResource(R.string.profile_stats_favorites), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpecStatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(value, style = Spec.statNumber, color = Spec.text, maxLines = 1)
        Text(label, style = Spec.statLabel, color = Spec.textMuted, maxLines = 1, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────── 2.5 بطاقة المستوى

/** بطاقة المستوى: تسمية بكسل · «المستوى N · x/100» · شريط 8dp بتدرّج أزرق. */
@Composable
fun SpecLevelCard(level: Int, xpInLevel: Int, modifier: Modifier = Modifier) {
    SpecCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            SpecSmallCaps(stringResource(R.string.profile_level_reputation))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.profile_xp_progress, level.toString(), xpInLevel),
                style = Spec.levelText,
                color = Spec.text,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Spec.statBarH)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spec.surfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(xpInLevel.coerceIn(0, 100) / 100f)
                        .height(Spec.statBarH)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(listOf(Spec.blue, Spec.blueLink))),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────── 2.6 بطاقة النبذة

/** بطاقة النبذة: «ABOUT ME» + النصّ (أو نصّ البديل) + فاصل + «MEMBER SINCE». */
@Composable
fun SpecAboutCard(bio: String?, memberSince: String?, modifier: Modifier = Modifier) {
    SpecCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            SpecSmallCaps(stringResource(R.string.profile_about_me))
            Spacer(Modifier.height(8.dp))
            Text(
                bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_no_bio),
                style = Spec.bioText,
                color = Spec.chipLabel,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
            Spacer(Modifier.height(12.dp))
            SpecSmallCaps(stringResource(R.string.profile_member_since_label))
            Spacer(Modifier.height(4.dp))
            Text(
                memberSince ?: "—",
                style = Spec.bioText.copy(fontWeight = FontWeight.Bold),
                color = Spec.text,
            )
        }
    }
}

// ─────────────────────────────────────────────────── البطاقة المختصرة للمحفظة

/** بطاقة الملف الشخصي المختصرة المطابقة لقسم 3 من مواصفة Wallet + Bottom Bar. */
@Composable
fun SpecWalletCard(
    balance: Int,
    streak: Int,
    dailyClaimed: Boolean,
    claiming: Boolean,
    onOpenWallet: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WalletCompactCard(
        balance = balance,
        streak = streak,
        dailyClaimed = dailyClaimed,
        claiming = claiming,
        onOpenWallet = onOpenWallet,
        onClaim = onClaim,
        modifier = modifier,
    )
}

/** بطاقة بديلة في شاشة C: عملة 36 بشفافية 50% · «محفظة AC» · «مخفية عن المستخدمين». */
@Composable
fun SpecHiddenWalletCard(modifier: Modifier = Modifier) {
    SpecDashedCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).alpha(0.5f)) { CoinIcon(size = 36.dp) }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.profile_wallet_title),
                    style = Spec.cardTitle,
                    color = Spec.chipLabel,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.profile_hidden_from_others),
                    style = Spec.secondary,
                    color = Spec.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

// ───────────────────────────────────────────── أزرار الشاشات A · B · C

/** زرّ ممتلئ أزرق (48h افتراضياً · نصف قطر 12 · نصّ أبيض 14/800). */
@Composable
fun SpecFillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonHeight: Dp = Spec.actionBtnH,
    horizontalPadding: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .height(buttonHeight)
            .clip(RoundedCornerShape(Spec.buttonRadius))
            .background(if (enabled) Spec.blue else Spec.surfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = horizontalPadding),
            style = Spec.button,
            color = if (enabled) Color.White else Spec.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** زر مخطَّط أزرق (1dp · نصّ `blueLink` 14/800) — «طلب صداقة». */
@Composable
fun SpecOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(Spec.actionBtnH)
            .clip(RoundedCornerShape(Spec.buttonRadius))
            .border(1.dp, if (enabled) Spec.blue else Spec.border, RoundedCornerShape(Spec.buttonRadius))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Spec.button,
            color = if (enabled) Spec.blueLink else Spec.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * زرّ «تسجيل الخروج»: عرض كامل ناقص الهوامش · 48h · نصف قطر 12 · شفاف ·
 * إطار 1dp `dangerBorder` · نصّ `danger` 14/800 · هامش 16.
 */
@Composable
fun SpecDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Spec.actionBtnH)
            .clip(RoundedCornerShape(Spec.buttonRadius))
            .border(1.dp, Spec.dangerBorder, RoundedCornerShape(Spec.buttonRadius))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Spec.button, color = Spec.danger)
    }
}

/** زرّ من شبكة 2×2 (56h · نصف قطر 14 · `surface` · 13/800). */
@Composable
fun SpecGridTile(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(Spec.gridBtnH)
            .clip(RoundedCornerShape(14.dp))
            .background(Spec.surface)
            .border(1.dp, Spec.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Spec.gridButton, color = Spec.text, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * صفّ داخل بطاقة السلامة/الإشراف (52h · 14/800 محاذاة يسار · فواصل 1dp).
 * `trailing` شريحة في الطرف — تُستعمل لشارة «Legendary» على زرّ الكتم.
 */
@Composable
fun SpecActionRow(
    text: String,
    color: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
        Row(
            Modifier
                .fillMaxWidth()
                .height(Spec.safetyRowH)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = Spec.button, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
    }
}

/** تاريخ «عضو منذ» جاهز للعرض (يوم/شهر/سنة بلغة الجهاز). */
fun specMemberSince(createdAt: String?): String =
    TimeFormat.dateOnly(createdAt, Locale.getDefault())
