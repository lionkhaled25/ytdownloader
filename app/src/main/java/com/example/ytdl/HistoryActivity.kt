package com.example.ytdl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var adapter: ArrayAdapter<HistoryItem>
    private val items = mutableListOf<HistoryItem>()
    private val fmt = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        listView = findViewById(R.id.historyList)
        emptyText = findViewById(R.id.emptyText)

        adapter = object : ArrayAdapter<HistoryItem>(
            this@HistoryActivity, android.R.layout.simple_list_item_2, android.R.id.text1, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val item = getItem(position)!!
                v.findViewById<TextView>(android.R.id.text1).text =
                    (if (item.isVideo) "🎬 " else "🎵 ") + item.name
                v.findViewById<TextView>(android.R.id.text2).text = fmt.format(Date(item.time)) +
                    (if (item.size > 0) "  •  " + formatSize(item.size) else "")
                return v
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ -> openItem(items[pos]) }
        listView.setOnItemLongClickListener { _, _, pos, _ -> showMenu(items[pos]); true }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun exists(uri: Uri): Boolean = try {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    private fun reload() {
        val all = History.load(this)
        val alive = all.filter { exists(Uri.parse(it.uri)) }
        all.filter { it !in alive }.forEach { History.remove(this, it.uri) }
        items.clear()
        items.addAll(alive)
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openItem(item: HistoryItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(item.uri), item.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "مفيش تطبيق يقدر يفتح الملف ده", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu(item: HistoryItem) {
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(arrayOf("فتح", "حذف من القائمة فقط", "حذف الملف من الموبايل")) { _, which ->
                when (which) {
                    0 -> openItem(item)
                    1 -> { History.remove(this, item.uri); reload() }
                    2 -> {
                        try {
                            contentResolver.delete(Uri.parse(item.uri), null, null)
                            History.remove(this, item.uri)
                            reload()
                        } catch (_: Exception) {
                            Toast.makeText(this, "مقدرتش أمسح الملف، امسحه من تطبيق الملفات", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }
}
