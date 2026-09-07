package com.example.aestheticcamera.theme

import androidx.compose.ui.graphics.Color

// ─── Âmbar — Accent Principal ─────────────────────────────────────────────
val Amber300 = Color(0xFFFFE082)
val Amber400 = Color(0xFFFFD54F)  // primary accent
val Amber500 = Color(0xFFFFC107)
val Amber600 = Color(0xFFFFB300)
val AmberGlow = Color(0x33FFD54F) // 20% alpha para glows suaves

// ─── Superfícies (Dark Hierarchy) ─────────────────────────────────────────
val Surface0 = Color(0xFF0A0A0C)  // background raiz
val Surface1 = Color(0xFF121214)  // bottom nav, base dos cards
val Surface2 = Color(0xFF1A1A1E)  // cards elevados, modais
val Surface3 = Color(0xFF222228)  // inputs, pills, chips

// ─── Texto ─────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99FFFFFF) // 60% alpha
val TextTertiary  = Color(0x59FFFFFF) // 35% alpha
val TextDisabled  = Color(0x38FFFFFF) // 22% alpha

// ─── Bordas Glassmorphism ─────────────────────────────────────────────────
val BorderSubtle = Color(0x0FFFFFFF) // 6%  — separadores imperceptíveis
val BorderMid    = Color(0x1FFFFFFF) // 12% — cards padrão
val BorderStrong = Color(0x33FFFFFF) // 20% — cards em destaque
val BorderAmber  = Color(0x66FFD54F) // 40% — glow âmbar

// ─── Status / Feedback ────────────────────────────────────────────────────
val SuccessGreen = Color(0xFF4CAF50)
val ErrorRed     = Color(0xFFEF5350)
val WarningAmber = Amber400
