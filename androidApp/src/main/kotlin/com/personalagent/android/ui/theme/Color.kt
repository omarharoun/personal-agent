package com.personalagent.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Open-WebUI-style palette — neutral, near-monochrome, dark-first. Surfaces are
 * cool near-blacks/greys with hairline borders; text is light; the only accent is
 * a near-white primary action (the modern "white send button on dark" look) plus
 * a restrained blue for links/selection. The matching light theme inverts to
 * clean whites/greys.
 */

// --- Dark scheme (the default) ----------------------------------------------
val Gray950 = Color(0xFF0D0D0D) // app background (gray-950)
val Gray900 = Color(0xFF171717) // sidebar / cards / top bar
val Gray850 = Color(0xFF1F1F1F) // raised surface / user bubble / code background
val Gray800 = Color(0xFF2A2A2A) // hairline borders / dividers
val GrayText = Color(0xFFECECEC) // primary text on dark
val GrayTextMuted = Color(0xFF9B9B9B) // secondary text on dark

// --- Light scheme (matching) ------------------------------------------------
val White = Color(0xFFFFFFFF) // app background (light)
val Gray50 = Color(0xFFF7F7F8) // sidebar / cards / top bar (light)
val Gray100 = Color(0xFFF0F0F2) // raised surface / user bubble / code background (light)
val Gray200 = Color(0xFFE5E5E5) // hairline borders / dividers (light)
val InkText = Color(0xFF1A1A1A) // primary text on light
val InkTextMuted = Color(0xFF6B6B6B) // secondary text on light

// --- Accents (used sparingly) ------------------------------------------------
// The primary action is near-monochrome: a near-white pill on dark, near-black on
// light, so the composer's send button reads as a clean modern control.
val OnPrimaryDark = Color(0xFF0D0D0D)
val OnPrimaryLight = Color(0xFFFFFFFF)
val AccentBlueDark = Color(0xFF6CA0F6)  // links / selection on dark
val AccentBlueLight = Color(0xFF2563EB) // links / selection on light
