package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityPinBinding

class PinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private lateinit var prefs: PrefsRepository
    private var setupMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsRepository(this)

        setupMode = !prefs.hasPin()
        if (setupMode) {
            binding.pinTitle.setText(R.string.pin_setup_title)
            binding.pinHint.setText(R.string.pin_setup_hint)
            binding.pinLayout.hint = getString(R.string.pin_setup_hint)
            binding.pinConfirmLayout.visibility = android.view.View.VISIBLE
            binding.pinSubmit.setText(R.string.pin_continue)
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
            openMain()
            return
        }

        if (!prefs.verifyPin(pin)) {
            Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            binding.pinInput.text?.clear()
            return
        }
        openMain()
    }

    private fun openMain() {
        val next = if (!prefs.setupCompleted ||
            !com.appguard.blocker.util.PermissionHelper.criticalReady(this)
        ) {
            Intent(this, SetupActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
