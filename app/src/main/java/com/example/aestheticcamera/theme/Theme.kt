package com.example.aestheticcamera.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFFFD54F),
    secondary = androidx.compose.ui.graphics.Color(0xFFFFB300),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFF8C00),
    background = androidx.compose.ui.graphics.Color(0xFF0A0A0C),
    surface = androidx.compose.ui.graphics.Color(0xFF161618),
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    onBackground = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.White
)

@Composable
fun AestheticCameraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        dynamicDarkColorScheme(context).copy(
            primary = androidx.compose.ui.graphics.Color(0xFFFFD54F),
            onPrimary = androidx.compose.ui.graphics.Color.Black
        )
      }
      else -> DarkColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
