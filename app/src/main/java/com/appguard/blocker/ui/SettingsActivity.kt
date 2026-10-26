package com.appguard.blocker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivitySettingsBinding
import com.appguard.blocker.dns.PrivateDns
import com.appguard.blocker.dns.PrivateDnsEnforcer
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
        binding.dnsModeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.dnsHostLayout.visibility =
                if (checkedId == R.id.dnsModeHost) View.VISIBLE else View.GONE
        }
        binding.dnsPresetCloudflare.setOnClickListener { applyPreset(PrivateDns.HOST_CLOUDFLARE) }
        binding.dnsPresetGoogle.setOnClickListener { applyPreset(PrivateDns.HOST_GOOGLE) }
        binding.dnsPresetQuad9.setOnClickListener { applyPreset(PrivateDns.HOST_QUAD9) }
        binding.dnsPresetAdguard.setOnClickListener { applyPreset(PrivateDns.HOST_ADGUARD) }
        binding.btnDnsApply.setOnClickListener { applyDns() }
        binding.btnDnsUnlock.setOnClickListener { unlockDns() }
        binding.btnDnsCopyAdb.setOnClickListener { copyAdb() }
        refreshUnlockState()
        refreshDnsUi()
    }

    override fun onResume() {
        super.onResume()
        PrivateDnsEnforcer.sync(this)
        refreshUnlockState()
        refreshDnsUi()
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

    private fun refreshDnsUi() {
        if (!PrivateDns.supported()) {
            binding.dnsPhoneNow.setText(R.string.dns_unsupported)
            binding.btnDnsApply.isEnabled = false
            binding.dnsAdbBlock.visibility = View.GONE
            return
        }
        val phone = PrivateDns.read(this)
        binding.dnsPhoneNow.text = getString(R.string.dns_phone_now, PrivateDns.describe(this, phone))
        binding.dnsLockState.setText(
            if (prefs.dnsLockEnabled) R.string.dns_lock_on else R.string.dns_lock_off
        )
        val canWrite = PrivateDns.canWrite(this)
        binding.dnsPermission.setText(
            if (canWrite) R.string.dns_perm_ok else R.string.dns_perm_missing
        )
        binding.dnsAdbBlock.visibility = if (canWrite) View.GONE else View.VISIBLE
        binding.dnsAdbCommand.text = PrivateDns.adbGrant(this)
        binding.btnDnsUnlock.visibility = if (prefs.dnsLockEnabled) View.VISIBLE else View.GONE

        val mode = if (prefs.dnsLockEnabled) prefs.dnsMode else phone.mode
        val host = if (prefs.dnsLockEnabled) prefs.dnsHost else phone.host
        val radio = when (mode) {
            PrivateDns.MODE_OFF -> R.id.dnsModeOff
            PrivateDns.MODE_HOSTNAME -> R.id.dnsModeHost
            else -> R.id.dnsModeAuto
        }
        if (binding.dnsModeGroup.checkedRadioButtonId != radio) {
            binding.dnsModeGroup.check(radio)
        }
        if (binding.dnsHost.text?.toString() != host) {
            binding.dnsHost.setText(host)
        }
        binding.dnsHostLayout.visibility =
            if (radio == R.id.dnsModeHost) View.VISIBLE else View.GONE
    }

    private fun applyPreset(host: String) {
        binding.dnsModeHost.isChecked = true
        binding.dnsHost.setText(host)
        binding.dnsHostLayout.visibility = View.VISIBLE
    }

    private fun selectedDnsMode(): String = when (binding.dnsModeGroup.checkedRadioButtonId) {
        R.id.dnsModeOff -> PrivateDns.MODE_OFF
        R.id.dnsModeHost -> PrivateDns.MODE_HOSTNAME
        else -> PrivateDns.MODE_OPPORTUNISTIC
    }

    private fun applyDns() {
        val result = PrivateDnsEnforcer.lock(
            this,
            selectedDnsMode(),
            binding.dnsHost.text?.toString().orEmpty()
        )
        val message = when (result) {
            PrivateDns.ApplyResult.OK -> R.string.dns_applied
            PrivateDns.ApplyResult.NO_PERMISSION -> R.string.dns_saved_no_write
            PrivateDns.ApplyResult.BAD_HOSTNAME -> R.string.dns_bad_host
            PrivateDns.ApplyResult.WRITE_FAILED -> R.string.dns_write_failed
            PrivateDns.ApplyResult.UNSUPPORTED -> R.string.dns_unsupported
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        refreshDnsUi()
    }

    private fun unlockDns() {
        val result = PrivateDnsEnforcer.unlock(this)
        val message = when (result) {
            PrivateDns.ApplyResult.OK -> R.string.dns_unlocked
            PrivateDns.ApplyResult.NO_PERMISSION -> R.string.dns_unlock_no_write
            PrivateDns.ApplyResult.WRITE_FAILED -> R.string.dns_write_failed
            PrivateDns.ApplyResult.UNSUPPORTED -> R.string.dns_unsupported
            PrivateDns.ApplyResult.BAD_HOSTNAME -> R.string.dns_bad_host
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        refreshDnsUi()
    }

    private fun copyAdb() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("adb", PrivateDns.adbGrant(this)))
        Toast.makeText(this, R.string.dns_adb_copied, Toast.LENGTH_SHORT).show()
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
