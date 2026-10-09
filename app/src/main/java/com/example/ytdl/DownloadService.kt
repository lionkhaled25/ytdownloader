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

/** خدمة بتخلي التحميل شغال في الخلفية وبتعرض إشعار التقدّم */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            Notifier.ONGOING_ID,
            Notifier.progress(this, Downloader.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (!started) {
            started = true
            scope.launch {
                Downloader.state
                    .map { Triple(it.phase, it.percent, it.stopping) }
                    .distinctUntilChanged()
                    .collect {
                        val s = Downloader.state.value
                        if (s.phase == Downloader.Phase.DOWNLOADING) {
                            Notifier.post(this@DownloadService, Notifier.ONGOING_ID, Notifier.progress(this@DownloadService, s))
                        } else {
                            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
