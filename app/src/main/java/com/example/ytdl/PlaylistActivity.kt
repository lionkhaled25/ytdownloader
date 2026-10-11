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
                val out = withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(url)
                    req.addOption("--flat-playlist")
                    req.addOption("--yes-playlist")
                    req.addOption("--no-warnings")
                    req.addOption("--playlist-end", "500")
                    req.addOption("--print", "%(playlist_title)s|||%(title)s|||%(url)s")
                    YoutubeDL.getInstance().execute(req, "pl-fetch") { _, _, _ -> }.out
                }
                parse(out)
            } catch (e: Exception) {
                entries.clear()
                titles.clear()
                adapter.notifyDataSetChanged()
                infoText.text = "تعذّر جلب القايمة: ${e.message?.lineSequence()?.firstOrNull()?.take(150)}"
                addBtn.isEnabled = false
            } finally {
                fetchBtn.isEnabled = true
                fetchProgress.visibility = View.GONE
            }
        }
    }

    private fun parse(out: String) {
        entries.clear()
        titles.clear()
        playlistTitle = ""
        for (raw in out.lineSequence()) {
            val parts = raw.trim().split("|||")
            if (parts.size < 3) continue
            val url = parts.last().trim()
            if (!url.startsWith("http")) continue
            val title = parts.subList(1, parts.size - 1).joinToString("|||").trim()
            if (title.startsWith("[Private") || title.startsWith("[Deleted")) continue
            if (playlistTitle.isEmpty()) {
                val pt = parts[0].trim()
                if (pt.isNotEmpty() && pt != "NA") playlistTitle = pt
            }
            entries.add(Entry(if (title.isEmpty() || title == "NA") url else title, url))
        }
        for ((i, e) in entries.withIndex()) titles.add("${i + 1}.  ${e.title}")
        adapter.notifyDataSetChanged()

        if (entries.isEmpty()) {
            infoText.text = "مفيش فيديوهات في القايمة (أو الرابط مش قايمة تشغيل)"
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
