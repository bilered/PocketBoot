package com.pocketboot.app

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SurfaceMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED")
}

data class PocketPalette(
    val isDark: Boolean,
    val bgCanvas: Color,
    val surfaceCard: Color,
    val surfaceSubtle: Color,
    val surfaceInput: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentBg: Color,
    val onAccent: Color,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val infoBlue: Color,
    val infoBlueBg: Color,
    val violet: Color,
    val violetBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val switchOffTrack: Color,
    val switchOffThumb: Color,
    val switchOffBorder: Color
)

val LocalPocketPalette = staticCompositionLocalOf {
    buildPocketPalette(SurfaceMode.DARK, useMonet = false, customAccent = Color(0xFF6366F1), monetAccent = null)
}

object PocketColors {
    val bgCanvas: Color @Composable get() = LocalPocketPalette.current.bgCanvas
    val surfaceCard: Color @Composable get() = LocalPocketPalette.current.surfaceCard
    val surfaceSubtle: Color @Composable get() = LocalPocketPalette.current.surfaceSubtle
    val surfaceInput: Color @Composable get() = LocalPocketPalette.current.surfaceInput
    val border: Color @Composable get() = LocalPocketPalette.current.border
    val accent: Color @Composable get() = LocalPocketPalette.current.accent
    val accentSoft: Color @Composable get() = LocalPocketPalette.current.accentSoft
    val accentBg: Color @Composable get() = LocalPocketPalette.current.accentBg
    val onAccent: Color @Composable get() = LocalPocketPalette.current.onAccent
    val success: Color @Composable get() = LocalPocketPalette.current.success
    val successBg: Color @Composable get() = LocalPocketPalette.current.successBg
    val warning: Color @Composable get() = LocalPocketPalette.current.warning
    val warningBg: Color @Composable get() = LocalPocketPalette.current.warningBg
    val danger: Color @Composable get() = LocalPocketPalette.current.danger
    val dangerBg: Color @Composable get() = LocalPocketPalette.current.dangerBg
    val infoBlue: Color @Composable get() = LocalPocketPalette.current.infoBlue
    val infoBlueBg: Color @Composable get() = LocalPocketPalette.current.infoBlueBg
    val violet: Color @Composable get() = LocalPocketPalette.current.violet
    val violetBg: Color @Composable get() = LocalPocketPalette.current.violetBg
    val textPrimary: Color @Composable get() = LocalPocketPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalPocketPalette.current.textSecondary
    val textMuted: Color @Composable get() = LocalPocketPalette.current.textMuted
    val onBrightSurface: Color = Color(0xFF09090B)

    val monoStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
}

private fun blend(c1: Color, c2: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = c1.red * inv + c2.red * ratio,
        green = c1.green * inv + c2.green * ratio,
        blue = c1.blue * inv + c2.blue * ratio,
        alpha = 1f
    )
}

