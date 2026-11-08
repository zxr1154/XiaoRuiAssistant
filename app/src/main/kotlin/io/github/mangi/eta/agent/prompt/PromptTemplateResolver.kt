package io.github.mangi.eta.agent.prompt

import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.data.model.PromptTemplateDocument

/** Resolves one immutable run snapshot. A broken explicit choice never selects another template. */
internal object PromptTemplateResolver {
    enum class Source { NONE, CONVERSATION, MODEL, PROVIDER, DEFAULT }
    enum class Reason { APPLIED, MODE_DISABLED, CONVERSATION_DISABLED, ROLEPLAY_INHERITS_NONE, NO_SELECTION, MISSING, DISABLED, EMPTY }

    data class Resolution(
        val templateId: String? = null,
        val text: String = "",
        val source: Source = Source.NONE,
        val reason: Reason,
    )

    fun resolve(
        document: PromptTemplateDocument,
        mode: String,
        providerId: String,
        modelId: String,
        conversationId: String?,
    ): Resolution {
        if (mode != ConversationModePreference.FREEDOM && mode != ConversationModePreference.ROLEPLAY) {
            return Resolution(reason = Reason.MODE_DISABLED)
        }
        val selection = conversationId?.let(document.conversationSelections::get)
        if (selection?.disabled == true) return Resolution(reason = Reason.CONVERSATION_DISABLED)
        selection?.templateId?.let { return select(document, it, Source.CONVERSATION) }
        // A character's style is changed only by an explicit choice in that character conversation.
        if (mode == ConversationModePreference.ROLEPLAY) {
            return Resolution(reason = Reason.ROLEPLAY_INHERITS_NONE)
        }
        document.bindings.firstOrNull { it.providerId == providerId && it.modelId == modelId }
            ?.let { return select(document, it.templateId, Source.MODEL) }
        document.bindings.firstOrNull { it.providerId == providerId && it.modelId == null }
            ?.let { return select(document, it.templateId, Source.PROVIDER) }
        return document.defaultTemplateId?.let { select(document, it, Source.DEFAULT) }
            ?: Resolution(reason = Reason.NO_SELECTION)
    }

    private fun select(document: PromptTemplateDocument, id: String, source: Source): Resolution {
        val template = document.templates.firstOrNull { it.id == id }
            ?: return Resolution(source = source, reason = Reason.MISSING)
        if (!template.enabled) return Resolution(source = source, reason = Reason.DISABLED)
        if (template.prompt.isBlank()) return Resolution(source = source, reason = Reason.EMPTY)
        return Resolution(templateId = template.id, text = template.prompt, source = source, reason = Reason.APPLIED)
    }
}
