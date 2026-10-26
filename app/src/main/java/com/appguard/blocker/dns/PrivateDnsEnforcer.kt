package com.appguard.blocker.dns

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.appguard.blocker.data.PrefsRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * While DNS lock is on, rewrite private_dns_* if anything else changes it.
 * UI bounce of the Settings editor is separate (accessibility) and does not grant a lease.
 */
object PrivateDnsEnforcer {

    private val applying = AtomicBoolean(false)
    private var observer: ContentObserver? = null
    private var appContext: Context? = null

    fun sync(context: Context) {
        val app = context.applicationContext
        val prefs = PrefsRepository(app)
        if (prefs.dnsLockEnabled && PrivateDns.supported()) {
            start(app)
        } else {
            stop()
        }
    }

    fun lock(context: Context, mode: String, host: String): PrivateDns.ApplyResult {
        if (!PrivateDns.supported()) return PrivateDns.ApplyResult.UNSUPPORTED
        val hostname = host.trim().trimEnd('.')
        if (mode == PrivateDns.MODE_HOSTNAME && !PrivateDns.isValidHostname(hostname)) {
            return PrivateDns.ApplyResult.BAD_HOSTNAME
        }
        val app = context.applicationContext
        val prefs = PrefsRepository(app)
        if (!prefs.hasDnsSnapshot()) {
            val current = PrivateDns.read(app)
            prefs.setDnsSnapshot(current.mode, current.host)
        }
        prefs.dnsMode = mode
        prefs.dnsHost = if (mode == PrivateDns.MODE_HOSTNAME) hostname else ""
        prefs.dnsLockEnabled = true
        val wrote = if (PrivateDns.canWrite(app)) {
            PrivateDns.write(app, mode, hostname)
        } else {
            null
        }
        start(app)
        return when (wrote) {
            null -> PrivateDns.ApplyResult.NO_PERMISSION
            true -> PrivateDns.ApplyResult.OK
            false -> PrivateDns.ApplyResult.WRITE_FAILED
        }
    }

    fun unlock(context: Context): PrivateDns.ApplyResult {
        val app = context.applicationContext
        val prefs = PrefsRepository(app)
        val snap = prefs.dnsSnapshot()
        prefs.dnsLockEnabled = false
        stop()
        prefs.clearDnsSnapshot()
        if (!PrivateDns.supported()) return PrivateDns.ApplyResult.UNSUPPORTED
        if (snap == null) return PrivateDns.ApplyResult.OK
        if (!PrivateDns.canWrite(app)) return PrivateDns.ApplyResult.NO_PERMISSION
        return if (PrivateDns.write(app, snap.first, snap.second)) {
            PrivateDns.ApplyResult.OK
        } else {
            PrivateDns.ApplyResult.WRITE_FAILED
        }
    }

    private fun start(app: Context) {
        appContext = app
        if (observer == null) {
            val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    enforce(app)
                }
            }
            observer = obs
            val cr = app.contentResolver
            cr.registerContentObserver(Settings.Global.getUriFor("private_dns_mode"), false, obs)
            cr.registerContentObserver(Settings.Global.getUriFor("private_dns_specifier"), false, obs)
        }
        enforce(app)
    }

    private fun stop() {
        val app = appContext
        val obs = observer
        if (app != null && obs != null) {
            runCatching { app.contentResolver.unregisterContentObserver(obs) }
        }
        observer = null
        appContext = null
    }

    private fun enforce(context: Context) {
        val prefs = PrefsRepository(context)
        if (!prefs.dnsLockEnabled) return
        if (!PrivateDns.canWrite(context)) return
        if (!applying.compareAndSet(false, true)) return
        try {
            val desiredMode = prefs.dnsMode
            val desiredHost = prefs.dnsHost
            val current = PrivateDns.read(context)
            val same = current.mode == desiredMode &&
                (desiredMode != PrivateDns.MODE_HOSTNAME || current.host == desiredHost)
            if (same) return
            PrivateDns.write(context, desiredMode, desiredHost)
        } finally {
            applying.set(false)
        }
    }
}
