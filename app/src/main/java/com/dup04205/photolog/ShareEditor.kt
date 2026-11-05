package com.dup04205.photolog

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class RatioOption(val label: String, val value: Float?)

private val Ratios = listOf(
    RatioOption("원본", null),
    RatioOption("1:1", 1f),
    RatioOption("4:5", 4f / 5f),
    RatioOption("9:16", 9f / 16f),
)

/**
 * 공유할 사진 크기 조절 화면.
 * 비율을 고르고, 두 손가락으로 확대·끌어서 위치를 맞춘 뒤 기록과 함께 공유한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareEditor(entry: Entry, note: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(entry.uri) {
        bitmap = withContext(Dispatchers.IO) { PhotoStore.loadBitmap(ctx, Uri.parse(entry.uri), 2048) }
    }
    var ratioIdx by remember { mutableIntStateOf(2) } // 기본 4:5
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val hasNote = note.isNotBlank()
    var noteOnPhoto by remember { mutableStateOf(false) }
    var sendText by remember { mutableStateOf(hasNote) }
    var stamp by remember { mutableStateOf(settings.showStamp) }
    var working by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "공유할 사진 크기",
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                val bmp = bitmap
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val ratio = Ratios[ratioIdx].value
                        ?: bmp?.let { it.width.toFloat() / it.height }
                        ?: 1f
                    val w = minOf(maxWidth, maxHeight * ratio)
                    val h = w / ratio
                    Box(
                        Modifier
                            .size(w, h)
                            .clipToBounds()
                            .background(Color.Black)
                            .onSizeChanged { frame = it }
                            .pointerInput(ratioIdx, bmp) {
                                detectTransformGestures { _, pan, z, _ ->
                                    val b = bitmap ?: return@detectTransformGestures
                                    zoom = (zoom * z).coerceIn(1f, 5f)
                                    offset = clampOffset(offset + pan, b, frame, zoom)
                                }
                            },
                    ) {
                        if (bmp != null && frame.width > 0) {
                            val image = remember(bmp) { bmp.asImageBitmap() }
                            Canvas(Modifier.fillMaxSize()) {
                                val s = coverScale(bmp, frame) * zoom
                                val dw = bmp.width * s
                                val dh = bmp.height * s
                                val o = clampOffset(offset, bmp, frame, zoom)
                                drawImage(
                                    image,
                                    dstOffset = IntOffset(
                                        ((size.width - dw) / 2 + o.x).roundToInt(),
                                        ((size.height - dh) / 2 + o.y).roundToInt(),
                                    ),
                                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                                    filterQuality = FilterQuality.High,
                                )
                            }
                        } else {
                            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                        }

                        // 결과 이미지와 같은 비율로 날짜·기록을 미리 보여준다
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            if (stamp) {
                                FilmDateStamp(
                                    entry.date,
                                    Modifier
                                        .align(Alignment.End)
                                        .padding(end = w * 0.045f, bottom = w * 0.03f),
                                    fontSize = (w.value * 0.03f).sp,
                                )
                            }
                            if (noteOnPhoto && hasNote) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color(0xB3000000))
                                            )
                                        )
                                        .padding(horizontal = w * 0.05f, vertical = w * 0.05f)
                                ) {
                                    Text(
                                        note,
                                        color = Color.White,
                                        fontFamily = NoteFont,
                                        fontSize = (w.value * 0.035f).sp,
                                        lineHeight = (w.value * 0.035f * 1.4f).sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    "두 손가락으로 확대하고, 끌어서 보낼 부분을 맞춘다",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ratios.forEachIndexed { i, r ->
                            FilterChip(
                                selected = i == ratioIdx,
                                onClick = {
                                    ratioIdx = i
                                    zoom = 1f
                                    offset = Offset.Zero
                                },
                                label = { Text(r.label) },
                            )
                        }
                    }
                    SettingSwitch(
                        title = "기록을 사진 위에 넣기",
                        sub = if (hasNote) "사진 아래쪽에 기록 글을 얹는다" else "저장된 기록이 없다",
                        checked = noteOnPhoto && hasNote,
                        enabled = hasNote,
                        onChange = { noteOnPhoto = it },
                    )
                    SettingSwitch(
                        title = "기록을 글로 함께 보내기",
                        sub = "카카오톡·메시지에는 글로 붙고, 인스타그램용으로 클립보드에도 복사한다",
                        checked = sendText && hasNote,
                        enabled = hasNote,
                        onChange = { sendText = it },
                    )
                    SettingSwitch(
                        title = "필름카메라 날짜 넣기",
                        sub = null,
                        checked = stamp,
                        onChange = { stamp = it },
                    )
                }

                Button(
                    onClick = {
                        val b = bitmap
                        if (b != null && frame.width > 0) {
                        val ratio = Ratios[ratioIdx].value ?: (b.width.toFloat() / b.height)
                        val crop = cropRect(b, frame, zoom, clampOffset(offset, b, frame, zoom))
                        scope.launch {
                            working = true
                            val u = withContext(Dispatchers.IO) {
                                ShareImage.render(
                                    ctx, b, crop, ratio, entry,
                                    overlayNote = if (noteOnPhoto && hasNote) note else "",
                                    stamp = stamp,
                                )
                            }
                            working = false
                            if (u == null) {
                                Toast.makeText(ctx, "이미지를 만들지 못했습니다", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val withText = sendText && hasNote
                            if (withText) Sharing.copy(ctx, Sharing.text(entry, note))
                            Sharing.sendImage(ctx, u, if (withText) Sharing.text(entry, note) else null)
                            onDismiss()
                        }
                        }
                    },
                    enabled = bmp != null && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (working) "만드는 중…" else "공유") }
            }
        }
    }
}

private fun coverScale(b: Bitmap, f: IntSize): Float =
    maxOf(f.width.toFloat() / b.width, f.height.toFloat() / b.height)

/** 사진이 항상 틀을 빈틈없이 덮도록 이동 범위를 제한한다. */
private fun clampOffset(o: Offset, b: Bitmap, f: IntSize, zoom: Float): Offset {
    if (f.width == 0 || f.height == 0) return Offset.Zero
    val s = coverScale(b, f) * zoom
    val mx = maxOf(0f, (b.width * s - f.width) / 2)
    val my = maxOf(0f, (b.height * s - f.height) / 2)
    return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
}

/** 화면의 틀에 보이는 부분을 원본 사진 좌표로 바꾼다. */
private fun cropRect(b: Bitmap, f: IntSize, zoom: Float, o: Offset): RectF {
    val s = coverScale(b, f) * zoom
    val cx = b.width / 2f - o.x / s
    val cy = b.height / 2f - o.y / s
    val hw = f.width / (2 * s)
    val hh = f.height / (2 * s)
    return RectF(cx - hw, cy - hh, cx + hw, cy + hh)
}
