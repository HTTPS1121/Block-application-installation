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
    private lateinit var shownCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SecureActivity.applyInsets(this, binding.root)
        prefs = PrefsRepository(this)
        AuthSession.unlock()

        // Prefer restored instance / intent — never regenerate on rotation (would overwrite hash)
        shownCode = savedInstanceState?.getString(STATE_CODE)
            ?: intent.getStringExtra(EXTRA_CODE)
            ?: prefs.generateAndStoreRecoveryCode()
        intent.putExtra(EXTRA_CODE, shownCode)

        OnboardingSteps.bind(binding.root, OnboardingSteps.RECOVERY)
        binding.recoveryCode.text = shownCode
        binding.btnConfirmShot.setOnClickListener {
            prefs.recoveryShown = true
            Toast.makeText(this, R.string.recovery_saved_hint, Toast.LENGTH_LONG).show()
            continueFlow()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::shownCode.isInitialized) {
            outState.putString(STATE_CODE, shownCode)
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
        private const val STATE_CODE = "state_recovery_code"
    }
}
