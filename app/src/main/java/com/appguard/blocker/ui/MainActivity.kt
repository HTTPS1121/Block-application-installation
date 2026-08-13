package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityMainBinding
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.service.UsageMonitorService
import com.appguard.blocker.util.PermissionHelper

class MainActivity : SecureActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsRepository
    private var syncingSwitches = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)
        binding.pendingCount.visibility = View.GONE
        binding.appVersion.text = getString(R.string.app_version_label, appVersionName())

        binding.btnSetupPermissions.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.btnAllowlist.setOnClickListener {
            startActivity(Intent(this, AllowlistActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnEnableBoth.setOnClickListener { enableProtection() }

        binding.switchAllowlist.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            if (checked && !PermissionHelper.criticalReady(this)) {
                setSwitchQuiet(false)
                Toast.makeText(this, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SetupActivity::class.java))
                return@setOnCheckedChangeListener
            }
            prefs.allowlistEnabled = checked
            // Self-protect (Admin/A11y) follows the same switch
            prefs.protectionArmed = checked && PermissionHelper.criticalReady(this)
            updateMonitor()
            refreshCounters()
        }
    }

    override fun onResume() {
        super.onResume()
        ProtectionController.expireLeaseIfNeeded(this)
        refreshStatus()
        updateMonitor()
    }

    private fun enableProtection() {
        if (!PermissionHelper.criticalReady(this)) {
            Toast.makeText(this, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        Toast.makeText(this, R.string.full_protection_on, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, AllowlistActivity::class.java)
                .putExtra(AllowlistActivity.EXTRA_FROM_SETUP, true)
        )
    }

    private fun updateMonitor() {
        if (prefs.allowlistEnabled && PermissionHelper.usageAccessGranted(this)) {
            UsageMonitorService.start(this)
        } else if (!prefs.allowlistEnabled) {
            UsageMonitorService.stop(this)
        }
    }

    private fun setSwitchQuiet(checked: Boolean) {
        syncingSwitches = true
        binding.switchAllowlist.isChecked = checked
        syncingSwitches = false
    }

    private fun refreshStatus() {
        val ready = PermissionHelper.criticalReady(this)
        binding.permissionsSummary.text = if (ready) {
            getString(R.string.permissions_ready)
        } else {
            getString(R.string.permissions_missing)
        }
        binding.permissionsSummary.setTextColor(
            getColor(if (ready) R.color.accent else R.color.danger)
        )

        setSwitchQuiet(prefs.allowlistEnabled)

        val active = prefs.allowlistEnabled
        binding.protectionBadge.text = if (active) {
            getString(R.string.protection_active)
        } else {
            getString(R.string.protection_inactive)
        }
        binding.protectionBadge.setTextColor(
            getColor(
                if (active) R.color.md_theme_on_secondary_container
                else R.color.md_theme_on_error_container
            )
        )
        binding.protectionBadge.setBackgroundResource(
            if (active) R.drawable.bg_badge_on else R.drawable.bg_badge_off
        )
        binding.btnEnableBoth.visibility = if (active) View.GONE else View.VISIBLE
        refreshCounters()
    }

    private fun refreshCounters() {
        binding.allowlistCount.text =
            getString(R.string.allowlist_count, prefs.getAllowedPackages().size)
        binding.pendingCount.visibility = View.GONE
    }

    private fun appVersionName(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
}
