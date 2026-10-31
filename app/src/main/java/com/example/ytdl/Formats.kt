package com.example.ytdl

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner

object Formats {
    val types = listOf("فيديو", "صوت فقط")
    val videoLabels = listOf(
        "2160p (4K)", "1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p (حجم صغير)"
    )

    /** رقم 1080p في القايمة اللي فوق */
    const val DEFAULT_VIDEO_INDEX = 2
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

    private var currentVideo: Boolean? = null
    private var pendingQuality: Int? = null

    init {
        type.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Formats.types)
        codec.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Formats.codecLabels)
        setQualityList(true)
        type.onSelected { pos ->
            val v = pos == 0
            if (v != currentVideo) setQualityList(v)
        }
    }

    private fun setQualityList(video: Boolean) {
        currentVideo = video
        val list = if (video) Formats.videoLabels else Formats.audioLabels
        quality.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, list)
        val pending = pendingQuality
        pendingQuality = null
        when {
            pending != null && pending in list.indices -> quality.setSelection(pending)
            video -> quality.setSelection(Formats.DEFAULT_VIDEO_INDEX) // 1080p افتراضيًا
        }
        codecGroup.visibility = if (video) View.VISIBLE else View.GONE
    }

    /** بيرجّع آخر اختيار استخدمه المستخدم */
    fun restore(video: Boolean, qualityIdx: Int, codecIdx: Int) {
        codec.setSelection(codecIdx.coerceIn(0, Formats.codecLabels.size - 1))
        pendingQuality = qualityIdx
        val pos = if (video) 0 else 1
        if (type.selectedItemPosition != pos) {
            type.setSelection(pos) // الـ listener هيبني قايمة الجودة ويطبّق pendingQuality
        } else {
            setQualityList(video)
        }
    }

    fun save() {
        AppSettings.saveLast(ctx, isVideo, qualityIndex, codecIndex)
    }

    fun setEnabled(on: Boolean) {
        type.isEnabled = on
        codec.isEnabled = on
        quality.isEnabled = on
    }
}
