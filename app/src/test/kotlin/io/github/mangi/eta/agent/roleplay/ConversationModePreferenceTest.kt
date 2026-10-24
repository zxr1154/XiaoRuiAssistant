package io.github.mangi.eta.agent.roleplay

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConversationModePreferenceTest {
    private val context get() = RuntimeEnvironment.getApplication<Application>()

    @Before fun clearPreferences() {
        context.getSharedPreferences("eta_conversation_modes", 0).edit().clear().commit()
    }

    @Test fun draftModeIsCopiedBeforeFirstMessageAndDoesNotChangeOtherConversations() {
        ConversationModePreference.setMode(context, null, ConversationModePreference.FREEDOM)
        ConversationModePreference.initializeConversation(context, "first")
        ConversationModePreference.setMode(context, null, ConversationModePreference.NORMAL)
        ConversationModePreference.initializeConversation(context, "second")
        assertEquals(ConversationModePreference.FREEDOM, ConversationModePreference.getMode(context, "first"))
        assertEquals(ConversationModePreference.NORMAL, ConversationModePreference.getMode(context, "second"))
    }

    @Test fun characterConversationStartsInRoleplayEvenWhenDraftIsFreedom() {
        ConversationModePreference.setMode(context, null, ConversationModePreference.FREEDOM)
        ConversationModePreference.initializeConversation(context, "character", hasCharacter = true)
        assertEquals(ConversationModePreference.ROLEPLAY, ConversationModePreference.getMode(context, "character"))
    }

    @Test fun reopeningDoesNotOverwriteAnExplicitConversationMode() {
        ConversationModePreference.setMode(context, "character", ConversationModePreference.NORMAL)
        ConversationModePreference.initializeConversation(context, "character", hasCharacter = true)
        assertEquals(ConversationModePreference.NORMAL, ConversationModePreference.getMode(context, "character", true))
    }

    @Test fun oldCharacterConversationDefaultsToRoleplayAndOrdinaryConversationToNormal() {
        assertEquals(ConversationModePreference.ROLEPLAY, ConversationModePreference.getMode(context, "old-character", true))
        assertEquals(ConversationModePreference.NORMAL, ConversationModePreference.getMode(context, "ordinary"))
    }

    @Test fun legacyBooleanSelectionSurvivesMigration() {
        context.getSharedPreferences("eta_conversation_modes", 0).edit().putBoolean("legacy", true).commit()
        ConversationModePreference.initializeConversation(context, "legacy")
        assertEquals(ConversationModePreference.ROLEPLAY, ConversationModePreference.getMode(context, "legacy"))
    }
}
