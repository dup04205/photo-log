package com.dup04205.photolog

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 백업: 사진과 기록을 zip 파일 하나로 내보내고 다시 불러온다.
 * zip 구성: entries.json(기록 목록, 사진은 photos/파일이름으로 가리킴) + photos/ 폴더
 */
object Backup {
    private const val ENTRIES = "entries.json"
    private const val PHOTOS = "photos/"

    data class ExportResult(val entries: Int, val photos: Int)
    data class ImportResult(val added: List<Entry>, val skipped: Int)

    fun export(ctx: Context, entries: List<Entry>, dest: Uri): ExportResult {
        var photos = 0
        val out = ctx.contentResolver.openOutputStream(dest) ?: error("저장할 파일을 열 수 없습니다")
        ZipOutputStream(out.buffered()).use { zip ->
            val arr = JSONArray()
            entries.forEach { e ->
                val src = PhotoStore.fileOf(e.uri)
                val name: String
                if (src != null && src.exists()) {
                    name = PHOTOS + src.name
                    zip.putNextEntry(ZipEntry(name))
                    src.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    photos++
                } else {
                    name = ""
                }
                arr.put(e.toJson().apply { put("uri", name) })
            }
            zip.putNextEntry(ZipEntry(ENTRIES))
            zip.write(arr.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return ExportResult(entries.size, photos)
    }

    /** 이미 있는 기록(id가 같은 것)은 건너뛰고 새 기록만 더한다. */
    fun import(ctx: Context, src: Uri, existingIds: Set<String>): ImportResult {
        val copied = HashMap<String, File>()
        var json: String? = null
        val input = ctx.contentResolver.openInputStream(src) ?: error("백업 파일을 열 수 없습니다")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val ze = zip.nextEntry ?: break
                when {
                    ze.isDirectory -> Unit
                    ze.name == ENTRIES -> json = zip.readBytes().toString(Charsets.UTF_8)
                    ze.name.startsWith(PHOTOS) -> {
                        // 경로 조작을 막기 위해 파일 이름만 쓴다
                        val base = File(ze.name).name
                        if (base.isNotEmpty() && !base.startsWith(".")) {
                            val ext = base.substringAfterLast('.', "jpg")
                            val dst = PhotoStore.newPhotoFile(ctx, ext, preferredName = base)
                            dst.outputStream().use { zip.copyTo(it) }
                            copied[ze.name] = dst
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val text = json ?: run {
            copied.values.forEach { it.delete() }
            error("PHOTO-LOG 백업 파일이 아닙니다")
        }
        val arr = JSONArray(text)
        val added = mutableListOf<Entry>()
        val used = HashSet<String>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            val photo = copied[o.optString("uri")]
            if (id in existingIds || photo == null) {
                skipped++
                continue
            }
            used += o.optString("uri")
            added += entryFromJson(o, Uri.fromFile(photo).toString())
        }
        // 건너뛴 기록의 사진 복사본은 지운다
        copied.filterKeys { it !in used }.values.forEach { it.delete() }
        return ImportResult(added, skipped)
    }
}
