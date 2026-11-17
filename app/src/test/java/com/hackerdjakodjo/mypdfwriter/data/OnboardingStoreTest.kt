package com.hackerdjakodjo.mypdfwriter.data

import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey.HELP_BACKUP
import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey.HOME_QUICK_ACTIONS
import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey.NOTE_PANE
import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey.OPEN_READ_PDF
import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey.SELECT_HIGHLIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM checks for the one-time flag contract and the canonical card order.
 * The SharedPreferences instance itself is Android framework code, so only its
 * stable keys and the pure order helper are verified here.
 */
class OnboardingStoreTest {

    @Test
    fun flagUsesStableAppPrivateKeys() {
        assertEquals("onboarding_v1", OnboardingStore.PREFS_NAME)
        assertEquals("seen_v1", OnboardingStore.KEY_SEEN)
        assertTrue(OnboardingStore.PREFS_NAME.startsWith("onboarding"))
        assertTrue(OnboardingStore.KEY_SEEN.startsWith("seen"))
        // Must not collide with the existing recents file ("library").
        assertNotEquals("library", OnboardingStore.PREFS_NAME)
    }

    @Test
    fun topicOrderIsCanonicalAndComplete() {
        assertEquals(
            listOf(
                HOME_QUICK_ACTIONS,
                OPEN_READ_PDF,
                SELECT_HIGHLIGHT,
                NOTE_PANE,
                HELP_BACKUP,
            ),
            onboardingTopicOrder(),
        )
    }

    @Test
    fun topicOrderContainsEveryKeyExactlyOnce() {
        val order = onboardingTopicOrder()
        assertEquals(OnboardingTopicKey.entries.size, order.size)
        assertEquals(order.toSet().size, order.size)
        assertEquals(OnboardingTopicKey.entries.toSet(), order.toSet())
    }

    @Test
    fun positionHelperMatchesTheOrder() {
        val order = onboardingTopicOrder()
        order.forEachIndexed { index, key ->
            assertEquals(index, onboardingTopicPosition(key))
        }
    }
}
