package io.github.hansfbaier.modpoll.ui

import android.content.Context

/**
 * Keeps the Play-free upgrade prompt from becoming a nag. It appears after the
 * third launch and then at most once every seven days.
 */
class SupportPromptStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("support_prompts", Context.MODE_PRIVATE)

    fun shouldShowUpgradePrompt(now: Long = System.currentTimeMillis()): Boolean {
        val launches = prefs.getInt(KEY_LAUNCHES, 0) + 1
        prefs.edit().putInt(KEY_LAUNCHES, launches).apply()
        if (launches < MIN_LAUNCHES) return false

        val lastShown = prefs.getLong(KEY_LAST_UPGRADE, 0L)
        return lastShown == 0L || now - lastShown >= PROMPT_INTERVAL_MS
    }

    fun markUpgradePromptShown(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_UPGRADE, now).apply()
    }

    private companion object {
        const val KEY_LAUNCHES = "launches"
        const val KEY_LAST_UPGRADE = "last_upgrade"
        const val MIN_LAUNCHES = 3
        const val PROMPT_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
