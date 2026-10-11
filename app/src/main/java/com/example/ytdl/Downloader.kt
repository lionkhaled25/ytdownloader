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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** مدير التحميلات: قايمة مهام + قايمة انتظار + تشغيل أكتر من تحميل مع بعض */
object Downloader {

    enum class Status { QUEUED, RUNNING, PAUSED, DONE, FAILED }

    data class TaskUi(
        val id: Long,
        val url: String,
        val title: String,
        val isVideo: Boolean,
        val quality: Int,
        val codec: Int,          // 0 = VP9 ، 1 = AV1 ، 2 = H.264
        val folder: String?,
        val status: Status = Status.QUEUED,
        val percent: Int = 0,
        val eta: String = "",
        val message: String = "في الانتظار",
        val stopping: Boolean = false
    )

    data class Summary(val running: Int, val queued: Int, val percent: Int, val title: String)

    val videoHeights = listOf(1440, 1080, 720, 480, 360)

    /** بتتظبط من DownloadService */
    @Volatile
    var serviceActive = false

    private enum class Stop { PAUSE, CANCEL }

    private class Rt {
        @Volatile var stop: Stop? = null
        @Volatile var resume = false
    }

    private val OUT_EXT = setOf("mp4", "mp3", "m4a", "webm", "mkv", "opus")
    private val DEST_RE = Regex("""\[(?:download|ExtractAudio)\] Destination: (.+)""")
    private val FMT_RE = Regex("""\.f\d+\z""")

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _tasks = MutableStateFlow<List<TaskUi>>(emptyList())
    val tasks: StateFlow<List<TaskUi>> = _tasks

    private val runtimes = ConcurrentHashMap<Long, Rt>()
    private val nextId = AtomicLong(1)
    private var sessionDone = 0

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    fun summaryOf(list: List<TaskUi>): Summary {
        val run = list.filter { it.status == Status.RUNNING }
        val pct = if (run.isEmpty()) 0 else run.sumOf { it.percent } / run.size
        return Summary(
            run.size,
            list.count { it.status == Status.QUEUED },
            pct,
            run.firstOrNull()?.title ?: ""
        )
    }

    // ==================== إضافة مهام ====================

    fun enqueue(
        url: String, isVideo: Boolean, quality: Int, codec: Int,
        title: String? = null, folder: String? = null
    ) {
        enqueueAll(listOf(url to (title ?: url)), isVideo, quality, codec, folder)
    }

    fun enqueueAll(
        items: List<Pair<String, String>>,
        isVideo: Boolean, quality: Int, codec: Int, folder: String?
    ) {
        if (items.isEmpty()) return
        val newTasks = items.map { (u, t) ->
            TaskUi(nextId.getAndIncrement(), u, t, isVideo, quality, codec, folder)
        }
        _tasks.update { it + newTasks }
        schedule()
    }

    /** بيشغّل المهام اللي في الانتظار بحدود العدد المسموح (من الإعدادات) */
    fun schedule() {
        val max = AppSettings.maxConcurrent(app)
        var running = _tasks.value.count { it.status == Status.RUNNING }
        for (t in _tasks.value) {
            if (running >= max) break
            if (t.status == Status.QUEUED) {
                startTask(t.id)
                running++
            }
        }
    }

    // ==================== التحكم ====================

    private fun find(id: Long): TaskUi? = _tasks.value.firstOrNull { it.id == id }

