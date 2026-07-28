package com.appguard.blocker.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityAllowlistBinding
import com.appguard.blocker.databinding.ItemAppBinding

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    var allowed: Boolean
)

class AllowlistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllowlistBinding
    private lateinit var prefs: PrefsRepository
    private val allApps = mutableListOf<AppItem>()
    private val visibleApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllowlistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        adapter = AppsAdapter(visibleApps)
        binding.appsList.layoutManager = LinearLayoutManager(this)
        binding.appsList.adapter = adapter

        binding.searchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        binding.btnSaveAllowlist.setOnClickListener {
            val selected = allApps.filter { it.allowed }.map { it.packageName }.toSet()
            prefs.setAllowedPackages(selected)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        loadApps()
    }

    private fun loadApps() {
        val allowed = prefs.getAllowedPackages()
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .filter { it.packageName != packageName }
            .map { app ->
                AppItem(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    icon = pm.getApplicationIcon(app),
                    allowed = allowed.contains(app.packageName)
                )
            }
            .sortedBy { it.label.lowercase() }

        allApps.clear()
        allApps.addAll(apps)
        filter("")
    }

    private fun filter(query: String) {
        visibleApps.clear()
        if (query.isBlank()) {
            visibleApps.addAll(allApps)
        } else {
            val q = query.lowercase()
            visibleApps.addAll(
                allApps.filter {
                    it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
            )
        }
        adapter.notifyDataSetChanged()
    }

    private class AppsAdapter(
        private val items: List<AppItem>
    ) : RecyclerView.Adapter<AppsAdapter.VH>() {

        class VH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.binding.appIcon.setImageDrawable(item.icon)
            holder.binding.appLabel.text = item.label
            holder.binding.appPackage.text = item.packageName
            holder.binding.appAllowed.setOnCheckedChangeListener(null)
            holder.binding.appAllowed.isChecked = item.allowed
            holder.binding.appAllowed.setOnCheckedChangeListener { _, checked ->
                item.allowed = checked
            }
            holder.binding.root.setOnClickListener {
                holder.binding.appAllowed.isChecked = !holder.binding.appAllowed.isChecked
            }
        }
    }
}
