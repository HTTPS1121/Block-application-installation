package com.appguard.blocker.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.appguard.blocker.R
import com.appguard.blocker.databinding.ActivityBlockedBinding

class BlockedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityBlockedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SecureActivity.applyInsets(this, binding.root)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_APP
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)

        when (mode) {
            MODE_INSTALL -> {
                binding.blockedTitle.setText(R.string.blocked_install_title)
                binding.blockedMessage.setText(R.string.blocked_install_message)
            }
            MODE_UNINSTALL -> {
                binding.blockedTitle.setText(R.string.cannot_uninstall_title)
                binding.blockedMessage.setText(R.string.cannot_uninstall_message)
            }
            else -> {
                binding.blockedTitle.setText(R.string.blocked_title)
                binding.blockedMessage.text = getString(R.string.blocked_message, label.ifBlank { pkg })
            }
        }

        binding.btnGoHome.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PACKAGE = "package"
        const val MODE_APP = "app"
        const val MODE_INSTALL = "install"
        const val MODE_UNINSTALL = "uninstall"
    }
}
