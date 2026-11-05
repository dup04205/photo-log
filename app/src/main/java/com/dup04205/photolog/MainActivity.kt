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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = AppSettings(applicationContext)
        setContent {
            CompositionLocalProvider(LocalSettings provides settings) {
                PhotoLogTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        PhotoLogApp()
                    }
                }
            }
        }
    }
}

val Stamp = Color(0xFFE8752A)

/** 제목: 세리프 이탤릭(Playfair Display) */
val TitleFont = FontFamily(
    Font(R.font.playfair_display_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.playfair_display_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** 사이드바: 부드러운 바탕체(고운바탕) */
val IndexFont = FontFamily(
    Font(R.font.gowun_batang_regular, FontWeight.Normal),
    Font(R.font.gowun_batang_bold, FontWeight.Bold),
)

/** 필름카메라 날짜 각인: 7세그먼트 이탤릭(DSEG7 Classic) */
val StampFont = FontFamily(Font(R.font.dseg7_classic_italic))

/** 메모: 폰의 시스템 글꼴을 그대로 따른다(삼성 글꼴 스타일 등 포함). */
val NoteFont = FontFamily.Default

// 기본 모드는 흰색, 다크 모드는 검정. 강조색은 날짜 스탬프 주황 하나만 쓴다.
private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF111111),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF111111),
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF6E6E6E),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFF4F4F4),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE6E6E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFF5F5F5),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFFF5F5F5),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color.Black,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF2A2A2A),
)

@Composable
fun PhotoLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
