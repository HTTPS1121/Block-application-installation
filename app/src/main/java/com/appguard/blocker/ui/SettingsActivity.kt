package com.appguard.blocker.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.appguard.blocker.R
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivitySettingsBinding
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.protection.TamperReason
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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
        attachHoldToReveal(binding.currentPinLayout, binding.currentPin)
        attachHoldToReveal(binding.newPinLayout, binding.newPin)
        attachHoldToReveal(binding.confirmPinLayout, binding.confirmPin)
        refreshUnlockState()
    }

    override fun onPause() {
        setPinRevealed(binding.currentPin, false)
        setPinRevealed(binding.newPin, false)
        setPinRevealed(binding.confirmPin, false)
        super.onPause()
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
        confirmRememberPin(newPin)
    }

    /** Press-and-hold the eye: show digits. Release: circles again. */
    private fun attachHoldToReveal(layout: TextInputLayout, field: TextInputEditText) {
        layout.post {
            val endIcon = layout.findViewById<View>(
                com.google.android.material.R.id.text_input_end_icon
            ) ?: return@post
            endIcon.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        setPinRevealed(field, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        setPinRevealed(field, false)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> true
                    else -> false
                }
            }
        }
    }

    private fun setPinRevealed(field: TextInputEditText, revealed: Boolean) {
        val hidden = field.transformationMethod is PasswordTransformationMethod
        if (revealed == !hidden) return
        val sel = field.selectionStart
        field.transformationMethod =
            if (revealed) null else PasswordTransformationMethod.getInstance()
        val len = field.text?.length ?: 0
        if (sel in 0..len) field.setSelection(sel)
    }

    private fun confirmRememberPin(newPin: String) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(pad, pad / 2, pad, 0)
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.pin_remember_question)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        container.addView(TextView(this).apply {
            text = newPin
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, 0)
            textSize = 28f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(false)
        })
        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton(R.string.pin_remember_yes) { _, _ -> applyNewPin(newPin) }
            .setNegativeButton(R.string.pin_remember_back, null)
            .show()
    }

    private fun applyNewPin(newPin: String) {
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
