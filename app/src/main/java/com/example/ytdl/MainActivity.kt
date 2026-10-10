package com.example.ytdl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    private lateinit var typeSpinner: Spinner
    private lateinit var qualitySpinner: Spinner
    private lateinit var codecSpinner: Spinner
    private lateinit var codecGroup: View
    private lateinit var primaryBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var historyBtn: Button
    private lateinit var progress: ProgressBar
    private lateinit var percentText: TextView
    private lateinit var status: TextView

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val types = listOf("فيديو (MP4)", "صوت فقط")
    private val videoLabels = listOf("1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p (حجم صغير)")
    private val codecLabels = listOf(
        "VP9 — حجم صغير وتوافق جيد (الافتراضي)",
        "AV1 — الأصغر حجمًا (ممكن يتقطّع على موبايلات ضعيفة)",
        "H.264 (MP4) — أقصى توافق، حجم أكبر"
    )
    private val audioLabels = listOf(
        "MP3 - 320 kbps",
        "MP3 - 192 kbps",
        "MP3 - 128 kbps (حجم أصغر)",
        "M4A - الجودة الأصلية (بدون إعادة ضغط)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        typeSpinner = findViewById(R.id.typeSpinner)
        qualitySpinner = findViewById(R.id.qualitySpinner)
        codecSpinner = findViewById(R.id.codecSpinner)
        codecGroup = findViewById(R.id.codecGroup)
        primaryBtn = findViewById(R.id.primaryBtn)
        cancelBtn = findViewById(R.id.cancelBtn)
        historyBtn = findViewById(R.id.historyBtn)
        progress = findViewById(R.id.progress)
        percentText = findViewById(R.id.percentText)
        status = findViewById(R.id.status)

        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { urlInput.setText(it) }
        }

        // إذن الإشعارات (أندرويد 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        codecSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codecLabels)
        setQualityList(true)
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                setQualityList(pos == 0)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        primaryBtn.setOnClickListener {
            when (Downloader.state.value.phase) {
                Downloader.Phase.IDLE -> startNew()
                Downloader.Phase.DOWNLOADING -> Downloader.pause()
                Downloader.Phase.PAUSED -> Downloader.resume()
            }
        }
        cancelBtn.setOnClickListener { Downloader.cancel() }
        historyBtn.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloader.state.collect { render(it) }
            }
        }

        updateEngine()
    }

    private fun setQualityList(video: Boolean) {
        val list = if (video) videoLabels else audioLabels
        qualitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list)
        if (video) qualitySpinner.setSelection(1) // 1080p افتراضيًا
        codecGroup.visibility = if (video) View.VISIBLE else View.GONE
    }

    private fun updateEngine() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@MainActivity, YoutubeDL.UpdateChannel.STABLE
                    )
                }
            } catch (_: Exception) { }
        }
    }

    private fun startNew() {
        val url = Regex("https?://\\S+").find(urlInput.text.toString())?.value
        if (url == null) {
            Toast.makeText(this, "الصق رابط صحيح الأول", Toast.LENGTH_SHORT).show()
            return
        }
        Downloader.start(
            url,
            typeSpinner.selectedItemPosition == 0,
            qualitySpinner.selectedItemPosition,
            codecSpinner.selectedItemPosition
        )
    }

    private fun render(s: Downloader.UiState) {
        progress.progress = s.percent
        percentText.text =
            if (s.phase == Downloader.Phase.DOWNLOADING && s.eta.isNotEmpty())
                "${s.percent}%  •  باقي ${s.eta}"
            else "${s.percent}%"
        status.text = s.status

        val idle = s.phase == Downloader.Phase.IDLE
        urlInput.isEnabled = idle
        typeSpinner.isEnabled = idle
        qualitySpinner.isEnabled = idle
        codecSpinner.isEnabled = idle

        when (s.phase) {
            Downloader.Phase.IDLE -> {
                primaryBtn.text = "ابدأ التحميل"
                primaryBtn.isEnabled = true
                cancelBtn.isEnabled = false
            }
            Downloader.Phase.DOWNLOADING -> {
                primaryBtn.text = "⏸ إيقاف مؤقت"
                primaryBtn.isEnabled = !s.stopping
                cancelBtn.isEnabled = !s.stopping
            }
            Downloader.Phase.PAUSED -> {
                primaryBtn.text = "▶ استكمال"
                primaryBtn.isEnabled = true
                cancelBtn.isEnabled = true
            }
        }
    }
}
