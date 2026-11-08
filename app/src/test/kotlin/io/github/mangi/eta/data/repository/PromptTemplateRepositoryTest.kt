package io.github.mangi.eta.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateBinding
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.PromptTemplateSelection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromptTemplateRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    private fun template(id: String = "custom", prompt: String = "逐步说明技术问题") = PromptTemplate(id, "技术交流", prompt)

    @Before
    fun setUp() = clear()

    @After
    fun tearDown() = clear()

    private fun clear() {
        context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        PromptTemplateRepository.closeForTests(context)
    }

    @Test
    fun savedTemplatesBindingsAndDraftChoiceSurviveRepositoryRestart() {
        assertEquals(PromptTemplateRepository.BUILTIN_DIRECT_ID, PromptTemplateRepository.snapshot(context).defaultTemplateId)
        PromptTemplateRepository.saveTemplate(context, template())
        PromptTemplateRepository.setDefault(context, "custom")
        PromptTemplateRepository.setBinding(context, "provider", "actual-model-name", "custom")
        PromptTemplateRepository.setConversationSelection(context, null, PromptTemplateSelection("custom"))
        PromptTemplateRepository.initializeConversation(context, "conversation-1")
        PromptTemplateRepository.setConversationSelection(context, null, PromptTemplateSelection(disabled = true))
        PromptTemplateRepository.initializeConversation(context, "conversation-1")
        val expected = PromptTemplateRepository.snapshot(context)

        PromptTemplateRepository.closeForTests(context)

        assertEquals(expected, PromptTemplateRepository.observe(context).value)
        assertEquals(PromptTemplateSelection("custom"), PromptTemplateRepository.conversationSelection(context, "conversation-1"))
        assertTrue(PromptTemplateRepository.conversationSelection(context, null).disabled)
    }

    @Test
    fun deletingTemplateClearsBindingsAndDefaultAndDisablesExplicitConversationChoices() {
        PromptTemplateRepository.saveTemplate(context, template())
        PromptTemplateRepository.setDefault(context, "custom")
        PromptTemplateRepository.setBinding(context, "provider", null, "custom")
        PromptTemplateRepository.setConversationSelection(context, "explicit", PromptTemplateSelection("custom"))
        PromptTemplateRepository.setConversationSelection(context, "inherit", PromptTemplateSelection())

        PromptTemplateRepository.deleteTemplate(context, "custom")

        val document = PromptTemplateRepository.snapshot(context)
        assertFalse(document.templates.any { it.id == "custom" })
        assertTrue(document.bindings.isEmpty())
        assertEquals(null, document.defaultTemplateId)
        assertEquals(PromptTemplateSelection(disabled = true), document.conversationSelections["explicit"])
        assertEquals(PromptTemplateSelection(), document.conversationSelections["inherit"])
    }

    @Test
    fun strictImportRejectsMissingIdentityDuplicateKeysBadReferencesAndOversizedPrompts() {
        val valid = PromptTemplateDocument(templates = listOf(template()), defaultTemplateId = "custom")
        val invalid = listOf(
            "{}",
            """{"templates":[],"schemaVersion":1}""",
            """{"format":"xiaorui-prompt-templates","templates":[]}""",
            """{"format":"xiaorui-prompt-templates","schemaVersion":"1","templates":[]}""",
            """{"format":"xiaorui-prompt-templates","schemaVersion":2,"templates":[]}""",
            """{"format":"xiaorui-prompt-templates","schemaVersion":1,"schemaVersion":1,"templates":[]}""",
            json.encodeToString(valid.copy(templates = listOf(template(), template()))),
            json.encodeToString(valid.copy(defaultTemplateId = "missing")),
            json.encodeToString(valid.copy(bindings = listOf(PromptTemplateBinding("provider", templateId = "missing")))),
            json.encodeToString(valid.copy(bindings = List(2) { PromptTemplateBinding("provider", "same-model", "custom") })),
            json.encodeToString(valid.copy(conversationSelections = mapOf("conversation" to PromptTemplateSelection("missing")))),
            json.encodeToString(valid.copy(conversationSelections = mapOf("conversation" to PromptTemplateSelection("custom", disabled = true)))),
            json.encodeToString(valid.copy(templates = listOf(template(prompt = "字".repeat(PromptTemplateRepository.MAX_PROMPT_BYTES / 3 + 1))))),
        )
        val before = PromptTemplateRepository.snapshot(context)
        invalid.forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { PromptTemplateRepository.parseImport(input) }
            assertEquals(before, PromptTemplateRepository.snapshot(context))
        }
        assertEquals(valid, PromptTemplateRepository.parseImport(json.encodeToString(valid)))
    }

    @Test
    fun mergeOverwritesMatchingIdsAndBindingsButPreservesOtherChoicesAndSharingOmitsConversationIds() {
        PromptTemplateRepository.saveTemplate(context, template())
        PromptTemplateRepository.setBinding(context, "provider", "model-name", "custom")
        PromptTemplateRepository.setConversationSelection(context, "private-conversation", PromptTemplateSelection("custom"))
        val incoming = PromptTemplateDocument(templates = listOf(template(prompt = "更新后的正文"), template("another")),
            bindings = listOf(PromptTemplateBinding("provider", "model-name", "another")))

        PromptTemplateRepository.importDocument(context, incoming)

        val merged = PromptTemplateRepository.snapshot(context)
        assertEquals("更新后的正文", merged.templates.single { it.id == "custom" }.prompt)
        assertTrue(merged.templates.any { it.id == PromptTemplateRepository.BUILTIN_DIRECT_ID })
        assertEquals("another", merged.bindings.single().templateId)
        assertEquals(PromptTemplateSelection("custom"), merged.conversationSelections["private-conversation"])
        val exported = PromptTemplateRepository.exportDocument(context)
        assertFalse(exported.contains("private-conversation"))
        assertTrue(PromptTemplateRepository.parseImport(exported).conversationSelections.isEmpty())
    }

    @Test
    fun standaloneImportsPreserveLocalChoicesAndCannotOverrideOrCreateConversationSelections() {
        val local = PromptTemplateDocument(
            templates = listOf(template("local"), template("shared")), defaultTemplateId = "local",
            conversationSelections = mapOf(
                "off" to PromptTemplateSelection(disabled = true),
                "explicit" to PromptTemplateSelection("local"),
                "shared" to PromptTemplateSelection("shared"),
                "inherit" to PromptTemplateSelection(),
            ),
        )
        val incoming = PromptTemplateDocument(
            templates = listOf(template("shared", prompt = "导入后的正文")), defaultTemplateId = "shared",
            conversationSelections = mapOf(
                "off" to PromptTemplateSelection("shared"),
                "external-conversation" to PromptTemplateSelection("shared"),
            ),
        )
        for (replace in listOf(false, true)) {
            PromptTemplateRepository.restoreDocument(context, local)

            PromptTemplateRepository.importDocument(context, incoming, replace)

            val result = PromptTemplateRepository.snapshot(context)
            assertEquals(PromptTemplateSelection(disabled = true), result.conversationSelections["off"])
            assertEquals(PromptTemplateSelection("shared"), result.conversationSelections["shared"])
            assertEquals(PromptTemplateSelection(), result.conversationSelections["inherit"])
            assertFalse("external-conversation" in result.conversationSelections)
            assertEquals(if (replace) PromptTemplateSelection(disabled = true) else PromptTemplateSelection("local"),
                result.conversationSelections["explicit"])
            assertEquals("shared", result.defaultTemplateId)
            assertEquals("导入后的正文", result.templates.single { it.id == "shared" }.prompt)
        }
    }

    @Test
    fun invalidTypedImportNeverChangesStoredDocumentOrPublishedState() {
        PromptTemplateRepository.saveTemplate(context, template())
        val before = PromptTemplateRepository.snapshot(context)
        val encoded = context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PromptTemplateRepository.DOCUMENT_KEY, null)

        assertThrows(IllegalArgumentException::class.java) {
            PromptTemplateRepository.importDocument(context, PromptTemplateDocument(defaultTemplateId = "missing"), replace = true)
        }

        assertEquals(before, PromptTemplateRepository.observe(context).value)
        assertEquals(encoded, context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PromptTemplateRepository.DOCUMENT_KEY, null))
    }

    @Test
    fun corruptedSavedDocumentIsReportedAndCannotBeSilentlyOverwrittenBySaveOrMerge() {
        val preferences = context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putString(PromptTemplateRepository.DOCUMENT_KEY, "{broken-original").commit()

        assertThrows(IllegalStateException::class.java) { PromptTemplateRepository.snapshot(context) }
        assertThrows(IllegalStateException::class.java) { PromptTemplateRepository.saveTemplate(context, template()) }
        assertThrows(IllegalStateException::class.java) {
            PromptTemplateRepository.importDocument(context, PromptTemplateDocument(templates = listOf(template())))
        }

        assertEquals("{broken-original", preferences.getString(PromptTemplateRepository.DOCUMENT_KEY, null))
    }

    @Test
    fun failedDiskCommitDoesNotPublishTheProposedTemplate() {
        val flow = PromptTemplateRepository.observe(context)
        val before = flow.value
        val failingContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = context.getSharedPreferences(name, mode)
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = preferences.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = false
                        }
                    }
                }
            }
        }

        assertThrows(IllegalStateException::class.java) { PromptTemplateRepository.saveTemplate(failingContext, template()) }

        assertEquals(before, flow.value)
        PromptTemplateRepository.closeForTests(context)
        assertEquals(before, PromptTemplateRepository.snapshot(context))
    }
}
