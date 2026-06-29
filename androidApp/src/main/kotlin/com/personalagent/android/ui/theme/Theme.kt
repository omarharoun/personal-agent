package com.personalagent.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Open-WebUI-style theme — neutral, dark-first. Near-black surfaces with hairline
 * borders and light text; a matching clean-white light theme; a near-monochrome
 * primary action (see [Color.kt]). Dark is the default (see [ThemeMode]).
 */
private val DarkColors = darkColorScheme(
    primary = GrayText,            // near-white primary action (white send button)
    onPrimary = OnPrimaryDark,
    secondary = AccentBlueDark,
    onSecondary = OnPrimaryDark,
    tertiary = AccentBlueDark,
    background = Gray950,
    onBackground = GrayText,
    surface = Gray900,
    onSurface = GrayText,
    surfaceVariant = Gray850,
    onSurfaceVariant = GrayTextMuted,
    surfaceContainer = Gray900,
    surfaceContainerHigh = Gray850,
    surfaceContainerHighest = Gray850,
    outline = Gray800,
    outlineVariant = Gray800,
)

private val LightColors = lightColorScheme(
    primary = InkText,             // near-black primary action on light
    onPrimary = OnPrimaryLight,
    secondary = AccentBlueLight,
    onSecondary = OnPrimaryLight,
    tertiary = AccentBlueLight,
    background = White,
    onBackground = InkText,
    surface = Gray50,
    onSurface = InkText,
    surfaceVariant = Gray100,
    onSurfaceVariant = InkTextMuted,
    surfaceContainer = Gray50,
    surfaceContainerHigh = Gray100,
    surfaceContainerHighest = Gray100,
    outline = Gray200,
    outlineVariant = Gray200,
)

/** Persisted appearance preference. Dark is the default. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

// Slightly roomier body line-height than the Material default — chat reads better
// with generous leading. Only the body styles are tweaked; the rest is M3 default.
private val ChatTypography: Typography
    @Composable get() {
        val base = MaterialTheme.typography
        return base.copy(
            bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp, fontSize = 16.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
        )
    }

/** Monospace style for code (inline + blocks), sized to sit calmly in body text. */
val CodeTextStyle: TextStyle
    get() = TextStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    )

@Composable
fun PersonalAgentTheme(
    // Dark by default — even when the system is light — to match the Open WebUI look.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ChatTypography,
        content = content,
    )
}
