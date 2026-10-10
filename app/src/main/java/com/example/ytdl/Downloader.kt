package com.example.ytdl

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** المحرك: بيعيش مع التطبيق كله، فالتحميل مبيتأثرش بقفل الشاشة أو الخروج من الواجهة */
object Downloader {

    enum class Phase { IDLE, DOWNLOADING, PAUSED }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val percent: Int = 0,
        val eta: String = "",
        val status: String = "جاهز",
        val stopping: Boolean = false
    )

    val videoHeights = listOf(1440, 1080, 720, 480, 360)

    private const val PROCESS_ID = "ytdl-task"
    private val OUT_EXT = setOf("mp4", "mp3", "m4a", "webm", "mkv", "opus")

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var stopReason: Phase? = null   // PAUSED = إيقاف مؤقت ، IDLE = إلغاء
    private var curUrl = ""
    private var curIsVideo = true
    private var curQuality = 0
    private var curCodec = 0   // 0 = VP9 ، 1 = AV1 ، 2 = H.264

    private val tmpDir get() = File(app.cacheDir, "dl")

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    // ---------------- التحكم ----------------

    fun start(url: String, isVideo: Boolean, quality: Int, codec: Int) {
        if (_state.value.phase != Phase.IDLE) return
        curUrl = url
        curIsVideo = isVideo
        curQuality = quality
        curCodec = codec
        _state.value = UiState(Phase.DOWNLOADING, 0, "", "جاري التحضير...")
        run(resume = false)
    }

    fun resume() {
        if (_state.value.phase != Phase.PAUSED) return
        run(resume = true)
    }

    fun pause() {
        val s = _state.value
        if (s.phase != Phase.DOWNLOADING || stopReason != null) return
        stopReason = Phase.PAUSED
        _state.update { it.copy(stopping = true, status = "جاري الإيقاف المؤقت...") }
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
    }

    fun cancel() {
        val s = _state.value
        if (s.phase == Phase.DOWNLOADING && stopReason == null) {
            stopReason = Phase.IDLE
            _state.update { it.copy(stopping = true, status = "جاري الإلغاء...") }
            YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
        } else if (s.phase == Phase.PAUSED) {
            resetAfterCancel()
        }
    }

    private fun resetAfterCancel() {
        tmpDir.deleteRecursively()
        stopReason = null
        _state.value = UiState(Phase.IDLE, 0, "", "تم الإلغاء")
    }

    private fun handleStopped() {
        if (stopReason == Phase.PAUSED) {
            stopReason = null
            _state.update {
                it.copy(
                    phase = Phase.PAUSED,
                    stopping = false,
                    status = "متوقف مؤقتًا — اضغط استكمال لإكمال التحميل"
                )
            }
        } else {
            resetAfterCancel()
        }
    }

    // ---------------- التحميل ----------------

    private fun run(resume: Boolean) {
        stopReason = null
        _state.update {
            it.copy(
                phase = Phase.DOWNLOADING,
                stopping = false,
                status = if (resume) "جاري الاستكمال..." else "جاري التحضير..."
            )
        }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))

        scope.launch {
            try {
                if (!resume) tmpDir.deleteRecursively()
                tmpDir.mkdirs()

                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(buildRequest(), PROCESS_ID) { p, eta, line ->
                        // لو ضغطنا إيقاف/إلغاء قبل ما العملية تتسجّل، نكرر الإيقاف هنا
                        if (stopReason != null) YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
                        _state.update { s ->
                            s.copy(
                                percent = if (p >= 0) p.toInt() else s.percent,
                                eta = if (p >= 0) fmtEta(eta) else s.eta,
                                status = if (line.isNotBlank()) line.trim().take(100) else s.status
                            )
                        }
                    }
                }

                if (stopReason != null) { handleStopped(); return@launch }

                val file = tmpDir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in OUT_EXT }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("مفيش ملف ناتج")

                _state.update { it.copy(status = "جاري الحفظ في Downloads...") }
                val mime = mimeOf(file)
                val uri = withContext(Dispatchers.IO) { saveToDownloads(file, mime) }
                tmpDir.deleteRecursively()

                val size = file.length()
                History.add(app, HistoryItem(file.name, uri.toString(), mime, System.currentTimeMillis(), curIsVideo, size))
                Notifier.done(app, file.name, uri, mime, formatSize(size))
                _state.value = UiState(
                    Phase.IDLE, 100, "",
                    "تم ✅  الحجم: ${formatSize(size)}\nالملف في: Downloads/YTDL/${file.name}"
                )
            } catch (e: Exception) {
                if (stopReason != null) {
                    handleStopped()
                } else {
                    val msg = e.message?.take(200)
                    Notifier.failed(app, msg)
                    _state.value = UiState(Phase.IDLE, 0, "", "حصل خطأ: $msg")
                }
            }
        }
    }

    private fun buildRequest(): YoutubeDLRequest {
        val req = YoutubeDLRequest(curUrl)
        req.addOption("--no-playlist")
        req.addOption("--continue")
        req.addOption("-o", tmpDir.absolutePath + "/%(title).80s.%(ext)s")

        if (curIsVideo) {
            val h = videoHeights[curQuality]
            // 2K وأعلى متاحة على يوتيوب بـ VP9/AV1 فقط، فنتجاهل اختيار H.264 هنا
            val codec = if (h > 1080 && curCodec == 2) 0 else curCodec
            req.addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]")
            when (codec) {
                0 -> {
                    req.addOption("-S", "res,fps,vcodec:vp9")
                    req.addOption("--merge-output-format", "webm/mkv")
                }
                1 -> {
                    req.addOption("-S", "res,fps,vcodec:av01")
                    req.addOption("--merge-output-format", "webm/mkv")
                }
                else -> {
                    req.addOption("-S", "res,fps,vcodec:h264,acodec:aac")
                    req.addOption("--merge-output-format", "mp4/mkv")
                }
            }
        } else {
            req.addOption("-x")
            when (curQuality) {
                0 -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", "320K") }
                1 -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", "192K") }
                2 -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", "128K") }
                else -> { req.addOption("-f", "ba[ext=m4a]/ba/b"); req.addOption("--audio-format", "m4a") }
            }
        }
        return req
    }

    // ---------------- الحفظ ----------------

    private fun mimeOf(file: File) = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    private fun saveToDownloads(file: File, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YTDL")
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("فشل إنشاء الملف")
        resolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        return uri
    }

    private fun fmtEta(sec: Long): String =
        if (sec < 0) "--:--" else "%d:%02d".format(sec / 60, sec % 60)
}
