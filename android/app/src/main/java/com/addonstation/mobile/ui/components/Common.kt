package com.addonstation.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.rankColors
import com.addonstation.mobile.util.DevicePerformance

/* ============================ البطاقات والعناوين ============================ */

/** بطاقة زجاجية: سطح + حدود 1dp + نصف قطر 16 + حشوة 14 (كما في الوثيقة). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    color: Color = AppColors.Surface,
    borderColor: Color = AppColors.Border,
    radius: Dp = 16.dp,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** عنوان قسم + رابط «عرض الكل» على الطرف الآخر. */
@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    leadingEmoji: String? = null,
    actionColor: Color = AppColors.Blue,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (leadingEmoji != null) "$leadingEmoji $title" else title,
            style = AppText.sectionTitle,
            color = AppColors.Text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionText != null) {
            Text(
                text = actionText,
                style = AppText.bodySmall,
                color = actionColor,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

@Composable
fun Kicker(text: String, color: Color = AppColors.Blue) {
    Text(text = text.uppercase(), style = AppText.kicker, color = color)
}

/* ================================ الأزرار ================================ */

/** زر متدرّج (أخضر/ذهبي/أزرق/خطر) — ارتفاع أدنى 46dp، نصف قطر 13، مقياس ضغط 0.96. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(AppColors.GreenLight, AppColors.Green2),
    textColor: Color = AppColors.OnGreen,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    style: TextStyle = AppText.button,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "btnScale")
    Box(
        modifier = modifier
            .height(46.dp)
            .scale(scale)
            .shadow(if (enabled) 10.dp else 0.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) Brush.linearGradient(colors) else Brush.linearGradient(
                listOf(AppColors.Elevated2, AppColors.Elevated2)
            ))
            .then(
                if (enabled) Modifier.clickable(interaction, null) { onClick() }
                else Modifier.clickable(enabled = false) { }
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke()
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                style = style,
                color = if (enabled) textColor else AppColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** أيقونة عملة AC (صورة بكسل) — تُعرض بجانب أي رصيد أو سعر. */
@Composable
fun CoinIcon(size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.coin_ac),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** زر شبح (Ghost): خلفية elevated2، إطار 1dp، نص فاتح. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    borderColor: Color = AppColors.Border2,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(AppColors.Elevated2)
            .border(1.dp, borderColor, RoundedCornerShape(13.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = AppColors.Text, modifier = Modifier.size(18.dp))
            }
            Text(text, style = AppText.button, color = if (enabled) AppColors.Text else AppColors.Muted, maxLines = 1)
        }
    }
}

/* ================================ الشارات ================================ */

/** شارة رتبة/خطة (حبوب) حسب ألوان الوثيقة. */
@Composable
fun RankBadge(
    role: String = "user",
    plan: String = "free",
    modifier: Modifier = Modifier,
) {
    val colors = rankColors(role, plan)
    val label = when {
        role == "admin" -> stringResource(R.string.role_admin)
        role == "head_moderator" -> stringResource(R.string.role_head_moderator_plain)
        role == "moderator" -> stringResource(R.string.role_moderator_plain)
        role == "creator" -> stringResource(R.string.role_creator_plain)
        plan == "legendary" -> stringResource(R.string.role_legendary)
        plan == "epic" -> stringResource(R.string.role_epic)
        else -> stringResource(R.string.role_free)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.background)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (role == "head_moderator") {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.text, modifier = Modifier.size(12.dp))
        }
        Text(
            text = label,
            style = AppText.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default),
            color = colors.text,
            maxLines = 1,
        )
    }
}

@Composable
fun PlanChip(plan: String, modifier: Modifier = Modifier) {
    RankBadge(role = "user", plan = plan, modifier = modifier)
}

/* ================================ الصور ================================ */

@Composable
private fun animatedPreviewsAllowed(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context.applicationContext) {
        DevicePerformance.profile(context).allowAnimatedPreviews
    }
}

