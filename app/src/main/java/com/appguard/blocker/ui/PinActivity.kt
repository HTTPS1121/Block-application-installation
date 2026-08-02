package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityPinBinding
import com.appguard.blocker.protection.AppAccessGuard
import com.appguard.blocker.util.AuthSession
import com.appguard.blocker.util.PermissionHelper
import com.google.android.material.textfield.TextInputEditText

class PinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private lateinit var prefs: PrefsRepository
    private var setupMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SecureActivity.applyInsets(this, binding.root)
        prefs = PrefsRepository(this)
        AppAccessGuard.onGuardianUiOpened(this)

        setupMode = !prefs.hasPin()
        if (setupMode) {
            binding.pinTitle.setText(R.string.pin_setup_title)
            binding.pinHint.setText(R.string.pin_setup_hint)
            binding.pinLayout.hint = getString(R.string.pin_setup_hint)
            binding.pinConfirmLayout.visibility = View.VISIBLE
            binding.pinSubmit.setText(R.string.pin_continue)
            binding.btnForgot.visibility = View.GONE
        } else {
            binding.btnForgot.visibility = View.VISIBLE
            binding.btnForgot.setOnClickListener { recoverWithCode() }
        }

        binding.pinSubmit.setOnClickListener { onSubmit() }
    }

    private fun onSubmit() {
        val pin = binding.pinInput.text?.toString().orEmpty()
        if (pin.length < 4) {
            Toast.makeText(this, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
            return
        }

        if (setupMode) {
            val confirm = binding.pinConfirmInput.text?.toString().orEmpty()
            if (pin != confirm) {
                Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                return
            }
            prefs.setPin(pin)
            AuthSession.unlock()
            if (!prefs.recoveryShown) {
                val code = prefs.generateAndStoreRecoveryCode()
                startActivity(
                    Intent(this, RecoveryActivity::class.java)
                        .putExtra(RecoveryActivity.EXTRA_CODE, code)
                )
            } else {
                openNext()
            }
            finish()
            return
        }

        if (!prefs.verifyPin(pin)) {
            Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            binding.pinInput.text?.clear()
            return
        }
        AuthSession.unlock()
        openNext()
    }

    private fun recoverWithCode() {
        val input = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            hint = getString(R.string.recovery_enter)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.recovery_use_title)
            .setMessage(R.string.recovery_use_message)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val code = input.text?.toString().orEmpty()
                if (!prefs.verifyRecoveryCode(code)) {
                    Toast.makeText(this, R.string.recovery_wrong, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(this, R.string.recovery_ok_set_pin, Toast.LENGTH_LONG).show()
                setupMode = true
                binding.pinTitle.setText(R.string.pin_setup_title)
                binding.pinHint.setText(R.string.pin_setup_hint)
                binding.pinConfirmLayout.visibility = View.VISIBLE
                binding.pinSubmit.setText(R.string.pin_continue)
                binding.btnForgot.visibility = View.GONE
                binding.pinInput.text?.clear()
            }
            .show()
    }

    private fun openNext() {
        val next = when {
            !prefs.setupCompleted || !PermissionHelper.criticalReady(this) ->
                Intent(this, SetupActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
