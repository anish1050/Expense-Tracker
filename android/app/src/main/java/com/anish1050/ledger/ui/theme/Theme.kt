package com.anish1050.ledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monochrome palette from the mockup. Chip is the only decorative accent; Credit
// and Over are semantic (income, over budget) and never used for decoration.
val Ink = Color(0xFF111111)
val Paper = Color(0xFFFFFFFF)
val Mist = Color(0xFFF2F2F0)
val Graphite = Color(0xFF777777)
val Line = Color(0xFFD9D9D6)
val Chip = Color(0xFFFFF1C2)
val Credit = Color(0xFF1F7A3A)
val Over = Color(0xFFB3261E)

val Pill = RoundedCornerShape(50)

private val scheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Graphite,
    outline = Line,
    error = Over,
)

// Tabular figures keep amounts from jiggling sideways while they count up, and
// tight tracking on the large sizes gives the dense, iOS-style display look.
private const val TABULAR = "tnum"

private val type = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = Graphite),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 0.6.sp, color = Graphite),
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
