package com.dup04205.photolog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoLogTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PhotoLogApp()
                }
            }
        }
    }
}

val Stamp = Color(0xFFE8752A)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B54C9),
    onPrimary = Color.White,
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E8ED),
    onSurfaceVariant = Color(0xFF6B7280),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7D9CF5),
    onPrimary = Color(0xFF0F1320),
    background = Color(0xFF121418),
    surface = Color(0xFF1B1E24),
    surfaceVariant = Color(0xFF2B2F37),
    onSurfaceVariant = Color(0xFF9097A3),
)

@Composable
fun PhotoLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
