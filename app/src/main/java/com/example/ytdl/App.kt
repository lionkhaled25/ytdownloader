package com.example.ytdl

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Downloader.init(this)
        Notifier.ensureChannels(this)
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("YTDL", "init failed", e)
        }
    }
}
