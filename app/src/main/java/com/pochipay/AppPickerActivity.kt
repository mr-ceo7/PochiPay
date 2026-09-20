package com.pochipay

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pochipay.databinding.ActivityAppPickerBinding

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apps = getInstalledApps()
        val adapter = AppAdapter(apps) { app ->
            val intent = Intent()
            intent.putExtra("selected_app_package", app.packageName)
            setResult(RESULT_OK, intent)
            finish()
        }

        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter
    }

    private fun getInstalledApps(): List<ApplicationInfo> {
        val pm = packageManager
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return allApps.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
    }
}

class AppAdapter(
    private val apps: List<ApplicationInfo>,
    private val onAppSelected: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
        holder.itemView.setOnClickListener { onAppSelected(app) }
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.image_view_app_icon)
        private val appName: TextView = itemView.findViewById(R.id.text_view_app_name)
        private val packageName: TextView = itemView.findViewById(R.id.text_view_package_name)

        fun bind(app: ApplicationInfo) {
            val pm = itemView.context.packageManager
            appIcon.setImageDrawable(app.loadIcon(pm))
            appName.text = app.loadLabel(pm)
            packageName.text = app.packageName
        }
    }
}
