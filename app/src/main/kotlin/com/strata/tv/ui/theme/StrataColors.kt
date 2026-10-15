package com.strata.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * STREAM monochrome design system.
 *
 * Approved palette from design-reference — black / charcoal / silver / white.
 * No purple, blue, neon, or gradient brand accents in chrome UI.
 * Artwork and video retain their natural colours.
 */
object StrataColors {
    // Surfaces
    val SurfaceVoid = Color(0xFF08090B)
    val SurfaceBase = Color(0xFF101114)
    val SurfaceRaised = Color(0xFF191B1F)
    val SurfaceFloat = Color(0xFF202226)
    val SurfaceOverlay = Color(0xFF303238)

    // Cards / borders / selection
    val Card = Color(0xFF202226)
    val Border = Color(0xFF303238)
    val SelectedNav = Color(0xFF34363C)
    val Silver = Color(0xFFD7D9DD)

    // Legacy accent aliases mapped to monochrome so existing call sites
    // stay compiling without purple/cyan leaks.
    val AccentPrimary = Color(0xFFFFFFFF)
    val AccentPrimaryBright = Color(0xFFD7D9DD)
    val AccentPrimaryGlow = Color(0x40FFFFFF)
    val AccentSecondary = Color(0xFFA8AAB0)
    val AccentMagenta = Color(0xFFD7D9DD)
    val AccentBlue = Color(0xFFA8AAB0)
    val BrandGlow = Color(0xFF191B1F)

    // Status — restrained, not neon
    val StatusLive = Color(0xFFE8E8E8)
    val StatusError = Color(0xFFCFCFCF)
    val StatusSuccess = Color(0xFFB8B8B8)

    // Text
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA8AAB0)
    val TextTertiary = Color(0xFF74777E)

    // Gradients for hero readability (neutral black only)
    val GradientVoidStart = Color(0x00000000)
    val GradientVoidMid = Color(0xB3000000)
    val GradientVoidEnd = Color(0xFF08090B)

    val FocusRing = Color(0xFFFFFFFF)
    val FocusGlow = Color(0x33FFFFFF)

    val ShimmerBase = SurfaceRaised
    val ShimmerHighlight = Color(0xFF2A2C30)

    val ChipSurface = Color(0xFF191B1F)
    val ChipBorder = Color(0xFF303238)
}
