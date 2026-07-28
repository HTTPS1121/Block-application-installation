package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityMainBinding
import com.appguard.blocker.service.AppMonitorAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        binding.btnDeviceAdmin.setOnClickListener {
            if (!DeviceAdminHelper.isAdminActive(this)) {
                DeviceAdminHelper.requestEnable(this)
            }
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnAllowlist.setOnClickListener {
            startActivity(Intent(this, AllowlistActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.switchAllowlist.setOnCheckedChangeListener { _, checked ->
            prefs.allowlistEnabled = checked
        }
        binding.switchInstallBlock.setOnCheckedChangeListener { _, checked ->
            prefs.installBlockEnabled = checked
            DeviceAdminHelper.applyInstallRestriction(this, checked)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val adminOn = DeviceAdminHelper.isAdminActive(this)
        binding.btnDeviceAdmin.text = if (adminOn) {
            getString(R.string.device_admin_on)
        } else {
            getString(R.string.enable_device_admin)
        }
        binding.btnDeviceAdmin.isEnabled = !adminOn

        val a11yOn = AppMonitorAccessibilityService.isRunning() || isAccessibilityEnabled()
        binding.btnAccessibility.text = if (a11yOn) {
            getString(R.string.accessibility_on)
        } else {
            getString(R.string.enable_accessibility)
        }

        binding.switchAllowlist.isChecked = prefs.allowlistEnabled
        binding.switchInstallBlock.isChecked = prefs.installBlockEnabled
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "$packageName/${AppMonitorAccessibilityService::class.java.canonicalName}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
