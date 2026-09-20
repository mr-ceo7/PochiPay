package com.pochipay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pochipay.data.LogEntry
import com.pochipay.data.LogRepository
import kotlinx.coroutines.launch
import android.util.Log

class LogsActivity : AppCompatActivity() {

    private lateinit var adapter: LogsAdapter
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Live Logs"

        recycler = findViewById(R.id.logs_recycler)
        adapter = LogsAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // Start from bottom
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter

        // Observe Logs
        lifecycleScope.launch {
            LogRepository.logs.collect { logs ->
                adapter.submitList(logs)
                if (logs.isNotEmpty()) {
                    recycler.scrollToPosition(logs.size - 1)
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class LogsAdapter : androidx.recyclerview.widget.ListAdapter<LogEntry, LogsAdapter.LogViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class LogViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val timeView: TextView = itemView.findViewById(R.id.log_time)
            private val levelView: TextView = itemView.findViewById(R.id.log_level)
            private val tagView: TextView = itemView.findViewById(R.id.log_tag)
            private val msgView: TextView = itemView.findViewById(R.id.log_message)

            fun bind(entry: LogEntry) {
                timeView.text = entry.formattedTime
                tagView.text = entry.tag ?: "NoTag"
                msgView.text = entry.message
                
                when(entry.level) {
                    Log.ERROR -> {
                        levelView.text = "E"
                        levelView.setTextColor(android.graphics.Color.RED)
                    }
                    Log.WARN -> {
                        levelView.text = "W"
                         levelView.setTextColor(android.graphics.Color.YELLOW)
                    }
                    Log.INFO -> {
                        levelView.text = "I"
                        levelView.setTextColor(android.graphics.Color.GREEN)
                    }
                    Log.DEBUG -> {
                        levelView.text = "D"
                        levelView.setTextColor(android.graphics.Color.WHITE)
                    }
                    else -> {
                        levelView.text = "V"
                        levelView.setTextColor(android.graphics.Color.GRAY)
                    }
                }
            }
        }

        class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<LogEntry>() {
            override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean = oldItem === newItem
            override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean = oldItem == newItem
        }
    }
}
