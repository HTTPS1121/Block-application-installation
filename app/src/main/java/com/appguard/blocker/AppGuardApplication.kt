package com.appguard.blocker

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.appguard.blocker.service.PackageChangeReceiver

class AppGuardApplication : Application() {

    private val packageChangeReceiver = PackageChangeReceiver()

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(packageChangeReceiver, filter)
        }
    }
}
