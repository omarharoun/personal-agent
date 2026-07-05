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

/**
 * Hermes-Teal theme — adapted from the Hermes Agent web dashboard so the Life
 * Agent reads as a natural extension of it (see [Color.kt] + ATTRIBUTION.md).
 * Dark ("Hermes Teal") is the default; a warm-paper light theme matches.
 *
 * The signature Hermes treatments carried over: dark-teal canvas + warm-cream
 * text/accent, thin cream-tinted borders on cards, small radii (0.5rem), and
 * UPPERCASE wide-tracked "display" labels for headings/buttons (see [HermesText]).
 */
private val DarkColors = darkColorScheme(
    primary = HermesCream,
    onPrimary = HermesBackground,
    primaryContainer = HermesMuted,
    onPrimaryContainer = HermesCream,
    secondary = HermesAccent,
    onSecondary = HermesCream,
    secondaryContainer = HermesAccent,
    onSecondaryContainer = HermesCream,
    tertiary = HermesEmerald,
    onTertiary = HermesBackground,
    background = HermesBackground,
    onBackground = HermesCream,
    surface = HermesCard,
    onSurface = HermesCream,
    surfaceVariant = HermesMuted,
    onSurfaceVariant = HermesCreamDim,
    surfaceContainerLowest = HermesBackground,
    surfaceContainerLow = HermesCard,
    surfaceContainer = HermesCard,
    surfaceContainerHigh = HermesSecondary,
    surfaceContainerHighest = HermesAccent,
    error = HermesDestructive,
    onError = Color(0xFFFFFFFF),
    errorContainer = HermesDestructive.copy(alpha = 0.15f),
    onErrorContainer = HermesDestructive,
    outline = HermesBorder,
    outlineVariant = HermesBorder,
    inversePrimary = HermesAccent,
)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = PaperAccent,
    onPrimaryContainer = TealPrimary,
    secondary = TealPrimary,
    onSecondary = OnTealPrimary,
    secondaryContainer = PaperAccent,
    onSecondaryContainer = TealPrimary,
    tertiary = TealEmerald,
    onTertiary = PaperSurface,
    background = PaperBackground,
    onBackground = TealInk,
    surface = PaperSurface,
    onSurface = TealInk,
    surfaceVariant = PaperMuted,
    onSurfaceVariant = TealInkDim,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperBackground,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperMuted,
    surfaceContainerHighest = PaperMuted,
    error = HermesDestructive,
    onError = Color(0xFFFFFFFF),
    outline = PaperBorder,
    outlineVariant = PaperBorder,
)

/** Persisted appearance preference. Dark is the default. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

// Hermes uses small radii (0.5rem = 8px default, xl = 12px). Cards/inputs read as
// crisp, lightly-rounded panels rather than pill-soft Material defaults.
private val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * The signature Hermes text treatments, reusable across screens.
 *  - [displayLabel]: UPPERCASE, wide letter-spacing, semi-bold — for section
 *    headers + nav, matching the web's `text-display` (uppercase + tracking).
 *  - [mono]: tabular monospace for technical/metadata readouts (versions, counts).
 */
object HermesText {
    val displayLabel: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 2.2.sp, // ~0.17em tracking like the web display labels
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

// Roomier body leading for chat; button labels get the display treatment
// (uppercase applied at call sites) with wide tracking + bold.
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
    // Dark ("Hermes Teal") by default — even when the system is light.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HermesTypography,
        shapes = HermesShapes,
        content = content,
    )
}
