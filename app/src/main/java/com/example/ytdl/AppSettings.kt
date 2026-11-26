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

    // ---- إعادة المحاولة التلقائية ----
    fun autoRetry(c: Context): Boolean = p(c).getBoolean("auto_retry", true)
    fun setAutoRetry(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_retry", v).apply()

    // ---- واي فاي فقط ----
    fun wifiOnly(c: Context): Boolean = p(c).getBoolean("wifi_only", false)
    fun setWifiOnly(c: Context, v: Boolean) = p(c).edit().putBoolean("wifi_only", v).apply()

    // ---- المظهر: 0 تلقائي ، 1 فاتح ، 2 داكن (أسود AMOLED) ----
    val themeLabels = listOf("تلقائي (زي الموبايل)", "فاتح", "داكن (أسود AMOLED)")
    fun theme(c: Context): Int = p(c).getInt("theme", 0).coerceIn(0, 2)
    fun setTheme(c: Context, v: Int) = p(c).edit().putInt("theme", v).apply()
    fun applyTheme(c: Context) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            when (theme(c)) {
                1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    // ---- فولدر الحفظ (اختياري). فاضي = Downloads/YTDL ----
    fun saveTree(c: Context): String? = p(c).getString("save_tree", null)?.takeIf { it.isNotBlank() }
    fun setSaveTree(c: Context, v: String?) = p(c).edit().putString("save_tree", v ?: "").apply()

    // ---- آخر اختيار للنوع/الجودة/الترميز ----
    fun lastVideo(c: Context): Boolean = p(c).getBoolean("last_video", true)
    fun lastQuality(c: Context): Int =
        p(c).getInt("last_quality", if (lastVideo(c)) Formats.DEFAULT_VIDEO_INDEX else 0)
    fun lastCodec(c: Context): Int = p(c).getInt("last_codec", 0)
    fun saveLast(c: Context, video: Boolean, quality: Int, codec: Int) {
        p(c).edit().putBoolean("last_video", video).putInt("last_quality", quality)
            .putInt("last_codec", codec).apply()
    }
}