/** رمز لغة التطبيق الحالية (ar · en · …) — لاختيار النص العربي أو الإنجليزي القادم من الخادم. */
@Composable
fun appLocaleCode(): String {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
    return locale.language.ifBlank { "en" }
}

/** علم دائري مرسوم لكل لغة مدعومة (بدل إيموجي العلم). */
@androidx.annotation.DrawableRes
fun langFlagRes(languageCode: String): Int = when (languageCode.lowercase()) {
    "ar" -> R.drawable.ic_room_lang_ar
    "en" -> R.drawable.ic_room_lang_en
    "pt" -> R.drawable.ic_room_lang_pt
    "es" -> R.drawable.ic_room_lang_es
    "ru" -> R.drawable.ic_room_lang_ru
    "zh" -> R.drawable.ic_room_lang_zh
    "fr" -> R.drawable.ic_room_lang_fr
    "tr" -> R.drawable.ic_room_lang_tr
    else -> R.drawable.ic_room_lang_en
}

/** أيقونة غرفة الشات الدائرية: صورة جاهزة حسب معرّف الغرفة، وبالحجم المطلوب. */
@Composable
fun RoomIcon(channelId: String, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(roomIconRes(channelId)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * يحوّل معرّف الغرفة إلى أيقونتها المرسومة (بلا إيموجي).
 *
 * الأيقونات هي **أيقونات المالك الأصلية** المستوردة من مجلد `icons`
 * (عام · ماينكرافت · المبدعين · الدعم · صنّاع المحتوى · الأعلام · خدمات النقدية).
 */
@androidx.annotation.DrawableRes
fun roomIconRes(channelId: String): Int = when (channelId) {
    "general" -> R.drawable.ic_room_general
    "minecraft" -> R.drawable.ic_room_minecraft
    "creator" -> R.drawable.ic_room_creator
    "support" -> R.drawable.ic_room_support
    "social-media-makers" -> R.drawable.ic_room_social
    "cash-services" -> R.drawable.ic_room_cash
    "lang-ar" -> R.drawable.ic_room_lang_ar
    "lang-ru" -> R.drawable.ic_room_lang_ru
    "lang-pt" -> R.drawable.ic_room_lang_pt
    "lang-zh" -> R.drawable.ic_room_lang_zh
    "lang-en" -> R.drawable.ic_room_lang_en
    "lang-es" -> R.drawable.ic_room_lang_es
    "lang-fr" -> R.drawable.ic_room_lang_fr
    "lang-tr" -> R.drawable.ic_room_lang_tr
    else -> R.drawable.ic_room_general
}

/** الأصول الافتراضية في مكتبة الصور: المجاني للحساب، و«Default Profile» قبل الحساب. */
/**
 * روابط **ثبت غياب** لقطتها الثابتة (`…thumb.jpg`) — نسجّلها مرة واحدة في
 * الجلسة حتى لا يُعاد طلب رابط 404 مع كل تمرير أو إعادة تركيب.
 *
 * صور الأصول تُصلَّر في المخزن (بأداة `tools/optimize_site_assets.py` أو زر
 * الأدمن) فتُوجد اللقطة لكل GIF. قبل ذلك، أو إن غابت لصورة بعينها، نرجع
 * إلى الأصل بلا تكرار محاولات فاشلة.
 */
private val missingThumbUrls: MutableSet<String> =
    java.util.Collections.synchronizedSet(mutableSetOf<String>())

/** الرمز الموقّع خاص بمسار واحد؛ لا يجوز إلحاق .thumb.jpg به لمسار آخر. */
private fun assetThumbUrl(url: String?, animate: Boolean = false): String? {
    if (url.isNullOrBlank() || animate || !url.contains(".gif", ignoreCase = true)) return null
    if (url.contains("/object/sign/admin-assets/", ignoreCase = true)) return null
    return "$url.thumb.jpg"
}

private val ASSET_FREE_DEFAULT = listOf("profiles/profiles/onefree.png")
private val ASSET_AUTH_DEFAULT = listOf("profiles/profiles/default-profile.jpg")

/**
 * صورة من مكتبة الأصول (site-assets) مع نسخة محلية احتياطية: تظهر فوراً محلياً
 * ثم تُستبدل بالأصل الحقيقي عند وصوله — فتعمل بلا إنترنت أيضاً.
 *
 * المسارات تُجرَّب **بالترتيب** ثم ننتقل إلى الصورة المحلية. للأفاتار
 * المجاني مسار خادم معتمد واحد فقط (`profiles/profiles/onefree.png`)، والنسخة
 * المحلية هي نفس ملف المالك حرفياً، لذلك يظهر فوراً وبلا اختلاف عند انقطاع الشبكة.
 */
@Composable
private fun SiteAssetOrLocal(
    paths: List<String>,
    local: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val container = com.addonstation.mobile.ui.rememberContainer()
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var index by remember(paths) { mutableStateOf(0) }
    val url = remember(paths, index) { paths.getOrNull(index)?.let { container.siteAssetUrl(it) } }
    // الصورة المحلية (مورد داخل التطبيق) تُرسم كـplaceholder وكخطأ معاً:
    // painterResource لا يبني توليفاً فرعياً (بخلاف SubcomposeAsyncImage) فلا
    // يُثقل عناصر القوائم — وهي التي كانت تُقطّع التمرير.
    val localPainter = painterResource(local)
    if (url.isNullOrBlank()) {
        Image(
            painter = localPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size),
        )
    } else {
        val px = with(density) { size.roundToPx() }.coerceAtLeast(1)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                // مقاس الفكّ = مقاس العرض على الشاشة (لا أبعاد الأصل) ⇒ ذاكرة
                // أقلّ بمرات، وبلا تقطيع عند تمرير شبكات الصور الكبيرة.
                .size(px)
                // Uses the device-adaptive ImageLoader transition policy.
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = localPainter,
            error = localPainter,
            modifier = modifier.size(size),
            onError = {
                // المسار التالي قبل الاستسلام للصورة المحلية (بلا توليف فرعي)
                if (index + 1 < paths.size) index += 1
            },
        )
    }
}