    private fun upd(id: Long, f: (TaskUi) -> TaskUi) {
        _tasks.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun removeTask(id: Long) {
        _tasks.update { list -> list.filter { it.id != id } }
    }

    private fun procId(id: Long) = "ytdl-$id"

    fun pause(id: Long) {
        val t = find(id) ?: return
        when (t.status) {
            Status.RUNNING -> {
                val rt = runtimes[id] ?: return
                if (rt.stop != null) return
                rt.stop = Stop.PAUSE
                upd(id) { it.copy(stopping = true, message = "جاري الإيقاف المؤقت...") }
                YoutubeDL.getInstance().destroyProcessById(procId(id))
            }
            Status.QUEUED -> upd(id) { it.copy(status = Status.PAUSED, message = "متوقف مؤقتًا") }
            else -> {}
        }
    }

    fun resume(id: Long) {
        val t = find(id) ?: return
        if (t.status != Status.PAUSED) return
        upd(id) { it.copy(status = Status.QUEUED, message = "في الانتظار") }
        schedule()
    }

    fun cancel(id: Long) {
        val t = find(id) ?: return
        if (t.status == Status.RUNNING) {
            val rt = runtimes[id] ?: return
            rt.stop = Stop.CANCEL
            upd(id) { it.copy(stopping = true, message = "جاري الإلغاء...") }
            YoutubeDL.getInstance().destroyProcessById(procId(id))
        } else {
            removeTask(id)
            runtimes.remove(id)
            File(app.cacheDir, "dl/$id").deleteRecursively()
            schedule()
        }
    }

    fun remove(id: Long) {
        val t = find(id) ?: return
        if (t.status == Status.DONE || t.status == Status.FAILED) removeTask(id)
    }

    fun retry(id: Long) {
        val t = find(id) ?: return
        if (t.status != Status.FAILED) return
        runtimes[id]?.resume = false
        upd(id) { it.copy(status = Status.QUEUED, percent = 0, eta = "", message = "في الانتظار") }
        schedule()
    }

    fun pauseAll() {
        _tasks.value.filter { it.status == Status.RUNNING || it.status == Status.QUEUED }
            .forEach { pause(it.id) }
    }

    fun resumeAll() {
        _tasks.value.filter { it.status == Status.PAUSED }.forEach { resume(it.id) }
    }

    fun cancelAll() {
        _tasks.value.filter { it.status != Status.DONE && it.status != Status.FAILED }
            .forEach { cancel(it.id) }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.status != Status.DONE && it.status != Status.FAILED } }
    }

    // ==================== تنفيذ مهمة ====================

    private fun ensureService() {
        if (serviceActive) return
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun startTask(id: Long) {
        val rt = runtimes.getOrPut(id) { Rt() }
        rt.stop = null
        upd(id) {
            it.copy(
                status = Status.RUNNING,
                stopping = false,
                message = if (rt.resume) "جاري الاستكمال..." else "جاري التحضير..."
            )
        }
        ensureService()
        scope.launch { runTask(id, rt) }
    }

    private suspend fun runTask(id: Long, rt: Rt) {
        val t0 = find(id) ?: return
        val dir = File(app.cacheDir, "dl/$id")
        try {
            if (!rt.resume) dir.deleteRecursively()
            dir.mkdirs()

            try {
                withContext(Dispatchers.IO) { runYtdlp(t0, dir, rt, noEmbed = false) }
            } catch (e: Exception) {
                // لو دمج الغلاف فشل، نجرّب تاني من غيره بدل ما التحميل يفشل
                if (rt.stop == null && !t0.isVideo && AppSettings.embedCover(app)) {
                    upd(id) { it.copy(message = "تعذّر دمج الغلاف، جاري المحاولة بدونه...") }
                    withContext(Dispatchers.IO) { runYtdlp(t0, dir, rt, noEmbed = true) }
                } else {
                    throw e
                }
            }

            if (rt.stop != null) {
                handleStopped(id, rt, dir)
                return
            }
            finishTask(id, t0, dir)
        } catch (e: Exception) {
            if (rt.stop != null) handleStopped(id, rt, dir) else failTask(id, dir, e)
        } finally {
            schedule()
        }
    }

    private fun runYtdlp(t: TaskUi, dir: File, rt: Rt, noEmbed: Boolean) {
        val pid = procId(t.id)
        YoutubeDL.getInstance().execute(buildRequest(t, dir, noEmbed), pid) { p, eta, line ->
            // لو ضغطنا إيقاف/إلغاء قبل ما العملية تتسجّل، نكرر الإيقاف هنا
            if (rt.stop != null) YoutubeDL.getInstance().destroyProcessById(pid)
            val newTitle = titleFrom(line)
            upd(t.id) { s ->
                s.copy(
                    title = newTitle ?: s.title,
                    percent = if (p >= 0) p.toInt() else s.percent,
                    eta = if (p >= 0) fmtEta(eta) else s.eta,
                    message = if (line.isNotBlank()) line.trim().take(100) else s.message
                )
            }
        }
    }

    private fun titleFrom(line: String): String? {
        val m = DEST_RE.find(line) ?: return null
        val name = File(m.groupValues[1].trim()).nameWithoutExtension.replace(FMT_RE, "")
        return name.takeIf { it.isNotBlank() }
    }

    private fun handleStopped(id: Long, rt: Rt, dir: File) {
        val reason = rt.stop
        rt.stop = null
        if (reason == Stop.PAUSE) {
            rt.resume = true
            upd(id) { it.copy(status = Status.PAUSED, stopping = false, message = "متوقف مؤقتًا") }
        } else {
            removeTask(id)
            runtimes.remove(id)
            dir.deleteRecursively()
        }
    }

    private fun failTask(id: Long, dir: File, e: Exception) {
        val raw = e.message.orEmpty()
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.contains("ERROR") }
            ?: raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: "حصل خطأ"
        val title = find(id)?.title ?: ""
        upd(id) { it.copy(status = Status.FAILED, stopping = false, eta = "", message = "فشل: ${line.take(160)}") }
        dir.deleteRecursively()
        Notifier.failed(app, title, line.take(120))
    }

    private suspend fun finishTask(id: Long, t: TaskUi, dir: File) {
        val file = dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in OUT_EXT }
            ?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("مفيش ملف ناتج")

        val wantLyrics = !t.isVideo && AppSettings.lyrics(app)
        if (wantLyrics) upd(id) { it.copy(message = "جاري البحث عن كلمات الأغنية...") }
        val lrcText: String? =
            if (wantLyrics) withContext(Dispatchers.IO) { applyLyrics(dir, file) } else null

        upd(id) { it.copy(message = "جاري الحفظ في Downloads...") }
        val mime = mimeOf(file)
        val size = file.length()
        val uri = withContext(Dispatchers.IO) { saveToDownloads(file, mime, t.folder) }
        if (lrcText != null) {
            withContext(Dispatchers.IO) {
                try {
                    saveLrc(file.nameWithoutExtension, lrcText, t.folder)
                } catch (_: Exception) {
                }
            }
        }
        dir.deleteRecursively()

        History.add(app, HistoryItem(file.name, uri.toString(), mime, System.currentTimeMillis(), t.isVideo, size))

        val extra = if (lrcText != null) "  •  🎤 كلمات" else ""
        upd(id) {
            it.copy(
                status = Status.DONE, percent = 100, eta = "", title = file.nameWithoutExtension,
                message = "تم ✅  الحجم: ${formatSize(size)}$extra"
            )
        }
        runtimes.remove(id)

        sessionDone++
        val others = _tasks.value.any {
            it.id != id && (it.status == Status.RUNNING || it.status == Status.QUEUED)
        }
        Notifier.done(app, file.name, uri, mime, formatSize(size), alert = !others, total = if (!others) sessionDone else 0)
        if (!others) sessionDone = 0
    }

    /** بيجيب الكلمات ويدمجها في MP3، ويرجّع النص عشان يتحفظ كملف .lrc جنب الأغنية */
    private fun applyLyrics(dir: File, audio: File): String? {
        return try {
            val info = dir.listFiles()?.firstOrNull { it.name.endsWith(".info.json") } ?: return null
            val o = JSONObject(info.readText())
            val res = LyricsFetcher.find(
                title = o.str("title") ?: audio.nameWithoutExtension,
                artist = o.str("artist"),
                track = o.str("track"),
                uploader = o.str("uploader") ?: o.str("channel"),
                duration = o.optDouble("duration", Double.NaN)
            ) ?: return null
            if (audio.extension.equals("mp3", ignoreCase = true)) {
                res.plain?.let {
                    try {
                        Id3.writeLyrics(audio, it)
                    } catch (_: Exception) {
                    }
                }
            }
            res.synced ?: res.plain
        } catch (_: Exception) {
            null
        }
    }

    // ==================== بناء أمر yt-dlp ====================

    private fun buildRequest(t: TaskUi, dir: File, noEmbed: Boolean): YoutubeDLRequest {
        val req = YoutubeDLRequest(t.url)
        req.addOption("--no-playlist")
        req.addOption("--continue")
        req.addOption("--no-mtime")
        req.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")
        AppSettings.speedLimit(app)?.let { req.addOption("--limit-rate", it) }

        if (t.isVideo) {
            val h = videoHeights[t.quality]
            // 2K وأعلى متاحة على يوتيوب بـ VP9/AV1 فقط، فنتجاهل اختيار H.264 هنا
            val codec = if (h > 1080 && t.codec == 2) 0 else t.codec
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
            when (t.quality) {
                0 -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", "320K") }
                1 -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", "192K") }
                2 -> { req.addOption("-f", "ba/b"); req.addOption("--audio-format", "mp3"); req.addOption("--audio-quality", "128K") }
                else -> { req.addOption("-f", "ba[ext=m4a]/ba/b"); req.addOption("--audio-format", "m4a") }
            }
            if (AppSettings.embedMeta(app)) req.addOption("--embed-metadata")
            if (AppSettings.embedCover(app) && !noEmbed) {
                req.addOption("--embed-thumbnail")
                req.addOption("--convert-thumbnails", "jpg")
            }
            if (AppSettings.lyrics(app)) req.addOption("--write-info-json")
        }
        return req
    }

    // ==================== الحفظ ====================

    private fun mimeOf(file: File) = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }

    private fun relPath(folder: String?): String {
        val sub = if (folder.isNullOrBlank()) "" else "/$folder"
        return Environment.DIRECTORY_DOWNLOADS + "/YTDL" + sub
    }

    private fun saveToDownloads(file: File, mime: String, folder: String?): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relPath(folder))
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("فشل إنشاء الملف")
        resolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        return uri
    }

    private fun saveLrc(baseName: String, text: String, folder: String?) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$baseName.lrc")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, relPath(folder))
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun fmtEta(sec: Long): String =
        if (sec < 0) "--:--" else "%d:%02d".format(sec / 60, sec % 60)
}
