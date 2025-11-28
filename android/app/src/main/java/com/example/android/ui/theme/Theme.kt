package com.example.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * StockLab 테마 색상
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001849),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1E),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1E),
    surfaceVariant = Color(0xFFE2E2E5),
    onSurfaceVariant = Color(0xFF45464E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93B7FF),
    onPrimary = Color(0xFF00297A),
    primaryContainer = Color(0xFF0041AC),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF003258),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    background = Color(0xFF1C1B1E),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF1C1B1E),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF45464E),
    onSurfaceVariant = Color(0xFFC6C5D0)
)

/**
 * StockLab 테마
 */
@Composable
fun StockLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 타이포그래피
 */
private val Typography = Typography(
    // Material 3 기본 타이포그래피 사용
)