/**
 * بديل الأفاتار: يظهر عند غياب الرابط أو فشل تحميل الصورة.
 *
 * هو الأصل المجاني `onefree` من مكتبة الصور — وهو نفسه الذي تعطيه القاعدة لأي
 * حساب جديد (افتراضي عمود avatar_url)، فيظهر للحساب الجديد مباشرة بعد التسجيل
 * أو إنشاء الحساب، ولكل حساب مسجّل بلا صورة.
 * أمّا **الزائر** (بلا حساب / بعد تسجيل الخروج) فصورته [AnonymousAvatarImage].
 */
@Composable
fun AvatarFallback(size: Dp) {
    SiteAssetOrLocal(ASSET_FREE_DEFAULT, R.drawable.ic_free_profile, size)
}

/**
 * صورة البروفايل الافتراضية التي يطلبها المالك: تظهر في شاشة تسجيل الدخول
 * وفي شاشة إنشاء الحساب (قبل أن يكون للمستخدم صورة).
 */
@Composable
fun DefaultProfileAvatar(
    size: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    AnonymousAvatarImage(size = size, modifier = modifier)
}

/**
 * صورة **المجهول (الزائر)** — صورة المالك `default-profile.jpg`:
 * تُعرض في كل موضع يظهر فيه المستخدم **بلا حساب**:
 *   • قبل إنشاء الحساب أو تسجيل الدخول (شاشات الدخول والإنشاء)؛
 *   • وبعد **تسجيل الخروج**؛
 *   • وللزائر في الرئيسية · الإعدادات · لوحة المستخدم في المجتمع.
 *
 * تختلف عن [AvatarFallback] (صورة `onefree` التي تعطيها القاعدة لكل حساب
 * جديد ولكل حساب بلا صورة) — فالمجهول صورته هذه، والمسجّل صورته `onefree`.
 *
 * تُقرأ من مكتبة الأصول أولاً (`profiles/profiles/default-profile.jpg`) فإن
 * لم توجد عُرضت النسخة المضمّنة داخل التطبيق (`ic_default_profile`).
 */
