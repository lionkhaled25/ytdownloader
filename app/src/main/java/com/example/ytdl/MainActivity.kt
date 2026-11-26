package com.example.ytdl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var summaryText: TextView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var picker: FormatPicker

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val taskList = findViewById<ListView>(R.id.taskList)
        val header = layoutInflater.inflate(R.layout.header_main, taskList, false)
        taskList.addHeaderView(header, null, false)
        val adapter = TaskAdapter(layoutInflater)
        taskList.adapter = adapter
        taskAdapter = adapter

        urlInput = header.findViewById(R.id.urlInput)
        summaryText = header.findViewById(R.id.summaryText)
        picker = FormatPicker(
            this,
            header.findViewById<Spinner>(R.id.typeSpinner),
            header.findViewById(R.id.codecGroup),
            header.findViewById<Spinner>(R.id.codecSpinner),
            header.findViewById<Spinner>(R.id.qualitySpinner)
        )

        picker.restore(AppSettings.lastVideo(this), AppSettings.lastQuality(this), AppSettings.lastCodec(this))

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        header.findViewById<Button>(R.id.addBtn).setOnClickListener { addDownload() }
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
                Downloader.tasks.collect { taskAdapter.submit(it); renderSummary(it) }
            }
        }

        updateEngine()
    }

    private fun updateEngine() {
        lifecycleScope.launch {
            // لو المحرك فشل يتجهز في بداية التطبيق (غالبًا مفيش نت أول تشغيل)، نحاول تاني ونوضح للمستخدم
            if (App.engineError != null) {
                Toast.makeText(
                    this@MainActivity,
                    "محرك التحميل مش جاهز — بنحاول تجهيزه، استنى لحظة",
                    Toast.LENGTH_LONG
                ).show()
                val ok = withContext(Dispatchers.IO) { App.initEngine(this@MainActivity) }
                if (!ok) {
                    Toast.makeText(
                        this@MainActivity,
                        "محرك التحميل محتاج نت أول تشغيل — وصّل النت وافتح التطبيق تاني",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
            }
            Engine.update(this@MainActivity)
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
        picker.save()
        Downloader.enqueue(url, picker.isVideo, picker.qualityIndex, picker.codecIndex)
        urlInput.setText("")
        Toast.makeText(this, "تمت الإضافة ✅ — تابعها تحت", Toast.LENGTH_SHORT).show()
    }

    private fun renderSummary(list: List<Downloader.TaskUi>) {
        if (list.isEmpty()) {
            summaryText.text = "مفيش تحميلات دلوقتي"
            return
        }
        val s = Downloader.summaryOf(list)
        val paused = list.count { it.status == Downloader.Status.PAUSED }
        val done = list.count { it.status == Downloader.Status.DONE }
        val failed = list.count { it.status == Downloader.Status.FAILED }
        var text = "⬇ شغّال: ${s.running}  •  ⏳ انتظار: ${s.queued}  •  ⏸ متوقف: $paused  •  ✅ تم: $done"
        if (failed > 0) text += "  •  ⚠ فشل: $failed"
        summaryText.text = text
    }
}
