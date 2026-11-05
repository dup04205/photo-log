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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private class RatioOption(val label: String, val value: Float?)

private val Ratios = listOf(
    RatioOption("원본", null),
    RatioOption("1:1", 1f),
    RatioOption("4:5", 4f / 5f),
    RatioOption("9:16", 9f / 16f),
)

/** 공유 형식 */
private enum class ShareStyle(val label: String) {
    PHOTO("사진만"),
    OVERLAY("사진 위에 기록"),
    CARD("사진 + 기록 카드"),
}


/**
 * 공유할 사진 크기 조절 화면.
 * 비율과 형식을 고르고, 두 손가락으로 확대·끌어서 위치를 맞춘 뒤 기록과 함께 공유한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareEditor(entry: Entry, note: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
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
    var style by remember { mutableStateOf(ShareStyle.PHOTO) }
    var sendText by remember { mutableStateOf(hasNote) }
    var stamp by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    val density = LocalDensity.current

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
                    val card = style == ShareStyle.CARD

                    // 사진 틀 크기: 카드면 기록·바닥글 자리를 남기고 계산한다
                    val photoBox: @Composable (Dp, Dp) -> Unit = { w, h ->
                        PhotoFrame(
                            bmp = bmp,
                            w = w,
                            h = h,
                            zoom = zoom,
                            offset = offset,
                            frame = frame,
                            onFrame = { frame = it },
                            gestureKey = ratioIdx to style,
                            onTransform = { pan, z ->
                                val b = bitmap
                                if (b != null) {
                                    zoom = (zoom * z).coerceIn(1f, 5f)
                                    offset = clampOffset(offset + pan, b, frame, zoom)
                                }
                            },
                            date = entry.date,
                            stamp = stamp,
                            overlayNote = if (hasNote && style == ShareStyle.OVERLAY) note else null,
                        )
                    }

                    if (!card) {
                        val w = minOf(maxWidth, maxHeight * ratio)
                        photoBox(w, w / ratio)
                    } else {
                        // 카드 결과물과 같은 비율: 가로 1080 중 여백 54, 사진 폭 972
                        val lines = if (hasNote) minOf(ShareImage.noteLineCount(note), ShareImage.MAX_NOTE_LINES) else 0
                        val unit = 1f / 1080f
                        val padF = 54f * unit
                        val textF = if (lines > 0) (54f + lines * (37.8f + 15.1f)) * unit else 0f
                        val footF = (65f + 26f + 54f) * unit
                        // 카드 가로 cw 일 때 높이 = cw * (pad + (0.9/ratio) + text + foot)
                        val hPerW = padF + 0.9f / ratio + textF + footF
                        val cw = minOf(maxWidth, maxHeight / hPerW)
                        val pw = cw * 0.9f
                        Column(
                            Modifier
                                .width(cw)
                                .background(Color(0xFFF7F7F5))
                                .padding(start = cw * 0.05f, end = cw * 0.05f, top = cw * 0.05f, bottom = cw * 0.05f)
                        ) {
                            photoBox(pw, pw / ratio)
                            if (lines > 0) {
                                Text(
                                    note,
                                    color = Color(0xFF111111),
                                    fontFamily = NoteFont,
                                    fontSize = with(density) { (cw * 0.035f).toSp() },
                                    lineHeight = with(density) { (cw * 0.049f).toSp() },
                                    maxLines = ShareImage.MAX_NOTE_LINES,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = cw * 0.05f),
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = cw * 0.05f),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                val foot = buildList {
                                    add(entry.date.replace('-', '.'))
                                    if (entry.lat != null && entry.lon != null) {
                                        add(String.format(Locale.US, "%.4f, %.4f", entry.lat, entry.lon))
                                    }
                                }.joinToString("   ·   ")
                                Text(
                                    foot,
                                    color = Color(0xFF8A8F98),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = with(density) { (cw * 0.024f).toSp() },
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "PHOTO-LOG",
                                    color = Color(0xFF111111),
                                    fontFamily = TitleFont,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = with(density) { (cw * 0.029f).toSp() },
                                )
                            }
                        }
                    }
                }

                Text(
                    "두 손가락으로 확대하고, 끌어서 보낼 부분을 맞춥니다",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareStyle.entries.forEach { st ->
                            FilterChip(
                                selected = st == style,
                                enabled = st == ShareStyle.PHOTO || hasNote,
                                onClick = {
                                    if (st != style) {
                                        style = st
                                        zoom = 1f
                                        offset = Offset.Zero
                                    }
                                },
                                label = { Text(st.label) },
                            )
                        }
                    }
                    SettingSwitch(
                        title = "기록을 글로 함께 보내기",
                        sub = "카카오톡·메시지에는 글로 붙고, 인스타그램용으로 클립보드에도 복사합니다",
                        checked = sendText && hasNote,
                        enabled = hasNote,
                        onChange = { sendText = it },
                    )
                    SettingSwitch(
                        title = "필름카메라 날짜 넣기",
                        sub = "사진의 우측 하단에 날짜를 넣습니다",
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
                            val chosen = style
                            scope.launch {
                                working = true
                                val u = withContext(Dispatchers.IO) {
                                    when {
                                        chosen == ShareStyle.CARD && hasNote ->
                                            ShareImage.renderCard(ctx, b, crop, ratio, entry, note, stamp)
                                        chosen == ShareStyle.OVERLAY && hasNote ->
                                            ShareImage.render(ctx, b, crop, ratio, entry, note, stamp)
                                        else ->
                                            ShareImage.render(ctx, b, crop, ratio, entry, "", stamp)
                                    }
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

/** 확대·이동할 수 있는 사진 틀. 날짜는 항상 우측 하단에 고정, 사진 위 기록은 날짜 위쪽에 놓는다. */
@Composable
private fun PhotoFrame(
    bmp: Bitmap?,
    w: Dp,
    h: Dp,
    zoom: Float,
    offset: Offset,
    frame: IntSize,
    onFrame: (IntSize) -> Unit,
    gestureKey: Any,
    onTransform: (Offset, Float) -> Unit,
    date: String,
    stamp: Boolean,
    overlayNote: String?,
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .size(w, h)
            .clipToBounds()
            .background(Color.Black)
            .onSizeChanged(onFrame)
            .pointerInput(gestureKey, bmp) {
                detectTransformGestures { _, pan, z, _ -> onTransform(pan, z) }
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

        if (overlayNote != null) {
            val bottomGap = if (stamp) w * (0.035f + 0.03f + 0.025f) else w * 0.05f
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                    .padding(start = w * 0.05f, end = w * 0.05f, top = w * 0.05f, bottom = bottomGap)
            ) {
                Text(
                    overlayNote,
                    color = Color.White,
                    fontFamily = NoteFont,
                    fontSize = with(density) { (w * 0.035f).toSp() },
                    lineHeight = with(density) { (w * 0.049f).toSp() },
                    maxLines = ShareImage.MAX_NOTE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (stamp) {
            FilmDateStamp(
                date,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = w * 0.045f, bottom = w * 0.025f),
                fontSize = with(density) { (w * 0.03f).toSp() },
            )
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
