package com.example.ytdl

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

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
