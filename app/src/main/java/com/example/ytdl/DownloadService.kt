package com.example.ytdl

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** خدمة بتخلي التحميلات شغالة في الخلفية وبتعرض إشعار تقدّم واحد لكل التحميلات */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Downloader.serviceActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Downloader.serviceActive = true
        Notifier.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifier.ONGOING_ID,
            Notifier.progress(this, Downloader.summaryOf(Downloader.tasks.value)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (!started) {
            started = true
            scope.launch {
                Downloader.tasks
                    .map { Downloader.summaryOf(it) }
                    .distinctUntilChanged()
                    .collect { s ->
                        if (s.running + s.queued > 0) {
                            Notifier.post(this@DownloadService, Notifier.ONGOING_ID, Notifier.progress(this@DownloadService, s))
                        } else {
                            Downloader.serviceActive = false
                            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Downloader.serviceActive = false
        scope.cancel()
        super.onDestroy()
    }
}
