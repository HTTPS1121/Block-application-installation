package com.appguard.blocker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivitySettingsBinding
import com.appguard.blocker.service.BlockCoordinator
import com.appguard.blocker.service.UsageMonitorService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        binding.btnSavePin.setOnClickListener { changePin() }
        binding.btnUnlockUninstall.setOnClickListener { confirmUnlock() }
    }

    private fun changePin() {
        val current = binding.currentPin.text?.toString().orEmpty()
        val newPin = binding.newPin.text?.toString().orEmpty()
        val confirm = binding.confirmPin.text?.toString().orEmpty()

        if (!prefs.verifyPin(current)) {
            Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        if (newPin.length < 4) {
            Toast.makeText(this, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (newPin != confirm) {
            Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
            return
        }
        prefs.setPin(newPin)
        binding.currentPin.text?.clear()
        binding.newPin.text?.clear()
        binding.confirmPin.text?.clear()
        Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show()
    }

    private fun confirmUnlock() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unlock_confirm_title)
            .setMessage(R.string.unlock_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> unlockUninstall() }
            .show()
    }

    private fun unlockUninstall() {
        prefs.uninstallUnlocked = true
        prefs.allowlistEnabled = false
        prefs.installBlockEnabled = false
        prefs.setupCompleted = false
        DeviceAdminHelper.applyInstallRestriction(this, false)
        BlockCoordinator.dismissOverlay(this)
        UsageMonitorService.stop(this)

        if (DeviceAdminHelper.isAdminActive(this)) {
            DeviceAdminHelper.removeAdmin(this)
        }
        Toast.makeText(this, R.string.uninstall_unlocked, Toast.LENGTH_LONG).show()
    }
}
