package com.example.ytdl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** بيجيب عنوان الفيديو واسم القناة والصورة المصغّرة بسرعة (من غير تشغيل yt-dlp) */
object OEmbed {

    data class Info(val title: String, val author: String, val thumb: Bitmap?)

    fun fetch(url: String): Info? {
        val host = Uri.parse(url).host?.lowercase() ?: return null
        if (!(host.endsWith("youtube.com") || host == "youtu.be")) return null
        return try {
            val api = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
            val json = JSONObject(open(api).inputStream.bufferedReader().use { it.readText() })
            val thumb = json.str("thumbnail_url")?.let {
                try {
                    BitmapFactory.decodeStream(open(it).inputStream)
                } catch (_: Exception) {
                    null
                }
            }
            Info(json.str("title") ?: return null, json.str("author_name") ?: "", thumb)
        } catch (_: Exception) {
            null
        }
    }

    private fun open(u: String): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("User-Agent", "YTDownloader/6.0 (Android)")
        return c
    }
}
