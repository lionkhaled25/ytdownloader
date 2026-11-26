package com.example.ytdl

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {

    companion object {
        /** سبب فشل تجهيز محرك التحميل، أو null لو تمام */
        @Volatile
        var engineError: String? = null

        /** بيحاول تجهيز yt-dlp + ffmpeg وبيرجّع true لو نجح */
        fun initEngine(ctx: Context): Boolean {
            return try {
                YoutubeDL.getInstance().init(ctx)
                FFmpeg.getInstance().init(ctx)
                engineError = null
                true
            } catch (e: Exception) {
                engineError = e.message ?: "unknown"
                Log.e("YTDL", "init failed", e)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppSettings.applyTheme(this)
        Notifier.ensureChannels(this)
        // المحرك قبل استرجاع القايمة: لو فشل، التحميلات المسترجعة تفشل برسالة واضحة
        initEngine(this)
        Downloader.init(this)
    }
}