fun buildPocketPalette(
    surfaceMode: SurfaceMode,
    useMonet: Boolean,
    customAccent: Color,
    monetAccent: Color?
): PocketPalette {
    val baseAccent = if (useMonet && monetAccent != null) monetAccent else customAccent
    val isDark = surfaceMode != SurfaceMode.LIGHT

    val accent = if (isDark) {
        if (baseAccent.luminance() < 0.22f) blend(baseAccent, Color.White, 0.28f) else baseAccent
    } else {
        if (baseAccent.luminance() > 0.56f) blend(baseAccent, Color.Black, 0.46f) else baseAccent
    }

    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF09090B) else Color.White
    val darkAccentSoft = if (accent.luminance() > 0.55f) blend(accent, Color.White, 0.14f) else blend(accent, Color.White, 0.34f)

    return when (surfaceMode) {
        SurfaceMode.LIGHT -> PocketPalette(
            isDark = false,
            bgCanvas = Color(0xFFF3F5FA),
            surfaceCard = Color(0xFFFFFFFF),
            surfaceSubtle = Color(0xFFECEFF6),
            surfaceInput = Color(0xFFF7F8FC),
            border = Color(0xFFDDE2EE),
            accent = accent,
            accentSoft = blend(accent, Color.Black, 0.14f),
            accentBg = blend(accent, Color.White, 0.87f),
            onAccent = onAccent,
            success = Color(0xFF047857),
            successBg = Color(0xFFE6FAF2),
            warning = Color(0xFF9A4D00),
            warningBg = Color(0xFFFEF5E0),
            danger = Color(0xFFB91C1C),
            dangerBg = Color(0xFFFEEAEA),
            infoBlue = Color(0xFF0369A1),
            infoBlueBg = Color(0xFFE0F2FE),
            violet = Color(0xFF6D28D9),
            violetBg = Color(0xFFEDE9FE),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textMuted = Color(0xFF56657A),
            switchOffTrack = Color(0xFFCBD5E1),
            switchOffThumb = Color(0xFFFFFFFF),
            switchOffBorder = Color(0xFF94A3B8)
        )

        SurfaceMode.DARK -> PocketPalette(
            isDark = true,
            bgCanvas = Color(0xFF0A0D13),
            surfaceCard = Color(0xFF131823),
            surfaceSubtle = Color(0xFF1C2333),
            surfaceInput = Color(0xFF0E121B),
            border = Color(0xFF263044),
            accent = accent,
            accentSoft = darkAccentSoft,
            accentBg = blend(accent, Color(0xFF0A0D13), 0.84f),
            onAccent = onAccent,
            success = Color(0xFF10B981),
            successBg = Color(0xFF0B2B22),
            warning = Color(0xFFF59E0B),
            warningBg = Color(0xFF2D2009),
            danger = Color(0xFFF87171),
            dangerBg = Color(0xFF311318),
            infoBlue = Color(0xFF38BDF8),
            infoBlueBg = Color(0xFF0D2738),
            violet = Color(0xFFA78BFA),
            violetBg = Color(0xFF22183A),
            textPrimary = Color(0xFFF4F6FA),
            textSecondary = Color(0xFF9AA6BA),
            textMuted = Color(0xFF8593AA),
            switchOffTrack = Color(0xFF1E2636),
            switchOffThumb = Color(0xFFD7DFEC),
            switchOffBorder = Color(0xFF36425A)
        )

        SurfaceMode.AMOLED -> PocketPalette(
            isDark = true,
            bgCanvas = Color(0xFF000000),
            surfaceCard = Color(0xFF0B0E14),
            surfaceSubtle = Color(0xFF141925),
            surfaceInput = Color(0xFF06080C),
            border = Color(0xFF202838),
            accent = accent,
            accentSoft = darkAccentSoft,
            accentBg = blend(accent, Color(0xFF000000), 0.85f),
            onAccent = onAccent,
            success = Color(0xFF10B981),
            successBg = Color(0xFF062119),
            warning = Color(0xFFF59E0B),
            warningBg = Color(0xFF261A06),
            danger = Color(0xFFF87171),
            dangerBg = Color(0xFF2B0F13),
            infoBlue = Color(0xFF38BDF8),
            infoBlueBg = Color(0xFF082030),
            violet = Color(0xFFA78BFA),
            violetBg = Color(0xFF1C1330),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF9AA6BA),
            textMuted = Color(0xFF818FA6),
            switchOffTrack = Color(0xFF161C29),
            switchOffThumb = Color(0xFFD7DFEC),
            switchOffBorder = Color(0xFF2F3A50)
        )
    }
}

