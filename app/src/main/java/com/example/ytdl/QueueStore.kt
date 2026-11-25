package com.example.ytdl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * بيحفظ قايمة التحميلات المفتوحة (انتظار/متوقف/شغّال) عشان متضيعش
 * لو النظام قفل التطبيق. التحميل اللي كان شغّال بيترجّع "في الانتظار"
 * وبيكمّل من نفس المكان (--continue) لو الملف الجزئي لسه موجود في الكاش.
 */
object QueueStore {
    private const val PREF = "queue"
    private const val KEY = "items"

    fun save(ctx: Context, tasks: List<Downloader.TaskUi>) {
        val open = tasks.filter {
            it.status == Downloader.Status.QUEUED ||
                it.status == Downloader.Status.PAUSED ||
                it.status == Downloader.Status.RUNNING
        }
        val arr = JSONArray()
        open.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id).put("url", t.url).put("title", t.title)
                    .put("video", t.isVideo).put("quality", t.quality).put("codec", t.codec)
                    .put("folder", t.folder ?: JSONObject.NULL)
                    .put("status", t.status.name).put("percent", t.percent)
            )
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun load(ctx: Context): List<Downloader.TaskUi> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val out = mutableListOf<Downloader.TaskUi>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val wasPaused = o.optString("status") == "PAUSED"
                out.add(
                    Downloader.TaskUi(
                        id = o.getLong("id"),
                        url = o.getString("url"),
                        title = o.getString("title"),
                        isVideo = o.getBoolean("video"),
                        quality = o.getInt("quality"),
                        codec = o.getInt("codec"),
                        folder = if (o.isNull("folder")) null else o.optString("folder"),
                        status = if (wasPaused) Downloader.Status.PAUSED else Downloader.Status.QUEUED,
                        percent = o.optInt("percent", 0),
                        message = if (wasPaused) "متوقف مؤقتًا" else "في الانتظار"
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }
}
