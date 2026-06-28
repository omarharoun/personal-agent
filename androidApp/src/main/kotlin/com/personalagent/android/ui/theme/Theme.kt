package com.personalagent.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Claude-like theme: warm cream background, warm near-black text, a single coral
 * accent for the primary action. Plain and decluttered — no extra ornament.
 */
private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = OnCoral,
    secondary = InkMuted,
    onSecondary = OnCoral,
    tertiary = Coral,
    background = Bone,
    onBackground = InkNearBlack,
    surface = Bone,
    onSurface = InkNearBlack,
    surfaceVariant = BoneRaised,
    onSurfaceVariant = InkMuted,
    surfaceContainer = BoneRaised,
    surfaceContainerHigh = BoneRaised,
    outline = BoneBorder,
    outlineVariant = BoneBorder,
)

private val DarkColors = darkColorScheme(
    primary = CoralOnDark,
    onPrimary = OnCoral,
    secondary = BoneTextMuted,
    onSecondary = InkNearBlack,
    tertiary = CoralOnDark,
    background = InkBackground,
    onBackground = BoneText,
    surface = InkBackground,
    onSurface = BoneText,
    surfaceVariant = InkSurface,
    onSurfaceVariant = BoneTextMuted,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurface,
    outline = InkBorder,
    outlineVariant = InkBorder,
)

@Composable
fun PersonalAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography, // clean Material3 default sans
        content = content,
    )
}