private val PocketTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.35).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun PocketBootTheme(
    surfaceMode: SurfaceMode,
    useMonet: Boolean,
    accentHex: Long,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val monetAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (surfaceMode == SurfaceMode.LIGHT) {
            dynamicLightColorScheme(context).primary
        } else {
            dynamicDarkColorScheme(context).primary
        }
    } else {
        null
    }

    val target = buildPocketPalette(
        surfaceMode = surfaceMode,
        useMonet = useMonet,
        customAccent = Color(accentHex),
        monetAccent = monetAccent
    )

    val animSpec = tween<Color>(durationMillis = 260, easing = FastOutSlowInEasing)
    val bgCanvas by animateColorAsState(target.bgCanvas, animSpec, label = "bgCanvas")
    val surfaceCard by animateColorAsState(target.surfaceCard, animSpec, label = "surfaceCard")
    val surfaceSubtle by animateColorAsState(target.surfaceSubtle, animSpec, label = "surfaceSubtle")
    val surfaceInput by animateColorAsState(target.surfaceInput, animSpec, label = "surfaceInput")
    val border by animateColorAsState(target.border, animSpec, label = "border")
    val accent by animateColorAsState(target.accent, animSpec, label = "accent")
    val accentSoft by animateColorAsState(target.accentSoft, animSpec, label = "accentSoft")
    val accentBg by animateColorAsState(target.accentBg, animSpec, label = "accentBg")
    val onAccent by animateColorAsState(target.onAccent, animSpec, label = "onAccent")

    val palette = target.copy(
        bgCanvas = bgCanvas,
        surfaceCard = surfaceCard,
        surfaceSubtle = surfaceSubtle,
        surfaceInput = surfaceInput,
        border = border,
        accent = accent,
        accentSoft = accentSoft,
        accentBg = accentBg,
        onAccent = onAccent
    )

    val m3Scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentBg,
            onPrimaryContainer = palette.accentSoft,
            secondary = palette.success,
            tertiary = palette.warning,
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.surfaceCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSubtle,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentBg,
            onPrimaryContainer = palette.accentSoft,
            secondary = palette.success,
            tertiary = palette.warning,
            background = palette.bgCanvas,
            onBackground = palette.textPrimary,
            surface = palette.surfaceCard,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSubtle,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger
        )
    }

    CompositionLocalProvider(LocalPocketPalette provides palette) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = PocketTypography,
            content = content
        )
    }
}

/**
 * Tactile spring press scale animation for cards, tiles, chips, and pill buttons.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

@Composable
fun PulsingStatusDot(
    color: Color,
    pulsing: Boolean,
    dotSize: Dp = 8.dp
) {
    if (!pulsing) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
        return
    }
    val infinite = rememberInfiniteTransition(label = "statusPulse")
    val haloScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 2.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloScale"
    )
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAlpha"
    )
    Box(
        modifier = Modifier.size(dotSize * 2f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun PocketSectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
            color = PocketColors.textMuted
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = PocketColors.monoStyle,
                color = PocketColors.textMuted
            )
        }
    }
}

@Composable
fun PocketHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = PocketColors.border
    )
}

@Composable
fun PocketSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = PocketColors.accent
) {
    val palette = LocalPocketPalette.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else palette.switchOffTrack,
        animationSpec = tween(180),
        label = "switchTrack"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) activeColor else palette.switchOffBorder,
        animationSpec = tween(180),
        label = "switchBorder"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) palette.onAccent else palette.switchOffThumb,
        animationSpec = tween(180),
        label = "switchThumb"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "switchThumbOffset"
    )

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

object PocketTokens {
    val PrimaryButtonHeight = 48.dp
    val SecondaryButtonHeight = 42.dp
    val InputHeight = 52.dp
    val HeroCardRadius = 16.dp
    val CardRadius = 14.dp
    val InnerCardRadius = 12.dp
    val ButtonRadius = 12.dp
    val CompactButtonRadius = 10.dp
    val PillRadius = 999.dp
}

@Composable
fun PocketSheetHeader(
    icon: ImageVector,
    tint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    subtitleMono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = PocketColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = if (subtitleMono) PocketColors.monoStyle else MaterialTheme.typography.bodySmall,
                    color = PocketColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun PocketStatusBadge(
    text: String,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PocketBanner(
    icon: ImageVector,
    text: String,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = background,
        border = BorderStroke(0.75.dp, tint.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
        }
    }
}
