package com.example.ytdl

import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var formatGroup: RadioGroup
    private lateinit var downloadBtn: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        formatGroup = findViewById(R.id.formatGroup)
        downloadBtn = findViewById(R.id.downloadBtn)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)

        // لو الرابط جاي من Share
        if (intent?.action == android.content.Intent.ACTION_SEND) {
            intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { urlInput.setText(it) }
        }

        downloadBtn.setOnClickListener { startDownload() }
        updateEngine()
    }

    /** تحديث yt-dlp تلقائيًا (يوتيوب بيغيّر حاجات كتير فلازم يتحدّث) */
    private fun updateEngine() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@MainActivity, YoutubeDL.UpdateChannel.STABLE
                    )
                }
            } catch (_: Exception) { /* مفيش نت أو فشل التحديث، عادي */ }
        }
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "الصق الرابط الأول", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = formatGroup.checkedRadioButtonId

        lifecycleScope.launch {
            setBusy(true)
            status.text = "جاري التحضير..."
            try {
                val tmpDir = File(cacheDir, "dl").apply { deleteRecursively(); mkdirs() }

                withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(url)
                    req.addOption("--no-playlist")
                    req.addOption("-o", tmpDir.absolutePath + "/%(title).80s.%(ext)s")

                    when (checked) {
                        R.id.rbMp3 -> {
                            req.addOption("-x")
                            req.addOption("--audio-format", "mp3")
                            req.addOption("--audio-quality", "0")
                        }
                        else -> {
                            val h = if (checked == R.id.rb720) 720 else 1080
                            req.addOption(
                                "-f",
                                "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]"
                            )
                            req.addOption("--merge-output-format", "mp4")
                        }
                    }

                    YoutubeDL.getInstance().execute(req, "ytdl-task") { p, _, line ->
                        runOnUiThread {
                            if (p >= 0) progress.progress = p.toInt()
                            status.text = line.take(120)
                        }
                    }
                }

                val file = tmpDir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw IllegalStateException("مفيش ملف ناتج")

                status.text = "جاري الحفظ في Downloads..."
                withContext(Dispatchers.IO) { saveToDownloads(file) }
                file.delete()

                progress.progress = 100
                status.text = "تم ✅  الملف في: Downloads/YTDL/${file.name}"
            } catch (e: Exception) {
                status.text = "حصل خطأ: ${e.message?.take(200)}"
            } finally {
                setBusy(false)
            }
        }
    }

    private fun saveToDownloads(file: File) {
        val mime = when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YTDL")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("فشل إنشاء الملف")
        contentResolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }

    private fun setBusy(busy: Boolean) {
        downloadBtn.isEnabled = !busy
        if (busy) progress.progress = 0
    }
}
