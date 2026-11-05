package com.dup04205.photolog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoInfo(val date: String, val lat: Double?, val lon: Double?)

/**
 * 사진 파일을 앱 전용 저장 공간(files/photos)에 저장하고 읽는다.
 * 다른 앱(갤러리·Google 포토·파일 앱)에서는 보이지 않는다.
 * 앱을 지우면 함께 지워지므로, 백업(zip 내보내기/불러오기)으로 보관·이전한다.
 */
object PhotoStore {
    fun wantedPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }.toTypedArray()

    fun photosDir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** 앱 전용 공간에 새 사진 파일 자리를 만든다(이름이 겹치지 않게). */
    fun newPhotoFile(ctx: Context, ext: String = "jpg", preferredName: String? = null): File {
        val dir = photosDir(ctx)
        if (preferredName != null) {
            val f = File(dir, preferredName)
            if (!f.exists()) return f
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        var f = File(dir, "PHOTOLOG_$stamp.$ext")
        var n = 1
        while (f.exists()) f = File(dir, "PHOTOLOG_${stamp}_${n++}.$ext")
        return f
    }

    /** 카메라 앱이 사진을 써 넣을 파일과 그 주소. */
    fun cameraTarget(ctx: Context): Pair<File, Uri> {
        val f = newPhotoFile(ctx)
        return f to providerUri(ctx, f)
    }

    private fun providerUri(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    /** 기록의 사진 주소가 가리키는 앱 전용 파일(옛 공용 폴더 사진이면 null) */
    fun fileOf(uri: String): File? {
        val u = Uri.parse(uri)
        return if (u.scheme == "file") u.path?.let { File(it) } else null
    }

    /** 다른 앱에 넘길 수 있는 사진 주소(공유용) */
    fun shareUri(ctx: Context, uri: String): Uri = fileOf(uri)?.let { providerUri(ctx, it) } ?: Uri.parse(uri)

    fun discard(file: File) {
        file.delete()
    }

    /** 사진 파일을 지운다. */
    fun delete(ctx: Context, uri: Uri): Boolean = try {
        if (uri.scheme == "file") File(uri.path ?: "").delete()
        else ctx.contentResolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        false
    }

    /** 카메라 촬영이 끝난 사진에서 날짜·위치를 읽는다. 사진에 위치가 없으면 방금 전 폰 위치로 보완한다. */
    fun finishCapture(ctx: Context, file: File): PhotoInfo {
        val bytes = try { file.readBytes() } catch (_: Exception) { null }
        val info = bytes?.let { parse(it) } ?: PhotoInfo(today(), null, null)
        if (info.lat != null) return info
        val loc = lastLocation(ctx) ?: return info
        return info.copy(lat = loc.latitude, lon = loc.longitude)
    }

    /** 갤러리·Google 포토 사진을 원본 그대로(EXIF 포함) 앱 전용 공간에 복사한다. */
    fun importFrom(ctx: Context, src: Uri): Pair<Uri, PhotoInfo>? {
        val bytes = readBytes(ctx, original(ctx, src)) ?: readBytes(ctx, src) ?: return null
        val mime = ctx.contentResolver.getType(src) ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        val dst = newPhotoFile(ctx, ext)
        return try {
            dst.writeBytes(bytes)
            Uri.fromFile(dst) to (parse(bytes) ?: PhotoInfo(today(), null, null))
        } catch (e: Exception) {
            dst.delete()
            null
        }
    }

    /**
     * 0.9.2까지 공용 사진 폴더(갤러리에 보이던 곳)에 있던 사진을 앱 전용 공간으로 옮긴다.
     * 복사에 성공하면 공용 폴더의 원본은 지워 갤러리에서 사라지게 한다.
     * 돌려주는 값: 갱신된 기록 목록, 옮긴 장수, 실패 장수
     */
    fun migrateLegacy(ctx: Context, entries: List<Entry>): Triple<List<Entry>, Int, Int> {
        var moved = 0
        var failed = 0
        val out = entries.map { e ->
            val u = Uri.parse(e.uri)
            if (u.scheme != "content") return@map e
            val bytes = readBytes(ctx, original(ctx, u)) ?: readBytes(ctx, u)
            if (bytes == null) {
                failed++
                return@map e
            }
            val mime = ctx.contentResolver.getType(u) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val dst = newPhotoFile(ctx, ext)
            try {
                dst.writeBytes(bytes)
            } catch (_: Exception) {
                dst.delete()
                failed++
                return@map e
            }
            try {
                ctx.contentResolver.delete(u, null, null)
            } catch (_: Exception) {
            }
            moved++
            e.copy(uri = Uri.fromFile(dst).toString())
        }
        return Triple(out, moved, failed)
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
