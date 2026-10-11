package com.example.ytdl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

object Notifier {
    private const val CH_PROGRESS = "progress"
    private const val CH_DONE = "done"
    private const val CH_QUIET = "done_quiet"
    const val ONGOING_ID = 1001
    private val nextId = AtomicInteger(2000)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "تقدم التحميل", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "اكتمال التحميل", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUIET, "ملفات مكتملة (بدون صوت)", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, QueueActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun progress(ctx: Context, s: Downloader.Summary): Notification {
        val title = if (s.running == 1 && s.queued == 0) {
            "جاري التحميل: ${s.title}".take(60)
        } else {
            "جاري تحميل ${s.running} ملف" + if (s.queued > 0) "  •  ${s.queued} في الانتظار" else ""
        }
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (s.percent <= 0) "جاري التحضير..." else "${s.percent}%")
            .setProgress(100, s.percent, s.percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun post(ctx: Context, id: Int, n: Notification) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: Exception) {
        }
    }

    /** alert=true لما ده آخر ملف في القايمة (بصوت)، غير كده إشعار هادي */
    fun done(ctx: Context, name: String, uri: Uri, mime: String, sizeText: String, alert: Boolean, total: Int) {
        ensureChannels(ctx)
        val id = nextId.getAndIncrement()
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(ctx, if (alert) CH_DONE else CH_QUIET)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تم التحميل ✅")
            .setContentText("$name  •  $sizeText")
            .setContentIntent(pi)
            .setAutoCancel(true)
        if (alert && total > 1) b.setSubText("اكتمل $total تحميل")
        post(ctx, id, b.build())
    }

    fun failed(ctx: Context, title: String, msg: String) {
        ensureChannels(ctx)
        val id = nextId.getAndIncrement()
        val n = NotificationCompat.Builder(ctx, CH_QUIET)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("فشل التحميل")
            .setContentText(if (title.isNotBlank()) "$title — $msg" else msg)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        post(ctx, id, n)
    }
}
