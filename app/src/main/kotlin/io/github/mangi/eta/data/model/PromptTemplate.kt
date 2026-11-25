package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

@Serializable
internal data class PromptTemplate(
    val id: String,
    val name: String,
    val prompt: String,
    val enabled: Boolean = true,
)

/** modelId is the actual model name sent in ModelConfig.model, not its database row ID. */
@Serializable
internal data class PromptTemplateBinding(
    val providerId: String,
    val modelId: String? = null,
    val templateId: String,
)

/** A null template with disabled=false inherits the model/provider/default selection. */
@Serializable
internal data class PromptTemplateSelection(
    val templateId: String? = null,
    val disabled: Boolean = false,
)

@Serializable
internal data class PromptTemplateDocument(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val templates: List<PromptTemplate> = emptyList(),
    val bindings: List<PromptTemplateBinding> = emptyList(),
    val defaultTemplateId: String? = null,
    val conversationSelections: Map<String, PromptTemplateSelection> = emptyMap(),
) {
    companion object {
        const val FORMAT = "xiaorui-prompt-templates"
        const val SCHEMA_VERSION = 1
    }
}