@Composable
fun AnonymousAvatarImage(
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    borderColor: Color = AppColors.Border2,
    borderWidth: Dp = 1.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .background(AppColors.Elevated2),
    ) {
        SiteAssetOrLocal(ASSET_AUTH_DEFAULT, R.drawable.ic_default_profile, size)
    }
}

/**
 * صورة أصل صغيرة داخل الشبكات (صور البروفايل · البانرات · الأدوات).
 * أخفّ بكثير من [AvatarImage]: بلا توليف فرعي، وبلا مؤخّر متحرّك، وبحجم طلب
 * مضبوط — ففتح قائمة الاختيار لا ينتظر تحميل مئات الصور دفعةً واحدة.
 */
@Composable
fun AssetThumb(url: String?, size: Dp, modifier: Modifier = Modifier, animate: Boolean = false) {
    val effectiveAnimate = animate && animatedPreviewsAllowed()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Elevated2),
    ) {
        if (!url.isNullOrBlank()) {
            // نموذج الرابط مباشرة: بلا ImageRequest.Builder (بلا Crossfade ولا مقاس
            // يدوي) — أفتح الشاشات، وCoil يقرأ المقاس من مكان الخلية نفسه.
            //
            // الصورة المتحركة (GIF) في شبكة Assets تُعرض كلقطة ثابتة: تشغيل عشرات
            // المتحرّكات معاً يستهلك الذاكرة ويجعل التمرير يتهنّج. اللقطة الثابتة
            // تُنتجها أداة «تصغير كل صور الأصول» بنفس المسار + ‎.thumb.jpg‎، فإن لم
            // توجد نرجع إلى الأصل.
            //
            // animate = true (الغلاف المختار في المعاينة) يُبقي GIF متحرّكاً: صورة
            // واحدة فقط على الشاشة فلا خطر على الذاكرة.
            val thumb = remember(url, effectiveAnimate) { assetThumbUrl(url, effectiveAnimate) }
            // غياب اللقطة يُسجَّل في ذاكرة الجلسة (لا تُفقد مع إعادة تدوير الخلية)
            var thumbFailed by remember(url, effectiveAnimate) {
                mutableStateOf(thumb == null || thumb in missingThumbUrls)
            }
            if (thumb != null && !thumbFailed) {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                    onError = {
                        if (thumb != null) missingThumbUrls.add(thumb)
                        thumbFailed = true
                    },
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

/**
 * صورة **غلاف (بانر)** بملء الخلية وبنسبة 16:9 — تُستعمل في شبكة الأغلفة
 * وفي المعاينة داخل «تعديل الملف الشخصي».
 *
 * تختلف عن [AssetThumb] في أمرين:
 *   • تأخذ عرض الخلية كاملاً (لا مربّعاً صغيراً) لأن الغلاف صورة عريضة؛
 *   • `animate = true` افتراضياً ⇒ أي GIF **يتحرّك** في الشبكة وفي المعاينة،
 *     وعدد الأغلفة محدود (عشرات لا مئات) فالتحريك خفيف على الذاكرة.
 */
@Composable
fun BannerThumb(
    url: String?,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    radius: Dp = 10.dp,
) {
    val effectiveAnimate = animate && animatedPreviewsAllowed()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(AppColors.Elevated2),
    ) {
        if (!url.isNullOrBlank()) {
            val thumb = remember(url, effectiveAnimate) { assetThumbUrl(url, effectiveAnimate) }
            var thumbFailed by remember(url, effectiveAnimate) {
                mutableStateOf(thumb == null || thumb in missingThumbUrls)
            }
            AsyncImage(
                model = if (thumb != null && !thumbFailed) thumb else url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = {
                    if (thumb != null && !thumbFailed) missingThumbUrls.add(thumb)
                    thumbFailed = true
                },
            )
        }
    }
}

/** صورة رمزية دائرية مع نقطة حالة اختيارية. */
@Composable
fun AvatarImage(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    online: Boolean? = null,
    borderColor: Color = AppColors.Border2,
    borderWidth: Dp = 1.dp,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(AppColors.Elevated2)
                .border(borderWidth, borderColor, shape),
        ) {
            // سلسلة احتياطية بلا توليف فرعي: صورة المستخدم ⇒ `onefree` من مكتبة
            // الأصول ⇒ المورد المحلي. (كانت SubcomposeAsyncImage تبني توليفاً
            // فرعياً لكل صورة في كل قائمة ⇒ تقطيع في الشات وفي كل شبكة صور.)
            val paths = if (url.isNullOrBlank()) ASSET_FREE_DEFAULT else listOf(url) + ASSET_FREE_DEFAULT
            SiteAssetOrLocal(
                paths = paths,
                local = R.drawable.ic_free_profile,
                size = size,
                modifier = Modifier.size(size).clip(shape),
            )
        }
        if (online != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.26f)
                    .clip(CircleShape)
                    .background(if (online) AppColors.Green else AppColors.Dim)
                    .border(2.dp, AppColors.Surface, CircleShape),
            )
        }
    }
}

