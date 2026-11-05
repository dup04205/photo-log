package com.dup04205.photolog

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoInfo(val date: String, val lat: Double?, val lon: Double?)

/**
 * 사진 파일을 폰의 공용 사진 폴더(Pictures/PHOTO-LOG)에 저장하고 읽는다.
 * 갤러리 앱과 파일 앱에서 그대로 보이며, 앱을 지워도 사진은 남는다.
 */
object PhotoStore {
    private const val FOLDER = "Pictures/PHOTO-LOG"

    fun wantedPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_IMAGES)
        else add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }.toTypedArray()

    /** 폴더에 빈 사진 항목을 만든다. 카메라는 여기에 바로 쓴다. */
    fun newImageUri(
        ctx: Context,
        pending: Boolean = false,
        mime: String = "image/jpeg",
        ext: String = "jpg",
    ): Uri? = try {
        val name = "PHOTOLOG_" +
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + "." + ext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
            if (pending) put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (e: Exception) {
        null
    }

    private fun publish(ctx: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        ctx.contentResolver.update(uri, values, null, null)
    }

    fun discard(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    /** 폴더의 사진 파일을 지운다. 앱을 재설치한 뒤에는 이전 사진을 지울 권한이 없어 false가 될 수 있다. */
    fun delete(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        false
    }

    /** 카메라 촬영이 끝난 사진에서 날짜·위치를 읽는다. 사진에 위치가 없으면 방금 전 폰 위치로 보완한다. */
    fun finishCapture(ctx: Context, uri: Uri): PhotoInfo {
        val bytes = readBytes(ctx, original(ctx, uri)) ?: readBytes(ctx, uri)
        val info = bytes?.let { parse(it) } ?: PhotoInfo(today(), null, null)
        if (info.lat != null) return info
        val loc = lastLocation(ctx) ?: return info
        return info.copy(lat = loc.latitude, lon = loc.longitude)
    }

    /** 앨범 사진을 원본 그대로(EXIF 포함) PHOTO-LOG 폴더에 복사한다. */
    fun importFrom(ctx: Context, src: Uri): Pair<Uri, PhotoInfo>? {
        val bytes = readBytes(ctx, original(ctx, src)) ?: readBytes(ctx, src) ?: return null
        val mime = ctx.contentResolver.getType(src) ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        val dst = newImageUri(ctx, pending = true, mime = mime, ext = ext) ?: return null
        return try {
            val out = ctx.contentResolver.openOutputStream(dst) ?: throw IllegalStateException()
            out.use { it.write(bytes) }
            publish(ctx, dst)
            dst to (parse(bytes) ?: PhotoInfo(today(), null, null))
        } catch (e: Exception) {
            discard(ctx, dst)
            null
        }
    }

    /** 안드로이드가 위치 정보를 지우지 않은 원본 사진 주소. 권한이 없으면 원래 주소를 돌려준다. */
    private fun original(ctx: Context, uri: Uri): Uri {
        if (!granted(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION)) return uri
        return try {
            val media = if (DocumentsContract.isDocumentUri(ctx, uri)) {
                MediaStore.getMediaUri(ctx, uri) ?: uri
            } else uri
            if (media.authority == MediaStore.AUTHORITY) MediaStore.setRequireOriginal(media) else uri
        } catch (_: Exception) {
            uri
        }
    }

    private fun readBytes(ctx: Context, uri: Uri): ByteArray? = try {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun parse(bytes: ByteArray): PhotoInfo? = try {
        val ex = ExifInterface(ByteArrayInputStream(bytes))
        val ll = ex.latLong
        val dt = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: ex.getAttribute(ExifInterface.TAG_DATETIME)
        val date = dt
            ?.takeIf { it.length >= 10 && !it.startsWith("0000") }
            ?.substring(0, 10)
            ?.replace(':', '-')
            ?: today()
        PhotoInfo(date, ll?.getOrNull(0), ll?.getOrNull(1))
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(ctx: Context): Location? {
        if (!granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) &&
            !granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        ) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        return lm.getProviders(true)
            .mapNotNull { p ->
                try {
                    lm.getLastKnownLocation(p)
                } catch (_: Exception) {
                    null
                }
            }
            .filter { now - it.time < 15 * 60 * 1000 }
            .maxByOrNull { it.time }
    }

    /** 공유 카드용: 크기를 줄이고 사진 방향(세로/가로)을 바로잡아 읽는다. */
    fun loadBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bytes = readBytes(ctx, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val deg = try {
            ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
        } catch (_: Exception) {
            0
        }
        if (deg == 0) return bmp
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun granted(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
}
