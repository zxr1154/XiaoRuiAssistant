package io.github.mangi.eta.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModuleTest {
    private val assistant = ConversationSummaryUi("assistant", "普通对话", "", "刚刚", mode = ConversationModeUi.Chat)
    private val story = ConversationSummaryUi("story", "星海故事", "", "昨天", mode = ConversationModeUi.Chat, characterName = "星澜")
    private val pane = ConversationPaneUiState(listOf(story, assistant), "story", "星海")

    @Test fun historiesBelongToTheirOwnModuleWithoutChangingStoredData() {
        assertEquals(listOf(assistant), pane.forModule(AppModule.Assistant).conversations)
        assertEquals(listOf(story), pane.forModule(AppModule.Roleplay).conversations)
        assertEquals(listOf(story, assistant), pane.conversations)
    }

    @Test fun selectionCannotPointAtTheOtherWorkspace() {
        assertNull(pane.forModule(AppModule.Assistant).selectedConversationId)
        assertEquals("story", pane.forModule(AppModule.Roleplay).selectedConversationId)
        assertEquals("星海", pane.forModule(AppModule.Roleplay).searchQuery)
    }

    @Test fun characterBindingRemainsAStoryWhenCharacterNameIsBlank() {
        val unnamed = story.copy(characterName = "")
        assertEquals(listOf(unnamed), pane.copy(conversations = listOf(unnamed)).forModule(AppModule.Roleplay).conversations)
    }
}
