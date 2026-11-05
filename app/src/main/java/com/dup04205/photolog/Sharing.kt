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

/** 공유 이미지: 고른 비율로 자른 사진 + (선택) 기록 글 + (선택) 필름카메라 날짜. 가로 1080px. */
object ShareImage {
    fun render(
        ctx: Context,
        src: Bitmap,
        crop: RectF,
        ratio: Float,
        e: Entry,
        overlayNote: String,
        stamp: Boolean,
    ): Uri? {
        return try {
            val w = 1080
            val h = (w / ratio).roundToInt()
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(0xFF000000.toInt())
            val srcRect = Rect(
                crop.left.roundToInt(), crop.top.roundToInt(),
                crop.right.roundToInt(), crop.bottom.roundToInt(),
            )
            c.drawBitmap(src, srcRect, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

            var bottom = h.toFloat()
            if (overlayNote.isNotBlank()) {
                val pad = w * 0.05f
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = w * 0.035f
                }
                val layout = StaticLayout.Builder
                    .obtain(overlayNote, 0, overlayNote.length, tp, (w - 2 * pad).toInt())
                    .setLineSpacing(w * 0.014f, 1f)
                    .setMaxLines(4)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
                val top = h - (layout.height + pad * 2)
                val shade = Paint().apply {
                    shader = LinearGradient(
                        0f, top, 0f, h.toFloat(),
                        0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP,
                    )
                }
                c.drawRect(0f, top, w.toFloat(), h.toFloat(), shade)
                c.save()
                c.translate(pad, top + pad)
                layout.draw(c)
                c.restore()
                bottom = top
            }

            if (stamp) {
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFF7A1A.toInt()
                    textSize = w * 0.03f
                    typeface = try {
                        ResourcesCompat.getFont(ctx, R.font.dseg7_classic_italic)
                    } catch (_: Exception) {
                        null
                    } ?: Typeface.MONOSPACE
                    textAlign = Paint.Align.RIGHT
                    letterSpacing = 0.06f
                    setShadowLayer(w * 0.012f, 0f, 0f, 0xCCFF5A00.toInt())
                }
                c.drawText(stampOf(e.date), w - w * 0.045f, bottom - w * 0.035f, sp)
            }

            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "photolog_${e.date.replace("-", "")}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        } catch (t: Throwable) {
            null
        }
    }
}
