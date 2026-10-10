package com.example.ytdl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CH_PROGRESS = "progress"
    private const val CH_DONE = "done"
    const val ONGOING_ID = 1001

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "تقدم التحميل", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "اكتمال التحميل", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun progress(ctx: Context, s: Downloader.UiState): Notification {
        val text = when {
            s.stopping -> "جاري الإيقاف..."
            s.percent <= 0 -> "جاري التحضير..."
            s.eta.isNotEmpty() -> "${s.percent}%  •  باقي ${s.eta}"
            else -> "${s.percent}%"
        }
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("جاري التحميل")
            .setContentText(text)
            .setProgress(100, s.percent, s.percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun post(ctx: Context, id: Int, n: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) { }
    }

    fun done(ctx: Context, name: String, uri: Uri, mime: String, sizeText: String) {
        ensureChannels(ctx)
        val id = 2000 + (System.currentTimeMillis() % 100000).toInt()
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تم التحميل ✅")
            .setContentText("$name  •  $sizeText")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        post(ctx, id, n)
    }

    fun failed(ctx: Context, msg: String?) {
        ensureChannels(ctx)
        val id = 2000 + (System.currentTimeMillis() % 100000).toInt()
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("فشل التحميل")
            .setContentText(msg ?: "حصل خطأ")
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        post(ctx, id, n)
    }
}
