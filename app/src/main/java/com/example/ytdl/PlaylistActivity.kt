package com.example.ytdl

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PlaylistActivity : AppCompatActivity() {

    private class Entry(val title: String, val url: String)

    private val entries = mutableListOf<Entry>()
    private val titles = mutableListOf<String>()
    private var playlistTitle = ""

    private lateinit var urlInput: EditText
    private lateinit var fetchBtn: Button
    private lateinit var fetchProgress: ProgressBar
    private lateinit var infoText: TextView
    private lateinit var listView: ListView
    private lateinit var addBtn: Button
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var picker: FormatPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        urlInput = findViewById(R.id.urlInput)
        fetchBtn = findViewById(R.id.fetchBtn)
        fetchProgress = findViewById(R.id.fetchProgress)
        infoText = findViewById(R.id.infoText)
        listView = findViewById(R.id.entryList)
        addBtn = findViewById(R.id.addBtn)

        picker = FormatPicker(
            this,
            findViewById<Spinner>(R.id.typeSpinner),
            findViewById(R.id.codecGroup),
            findViewById<Spinner>(R.id.codecSpinner),
            findViewById<Spinner>(R.id.qualitySpinner)
        )

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, titles)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        listView.isNestedScrollingEnabled = true
        listView.setOnItemClickListener { _, _, _, _ -> updateCount() }

        fetchBtn.setOnClickListener { fetch() }
        findViewById<Button>(R.id.selectAllBtn).setOnClickListener {
            val all = listView.checkedItemCount == entries.size
            for (i in entries.indices) listView.setItemChecked(i, !all)
            updateCount()
        }
        addBtn.setOnClickListener { addSelected() }

        intent.getStringExtra("url")?.let {
            urlInput.setText(it)
            fetch()
        }
    }

    // ---------------- جلب القايمة ----------------

    /** بيطلب بيانات القايمة كـ JSON واحد (--flat-playlist -J) */
    private suspend fun runFetch(url: String): String = withContext(Dispatchers.IO) {
        val req = YoutubeDLRequest(url)
        req.addOption("--flat-playlist")
        req.addOption("--yes-playlist")
        req.addOption("--no-warnings")
        req.addOption("--playlist-end", "500")
        req.addOption("-J")
        YoutubeDL.getInstance().execute(req, "pl-fetch") { _, _, _ -> }.out
    }

    private suspend fun updateEngine() {
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    this@PlaylistActivity, YoutubeDL.UpdateChannel.STABLE
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun fetch() {
        val url = Regex("https?://\\S+").find(urlInput.text.toString())?.value
        if (url == null) {
            Toast.makeText(this, "الصق رابط قايمة التشغيل الأول", Toast.LENGTH_SHORT).show()
            return
        }
        fetchBtn.isEnabled = false
        fetchProgress.visibility = View.VISIBLE
        infoText.text = "جاري جلب القايمة..."

        lifecycleScope.launch {
            try {
                val out = try {
                    runFetch(url)
                } catch (e: Exception) {
                    // غالبًا محرك yt-dlp قديم: نحدّثه ونجرّب تاني مرة واحدة
                    infoText.text = "جاري تحديث محرك التحميل وإعادة المحاولة..."
                    updateEngine()
                    runFetch(url)
                }
                parse(out)
            } catch (e: Exception) {
                entries.clear()
                titles.clear()
                adapter.notifyDataSetChanged()
                addBtn.isEnabled = false
                val msg = e.message.orEmpty().lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(3)
                    .joinToString("\n")
                    .take(350)
                infoText.text = "تعذّر جلب القايمة:\n$msg"
            } finally {
                fetchBtn.isEnabled = true
                fetchProgress.visibility = View.GONE
            }
        }
    }

    private fun collect(o: JSONObject) {
        val arr = o.optJSONArray("entries") ?: return
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.has("entries")) { // قايمة جوه قايمة (زي تبويبات القناة)
                collect(e)
                continue
            }
            val title = e.str("title") ?: ""
            if (title.startsWith("[Private") || title.startsWith("[Deleted")) continue
            val id = e.str("id")
            val rawUrl = e.str("url") ?: e.str("webpage_url")
            val url = when {
                rawUrl != null && rawUrl.startsWith("http") -> rawUrl
                id != null -> "https://www.youtube.com/watch?v=$id"
                else -> continue
            }
            entries.add(Entry(if (title.isEmpty()) url else title, url))
        }
    }

    private fun parse(out: String) {
        entries.clear()
        titles.clear()
        playlistTitle = ""

        val start = out.indexOf('{')
        val end = out.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("الرد من yt-dlp مش مفهوم (مفيش بيانات)")
        val root = JSONObject(out.substring(start, end + 1))

        if (!root.has("entries")) {
            infoText.text = "الرابط ده فيديو واحد مش قايمة تشغيل — ضيفه من الشاشة الرئيسية"
            adapter.notifyDataSetChanged()
            addBtn.isEnabled = false
            return
        }
        playlistTitle = root.str("title") ?: ""
        collect(root)

        for ((i, e) in entries.withIndex()) titles.add("${i + 1}.  ${e.title}")
        adapter.notifyDataSetChanged()

        if (entries.isEmpty()) {
            infoText.text = "القايمة فاضية أو كلها فيديوهات خاصة/محذوفة"
            addBtn.isEnabled = false
            return
        }
        for (i in entries.indices) listView.setItemChecked(i, true)
        infoText.text = (if (playlistTitle.isNotEmpty()) "$playlistTitle — " else "") + "${entries.size} فيديو"
        updateCount()
    }

    private fun updateCount() {
        val n = listView.checkedItemCount
        addBtn.text = "📥 أضف المحدد للتحميل ($n)"
        addBtn.isEnabled = n > 0
    }

    private fun addSelected() {
        val chosen = entries.filterIndexed { i, _ -> listView.isItemChecked(i) }
        if (chosen.isEmpty()) return
        val folder = playlistTitle
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .take(60)
            .ifEmpty { "Playlist" }
        Downloader.enqueueAll(
            chosen.map { it.url to it.title },
            picker.isVideo, picker.qualityIndex, picker.codecIndex, folder
        )
        Toast.makeText(this, "تمت إضافة ${chosen.size} للقايمة ✅", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, QueueActivity::class.java))
        finish()
    }
}
