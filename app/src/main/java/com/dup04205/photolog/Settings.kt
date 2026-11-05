package com.dup04205.photolog

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 앱 설정. 폰 안의 SharedPreferences에 저장한다. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("photolog", Context.MODE_PRIVATE)

    /** 필름카메라 날짜 각인 표시 (상세 화면 + 공유 이미지 기본값) */
    var showStamp by mutableStateOf(prefs.getBoolean(KEY_STAMP, true))
        private set

    fun updateShowStamp(on: Boolean) {
        showStamp = on
        prefs.edit().putBoolean(KEY_STAMP, on).apply()
    }

    private companion object {
        const val KEY_STAMP = "showStamp"
    }
}

val LocalSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val settings = LocalSettings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column {
                SettingSwitch(
                    title = "필름카메라 날짜 표시",
                    sub = "사진 화면과 공유 이미지에 '26 11 05 형식의 날짜 각인을 넣는다",
                    checked = settings.showStamp,
                    onChange = { settings.updateShowStamp(it) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
fun SettingSwitch(
    title: String,
    sub: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sub != null) {
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
