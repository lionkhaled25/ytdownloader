package com.example.ytdl

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class QueueActivity : AppCompatActivity() {

    private lateinit var adapter: TaskAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)

        emptyText = findViewById(R.id.emptyText)
        val list = findViewById<ListView>(R.id.taskList)
        adapter = TaskAdapter()
        list.adapter = adapter

        findViewById<Button>(R.id.pauseAllBtn).setOnClickListener { Downloader.pauseAll() }
        findViewById<Button>(R.id.resumeAllBtn).setOnClickListener { Downloader.resumeAll() }
        findViewById<Button>(R.id.clearBtn).setOnClickListener { Downloader.clearFinished() }
        findViewById<Button>(R.id.cancelAllBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("إلغاء كل التحميلات؟")
                .setMessage("هيتمسح اللي شغّال واللي في الانتظار واللي متوقف.")
                .setPositiveButton("إلغاء الكل") { _, _ -> Downloader.cancelAll() }
                .setNegativeButton("رجوع", null)
                .show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloader.tasks.collect {
                    adapter.submit(it)
                    emptyText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private inner class TaskAdapter : BaseAdapter() {
        private var items: List<Downloader.TaskUi> = emptyList()

        fun submit(l: List<Downloader.TaskUi>) {
            items = l
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_task, parent, false)
            val t = items[position]

            v.findViewById<TextView>(R.id.taskTitle).text = (if (t.isVideo) "🎬 " else "🎵 ") + t.title

            val bar = v.findViewById<ProgressBar>(R.id.taskBar)
            bar.isIndeterminate = t.status == Downloader.Status.RUNNING && t.percent <= 0
            bar.progress = t.percent

            v.findViewById<TextView>(R.id.taskInfo).text = when (t.status) {
                Downloader.Status.QUEUED -> "⏳ في الانتظار"
                Downloader.Status.RUNNING ->
                    if (t.stopping) t.message
                    else "${t.percent}%" + (if (t.eta.isNotEmpty()) "  •  باقي ${t.eta}" else "") + "\n" + t.message
                Downloader.Status.PAUSED -> "⏸ متوقف مؤقتًا — ${t.percent}%"
                Downloader.Status.DONE -> t.message
                Downloader.Status.FAILED -> t.message
            }

            val pauseBtn = v.findViewById<Button>(R.id.pauseBtn)
            val cancelBtn = v.findViewById<Button>(R.id.cancelBtn)
            pauseBtn.visibility = View.VISIBLE
            pauseBtn.isEnabled = true
            cancelBtn.text = "✕ إلغاء"

            when (t.status) {
                Downloader.Status.RUNNING -> {
                    pauseBtn.text = "⏸ إيقاف مؤقت"
                    pauseBtn.isEnabled = !t.stopping
                    pauseBtn.setOnClickListener { Downloader.pause(t.id) }
                    cancelBtn.setOnClickListener { Downloader.cancel(t.id) }
                }
                Downloader.Status.QUEUED -> {
                    pauseBtn.text = "⏸ إيقاف مؤقت"
                    pauseBtn.setOnClickListener { Downloader.pause(t.id) }
                    cancelBtn.setOnClickListener { Downloader.cancel(t.id) }
                }
                Downloader.Status.PAUSED -> {
                    pauseBtn.text = "▶ استكمال"
                    pauseBtn.setOnClickListener { Downloader.resume(t.id) }
                    cancelBtn.setOnClickListener { Downloader.cancel(t.id) }
                }
                Downloader.Status.DONE -> {
                    pauseBtn.visibility = View.GONE
                    cancelBtn.text = "إزالة من القايمة"
                    cancelBtn.setOnClickListener { Downloader.remove(t.id) }
                }
                Downloader.Status.FAILED -> {
                    pauseBtn.text = "🔄 إعادة المحاولة"
                    pauseBtn.setOnClickListener { Downloader.retry(t.id) }
                    cancelBtn.text = "إزالة من القايمة"
                    cancelBtn.setOnClickListener { Downloader.remove(t.id) }
                }
            }
            return v
        }
    }
}
