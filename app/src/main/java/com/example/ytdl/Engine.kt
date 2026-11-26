package com.example.ytdl

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** تحديث محرك yt-dlp (مشترك بين الشاشات) */
object Engine {
    suspend fun update(ctx: Context) {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun updateQuietly(ctx: Context) = update(ctx)

    /** نسخة yt-dlp الحالية أو "غير معروف" */
    suspend fun version(ctx: Context): String = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().version(ctx) ?: "غير معروف"
        } catch (_: Exception) {
            "غير معروف"
        }
    }

    /** تحديث يدوي: بيرجّع رسالة للمستخدم */
    suspend fun updateNow(ctx: Context): String {
        if (App.engineError != null) {
            val ok = withContext(Dispatchers.IO) { App.initEngine(ctx) }
            if (!ok) return "محرك التحميل مش جاهز — تأكد من النت وجرّب تاني"
        }
        val before = version(ctx)
        val err = withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
                null
            } catch (e: Exception) {
                e.message ?: "خطأ غير معروف"
            }
        }
        if (err != null) return "فشل التحديث: ${err.lineSequence().firstOrNull()?.take(120)}"
        val after = version(ctx)
        return if (after != before) "اتحدّث ✅ $before ← $after" else "أنت على آخر نسخة ✅ ($after)"
    }
}
