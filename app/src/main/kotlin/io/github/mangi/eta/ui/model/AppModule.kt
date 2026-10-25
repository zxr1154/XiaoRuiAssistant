package io.github.mangi.eta.ui.model

/** Workspace membership follows the character binding, independent of a saved prompt mode. */
internal enum class AppModule { Assistant, Roleplay }

internal val ConversationSummaryUi.isRoleplayConversation: Boolean get() = characterName != null

internal fun ConversationPaneUiState.forModule(module: AppModule): ConversationPaneUiState {
    val matching = conversations.filter { it.isRoleplayConversation == (module == AppModule.Roleplay) }
    return copy(
        conversations = matching,
        selectedConversationId = selectedConversationId?.takeIf { id -> matching.any { it.id == id } },
    )
}

internal val AgentChatHomeUiState.isRoleplayConversation: Boolean
    get() = roleplay != null || conversationCharacterName != null
