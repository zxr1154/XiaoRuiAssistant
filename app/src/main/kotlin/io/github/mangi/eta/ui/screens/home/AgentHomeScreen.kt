package io.github.mangi.eta.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import io.github.mangi.eta.ui.components.chatConversationCompositionKey
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.ui.components.HomeModuleTabs
import io.github.mangi.eta.ui.components.LocalRoleplayChat
import io.github.mangi.eta.ui.model.AppModule

/** Assistant workspace: ordinary and direct-response styles, with a separate roleplay entry. */
@Composable
internal fun AgentHomeScreen(
    state: AgentChatHomeUiState,
    modelPickerState: AgentModelPickerUiState,
    conversationKey: String?,
    onAction: (AgentHomeAction) -> Unit,
    isLoadingConversation: Boolean = false,
    conversationLoadFailed: Boolean = false,
    historyLoadState: ConversationHistoryLoadState = ConversationHistoryLoadState.Idle,
    isDrawerOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedMode by remember(conversationKey) {
        mutableStateOf(ConversationModePreference.getMode(context, conversationKey).let {
            if (it == ConversationModePreference.ROLEPLAY) ConversationModePreference.NORMAL else it
        })
    }
    Column(modifier.fillMaxSize()) {
        HomeModuleTabs(AppModule.Assistant, onAssistant = {}, onRoleplay = { onAction(AgentHomeAction.OpenCharacters) })
        ConversationModeBar(
            selectedMode = selectedMode,
            enabled = state.isConversationLoaded && !state.isStreaming && !state.isCompacting,
            onModeSelected = { mode ->
                selectedMode = mode
                ConversationModePreference.setMode(context, conversationKey, mode)
            },
        )
        CompositionLocalProvider(LocalRoleplayChat provides null) {
            ConversationChatContent(
                state = state,
                modelPickerState = modelPickerState,
                conversationKey = conversationKey,
                onAction = onAction,
                isLoadingConversation = isLoadingConversation,
                conversationLoadFailed = conversationLoadFailed,
                historyLoadState = historyLoadState,
                isDrawerOpen = isDrawerOpen,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