/**
 * صورة غلاف مستطيلة بزوايا مستديرة.
 *
 * @param model نموذج بديل (ملف أو Uri محلي) يُحمّل **قبل** رابط الخادم —
 *              يستعمله الشات لمعاينة الصورة/إطار الفيديو لحظة اختياره.
 * @param backgroundColor لون الطبقة خلف الصورة؛ يمكن جعله شفافاً للأيقونات بلا بطاقة.
 */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    model: Any? = null,
    /**
     * `true` ⇒ يُعرض الملف كما هو بحركته (GIF متحرّك) — **للمعاينات المفردة
     * فقط**: صفحة العنصر · الغلاف المختار · عارض الصورة.
     *
     * الافتراضي `false`: الشبكات (المتجر · المفضّلة · بطاقات الرئيسية · الأدوات)
     * تعرض **لقطة ثابتة خفيفة** `…thumb.jpg` إن وُجدت. لماذا؟ تشغيل عشرات
     * الرسوم المتحركة في شبكة واحدة يستهلك الذاكرة والمعالج ويرهن التمرير —
     * وهو أشهر سبب لتقطيع القوائم. اللقطة تُولَّد بأداة
     * `tools/optimize_site_assets.py` (أو بزر الأدمن)، وإن غابت نرجع للأصل
     * بلا محاولات فاشلة متكرّرة (انظر [missingThumbUrls]).
     */
    animate: Boolean = false,
    backgroundColor: Color = AppColors.Elevated2,
) {
    val effectiveAnimate = animate && animatedPreviewsAllowed()
    val thumbCandidate = remember(url, effectiveAnimate) { assetThumbUrl(url) }
    // غياب اللقطة يُسجَّل في ذاكرة الجلسة (لا تُفقد مع إعادة تدوير الخلية)
    var thumbMissing by remember(url) {
        mutableStateOf(thumbCandidate == null || thumbCandidate in missingThumbUrls)
    }
    var failed by remember(url, model) { mutableStateOf(false) }

    val thumb = if (thumbCandidate != null && !thumbMissing) thumbCandidate else null
    // الأساس المعروض: اللقطة الخفيفة إن وُجدت (تظهر فوراً)، وإلا الملف الأصلي
    val base: Any? = thumb ?: (model ?: url)
    // الملف المتحرّك يُعرض **فوق** اللقطة عند جهوزيته — في المعاينات المفردة فقط
    // (صفحة العنصر · بانر الملف الشخصي). النتيجة: ظهور فوري ثم حركة، لا انتظار.
    val motion: Any? = if (effectiveAnimate && thumb != null) url else null

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(backgroundColor),
    ) {
        if (base != null) {
            if (failed) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = AppColors.Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                // AsyncImage بدل SubcomposeAsyncImage: الأغلفة تملأ الخلايا في
                // كل الشبكات (المتجر · الأغلفة · الأدوات) — والتوليف الفرعي فيها
                // كان من أكبر أسباب التقطيع عند التمرير.
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(base).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    onError = {
                        // غابت اللقطة؟ نعود للأصل فوراً ونسجّلها فلا نُعيد الطلب.
                        if (thumb != null) {
                            missingThumbUrls.add(thumb)
                            thumbMissing = true
                        } else {
                            failed = true
                        }
                    },
                )
                if (motion != null) {
                    var motionReady by remember(url) { mutableStateOf(false) }
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(motion).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                        alpha = if (motionReady) 1f else 0f,
                        onSuccess = { motionReady = true },
                    )
                }
            }
        }
    }
}

