package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityRecoveryBinding
import com.appguard.blocker.util.AuthSession
import com.appguard.blocker.util.PermissionHelper

class RecoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecoveryBinding
    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SecureActivity.applyInsets(this, binding.root)
        prefs = PrefsRepository(this)
        AuthSession.unlock()

        val code = intent.getStringExtra(EXTRA_CODE)
            ?: prefs.generateAndStoreRecoveryCode()

        binding.recoveryCode.text = code
        binding.btnConfirmShot.setOnClickListener {
            prefs.recoveryShown = true
            Toast.makeText(this, R.string.recovery_saved_hint, Toast.LENGTH_LONG).show()
            continueFlow()
        }
    }

    private fun continueFlow() {
        val next = if (!prefs.setupCompleted || !PermissionHelper.criticalReady(this)) {
            Intent(this, SetupActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    companion object {
        const val EXTRA_CODE = "recovery_code"
    }
}
