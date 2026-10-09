package com.example.ytdl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val name: String,
    val uri: String,
    val mime: String,
    val time: Long,
    val isVideo: Boolean
)

object History {
    private const val PREF = "history"
    private const val KEY = "items"
    private const val MAX = 100

    fun load(ctx: Context): MutableList<HistoryItem> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val out = mutableListOf<HistoryItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    HistoryItem(
                        o.getString("name"), o.getString("uri"), o.getString("mime"),
                        o.getLong("time"), o.getBoolean("video")
                    )
                )
            }
        } catch (_: Exception) { }
        return out
    }

    private fun save(ctx: Context, list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name).put("uri", it.uri).put("mime", it.mime)
                    .put("time", it.time).put("video", it.isVideo)
            )
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, item: HistoryItem) {
        val list = load(ctx)
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    fun remove(ctx: Context, uri: String) {
        save(ctx, load(ctx).filter { it.uri != uri })
    }
}
