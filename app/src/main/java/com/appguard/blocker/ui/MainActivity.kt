package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityMainBinding
import com.appguard.blocker.service.UsageMonitorService
import com.appguard.blocker.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        binding.btnSetupPermissions.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        binding.btnAllowlist.setOnClickListener {
            startActivity(Intent(this, AllowlistActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.switchAllowlist.setOnCheckedChangeListener { _, checked ->
            if (checked && !PermissionHelper.criticalReady(this)) {
                binding.switchAllowlist.isChecked = false
                Toast.makeText(this, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SetupActivity::class.java))
                return@setOnCheckedChangeListener
            }
            prefs.allowlistEnabled = checked
            if (checked) UsageMonitorService.start(this) else maybeStopMonitor()
        }
        binding.switchInstallBlock.setOnCheckedChangeListener { _, checked ->
            if (checked && !PermissionHelper.criticalReady(this)) {
                binding.switchInstallBlock.isChecked = false
                Toast.makeText(this, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SetupActivity::class.java))
                return@setOnCheckedChangeListener
            }
            prefs.installBlockEnabled = checked
            DeviceAdminHelper.applyInstallRestriction(this, checked)
            if (checked) UsageMonitorService.start(this) else maybeStopMonitor()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if ((prefs.allowlistEnabled || prefs.installBlockEnabled) &&
            PermissionHelper.usageAccessGranted(this)
        ) {
            UsageMonitorService.start(this)
        }
    }

    private fun maybeStopMonitor() {
        if (!prefs.allowlistEnabled && !prefs.installBlockEnabled) {
            UsageMonitorService.stop(this)
        }
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

        binding.switchAllowlist.isChecked = prefs.allowlistEnabled
        binding.switchInstallBlock.isChecked = prefs.installBlockEnabled
    }
}
