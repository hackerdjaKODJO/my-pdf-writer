package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context

/**
 * Stable identity for a first-run onboarding card. The enum declaration order
 * is not used by the UI; [onboardingTopicOrder] is the single source of truth.
 */
enum class OnboardingTopicKey {
    HOME_QUICK_ACTIONS,
    OPEN_READ_PDF,
    SELECT_HIGHLIGHT,
    NOTE_PANE,
    HELP_BACKUP,
}

/**
 * Pure, deterministic display order for the onboarding cards. It reads no
 * Android state, so the sheet and the JVM tests always agree on the sequence.
 */
fun onboardingTopicOrder(): List<OnboardingTopicKey> = listOf(
    OnboardingTopicKey.HOME_QUICK_ACTIONS,
    OnboardingTopicKey.OPEN_READ_PDF,
    OnboardingTopicKey.SELECT_HIGHLIGHT,
    OnboardingTopicKey.NOTE_PANE,
    OnboardingTopicKey.HELP_BACKUP,
)

/** Position of [key] in the canonical order, or -1 when it is not part of it. */
fun onboardingTopicPosition(key: OnboardingTopicKey): Int =
    onboardingTopicOrder().indexOf(key)

/**
 * Remembers whether the first-run walkthrough has already been shown.
 *
 * Only app-private SharedPreferences are used: no new permission, no file in
 * shared storage, and no network, account or analytics dependency.
 */
class OnboardingStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).commit()
    }

    companion object {
        const val PREFS_NAME = "onboarding_v1"
        const val KEY_SEEN = "seen_v1"
    }
}
