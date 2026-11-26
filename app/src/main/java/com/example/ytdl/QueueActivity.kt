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
        adapter = TaskAdapter(layoutInflater)
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
}
