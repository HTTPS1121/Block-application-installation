package com.appguard.blocker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivitySettingsBinding
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.protection.TamperReason
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : SecureActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        binding.btnSavePin.setOnClickListener { changePin() }
        binding.btnUnlockUninstall.setOnClickListener { confirmUnlock() }
        binding.btnRelockUninstall.setOnClickListener { relock() }
        refreshUnlockState()
    }

    override fun onResume() {
        super.onResume()
        refreshUnlockState()
    }

    private fun refreshUnlockState() {
        val unlocked = prefs.hasValidRemovalLease() || !DeviceAdminHelper.isAdminActive(this)
        binding.unlockStatus.text = if (unlocked) {
            getString(R.string.unlock_status_open)
        } else {
            getString(R.string.unlock_status_locked)
        }
        binding.btnRelockUninstall.visibility =
            if (unlocked) android.view.View.VISIBLE else android.view.View.GONE
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
        val input = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.pin_enter_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unlock_confirm_title)
            .setMessage(R.string.unlock_confirm_message)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                unlockUninstall(input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun unlockUninstall(pin: String) {
        if (!prefs.verifyPin(pin) && !prefs.verifyRecoveryCode(pin)) {
            Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        // Lease 10 min + open App Info — keep Level 1/2 blocking on
        ProtectionController.onPinSuccess(this, TamperReason.MANUAL_UNLOCK)
        refreshUnlockState()
        Toast.makeText(this, R.string.challenge_ok_lease, Toast.LENGTH_LONG).show()
    }

    private fun relock() {
        ProtectionController.clearLease(this)
        if (!DeviceAdminHelper.isAdminActive(this)) {
            DeviceAdminHelper.requestEnable(this)
        }
        refreshUnlockState()
        Toast.makeText(this, R.string.device_admin_on, Toast.LENGTH_SHORT).show()
    }
}
