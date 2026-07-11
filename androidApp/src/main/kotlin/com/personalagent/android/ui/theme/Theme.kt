package com.personalagent.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalagent.shared.appearance.AccentOption
import com.personalagent.shared.appearance.AccentPalette

/**
 * Neutral base + user-chosen ACCENT theme. The canvas is neutral (charcoal in
 * dark, warm paper in light — see [Color.kt]); the [AccentOption] the user picks
 * in Settings recolors primary / active / highlight roles in BOTH modes. Nothing
 * is force-green anymore. The signature Hermes *treatments* stay (small radii,
 * uppercase wide-tracked display labels, roomy body leading).
 *
 * Both schemes are built at runtime from the accent, so changing the accent
 * recolors every screen (all screens read `MaterialTheme.colorScheme.*`).
 */

private fun Long.toAccentColor(): Color = Color(0xFF000000L or (this and 0xFFFFFF))

/** Mix [a] toward [b] by [r] (0 = all a, 1 = all b). Used for accent-tinted surfaces. */
private fun blend(a: Color, b: Color, r: Float): Color = Color(
    red = a.red + (b.red - a.red) * r,
    green = a.green + (b.green - a.green) * r,
    blue = a.blue + (b.blue - a.blue) * r,
    alpha = 1f,
)

private fun darkScheme(accent: AccentOption) = run {
    val a = accent.darkRgb.toAccentColor()
    val onA = accent.onDarkRgb.toAccentColor()
    darkColorScheme(
        primary = a,
        onPrimary = onA,
        primaryContainer = blend(NeutralDarkCard, a, 0.24f),
        onPrimaryContainer = NeutralDarkInk,
        secondary = a,
        onSecondary = onA,
        secondaryContainer = blend(NeutralDarkSecondary, a, 0.26f),
        onSecondaryContainer = NeutralDarkInk,
        tertiary = a,
        onTertiary = onA,
        background = NeutralDarkBackground,
        onBackground = NeutralDarkInk,
        surface = NeutralDarkCard,
        onSurface = NeutralDarkInk,
        surfaceVariant = NeutralDarkMuted,
        onSurfaceVariant = NeutralDarkInkDim,
        surfaceContainerLowest = NeutralDarkBackground,
        surfaceContainerLow = NeutralDarkCard,
        surfaceContainer = NeutralDarkCard,
        surfaceContainerHigh = NeutralDarkSecondary,
        surfaceContainerHighest = NeutralDarkSecondary,
        error = StatusDestructive,
        onError = Color(0xFFFFFFFF),
        errorContainer = StatusDestructive.copy(alpha = 0.15f),
        onErrorContainer = StatusDestructive,
        outline = NeutralDarkBorder,
        outlineVariant = NeutralDarkBorder,
        inversePrimary = a,
    )
}

private fun lightScheme(accent: AccentOption) = run {
    val a = accent.lightRgb.toAccentColor()
    val onA = accent.onLightRgb.toAccentColor()
    lightColorScheme(
        primary = a,
        onPrimary = onA,
        primaryContainer = blend(NeutralLightMuted, a, 0.16f),
        onPrimaryContainer = NeutralLightInk,
        secondary = a,
        onSecondary = onA,
        secondaryContainer = blend(NeutralLightMuted, a, 0.14f),
        onSecondaryContainer = NeutralLightInk,
        tertiary = a,
        onTertiary = onA,
        background = NeutralLightBackground,
        onBackground = NeutralLightInk,
        surface = NeutralLightSurface,
        onSurface = NeutralLightInk,
        surfaceVariant = NeutralLightMuted,
        onSurfaceVariant = NeutralLightInkDim,
        surfaceContainerLowest = NeutralLightSurface,
        surfaceContainerLow = NeutralLightBackground,
        surfaceContainer = NeutralLightSurface,
        surfaceContainerHigh = NeutralLightMuted,
        surfaceContainerHighest = NeutralLightMuted,
        error = StatusDestructive,
        onError = Color(0xFFFFFFFF),
        outline = NeutralLightBorder,
        outlineVariant = NeutralLightBorder,
    )
}

/** Persisted appearance preference. Dark is the default mode. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

// Small radii (0.5rem = 8px default, xl = 12px): crisp, lightly-rounded panels.
private val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * The signature Hermes text treatments, reusable across screens.
 *  - [displayLabel]: UPPERCASE, wide letter-spacing, semi-bold — section headers/nav.
 *  - [mono]: tabular monospace for technical/metadata readouts (versions, counts).
 */
object HermesText {
    val displayLabel: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 2.2.sp,
        lineHeight = 16.sp,
    )
    val displayLarge: TextStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.6.sp,
        lineHeight = 26.sp,
    )
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 18.sp,
    )
}

private val HermesTypography: Typography
    @Composable get() {
        val base = MaterialTheme.typography
        return base.copy(
            bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp, fontSize = 16.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
            labelLarge = base.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            ),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        )
    }

/** Monospace style for code (inline + blocks), sized to sit calmly in body text. */
val CodeTextStyle: TextStyle
    get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    )

@Composable
fun PersonalAgentTheme(
    // Dark by default — even when the system is light.
    darkTheme: Boolean = true,
    // The user-selected accent (neutral Blue by default; picked in Settings).
    accent: AccentOption = AccentPalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent),
        typography = HermesTypography,
        shapes = HermesShapes,
        content = content,
    )
}
