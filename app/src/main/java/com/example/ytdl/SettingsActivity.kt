package com.example.ytdl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    AppSettings.setSaveTree(this, uri.toString())
                } catch (_: Exception) {
                    Toast.makeText(this, "مقدرتش أحفظ صلاحية الفولدر ده", Toast.LENGTH_LONG).show()
                }
                showFolder()
            }
        }

    private fun showFolder() {
        val t = AppSettings.saveTree(this)
        val name = t?.let { DocumentFile.fromTreeUri(this, Uri.parse(it))?.name }
        findViewById<TextView>(R.id.folderText).text =
            if (name != null) "فولدر الحفظ: $name" else "فولدر الحفظ: Downloads/YTDL (الافتراضي)"
    }

    private fun showVersion() {
        lifecycleScope.launch {
            findViewById<TextView>(R.id.engineVersion).text = "نسخة yt-dlp: " + Engine.version(this@SettingsActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val maxSpinner = findViewById<Spinner>(R.id.maxSpinner)
        maxSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("1", "2", "3", "4", "5")
        )
        maxSpinner.setSelection(AppSettings.maxConcurrent(this) - 1)
        maxSpinner.onSelected { pos ->
            AppSettings.setMaxConcurrent(this, pos + 1)
            Downloader.schedule() // لو زوّدنا العدد، اللي في الانتظار يبدأ فورًا
        }

        val speedSpinner = findViewById<Spinner>(R.id.speedSpinner)
        speedSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, AppSettings.speedLabels
        )
        speedSpinner.setSelection(AppSettings.speedIndex(this))
        speedSpinner.onSelected { pos -> AppSettings.setSpeedIndex(this, pos) }

        val themeSpinner = findViewById<Spinner>(R.id.themeSpinner)
        themeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, AppSettings.themeLabels
        )
        themeSpinner.setSelection(AppSettings.theme(this))
        themeSpinner.onSelected { pos ->
            if (pos != AppSettings.theme(this)) {
                AppSettings.setTheme(this, pos)
                AppSettings.applyTheme(this)
            }
        }

        findViewById<Button>(R.id.folderBtn).setOnClickListener { pickFolder.launch(null) }
        findViewById<Button>(R.id.folderResetBtn).setOnClickListener {
            AppSettings.setSaveTree(this, null)
            showFolder()
        }
        showFolder()

        val cbWifi = findViewById<CheckBox>(R.id.cbWifi)
        cbWifi.isChecked = AppSettings.wifiOnly(this)
        cbWifi.setOnCheckedChangeListener { _, c ->
            AppSettings.setWifiOnly(this, c)
            Downloader.onNetChanged()
        }

        val cbRetry = findViewById<CheckBox>(R.id.cbRetry)
        cbRetry.isChecked = AppSettings.autoRetry(this)
        cbRetry.setOnCheckedChangeListener { _, c -> AppSettings.setAutoRetry(this, c) }

        showVersion()
        val updateBtn = findViewById<Button>(R.id.updateBtn)
        updateBtn.setOnClickListener {
            updateBtn.isEnabled = false
            updateBtn.text = "جاري التحديث..."
            lifecycleScope.launch {
                val msg = Engine.updateNow(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                updateBtn.isEnabled = true
                updateBtn.text = "🔄 حدّث yt-dlp دلوقتي"
                showVersion()
            }
        }

        val cbCover = findViewById<CheckBox>(R.id.cbCover)
        cbCover.isChecked = AppSettings.embedCover(this)
        cbCover.setOnCheckedChangeListener { _, c -> AppSettings.setEmbedCover(this, c) }

        val cbMeta = findViewById<CheckBox>(R.id.cbMeta)
        cbMeta.isChecked = AppSettings.embedMeta(this)
        cbMeta.setOnCheckedChangeListener { _, c -> AppSettings.setEmbedMeta(this, c) }

        val cbLyrics = findViewById<CheckBox>(R.id.cbLyrics)
        cbLyrics.isChecked = AppSettings.lyrics(this)
        cbLyrics.setOnCheckedChangeListener { _, c -> AppSettings.setLyrics(this, c) }
    }
}
