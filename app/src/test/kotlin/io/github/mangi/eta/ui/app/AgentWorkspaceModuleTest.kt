package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.UserMessageUi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentWorkspaceModuleTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private val binding = RoleplayBinding("card", "{\"name\":\"星澜\"}", "星澜")
    private val assistant = AgentChatHomeUiState(
        messages = listOf(UserMessageUi("a", "助手历史")), input = "助手草稿", isStreaming = false, thinkingEnabled = false,
    )
    private val story = assistant.copy(messages = listOf(UserMessageUi("r", "剧情历史")), input = "", roleplay = binding)

    @Before fun setup() {
        shadowOf(context).declareActionUnbindable(AgentRuntimeWire.ACTION_BIND)
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
    }

    @After fun close() {
        scope.cancel()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!job.isCompleted && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper(10, TimeUnit.MILLISECONDS)
            Thread.sleep(1)
        }
        EtaDatabase.closeForTests()
    }

    @Test fun startingAStoryAndReturningRestoresTheAssistantConversation() {
        val state = appState("assistant")
        state.startCharacterConversation(binding, "故事开场")
        assertTrue(state.isRoleplayConversationSelected)
        val storyId = state.conversationPaneState.selectedConversationId
        state.openAssistantWorkspace()
        assertFalse(state.isRoleplayConversationSelected)
        assertEquals("assistant", state.conversationPaneState.selectedConversationId)
        assertEquals("助手草稿", state.homeState.input)
        assertTrue(state.conversationPaneState.conversations.any { it.id == storyId })
        assertTrue(state.conversationPaneState.conversations.any { it.id == "story" })
    }

    @Test fun openingAnOldStoryRestoresRoleplayExecutionAsWellAsThePage() {
        val state = appState("assistant")
        ConversationModePreference.setMode(context, "story", ConversationModePreference.FREEDOM)
        state.selectConversation("story")
        assertTrue(state.isRoleplayConversationSelected)
        assertEquals(ConversationModePreference.ROLEPLAY, ConversationModePreference.getMode(context, "story"))
        state.openAssistantWorkspace()
        assertEquals("assistant", state.conversationPaneState.selectedConversationId)
    }

    @Test fun aNewAssistantWorkspaceDoesNotReuseTheCurrentCharacterBinding() {
        val state = appState(null)
        state.startCharacterConversation(binding, "开场")
        state.openAssistantWorkspace()
        assertFalse(state.isRoleplayConversationSelected)
        assertEquals(null, state.conversationPaneState.selectedConversationId)
        assertTrue(state.homeState.messages.isEmpty())
    }

    @Test fun storyRecordsRemainCompleteWhileTheAssistantHistoryIsFiltered() {
        val state = appState("assistant")
        state.updateSearchQuery("助手")
        assertEquals(listOf("assistant"), state.conversationPaneState.conversations.map { it.id })
        assertEquals(listOf("story"), state.roleplayConversations.map { it.id })
    }

    private fun appState(selected: String?): AgentAppState = AgentAppState(
        context, scope,
        AgentConversationStore.Snapshot(
            selectedConversationId = selected,
            conversationsById = if (selected == null) mapOf("story" to story) else mapOf("assistant" to assistant, "story" to story),
            titles = mapOf("assistant" to "助手", "story" to "旧剧情"),
            updatedAt = mapOf("assistant" to 1L, "story" to 2L),
        ),
    )
}
