package com.sight.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val SightBlack = Color(0xFF0A0E14)
val SightCharcoal = Color(0xFF121826)
val SightCard = Color(0xFF1A2233)
val SightCyan = Color(0xFF22D3EE)
val SightBlue = Color(0xFF38BDF8)
val SightText = Color(0xFFE2E8F0)
val SightMuted = Color(0xFF94A3B8)
val SightGreen = Color(0xFF34D399)
val SightAmber = Color(0xFFFBBF24)
val SightRed = Color(0xFFF87171)

private val SightColorScheme = darkColorScheme(
    primary = SightCyan,
    secondary = SightBlue,
    background = SightBlack,
    surface = SightCharcoal,
    surfaceVariant = SightCard,
    onPrimary = Color.Black,
    onBackground = SightText,
    onSurface = SightText
)

@Composable
fun SightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SightColorScheme,
        typography = Typography(
            headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = TextStyle(fontSize = 14.sp),
            bodySmall = TextStyle(fontSize = 12.sp, color = SightMuted)
        ),
        shapes = Shapes(
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        ),
        content = content
    )
}
