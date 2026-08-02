package com.appguard.blocker.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.appguard.blocker.admin.DeviceAdminHelper
import com.appguard.blocker.data.PrefsRepository
import com.appguard.blocker.protection.ProtectionController
import com.appguard.blocker.service.UsageMonitorService

/** ADB/emergency: disarm + remove Device Admin so the package can be uninstalled. */
class RescueActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            ProtectionController.disarm(this)
            DeviceAdminHelper.removeAdmin(this)
            UsageMonitorService.stop(this)
            PrefsRepository(this).challengeActive = false
        }
        Toast.makeText(this, "AppGuard disarmed — ניתן להסיר", Toast.LENGTH_LONG).show()
        finish()
    }
}
