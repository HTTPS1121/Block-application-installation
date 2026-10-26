package com.appguard.blocker.dns

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.appguard.blocker.R

/**
 * Android Private DNS (Network & internet). Hostname only, not an IP.
 * Writing needs WRITE_SECURE_SETTINGS (one-time adb pm grant).
 */
object PrivateDns {

    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_HOSTNAME = "hostname"

    const val HOST_CLOUDFLARE = "one.one.one.one"
    const val HOST_GOOGLE = "dns.google"
    const val HOST_QUAD9 = "dns.quad9.net"
    const val HOST_ADGUARD = "dns.adguard-dns.com"

    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_HOST = "private_dns_specifier"

    private val HOSTNAME = Regex(
        "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+(?!-)[A-Za-z0-9-]{1,63}(?<!-)\$"
    )
    private val IPV4 = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")

    enum class ApplyResult {
        OK,
        NO_PERMISSION,
        BAD_HOSTNAME,
        WRITE_FAILED,
        UNSUPPORTED
    }

    data class State(val mode: String, val host: String)

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun adbGrant(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun isValidHostname(raw: String): Boolean {
        val host = raw.trim().trimEnd('.')
        if (host.length !in 1..253) return false
        if (host.any { it.isWhitespace() || it == '/' || it == ':' }) return false
        if (IPV4.matches(host)) return false
        return HOSTNAME.matches(host)
    }

    fun read(context: Context): State {
        if (!supported()) return State(MODE_OPPORTUNISTIC, "")
        val cr = context.applicationContext.contentResolver
        val mode = Settings.Global.getString(cr, KEY_MODE)
        val host = Settings.Global.getString(cr, KEY_HOST).orEmpty()
        val normalized = when (mode) {
            MODE_OFF, MODE_HOSTNAME, MODE_OPPORTUNISTIC -> mode
            else -> MODE_OPPORTUNISTIC
        }
        return State(normalized, host)
    }

    /** Specifier first when pinning a host, so strict mode is never saved empty. */
    fun write(context: Context, mode: String, host: String): Boolean {
        if (!supported()) return false
        val cr = context.applicationContext.contentResolver
        return runCatching {
            if (mode == MODE_HOSTNAME) {
                val hostOk = Settings.Global.putString(cr, KEY_HOST, host.trim().trimEnd('.'))
                val modeOk = Settings.Global.putString(cr, KEY_MODE, MODE_HOSTNAME)
                hostOk && modeOk
            } else {
                val modeOk = Settings.Global.putString(cr, KEY_MODE, mode)
                Settings.Global.putString(cr, KEY_HOST, "")
                modeOk
            }
        }.getOrDefault(false)
    }

    fun describe(context: Context, state: State): String = when (state.mode) {
        MODE_OFF -> context.getString(R.string.dns_value_off)
        MODE_HOSTNAME -> state.host.ifBlank { context.getString(R.string.dns_value_host_empty) }
        else -> context.getString(R.string.dns_value_auto)
    }
}
