package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.agent.prompt.PromptTemplateResolver
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.ConversationEntity
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.PromptTemplateSelection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationModeBackupTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    private fun modes() = context.getSharedPreferences("eta_conversation_modes", Context.MODE_PRIVATE)
    private fun conversation(id: String) = ConversationEntity(id, id, false, createdAt = 1, updatedAt = 1)

    @Before
    fun setUp() {
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
        SettingsDataStore.init(context)
        ProviderRepository.init(context)
        AgentMemoryRepository.init(context)
        AgentMemoryRepository.replaceAll("")
        clearPreferences()
        runBlocking {
            SettingsDataStore.setSelection(null, null)
            SettingsDataStore.setOfficialModelCatalogRevision(0)
        }
    }

    @After
    fun tearDown() = clearPreferences()

    private fun clearPreferences() {
        modes().edit().clear().commit()
        context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        PromptTemplateRepository.closeForTests(context)
    }

    @Test
    fun cleanInstallationRestoreKeepsFreedomModeAndActuallyResolvesItsCustomTemplate() = runBlocking {
        EtaDatabase.get(context).conversationDao().insertConversations(listOf(conversation("restored")))
        ConversationModePreference.setMode(context, "restored", ConversationModePreference.FREEDOM)
        PromptTemplateRepository.restoreDocument(context, PromptTemplateDocument(
            templates = listOf(PromptTemplate("custom", "自定义", "恢复后继续使用此正文")),
            conversationSelections = mapOf("restored" to PromptTemplateSelection("custom")),
        ))
        val output = ByteArrayOutputStream()
        EtaBackupRepository.export(context, output)
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
        clearPreferences()
        assertEquals(ConversationModePreference.NORMAL, ConversationModePreference.getMode(context, "restored"))

        EtaBackupRepository.import(context, ByteArrayInputStream(output.toByteArray()), forgetCredentials = {})

        val mode = ConversationModePreference.getMode(context, "restored")
        assertEquals(ConversationModePreference.FREEDOM, mode)
        val resolved = PromptTemplateResolver.resolve(PromptTemplateRepository.snapshot(context), mode, "provider", "model", "restored")
        assertEquals(PromptTemplateResolver.Reason.APPLIED, resolved.reason)
        assertEquals("custom", resolved.templateId)
        assertEquals("恢复后继续使用此正文", resolved.text)
    }

    @Test
    fun oldBackupWithoutModeFieldPreservesAllLocalModePreferences() = runBlocking {
        modes().edit().putString("existing_mode", ConversationModePreference.FREEDOM)
            .putString("__draft_mode", ConversationModePreference.FREEDOM)
            .putBoolean("legacy", true).putString("future-setting", "unchanged").commit()
        val before = ConversationModePreference.captureSnapshot(context)
        val old = EtaBackupDocument(exportedAt = 0, conversations = listOf(conversation("existing")))
        val encoded = json.encodeToString(JsonObject(json.encodeToJsonElement(old).jsonObject - "conversationModes"))

        EtaBackupRepository.import(context, ByteArrayInputStream(encoded.toByteArray()), forgetCredentials = {})

        assertEquals(before, ConversationModePreference.captureSnapshot(context))
        assertEquals(ConversationModePreference.FREEDOM, ConversationModePreference.getMode(context, "existing"))
    }

    @Test
    fun exportConvertsLegacyBooleanModesAndFiltersDeletedConversationsAndDraft() = runBlocking {
        EtaDatabase.get(context).conversationDao().insertConversations(listOf(conversation("legacy"), conversation("off")))
        modes().edit().putBoolean("legacy", true).putBoolean("off", false).putBoolean("deleted", true)
            .putString("__draft_mode", ConversationModePreference.FREEDOM).commit()
        val output = ByteArrayOutputStream()

        EtaBackupRepository.export(context, output)

        val backup = json.decodeFromString<EtaBackupDocument>(output.toString(Charsets.UTF_8.name()))
        assertEquals(mapOf("legacy" to ConversationModePreference.ROLEPLAY, "off" to ConversationModePreference.NORMAL), backup.conversationModes)
        assertEquals(2, EtaBackupRepository.inspect(context, ByteArrayInputStream(output.toByteArray())).conversationCount)
    }

    @Test
    fun invalidModeOrMissingConversationIsRejectedBeforeAnySavedDataChanges() = runBlocking {
        val database = EtaDatabase.get(context)
        database.conversationDao().insertConversations(listOf(conversation("original")))
        modes().edit().putString("original_mode", ConversationModePreference.FREEDOM).putBoolean("legacy", true).commit()
        val before = ConversationModePreference.captureSnapshot(context)
        val templates = PromptTemplateRepository.snapshot(context)
        for (invalidModes in listOf(mapOf("original" to "unsupported"), mapOf("missing" to ConversationModePreference.FREEDOM))) {
            val document = EtaBackupDocument(exportedAt = 0, conversations = listOf(conversation("original")), conversationModes = invalidModes)
            var rejected = false
            try {
                EtaBackupRepository.import(context, ByteArrayInputStream(json.encodeToString(document).toByteArray()), forgetCredentials = {})
            } catch (_: EtaBackupException) { rejected = true }
            assertTrue(rejected)
            assertEquals(before, ConversationModePreference.captureSnapshot(context))
            assertEquals(listOf("original"), database.conversationDao().conversationEntities().map { it.id })
            assertEquals(templates, PromptTemplateRepository.snapshot(context))
        }
    }
}
