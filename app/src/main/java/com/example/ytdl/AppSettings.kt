package com.example.ytdl

import android.content.Context

object AppSettings {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---- عدد التحميلات المتزامنة (1 - 5) ----
    fun maxConcurrent(c: Context): Int = p(c).getInt("max", 2).coerceIn(1, 5)
    fun setMaxConcurrent(c: Context, v: Int) = p(c).edit().putInt("max", v).apply()

    // ---- حد السرعة (لكل تحميل) ----
    val speedLabels = listOf("بدون حد", "250 KB/s", "500 KB/s", "1 MB/s", "2 MB/s", "5 MB/s")
    private val speedValues = listOf("", "250K", "500K", "1M", "2M", "5M")
    fun speedIndex(c: Context): Int = p(c).getInt("speed", 0).coerceIn(0, speedValues.size - 1)
    fun setSpeedIndex(c: Context, i: Int) = p(c).edit().putInt("speed", i).apply()
    fun speedLimit(c: Context): String? = speedValues[speedIndex(c)].takeIf { it.isNotEmpty() }

    // ---- تحميل الصوت ----
    fun embedCover(c: Context): Boolean = p(c).getBoolean("cover", true)
    fun setEmbedCover(c: Context, v: Boolean) = p(c).edit().putBoolean("cover", v).apply()

    fun embedMeta(c: Context): Boolean = p(c).getBoolean("meta", true)
    fun setEmbedMeta(c: Context, v: Boolean) = p(c).edit().putBoolean("meta", v).apply()

    fun lyrics(c: Context): Boolean = p(c).getBoolean("lyrics", true)
    fun setLyrics(c: Context, v: Boolean) = p(c).edit().putBoolean("lyrics", v).apply()
}