/* ============================== حالات عامة ============================== */

@Composable
fun LoadingBox(modifier: Modifier = Modifier, text: String? = null) {
    LoadingState(
        modifier = modifier,
        pattern = LoadingPattern.CompactBrand,
        label = text,
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppText.bodySmall, color = AppColors.Muted, textAlign = TextAlign.Center)
    }
}

/** بلاطة إحصائية (رقم بخط البكسل + تسمية صغيرة). */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppColors.Text,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = AppText.statNumber, color = valueColor, maxLines = 1)
        Text(label, style = AppText.tiny, color = AppColors.Muted, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** بلاطة أيقونة صغيرة (34dp) مع تسمية — تُستخدم في الإجراءات السريعة. */
@Composable
fun IconTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.Green,
    badge: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(AppColors.Elevated2)
                .border(1.dp, AppColors.Border, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            if (badge != null) {
                Text(
                    badge,
                    style = AppText.tiny,
                    color = AppColors.Bg,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(AppColors.Red)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        Text(
            label,
            style = AppText.small,
            color = AppColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** شريط تقدم لطيف (المستوى/السمعة). */
@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier, color: Color = AppColors.Green) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(AppColors.Elevated2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))),
        )
    }
}

@Composable
fun SpacerH(height: Dp) = Spacer(Modifier.height(height))

/** بطاقة عدّاد بسيطة (لوحة الإدارة). */
@Composable
fun CounterCard(value: String, label: String, modifier: Modifier = Modifier, accent: Color = AppColors.Green) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = AppText.statNumber, color = accent, maxLines = 1)
        Text(label, style = AppText.tiny, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** صف قابل للنقر بأيقونة وسهم — يستخدم في الإعدادات و«المزيد». */
@Composable
fun RowItem(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.Green,
    /** مورد أيقونة صورة جاهزة (اختياري) — يُعرض بدل الأيقونة المتجهة. */
    iconRes: Int? = null,
    /** صورة ختامية (اختياري) — مثل صورة الأداة المجهّزة، وتتحرّك إن كانت GIF. */
    trailingImageUrl: String? = null,
    /** إظهار سهم الانتقال «›» (يُخفى في الصفوف المعلوماتية مثل «إصدار التطبيق»). */
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (iconRes != null) {
            // أيقونة ملوّنة جاهزة (مربّع بأطراف دائرية) — تُعرض كما هي بلا تلوين
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AppColors.Elevated2),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppText.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = AppColors.Text)
            if (subtitle != null) {
                Text(subtitle, style = AppText.tiny, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingImageUrl != null) {
            CoverImage(url = trailingImageUrl, radius = 8.dp, modifier = Modifier.size(28.dp))
        }
        if (trailing != null) trailing()
        else if (trailingImageUrl == null && showChevron) Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.Dim,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** عنوان شاشة داخلي مع زر رجوع. */
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Elevated2)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = AppColors.Text,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = PixelFont),
            color = AppColors.Text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** فاصل رفيع. */
@Composable
fun DividerLine(modifier: Modifier = Modifier, color: Color = AppColors.Border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** شريحة فلتر (Sort chips). */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) AppColors.AuthBlue.copy(alpha = 0.18f) else AppColors.Elevated)
            .border(1.dp, if (selected) AppColors.AuthBlue else AppColors.Border, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, style = AppText.small, color = if (selected) AppColors.AuthBlueLight else AppColors.Muted, maxLines = 1)
    }
}

