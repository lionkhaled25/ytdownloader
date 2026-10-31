package com.example.ytdl

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * نافذة منبثقة (زي ADM): بتظهر فوق يوتيوب لما تعمل مشاركة للينك واختار التطبيق،
 * من غير ما تفتح التطبيق نفسه.
 */
class ShareActivity : AppCompatActivity() {

    private lateinit var picker: FormatPicker
    private var knownTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raw = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("https?://\\S+").find(raw)?.value
        if (url == null) {
            Toast.makeText(this, "مفيش رابط في اللي اتشارك", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // رابط قايمة تشغيل كاملة؟ نفتح شاشة القوايم على طول
        if (url.contains("/playlist?")) {
            startActivity(Intent(this, PlaylistActivity::class.java).putExtra("url", url))
            finish()
            return
        }
        showSheet(url)
    }

    private fun showSheet(url: String) {
        val dialog = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_download, null)
        dialog.setContentView(v)

        val titleView = v.findViewById<TextView>(R.id.sheetTitle)
        val authorView = v.findViewById<TextView>(R.id.sheetAuthor)
        val thumbView = v.findViewById<ImageView>(R.id.sheetThumb)
        titleView.text = url

        picker = FormatPicker(
            this,
            v.findViewById<Spinner>(R.id.typeSpinner),
            v.findViewById(R.id.codecGroup),
            v.findViewById<Spinner>(R.id.codecSpinner),
            v.findViewById<Spinner>(R.id.qualitySpinner)
        )
        picker.restore(AppSettings.lastVideo(this), AppSettings.lastQuality(this), AppSettings.lastCodec(this))

        v.findViewById<Button>(R.id.sheetDownload).setOnClickListener {
            picker.save()
            Downloader.enqueue(url, picker.isVideo, picker.qualityIndex, picker.codecIndex, title = knownTitle)
            Toast.makeText(this, "بدأ التحميل ✅ — التقدّم في الإشعارات", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        v.findViewById<Button>(R.id.sheetQueue).setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            dialog.dismiss()
        }
        // لينك فيديو جوه قايمة تشغيل: نعرض زرار تحميل القايمة كلها
        if (url.contains("list=")) {
            val plBtn = v.findViewById<Button>(R.id.sheetPlaylist)
            plBtn.visibility = View.VISIBLE
            plBtn.setOnClickListener {
                startActivity(
                    Intent(this, PlaylistActivity::class.java)
                        .putExtra("url", url)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener { finish() }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()

        // العنوان والصورة بيظهروا لما يجهزوا
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { OEmbed.fetch(url) } ?: return@launch
            knownTitle = info.title
            titleView.text = info.title
            authorView.text = info.author
            info.thumb?.let { thumbView.setImageBitmap(it) }
        }
    }
}
