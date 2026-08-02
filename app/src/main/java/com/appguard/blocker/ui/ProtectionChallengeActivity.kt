package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.appguard.blocker.R
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.databinding.ActivityProtectionChallengeBinding
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.protection.TamperReason
import com.appguard.blocker.util.AuthSession

/**
 * Kaspersky-style PIN gate. Does not HOME-kick on every pause (that locked the app out).
 */
class ProtectionChallengeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProtectionChallengeBinding
    private lateinit var prefs: PrefsRepository
    private var reason: TamperReason = TamperReason.UNINSTALL
    private var intentionalLeave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityProtectionChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SecureActivity.applyInsets(this, binding.root)

        prefs = PrefsRepository(this)
        reason = parseReason(intent)
        prefs.challengeActive = true
        prefs.pendingTamperReason = reason.name

        binding.challengeTitle.setText(titleFor(reason))
        binding.challengeMessage.setText(messageFor(reason))
        binding.challengeSubmit.setOnClickListener { submit() }
        binding.challengeCancel.setOnClickListener { cancelChallenge() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = cancelChallenge()
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reason = parseReason(intent)
        prefs.challengeActive = true
        prefs.pendingTamperReason = reason.name
        binding.challengeTitle.setText(titleFor(reason))
        binding.challengeMessage.setText(messageFor(reason))
        binding.challengePin.text?.clear()
    }

    private fun submit() {
        val pin = binding.challengePin.text?.toString().orEmpty()
        if (!prefs.verifyPin(pin) && !prefs.verifyRecoveryCode(pin)) {
            Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            binding.challengePin.text?.clear()
            return
        }
        intentionalLeave = true
        AuthSession.unlock()
        ProtectionController.onPinSuccess(
            this,
            reason,
            navigateToSystem = reason == TamperReason.MANUAL_UNLOCK
        )
        Toast.makeText(this, R.string.challenge_ok_lease, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun cancelChallenge() {
        intentionalLeave = true
        ProtectionController.onChallengeCancelled(this)
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
        finish()
    }

    private fun parseReason(intent: Intent?): TamperReason {
        val name = intent?.getStringExtra(EXTRA_REASON)
            ?: prefs.pendingTamperReason
            ?: TamperReason.UNINSTALL.name
        return runCatching { TamperReason.valueOf(name) }.getOrDefault(TamperReason.UNINSTALL)
    }

    private fun titleFor(reason: TamperReason): Int = when (reason) {
        TamperReason.DISABLE_DEVICE_ADMIN -> R.string.challenge_title_admin
        TamperReason.DISABLE_ACCESSIBILITY -> R.string.challenge_title_a11y
        TamperReason.MANUAL_UNLOCK -> R.string.unlock_confirm_title
        TamperReason.UNINSTALL -> R.string.challenge_title
    }

    private fun messageFor(reason: TamperReason): Int = when (reason) {
        TamperReason.DISABLE_DEVICE_ADMIN -> R.string.challenge_message_admin
        TamperReason.DISABLE_ACCESSIBILITY -> R.string.challenge_message_a11y
        TamperReason.MANUAL_UNLOCK -> R.string.challenge_message_manual
        TamperReason.UNINSTALL -> R.string.challenge_message_uninstall
    }

    companion object {
        const val EXTRA_REASON = "tamper_reason"
    }
}
