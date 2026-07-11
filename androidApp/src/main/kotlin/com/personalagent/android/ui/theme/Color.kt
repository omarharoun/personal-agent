package com.personalagent.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NEUTRAL base palette. The app is no longer force-branded a fixed dark green
 * ("Hermes Teal"): the canvases here are neutral — a warm charcoal in dark, warm
 * paper in light — and the ACCENT the user picks in Settings (from the shared
 * [com.personalagent.shared.appearance.AccentPalette]) recolors highlights /
 * primary / active states on top, in BOTH modes. See [Theme.kt], which blends the
 * chosen accent into these neutrals at runtime.
 *
 * Only [Theme.kt] references these; every screen uses `MaterialTheme.colorScheme.*`
 * tokens, so switching the accent recolors everything with no per-screen changes.
 */

// --- Dark: neutral warm-charcoal (NOT teal/green) ---------------------------
val NeutralDarkBackground = Color(0xFF121415) // near-black warm charcoal
val NeutralDarkCard = Color(0xFF1B1E20)       // cards / surfaces
val NeutralDarkSecondary = Color(0xFF23272A)  // raised container
val NeutralDarkMuted = Color(0xFF212528)      // muted surface-variant
val NeutralDarkField = Color(0xFF161819)      // input fields, set off the bg
val NeutralDarkInk = Color(0xFFECEAE4)         // primary text (warm near-white)
val NeutralDarkInkDim = Color(0xFFA9ADAB)      // secondary text (WCAG-AA on the bg)
val NeutralDarkBorder = Color(0xFF35393C)      // dividers / card borders

// --- Light: neutral warm-paper (NOT green-tinted) ---------------------------
val NeutralLightBackground = Color(0xFFF6F3EC) // warm paper
val NeutralLightSurface = Color(0xFFFFFFFF)
val NeutralLightMuted = Color(0xFFECE7DD)      // muted surface-variant
val NeutralLightField = Color(0xFFFFFFFF)
val NeutralLightInk = Color(0xFF1D1F20)        // primary text (near-black ink)
val NeutralLightInkDim = Color(0xFF5D605F)     // secondary text
val NeutralLightBorder = Color(0xFFDED8CC)     // dividers / card borders

// --- Semantic accents (mode-independent; NOT the user accent) ----------------
val StatusSuccess = Color(0xFF4ADE80)
val StatusWarning = Color(0xFFFFBD38)
val StatusDestructive = Color(0xFFFB2C36)
