package com.dup04205.photolog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 사진 한 장과 그 기록. 사진 파일은 폰의 사진/PHOTO-LOG 폴더에 있고, uri가 그것을 가리킨다. */
data class Entry(
    val id: String,
    val uri: String,
    val date: String,      // YYYY-MM-DD
    val note: String,
    val lat: Double?,
    val lon: Double?,
    val created: Long,
)

/** 메모·위치·날짜는 앱 내부 저장소의 entries.json 하나에 보관한다. 서버는 없다. */
class EntryRepo(context: Context) {
    private val file = File(context.filesDir, "entries.json")

    fun load(): List<Entry> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                id = o.getString("id"),
                uri = o.getString("uri"),
                date = o.getString("date"),
                note = o.optString("note", ""),
                lat = if (o.has("lat")) o.getDouble("lat") else null,
                lon = if (o.has("lon")) o.getDouble("lon") else null,
                created = o.optLong("created"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("uri", e.uri)
                put("date", e.date)
                put("note", e.note)
                e.lat?.let { put("lat", it) }
                e.lon?.let { put("lon", it) }
                put("created", e.created)
            })
        }
        val tmp = File(file.parentFile, "entries.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}

fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/** 필름 카메라식 날짜 스탬프: '26 11 05 */
fun stampOf(date: String): String {
    val p = date.split("-")
    return if (p.size == 3) "'${p[0].takeLast(2)} ${p[1]} ${p[2]}" else date
}
