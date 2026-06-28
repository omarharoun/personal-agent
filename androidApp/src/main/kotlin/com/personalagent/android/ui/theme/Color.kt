package com.personalagent.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Claude-like palette — warm, plain, decluttered. One accent (coral/clay) used
 * sparingly for the primary action; everything else is warm neutrals.
 */

// --- Core tokens ---
val Bone = Color(0xFFF0EEE6)        // warm cream/ivory background (light)
val BoneRaised = Color(0xFFFAF9F5)  // slightly lighter raised surface (cards/input)
val BoneBorder = Color(0xFFE4E2D9)  // subtle warm border/outline
val InkNearBlack = Color(0xFF1F1E1D) // warm near-black text
val InkMuted = Color(0xFF6B6A65)     // muted warm grey for secondary text

val Coral = Color(0xFFD97757)        // Claude clay/coral accent (primary action)
val CoralOnDark = Color(0xFFE0896B)  // slightly lifted coral for dark surfaces
val OnCoral = Color(0xFFFFFFFF)      // text/icon on the coral accent

// --- Dark scheme tokens ---
val InkBackground = Color(0xFF1F1E1D) // warm near-black background (dark)
val InkSurface = Color(0xFF262624)    // raised surface (dark)
val InkBorder = Color(0xFF3A3A37)     // subtle border (dark)
val BoneText = Color(0xFFF0EEE6)      // warm off-white text (dark)
val BoneTextMuted = Color(0xFFAFADA4) // muted text (dark)
