package com.appguard.blocker.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityAllowlistBinding
import com.appguard.blocker.databinding.ItemAppBinding
import com.appguard.blocker.protection.ProtectionController

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    var allowed: Boolean
)

class AllowlistActivity : SecureActivity() {

    private lateinit var binding: ActivityAllowlistBinding
    private lateinit var prefs: PrefsRepository
    private val allApps = mutableListOf<AppItem>()
    private val visibleApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppsAdapter
    private var fromSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllowlistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)
        fromSetup = intent.getBooleanExtra(EXTRA_FROM_SETUP, false)

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

        binding.btnSelectAll.setOnClickListener {
            allApps.forEach { it.allowed = true }
            adapter.notifyDataSetChanged()
        }
        binding.btnDeselectAll.setOnClickListener {
            allApps.forEach { it.allowed = false }
            adapter.notifyDataSetChanged()
        }
        binding.btnApprove.setOnClickListener { save() }
        // No separate "reject/pending" — one list: checked = allowed
        binding.btnReject.visibility = android.view.View.GONE

        if (fromSetup) {
            binding.setupBanner.visibility = android.view.View.VISIBLE
            binding.stepCaption.visibility = android.view.View.VISIBLE
            OnboardingSteps.bind(binding.root, OnboardingSteps.ALLOWLIST)
        }

        loadApps()
    }

    /** שמור סימונים כרשימה המותרת. מאשר setup → מדליק הגנה. */
    private fun save() {
        val selected = allApps.filter { it.allowed }.map { it.packageName }.toSet()
        prefs.setAllowedPackages(selected)
        // Clear obsolete pending tracking
        prefs.getRecentlyInstalled().toList().forEach { prefs.clearRecentlyInstalled(it) }

        if (fromSetup) {
            ProtectionController.arm(this)
        } else if (prefs.protectionArmed || prefs.allowlistEnabled) {
            prefs.allowlistEnabled = true
        }
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        leaveScreen()
    }

    private fun leaveScreen() {
        if (fromSetup) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun loadApps() {
        val allowed = prefs.getAllowedPackages()
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                val pkg = app.packageName
                if (pkg == packageName) return@filter false
                // System / installer / core — always open; not managed by allowlist
                if (prefs.isAlwaysOpen(pkg)) return@filter false
                // Only user-space apps the child can actually launch
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    pm.getLaunchIntentForPackage(pkg) != null
            }
            .map { app ->
                AppItem(
                    packageName = app.packageName,
                    label = pm.getApplicationLabel(app).toString(),
                    icon = pm.getApplicationIcon(app),
                    allowed = allowed.contains(app.packageName)
                )
            }
            .sortedWith(
                compareByDescending<AppItem> { it.allowed }
                    .thenBy { it.label.lowercase() }
            )

        allApps.clear()
        allApps.addAll(apps)
        filter(binding.searchApps.text?.toString().orEmpty())
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

    companion object {
        const val EXTRA_FROM_SETUP = "from_setup"
    }
}
