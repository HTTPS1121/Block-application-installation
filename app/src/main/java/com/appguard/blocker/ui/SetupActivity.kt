package com.appguard.blocker.ui

import android.Manifest
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivitySetupBinding
import com.appguard.blocker.databinding.ItemPermissionBinding
import com.appguard.blocker.service.UsageMonitorService
import com.appguard.blocker.util.OemAutostartHelper
import com.appguard.blocker.util.PermissionHelper
import com.appguard.blocker.util.PermissionItem

class SetupActivity : SecureActivity() {
    // During setup we leave to system Settings often — don't bounce to PIN
    override val requireAuth: Boolean = false

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: PrefsRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        // Ensure self-protect is OFF while user grants permissions
        prefs.protectionArmed = false

        binding.btnContinue.setOnClickListener {
            if (!PermissionHelper.criticalReady(this)) {
                Toast.makeText(this, R.string.setup_incomplete, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            prefs.setupCompleted = true
            prefs.uninstallUnlocked = false
            // Do NOT arm until allowlist is saved (אשר)
            prefs.allowlistEnabled = false
            prefs.protectionArmed = false

            startActivity(
                Intent(this, AllowlistActivity::class.java)
                    .putExtra(AllowlistActivity.EXTRA_FROM_SETUP, true)
            )
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val states = PermissionHelper.allStates(this)
        val grantedCritical = listOf(
            PermissionItem.ACCESSIBILITY,
            PermissionItem.USAGE_ACCESS,
            PermissionItem.DEVICE_ADMIN,
            PermissionItem.OVERLAY,
            PermissionItem.BATTERY_OPTIMIZATION
        ).count { item -> states.first { it.item == item }.granted }

        binding.setupProgress.text = getString(R.string.setup_progress, grantedCritical, 5)

        binding.permissionList.removeAllViews()
        states.forEach { state ->
            if (state.item == PermissionItem.OEM_AUTOSTART &&
                OemAutostartHelper.findAutostartIntent(this) == null
            ) {
                return@forEach
            }
            binding.permissionList.addView(buildRow(state.item, state.granted))
        }

        binding.btnContinue.isEnabled = PermissionHelper.criticalReady(this)
        binding.btnContinue.alpha = if (binding.btnContinue.isEnabled) 1f else 0.55f
    }

    private fun buildRow(item: PermissionItem, granted: Boolean): LinearLayout {
        val row = ItemPermissionBinding.inflate(LayoutInflater.from(this))
        val (title, desc) = when (item) {
            PermissionItem.ACCESSIBILITY ->
                R.string.perm_accessibility_title to R.string.perm_accessibility_desc
            PermissionItem.USAGE_ACCESS ->
                R.string.perm_usage_title to R.string.perm_usage_desc
            PermissionItem.DEVICE_ADMIN ->
                R.string.perm_admin_title to R.string.perm_admin_desc
            PermissionItem.OVERLAY ->
                R.string.perm_overlay_title to R.string.perm_overlay_desc
            PermissionItem.BATTERY_OPTIMIZATION ->
                R.string.perm_battery_title to R.string.perm_battery_desc
            PermissionItem.NOTIFICATIONS ->
                R.string.perm_notifications_title to R.string.perm_notifications_desc
            PermissionItem.OEM_AUTOSTART ->
                R.string.perm_oem_title to R.string.perm_oem_desc
        }
        row.permTitle.setText(title)
        row.permDesc.setText(desc)

        styleStatus(row.permStatus, granted)
        if (granted && item != PermissionItem.OEM_AUTOSTART) {
            row.permAction.setText(R.string.setup_done)
            row.permAction.isEnabled = false
        } else {
            row.permAction.setText(
                if (item == PermissionItem.OEM_AUTOSTART) R.string.setup_open_oem
                else R.string.setup_enable
            )
            row.permAction.isEnabled = true
            row.permAction.setOnClickListener { request(item) }
        }
        return row.root
    }

    private fun styleStatus(view: TextView, granted: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    this@SetupActivity,
                    if (granted) R.color.accent else R.color.danger
                )
            )
        }
        view.background = bg
        view.text = if (granted) "✓" else "!"
        view.setTextColor(ContextCompat.getColor(this, R.color.bg))
    }

    private fun request(item: PermissionItem) {
        when (item) {
            PermissionItem.ACCESSIBILITY -> PermissionHelper.openAccessibility(this)
            PermissionItem.USAGE_ACCESS -> PermissionHelper.openUsageAccess(this)
            PermissionItem.DEVICE_ADMIN -> DeviceAdminHelper.requestEnable(this)
            PermissionItem.OVERLAY -> PermissionHelper.openOverlay(this)
            PermissionItem.BATTERY_OPTIMIZATION -> PermissionHelper.openBatteryOptimization(this)
            PermissionItem.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionItem.OEM_AUTOSTART -> {
                if (!OemAutostartHelper.open(this)) {
                    Toast.makeText(this, R.string.perm_oem_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
