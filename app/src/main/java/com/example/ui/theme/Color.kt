package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Ultra-Dark Pitch Black & Very Deep Royal Purple Palette (No garish bright purple)
val FoulBlackBg = Color(0xFF040407)
val FoulBottomBarBlack = Color(0xFF020204)
val FoulSurfaceDark = Color(0xFF0A0A10)
val FoulCardDark = Color(0xFF101018)
val FoulCardElevated = Color(0xFF171722)

// Very dark, refined purple tones for subtle depth & active containers
val FoulPurpleDeep = Color(0xFF18092C)
val FoulPurplePrimary = Color(0xFF260F45)
val FoulPurpleBright = Color(0xFF34155E)
val FoulPurpleGlow = Color(0xFF4C1D95)
val FoulAccentPink = Color(0xFFFFFFFF)

// Crisp White & Neutral Silver Typography (No purple text)
val FoulTextPrimary = Color(0xFFFFFFFF)
val FoulTextSecondary = Color(0xFFD4D4D8)
val FoulTextMuted = Color(0xFF8E8E99)

// Functional & Match Accents
val FoulDivider = Color(0xFF1F1F2E)
val FoulLiveRed = Color(0xFFEF4444)
val FoulLiveGreen = Color(0xFF10B981)
val FoulTeamAwayCyan = Color(0xFF0EA5E9)
val FoulTeamHomeIndigo = Color(0xFF6366F1)
val FoulPitchGreenDark = Color(0xFF0D3B26)
val FoulPitchGreenLight = Color(0xFF124A30)
val FoulGoldStar = Color(0xFFF59E0B)

// Ordered Colorful Divider Brush ("الخط الفاصل بشكل مرتب ملون بترتيب")
val FoulOrderedColorLineBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF1E0B36),
        Color(0xFF4C1D95),
        Color(0xFF2563EB),
        Color(0xFF06B6D4),
        Color(0xFF10B981),
        Color(0xFF2563EB),
        Color(0xFF4C1D95),
        Color(0xFF1E0B36)
    )
)
