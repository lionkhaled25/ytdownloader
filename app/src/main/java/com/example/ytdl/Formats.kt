package com.example.ytdl

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

object Formats {
    val types = listOf("فيديو", "صوت فقط")
    val videoLabels = listOf("1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p (حجم صغير)")
    val codecLabels = listOf(
        "VP9 — حجم صغير وتوافق جيد (الافتراضي)",
        "AV1 — الأصغر حجمًا (ممكن يتقطّع على موبايلات ضعيفة)",
        "H.264 (MP4) — أقصى توافق، حجم أكبر"
    )
    val audioLabels = listOf(
        "MP3 - 320 kbps",
        "MP3 - 192 kbps",
        "MP3 - 128 kbps (حجم أصغر)",
        "M4A - الجودة الأصلية (بدون إعادة ضغط)"
    )
}

fun Spinner.onSelected(block: (Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
            block(pos)
        }

        override fun onNothingSelected(p: AdapterView<*>?) {}
    }
}

/** بيربط 3 Spinners (النوع / الترميز / الجودة) ببعض */
class FormatPicker(
    private val ctx: Context,
    private val type: Spinner,
    private val codecGroup: View,
    private val codec: Spinner,
    private val quality: Spinner
) {
    val isVideo: Boolean get() = type.selectedItemPosition == 0
    val qualityIndex: Int get() = quality.selectedItemPosition
    val codecIndex: Int get() = codec.selectedItemPosition

    init {
        type.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Formats.types)
        codec.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Formats.codecLabels)
        setQualityList(true)
        type.onSelected { setQualityList(it == 0) }
    }

    private fun setQualityList(video: Boolean) {
        val list = if (video) Formats.videoLabels else Formats.audioLabels
        quality.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, list)
        if (video) quality.setSelection(1) // 1080p افتراضيًا
        codecGroup.visibility = if (video) View.VISIBLE else View.GONE
    }

    fun setEnabled(on: Boolean) {
        type.isEnabled = on
        codec.isEnabled = on
        quality.isEnabled = on
    }
}
