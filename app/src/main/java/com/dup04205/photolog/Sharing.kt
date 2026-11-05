package com.dup04205.photolog

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import java.util.Locale

object Sharing {
    fun text(e: Entry, note: String): String = buildList {
        if (note.isNotBlank()) add(note)
        add(e.date.replace('-', '.'))
        if (e.lat != null && e.lon != null) {
            add("https://map.kakao.com/link/map/${Uri.encode("촬영 위치")},${e.lat},${e.lon}")
        }
    }.joinToString("\n")

    /** 안드로이드 공유 메뉴를 연다. 인스타그램·카카오톡·메시지 등이 여기에 뜬다. */
    fun send(ctx: Context, image: Uri, e: Entry, note: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, image)
            putExtra(Intent.EXTRA_TEXT, text(e, note))
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

/** 인스타그램 피드 비율(4:5, 1080x1350) 공유 카드: 사진 + 날짜 스탬프 + 메모 + 날짜·좌표 */
object ShareCard {
    fun render(ctx: Context, e: Entry, note: String): Uri? {
        return try {
            val src = PhotoStore.loadBitmap(ctx, Uri.parse(e.uri), 1600) ?: return null
            val w = 1080
            val h = 1350
            val p = 72f
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFFF7F7F5.toInt())

            val pw = w - 2 * p
            val ph = if (note.isNotBlank()) 820f else 1080f
            val s = maxOf(pw / src.width, ph / src.height)
            val sw = pw / s
            val sh = ph / s
            val srcRect = Rect(
                ((src.width - sw) / 2).toInt(), ((src.height - sh) / 2).toInt(),
                ((src.width + sw) / 2).toInt(), ((src.height + sh) / 2).toInt(),
            )
            c.drawBitmap(src, srcRect, RectF(p, p, p + pw, p + ph), Paint(Paint.FILTER_BITMAP_FLAG))

            val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFF7A1A.toInt()
                textSize = 40f
                typeface = try {
                    ResourcesCompat.getFont(ctx, R.font.dseg7_classic_italic)
                } catch (_: Exception) {
                    null
                } ?: Typeface.MONOSPACE
                textAlign = Paint.Align.RIGHT
                letterSpacing = 0.06f
                setShadowLayer(14f, 0f, 0f, 0xCCFF5A00.toInt())
            }
            c.drawText(stampOf(e.date), p + pw - 32f, p + ph - 30f, stamp)

            if (note.isNotBlank()) {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1B1E24.toInt()
                    textSize = 38f
                }
                val layout = StaticLayout.Builder.obtain(note, 0, note.length, tp, pw.toInt())
                    .setLineSpacing(16f, 1f)
                    .setMaxLines(if (e.lat != null) 5 else 6)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
                c.save()
                c.translate(p, p + ph + 40f)
                layout.draw(c)
                c.restore()
            }

            val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8A8F98.toInt()
                textSize = 26f
                typeface = Typeface.MONOSPACE
            }
            val parts = mutableListOf(e.date.replace('-', '.'))
            if (e.lat != null && e.lon != null) {
                parts.add(String.format(Locale.US, "%.4f, %.4f", e.lat, e.lon))
            }
            c.drawText(parts.joinToString("   ·   "), p, h - p, foot)

            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1B1E24.toInt()
                textSize = 26f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            c.drawText("PHOTO-LOG", w - p, h - p, brand)

            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val f = File(dir, "photolog_card_${e.id}.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        } catch (t: Throwable) {
            null
        }
    }
}
