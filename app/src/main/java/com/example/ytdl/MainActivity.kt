package com.example.ytdl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var summaryText: TextView
    private lateinit var summaryBar: ProgressBar
    private lateinit var picker: FormatPicker

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        summaryText = findViewById(R.id.summaryText)
        summaryBar = findViewById(R.id.summaryBar)
        picker = FormatPicker(
            this,
            findViewById<Spinner>(R.id.typeSpinner),
            findViewById(R.id.codecGroup),
            findViewById<Spinner>(R.id.codecSpinner),
            findViewById<Spinner>(R.id.qualitySpinner)
        )

        handleShare(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.addBtn).setOnClickListener { addDownload() }
        findViewById<Button>(R.id.queueBtn).setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }
        findViewById<Button>(R.id.playlistBtn).setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.historyBtn).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloader.tasks.collect { renderSummary(it) }
            }
        }

        updateEngine()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i != null && i.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { urlInput.setText(it) }
        }
    }

    private fun updateEngine() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@MainActivity, YoutubeDL.UpdateChannel.STABLE
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun addDownload() {
        val url = Regex("https?://\\S+").find(urlInput.text.toString())?.value
        if (url == null) {
            Toast.makeText(this, "الصق رابط صحيح الأول", Toast.LENGTH_SHORT).show()
            return
        }
        // رابط قايمة تشغيل؟ نفتح شاشة القوايم
        if (url.contains("/playlist?")) {
            startActivity(Intent(this, PlaylistActivity::class.java).putExtra("url", url))
            return
        }
        Downloader.enqueue(url, picker.isVideo, picker.qualityIndex, picker.codecIndex)
        urlInput.setText("")
        Toast.makeText(this, "تمت الإضافة ✅ — تابعها من «التحميلات الجارية»", Toast.LENGTH_SHORT).show()
    }

    private fun renderSummary(list: List<Downloader.TaskUi>) {
        if (list.isEmpty()) {
            summaryText.text = "مفيش تحميلات دلوقتي"
            summaryBar.visibility = android.view.View.GONE
            return
        }
        val s = Downloader.summaryOf(list)
        val paused = list.count { it.status == Downloader.Status.PAUSED }
        val done = list.count { it.status == Downloader.Status.DONE }
        val failed = list.count { it.status == Downloader.Status.FAILED }
        var text = "⬇ شغّال: ${s.running}  •  ⏳ انتظار: ${s.queued}  •  ⏸ متوقف: $paused  •  ✅ تم: $done"
        if (failed > 0) text += "  •  ⚠ فشل: $failed"
        summaryText.text = text
        if (s.running > 0) {
            summaryBar.visibility = android.view.View.VISIBLE
            summaryBar.progress = s.percent
        } else {
            summaryBar.visibility = android.view.View.GONE
        }
    }
}
