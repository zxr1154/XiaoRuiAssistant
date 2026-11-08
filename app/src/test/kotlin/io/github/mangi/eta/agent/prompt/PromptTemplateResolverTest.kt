package io.github.mangi.eta.agent.prompt

import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateBinding
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.PromptTemplateSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateResolverTest {
    @Test
    fun conversationThenActualModelThenProviderThenDefaultAreResolvedInOrder() {
        val document = fixture().copy(conversationSelections = mapOf("chat" to PromptTemplateSelection("conversation")))
        assertEquals("conversation", resolve(document).templateId)
        val inherited = document.copy(conversationSelections = mapOf("chat" to PromptTemplateSelection()))
        assertEquals("model", resolve(inherited).templateId)
        // This is the API model name, not a display name or database row identifier.
        assertEquals("provider", resolve(inherited, model = "model-row-id").templateId)
        assertEquals("default", resolve(inherited, provider = "another-provider").templateId)
    }

    @Test
    fun normalModeNeverUsesEvenAnExplicitTemplate() {
        val document = fixture().copy(conversationSelections = mapOf("chat" to PromptTemplateSelection("conversation")))
        val result = resolve(document, mode = ConversationModePreference.NORMAL)
        assertEquals(PromptTemplateResolver.Reason.MODE_DISABLED, result.reason)
        assertTrue(result.text.isEmpty())
    }

    @Test
    fun conversationDisabledWinsOverExplicitAndAllBindings() {
        val document = fixture().copy(conversationSelections = mapOf("chat" to PromptTemplateSelection("conversation", disabled = true)))
        for (mode in listOf(ConversationModePreference.FREEDOM, ConversationModePreference.ROLEPLAY)) {
            val result = resolve(document, mode = mode)
            assertEquals(PromptTemplateResolver.Reason.CONVERSATION_DISABLED, result.reason)
            assertTrue(result.text.isEmpty())
        }
    }

    @Test
    fun roleplayDoesNotInheritModelProviderOrDefaultButAllowsExplicitChoice() {
        assertEquals(PromptTemplateResolver.Reason.ROLEPLAY_INHERITS_NONE,
            resolve(fixture(), mode = ConversationModePreference.ROLEPLAY).reason)
        val explicit = fixture().copy(conversationSelections = mapOf("chat" to PromptTemplateSelection("conversation")))
        assertEquals("conversation", resolve(explicit, mode = ConversationModePreference.ROLEPLAY).templateId)
        assertEquals(PromptTemplateResolver.Reason.ROLEPLAY_INHERITS_NONE,
            resolve(explicit, mode = ConversationModePreference.ROLEPLAY, conversation = "another-chat").reason)
    }

    @Test
    fun missingDisabledAndEmptyExplicitTemplatesNeverFallBack() {
        val document = fixture().copy(templates = fixture().templates + listOf(
            PromptTemplate("disabled", "Disabled", "unused", enabled = false),
            PromptTemplate("empty", "Empty", "  "),
        ))
        for ((id, reason) in listOf(
            "missing" to PromptTemplateResolver.Reason.MISSING,
            "disabled" to PromptTemplateResolver.Reason.DISABLED,
            "empty" to PromptTemplateResolver.Reason.EMPTY,
        )) {
            val result = resolve(document.copy(conversationSelections = mapOf("chat" to PromptTemplateSelection(id))))
            assertEquals(reason, result.reason)
            assertTrue(result.text.isEmpty())
        }
    }

    @Test
    fun brokenBindingDoesNotSilentlySelectProviderOrDefault() {
        val document = fixture().copy(bindings = listOf(
            PromptTemplateBinding("provider-id", "api-model", "missing"),
            PromptTemplateBinding("provider-id", templateId = "provider"),
        ))
        val result = resolve(document)
        assertEquals(PromptTemplateResolver.Source.MODEL, result.source)
        assertEquals(PromptTemplateResolver.Reason.MISSING, result.reason)
        assertTrue(result.text.isEmpty())
    }

    @Test
    fun nullDefaultMeansNoTemplateAndAResolvedSnapshotDoesNotChangeWithLaterEdits() {
        val document = fixture().copy(bindings = emptyList())
        val frozen = resolve(document)
        val edited = document.copy(templates = document.templates.map { it.copy(prompt = "new text") })
        assertEquals("default text", frozen.text)
        assertEquals("new text", resolve(edited).text)
        assertEquals(PromptTemplateResolver.Reason.NO_SELECTION, resolve(document.copy(defaultTemplateId = null)).reason)
    }

    private fun fixture() = PromptTemplateDocument(
        templates = listOf("conversation", "model", "provider", "default").map { PromptTemplate(it, it, "$it text") },
        bindings = listOf(
            PromptTemplateBinding("provider-id", "api-model", "model"),
            PromptTemplateBinding("provider-id", templateId = "provider"),
        ),
        defaultTemplateId = "default",
    )

    private fun resolve(
        document: PromptTemplateDocument,
        mode: String = ConversationModePreference.FREEDOM,
        provider: String = "provider-id",
        model: String = "api-model",
        conversation: String = "chat",
    ) = PromptTemplateResolver.resolve(document, mode, provider, model, conversation)
}
