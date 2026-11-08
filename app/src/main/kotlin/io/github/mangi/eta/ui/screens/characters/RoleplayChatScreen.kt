package io.github.mangi.eta.ui.screens.characters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import io.github.mangi.eta.ui.components.chatConversationCompositionKey
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.HomeModuleTabs
import io.github.mangi.eta.ui.components.LocalRoleplayChat
import io.github.mangi.eta.ui.model.AppModule
import io.github.mangi.eta.ui.model.roleplayChatUi
import io.github.mangi.eta.ui.screens.home.ConversationChatContent
import io.github.mangi.eta.ui.screens.prompts.PromptTemplateBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A dedicated story conversation, with character identity instead of assistant mode chips. */
@Composable
internal fun RoleplayChatScreen(
    state: AgentChatHomeUiState,
    modelPickerState: AgentModelPickerUiState,
    conversationKey: String?,
    onAction: (AgentHomeAction) -> Unit,
    onAssistant: () -> Unit = {},
    isLoadingConversation: Boolean = false,
    conversationLoadFailed: Boolean = false,
    historyLoadState: ConversationHistoryLoadState = ConversationHistoryLoadState.Idle,
    isDrawerOpen: Boolean = false,
    modifier: Modifier = Modifier,
    onManageTemplates: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().testTag("roleplay-chat")) {
        HomeModuleTabs(AppModule.Roleplay, onAssistant, onRoleplay = { onAction(AgentHomeAction.OpenCharacters) })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("roleplay-chat-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.roleplay?.characterName ?: state.conversationCharacterName ?: "角色对话",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            TextButton("角色库", onClick = { onAction(AgentHomeAction.OpenCharacters) })
        }
        PromptTemplateBar(
            conversationId = conversationKey,
            enabled = state.isConversationLoaded && !state.isStreaming && !state.isCompacting,
            onManage = onManageTemplates,
            roleplay = true,
        )
        CompositionLocalProvider(LocalRoleplayChat provides state.roleplayChatUi()) {
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
