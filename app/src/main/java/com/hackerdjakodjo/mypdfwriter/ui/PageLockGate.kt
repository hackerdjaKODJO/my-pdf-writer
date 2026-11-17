package com.hackerdjakodjo.mypdfwriter.ui

/**
 * Pure decision helper for the session-only page lock.
 *
 * Keeping the gate in a small pure object makes the navigation policy testable
 * without an Android ViewModel, and gives every navigation entry point one rule.
 */
internal object PageLockGate {

    /**
     * Ordinary page jumps (page pill, search hit, bookmark, outline, annotation
     * row, read-aloud) are blocked while the page is locked.
     *
     * [explicitSourceJump] marks a study-note source-link jump. Those stay
     * available while locked because the user is following a citation, not
     * browsing pages.
     */
    fun allowsNavigation(pageLocked: Boolean, explicitSourceJump: Boolean = false): Boolean =
        !pageLocked || explicitSourceJump
}
