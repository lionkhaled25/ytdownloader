package com.example.ytdl

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** تحديث محرك yt-dlp في الخلفية (مشترك بين الشاشات) */
object Engine {
    suspend fun update(ctx: Context) {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
            } catch (_: Exception) {
            }
        }
    }
}
