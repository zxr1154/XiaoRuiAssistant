package io.github.mangi.eta.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import io.github.mangi.eta.ui.components.chatConversationCompositionKey
import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.ConversationHistoryLoadState
import io.github.mangi.eta.ui.components.AgentChatBody
import io.github.mangi.eta.ui.components.AgentConversationLoadState

/** Shared message rendering and actions; each workspace owns its header and identity. */
@Composable
internal fun ConversationChatContent(
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
    key(chatConversationCompositionKey(conversationKey)) {
        if (!state.isConversationLoaded) {
            AgentConversationLoadState(
                isLoading = isLoadingConversation,
                failed = conversationLoadFailed,
                onRetry = { onAction(AgentHomeAction.RetryConversationLoad) },
                modifier = modifier,
            )
        } else {
            AgentChatBody(
                messages = state.messages,
                modelPickerState = modelPickerState,
                isCompacting = state.isCompacting,
                input = state.input,
                isStreaming = state.isStreaming,
                reasoningEffort = state.reasoningEffort,
                availableReasoningEfforts = state.availableReasoningEfforts,
                pendingImages = state.pendingImages,
                pendingFileReferences = state.pendingFileReferences,
                queuedMessages = state.queuedMessages,
                inputSyncVersion = state.inputSyncVersion,
                messageEdit = state.messageEdit,
                onReasoningEffortChange = { onAction(AgentHomeAction.ReasoningEffortChanged(it)) },
                onCompactContext = { onAction(AgentHomeAction.CompactContext) },
                canCompactContext = state.canCompactContext,
                onModelSelected = { onAction(AgentHomeAction.ModelSelected(it)) },
                onSubmit = { text -> onAction(AgentHomeAction.SubmitMessage(text)) },
                onInterruptAndSubmit = { text -> onAction(AgentHomeAction.InterruptAndSubmitMessage(text)) },
                onSteerQueuedMessage = { id -> onAction(AgentHomeAction.SteerQueuedMessage(id)) },
                onRecallQueuedMessage = { id, draft -> onAction(AgentHomeAction.RecallQueuedMessage(id, draft)) },
                onRemoveQueuedMessage = { id -> onAction(AgentHomeAction.RemoveQueuedMessage(id)) },
                onStop = { onAction(AgentHomeAction.StopRun) },
                onAttachImage = { uri -> onAction(AgentHomeAction.ImageAttached(uri)) },
                onRemoveImage = { id -> onAction(AgentHomeAction.RemoveImage(id)) },
                onAttachFiles = { uris -> onAction(AgentHomeAction.FilesAttached(uris)) },
                onAttachFolder = { uri -> onAction(AgentHomeAction.FolderAttached(uri)) },
                onAttachFilePath = { path -> onAction(AgentHomeAction.FilePathAttached(path)) },
                onRemoveFileReference = { id -> onAction(AgentHomeAction.RemoveFileReference(id)) },
                onEditMessage = { id -> onAction(AgentHomeAction.EditMessage(id)) },
                onCancelMessageEdit = { onAction(AgentHomeAction.CancelMessageEdit) },
                onDeleteMessage = { id -> onAction(AgentHomeAction.DeleteMessage(id)) },
                onRegenerateMessage = { id -> onAction(AgentHomeAction.RegenerateMessage(id)) },
                onSelectReplyCandidate = { id, index -> onAction(AgentHomeAction.SelectReplyCandidate(id, index)) },
                onSuggestionClick = { prompt ->
                    onAction(AgentHomeAction.SubmitMessage(prompt))
                },
                onRunTraceClick = { onAction(AgentHomeAction.ExpandRunTrace) },
                onOpenBrowser = { onAction(AgentHomeAction.OpenBrowser) },
                hasEarlierMessages = state.hasEarlierMessages,
                messageStartIndex = state.messageStartIndex,
                historyLoadState = historyLoadState,
                onLoadEarlierMessages = { onAction(AgentHomeAction.LoadEarlierMessages) },
                onHistoryViewportChanged = { onAction(AgentHomeAction.HistoryViewportChanged(it)) },
                isDrawerOpen = isDrawerOpen,
                modifier = modifier,
            )
        }
    }
}
