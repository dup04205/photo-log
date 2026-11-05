package com.dup04205.photolog

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object Sharing {
    fun text(e: Entry, note: String): String = buildList {
        if (note.isNotBlank()) add(note)
        add(e.date.replace('-', '.'))
        if (e.lat != null && e.lon != null) {
            add("https://map.kakao.com/link/map/${Uri.encode("촬영 위치")},${e.lat},${e.lon}")
        }
    }.joinToString("\n")

    /** 안드로이드 공유 메뉴를 연다. 인스타그램·카카오톡·메시지 등이 여기에 뜬다. */
    fun send(ctx: Context, image: Uri, e: Entry, note: String) = sendImage(ctx, image, text(e, note))

    fun sendImage(ctx: Context, image: Uri, text: String?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, image)
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("", image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "공유"))
    }

    fun copy(ctx: Context, t: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("PHOTO-LOG", t))
        Toast.makeText(ctx, "메모를 복사했습니다", Toast.LENGTH_SHORT).show()
    }

    fun openMap(ctx: Context, lat: Double, lon: Double) {
        val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode("촬영 위치")})")
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (_: ActivityNotFoundException) {
            val web = Uri.parse("https://map.kakao.com/link/map/${Uri.encode("촬영 위치")},$lat,$lon")
            ctx.startActivity(Intent(Intent.ACTION_VIEW, web))
        }
    }
}

/**
 * 공유 이미지. 가로 1080px.
 * - render: 고른 비율로 자른 사진 (+ 사진 위 기록) (+ 필름 날짜)
 * - renderCard: 사진 + 아래 기록 + 날짜·PHOTO-LOG 바닥글 카드
 * 날짜 각인은 기록 유무와 상관없이 사진의 우측 하단에 고정한다.
 */
object ShareImage {
    /** 사진과 기록을 합쳐 공유할 때 최대 줄 수 */
    const val MAX_NOTE_LINES = 5
    private const val W = 1080
    private const val PAD = W * 0.05f            // 54px
    private const val TEXT_W = (W - 2 * PAD)     // 972px, 카드·사진 위 기록 모두 같은 폭

    private fun noteLayout(text: String, color: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = W * 0.035f
        }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, tp, TEXT_W.toInt())
            .setLineSpacing(W * 0.014f, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    /** 공유 이미지에 넣었을 때 몇 줄이 되는지(엔터 줄 + 자동 줄바꿈). */
    fun noteLineCount(text: String): Int =
        if (text.isEmpty()) 0 else noteLayout(text, 0xFFFFFFFF.toInt()).lineCount

    /** 사진 영역(left..right, ..bottom)의 우측 하단에 필름카메라 날짜를 찍는다. */
    private fun drawStamp(ctx: Context, c: Canvas, date: String, right: Float, bottom: Float, photoW: Float) {
        val size = photoW * 0.03f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFF7A1A.toInt()
            textSize = size
            typeface = font(ctx, R.font.dseg7_classic_italic) ?: Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.06f
            setShadowLayer(size * 0.4f, 0f, 0f, 0xCCFF5A00.toInt())
        }
        c.drawText(stampOf(date), right - photoW * 0.045f, bottom - photoW * 0.035f, paint)
    }

    /** 날짜 각인이 차지하는 아래쪽 높이(사진 위 기록이 날짜와 겹치지 않도록 띄우는 값) */
    private fun stampRowHeight(photoW: Float) = photoW * (0.035f + 0.03f + 0.025f)

    private fun font(ctx: Context, id: Int): Typeface? = try {
        ResourcesCompat.getFont(ctx, id)
    } catch (_: Exception) {
        null
    }

    private fun drawCrop(c: Canvas, src: Bitmap, crop: RectF, dst: RectF) {
        val srcRect = Rect(
            crop.left.roundToInt(), crop.top.roundToInt(),
            crop.right.roundToInt(), crop.bottom.roundToInt(),
        )
        c.drawBitmap(src, srcRect, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun render(
        ctx: Context,
        src: Bitmap,
        crop: RectF,
        ratio: Float,
        e: Entry,
        overlayNote: String,
        stamp: Boolean,
    ): Uri? = save(ctx, e) {
        val w = W
        val h = (w / ratio).roundToInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF000000.toInt())
        drawCrop(c, src, crop, RectF(0f, 0f, w.toFloat(), h.toFloat()))

        if (overlayNote.isNotBlank()) {
            val layout = noteLayout(overlayNote, 0xFFFFFFFF.toInt(), MAX_NOTE_LINES)
            val bottomGap = if (stamp) stampRowHeight(w.toFloat()) else PAD
            val textTop = h - bottomGap - layout.height
            val shadeTop = textTop - PAD
            val shade = Paint().apply {
                shader = LinearGradient(
                    0f, shadeTop, 0f, h.toFloat(),
                    0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, shadeTop, w.toFloat(), h.toFloat(), shade)
            c.save()
            c.translate(PAD, textTop)
            layout.draw(c)
            c.restore()
        }
        if (stamp) drawStamp(ctx, c, e.date, w.toFloat(), h.toFloat(), w.toFloat())
        out
    }

    fun renderCard(
        ctx: Context,
        src: Bitmap,
        crop: RectF,
        ratio: Float,
        e: Entry,
        note: String,
        stamp: Boolean,
    ): Uri? = save(ctx, e) {
        val w = W
        val pw = TEXT_W
        val ph = pw / ratio
        val layout = if (note.isNotBlank()) noteLayout(note, 0xFF111111.toInt(), MAX_NOTE_LINES) else null
        val textGap = W * 0.05f
        val footerGap = W * 0.06f
        val footerSize = W * 0.024f
        val textH = layout?.let { textGap + it.height } ?: 0f
        val h = (PAD + ph + textH + footerGap + footerSize + PAD).roundToInt()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFFF7F7F5.toInt())
        val photo = RectF(PAD, PAD, PAD + pw, PAD + ph)
        drawCrop(c, src, crop, photo)
        if (stamp) drawStamp(ctx, c, e.date, photo.right, photo.bottom, pw)

        if (layout != null) {
            c.save()
            c.translate(PAD, photo.bottom + textGap)
            layout.draw(c)
            c.restore()
        }

        val baseline = h - PAD
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8A8F98.toInt()
            textSize = footerSize
            typeface = Typeface.MONOSPACE
        }
        val parts = mutableListOf(e.date.replace('-', '.'))
        if (e.lat != null && e.lon != null) {
            parts.add(String.format(java.util.Locale.US, "%.4f, %.4f", e.lat, e.lon))
        }
        c.drawText(parts.joinToString("   ·   "), PAD, baseline, foot)
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF111111.toInt()
            textSize = footerSize * 1.2f
            typeface = font(ctx, R.font.playfair_display_semibold_italic) ?: Typeface.SERIF
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("PHOTO-LOG", w - PAD, baseline, brand)
        out
    }

    private fun save(ctx: Context, e: Entry, draw: () -> Bitmap): Uri? = try {
        val bmp = draw()
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "photolog_${e.date.replace("-", "")}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    } catch (t: Throwable) {
        null
    }
}