/** كيكر + عنوان + نص — بطاقة «كن مبدعاً» وأنواعها. */
@Composable
fun PromoCard(
    kicker: String,
    title: String,
    text: String,
    buttonText: String,
    onButton: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(AppColors.Elevated, AppColors.Elevated),
    accent: Color = AppColors.Green,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Kicker(kicker, accent)
        Text(title, style = AppText.sectionTitle, color = AppColors.Text)
        Text(text, style = AppText.bodySmall, color = AppColors.Muted)
        GradientButton(
            text = buttonText,
            onClick = onButton,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(AppColors.GreenLight, AppColors.Green2),
        )
    }
}

/** بطاقة محتوى (كبير للقوائم الأفقية / عادي للشبكة). */
@Composable
fun ContentCard(
    title: String,
    coverUrl: String?,
    planLabel: String,
    metaLine: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    favourite: Boolean = false,
    onFavourite: (() -> Unit)? = null,
) {
    val width = if (large) 240.dp else Dp.Unspecified
    Column(
        modifier = modifier
            .then(if (large) Modifier.width(width) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            CoverImage(
                url = coverUrl,
                radius = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (large) 120.dp else 108.dp),
            )
            Text(
                text = planLabel,
                style = AppText.tiny,
                color = AppColors.Bg,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(AppColors.Gold)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            if (onFavourite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(AppColors.Bg.copy(alpha = 0.65f))
                        .clickable { onFavourite() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (favourite) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (favourite) AppColors.Red else AppColors.Text,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        Text(
            title,
            style = AppText.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = AppColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(metaLine, style = AppText.tiny, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** صف «عرض المزيد» بنمط بطاقة (Exclusives link row). */
@Composable
fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.Gold,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppText.sectionTitle, color = accent)
            Text(subtitle, style = AppText.tiny, color = AppColors.Muted, maxLines = 2)
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.Dim,
        )
    }
}

/** حشوة سفلية موحّدة = ارتفاع الشريط + منطقة آمنة + 18dp. */
fun bottomContentPadding(): PaddingValues = PaddingValues(bottom = 96.dp, top = 8.dp, start = 14.dp, end = 14.dp)

/**
 * عمود محتوى بعرض أقصى 720dp ومتمركز — كما تطلب الوثيقة للأجهزة اللوحية.
 * الحشوة الأفقية 14dp والعلوية 14dp، والسفلية تُضاف من كل شاشة حسب الشريط السفلي.
 */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** شارة صغيرة منزلقة (Red pill للـ mentions). */
@Composable
fun CountPill(count: Int, color: Color = AppColors.Red) {
    if (count <= 0) return
    Text(
        text = if (count > 99) "99+" else count.toString(),
        style = AppText.tiny.copy(color = Color.White),
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** أفاتار + اسم + شارة رتبة — يُستخدم في قوائم الرسائل والأعضاء. */
@Composable
fun ProfileRow(
    profile: Profile?,
    fallbackName: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarImage(url = avatarUrl, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    profile?.displayName ?: profile?.username ?: fallbackName,
                    style = AppText.body,
                    color = AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile != null) {
                    RankBadge(role = profile.role, plan = profile.effectivePlan)
                }
            }
            Text(
                "@${profile?.username ?: fallbackName}",
                style = AppText.tiny,
                color = AppColors.Muted,
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun BorderBox(
    modifier: Modifier = Modifier,
    color: Color = AppColors.Elevated2,
    border: Color = AppColors.Border,
    radius: Dp = 14.dp,
    stroke: Dp = 1.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .border(BorderStroke(stroke, border), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) { content() }
}


/**
 * عنوان فقرة/قسم في ورقة الأصول: اسم القسم (اسم المجلد) + عدده، يفصل بين الفقرات.
 * يُستخدم في تعديل الملف الشخصي وفي نافذة الملصقات فتبدو الأصول مجمّعة كما في المجلدات.
 */
@Composable
fun AssetSectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title.uppercase(),
            style = AppText.tiny,
            color = AppColors.Green,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            count.toString(),
            style = AppText.tiny,
            color = AppColors.Dim,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppColors.Border),
        )
    }
}
