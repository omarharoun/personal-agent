package com.personalagent.shared.appearance

/**
 * The curated set of user-selectable ACCENT colors — the single source of truth so
 * Android (Compose) and iOS (SwiftUI) offer the exact same options and hexes (the
 * same "define shared copy once" move as `LearningStatusText`/`SuggestionChips`).
 *
 * The app is no longer force-branded a fixed dark green. The base surfaces are
 * NEUTRAL (charcoal in dark, warm paper in light — see each platform's theme) and
 * the accent chosen here recolors highlights / primary / active states in BOTH
 * modes. Every option ships a dark-mode and a light-mode variant so contrast holds
 * on both canvases, plus the ink color to place ON the accent when it's a fill.
 *
 * Colors are RGB longs (0xRRGGBB); each platform adds the alpha channel.
 */
data class AccentOption(
    val id: String,
    val name: String,
    /** Accent used on the dark (charcoal) canvas. */
    val darkRgb: Long,
    /** Ink/text placed on top of the dark-mode accent when it's a fill. */
    val onDarkRgb: Long,
    /** Accent used on the light (warm-paper) canvas. */
    val lightRgb: Long,
    /** Ink/text placed on top of the light-mode accent when it's a fill. */
    val onLightRgb: Long,
)

object AccentPalette {

    /**
     * ~8 tasteful options, each legible on both dark and light. The dark variant is
     * brighter (reads on charcoal); the light variant is deeper (reads on paper).
     */
    val OPTIONS: List<AccentOption> = listOf(
        AccentOption("teal", "Teal", 0x2DD4BF, 0x04211E, 0x0F9E74, 0xFFFFFF),
        AccentOption("blue", "Blue", 0x60A5FA, 0x0A1A2F, 0x2563EB, 0xFFFFFF),
        AccentOption("violet", "Violet", 0xA78BFA, 0x1A1030, 0x7C3AED, 0xFFFFFF),
        AccentOption("amber", "Amber", 0xFBBF24, 0x2A1E00, 0xB45309, 0xFFFFFF),
        AccentOption("rust", "Coral", 0xFB8C5A, 0x2A1200, 0xC2410C, 0xFFFFFF),
        AccentOption("green", "Green", 0x4ADE80, 0x052E16, 0x16A34A, 0xFFFFFF),
        AccentOption("rose", "Rose", 0xFB7185, 0x2A0813, 0xE11D48, 0xFFFFFF),
        AccentOption("slate", "Slate", 0x94A3B8, 0x0B1220, 0x475569, 0xFFFFFF),
    )

    /**
     * A sensible NEUTRAL default (deliberately not the old forced green): a calm,
     * universally-legible blue.
     */
    const val DEFAULT_ID: String = "blue"

    val DEFAULT: AccentOption get() = byId(DEFAULT_ID)

    /** Resolve an id to its option, falling back to [DEFAULT] for unknown ids. */
    fun byId(id: String?): AccentOption =
        OPTIONS.firstOrNull { it.id == id } ?: OPTIONS.first { it.id == DEFAULT_ID }
}
