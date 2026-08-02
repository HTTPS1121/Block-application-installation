package com.appguard.blocker.ui

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.appguard.blocker.R

/** Shared 4-step onboarding rail: PIN → שחזור → הרשאות → רשימה */
object OnboardingSteps {
    const val PIN = 1
    const val RECOVERY = 2
    const val PERMISSIONS = 3
    const val ALLOWLIST = 4

    fun bind(root: View?, active: Int) {
        if (root == null) return
        val rail = root.findViewById<View>(R.id.onboardingRail) ?: return
        rail.visibility = View.VISIBLE
        style(rail.findViewById(R.id.step1), rail.findViewById(R.id.step1Label), 1, active)
        style(rail.findViewById(R.id.step2), rail.findViewById(R.id.step2Label), 2, active)
        style(rail.findViewById(R.id.step3), rail.findViewById(R.id.step3Label), 3, active)
        style(rail.findViewById(R.id.step4), rail.findViewById(R.id.step4Label), 4, active)
    }

    private fun style(dot: TextView?, label: TextView?, index: Int, active: Int) {
        if (dot == null || label == null) return
        val ctx = dot.context
        val done = index < active
        val current = index == active
        when {
            current -> {
                dot.setBackgroundResource(R.drawable.bg_step_active)
                dot.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_on_primary))
                label.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_primary))
            }
            done -> {
                dot.setBackgroundResource(R.drawable.bg_step_done)
                dot.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_on_primary_container))
                label.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_on_surface_variant))
            }
            else -> {
                dot.setBackgroundResource(R.drawable.bg_step_idle)
                dot.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_outline))
                label.setTextColor(ContextCompat.getColor(ctx, R.color.md_theme_outline))
            }
        }
        dot.text = if (done) "✓" else index.toString()
    }
}
