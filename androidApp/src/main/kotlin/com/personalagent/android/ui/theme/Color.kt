package com.personalagent.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette adapted from the Hermes Agent web dashboard's default "Hermes Teal"
 * (LENS_0) theme so the Life Agent feels like a natural extension of Hermes.
 *
 * Design tokens are adapted from the MIT-licensed Hermes Agent web frontend and
 * @nous-research/ui design system (© 2025 Nous Research) — see ATTRIBUTION.md.
 * The base tokens are `--background #041c1c` (dark teal) and `--midground #ffe6cb`
 * (warm cream, used for text + the primary accent); the surface/muted/border
 * values below are the resolved `color-mix(midground%, background)` blends from
 * the web `index.css`.
 */

// --- Dark: "Hermes Teal" (the default) --------------------------------------
val HermesBackground = Color(0xFF041C1C) // --background
val HermesCard = Color(0xFF0E2423)       // midground 4% over background (--color-card)
val HermesSecondary = Color(0xFF132826)  // midground 6%
val HermesMuted = Color(0xFF182C2A)      // midground 8% (--color-muted)
val HermesAccent = Color(0xFF1D302E)     // midground 10% (--color-accent)
val HermesCream = Color(0xFFFFE6CB)      // --midground (primary text + accent)
val HermesCreamDim = Color(0xFFA79F8D)   // midground ~65% — secondary text
val HermesBorder = Color(0xFF2A3A36)     // midground 15% over background (--color-border)

// Semantic accents (verbatim from the web tokens).
val HermesEmerald = Color(0xFF34D399)    // --series-output-token (positive accent)
val HermesSuccess = Color(0xFF4ADE80)    // --color-success
val HermesWarning = Color(0xFFFFBD38)    // --color-warning
val HermesDestructive = Color(0xFFFB2C36) // --color-destructive

// --- Light: warm-paper counterpart (cohesive, teal-ink) ---------------------
val PaperBackground = Color(0xFFF7F1E7)  // warm paper
val PaperSurface = Color(0xFFFFFFFF)
val PaperMuted = Color(0xFFEFE7D7)
val PaperAccent = Color(0xFFEAF0EC)
val TealPrimary = Color(0xFF0B3B37)      // deep teal (ink/primary on light)
val OnTealPrimary = Color(0xFFFFF3E4)
val TealInk = Color(0xFF0A2422)          // primary text on light
val TealInkDim = Color(0xFF55635F)       // secondary text on light
val PaperBorder = Color(0xFFDDD2BF)
val TealEmerald = Color(0xFF0F9E74)
