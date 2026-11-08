package io.github.mangi.eta.data.repository

import android.content.Context
import io.github.mangi.eta.agent.roleplay.CharacterCardCodec
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.ConversationEntity
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.PromptTemplateSelection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromptTemplateBackupTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    @Before
    fun setUp() {
        EtaDatabase.closeForTests()
        context.deleteDatabase("eta.db")
        SettingsDataStore.init(context)
        ProviderRepository.init(context)
        AgentMemoryRepository.init(context)
        AgentMemoryRepository.replaceAll("")
        clearTemplates()
        runBlocking {
            SettingsDataStore.setSelection(null, null)
            SettingsDataStore.setOfficialModelCatalogRevision(0)
        }
    }

    @After
    fun tearDown() = clearTemplates()

    private fun clearTemplates() {
        context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        PromptTemplateRepository.closeForTests(context)
    }

    private fun document(prompt: String) = PromptTemplateDocument(
        templates = listOf(PromptTemplate("backup-template", "备份模板", prompt)), defaultTemplateId = "backup-template",
        conversationSelections = mapOf("conversation" to PromptTemplateSelection("backup-template")),
    )

    @Test
    fun fullBackupRoundTripsTemplatesAndConversationChoicesAndOldBackupsPreserveThem() = runBlocking {
        val expected = document("导出的正文")
        PromptTemplateRepository.restoreDocument(context, expected)
        val output = ByteArrayOutputStream()
        EtaBackupRepository.export(context, output)
        assertEquals(expected, json.decodeFromString<EtaBackupDocument>(output.toString(Charsets.UTF_8.name())).promptTemplates)
        PromptTemplateRepository.restoreDocument(context, document("本机改动"))

        EtaBackupRepository.import(context, ByteArrayInputStream(output.toByteArray()), forgetCredentials = {})

        assertEquals(expected, PromptTemplateRepository.snapshot(context))
        EtaBackupRepository.import(context, ByteArrayInputStream(json.encodeToString(EtaBackupDocument(exportedAt = 0)).toByteArray()),
            forgetCredentials = {})
        assertEquals(expected, PromptTemplateRepository.snapshot(context))
    }

    @Test
    fun invalidTemplateReferencesAreRejectedBeforeDatabaseOrSavedTemplatesChange() = runBlocking {
        val database = EtaDatabase.get(context)
        database.conversationDao().insertConversations(listOf(ConversationEntity(
            id = "existing", title = "原会话", thinkingEnabled = false, createdAt = 1, updatedAt = 1,
        )))
        val before = PromptTemplateRepository.snapshot(context)
        val invalid = EtaBackupDocument(exportedAt = 0, promptTemplates = PromptTemplateDocument(defaultTemplateId = "missing"))
        var rejected = false

        try {
            EtaBackupRepository.import(context, ByteArrayInputStream(json.encodeToString(invalid).toByteArray()), forgetCredentials = {})
        } catch (_: EtaBackupException) { rejected = true }

        assertTrue(rejected)
        assertEquals(listOf("existing"), database.conversationDao().conversationEntities().map { it.id })
        assertEquals(before, PromptTemplateRepository.snapshot(context))
    }

    @Test
    fun laterMemoryFailureRestoresTemplatesAlongWithDatabaseAndOtherMemoryFiles() = runBlocking {
        CharacterRepository.initialize(context)
        val blocked = CharacterRepository.create(CharacterCardCodec.create("阻塞角色"))
        val database = EtaDatabase.get(context)
        val originalConversation = ConversationEntity("conversation", "原会话", false, createdAt = 1, updatedAt = 1)
        database.conversationDao().insertConversations(listOf(originalConversation))
        val modePrefs = context.getSharedPreferences("eta_conversation_modes", Context.MODE_PRIVATE)
        val outsideModes = ConversationModePreference.captureSnapshot(context)
        modePrefs.edit().clear().putString("conversation_mode", ConversationModePreference.NORMAL)
            .putString("__draft_mode", ConversationModePreference.FREEDOM).putBoolean("legacy-conversation", true)
            .putString("future-value", "原值").putInt("future-int", 7).putLong("future-long", 9L)
            .putFloat("future-float", 1.5f).putStringSet("future-set", setOf("保留", "全部")).commit()
        val originalModes = ConversationModePreference.captureSnapshot(context)
        val previous = document("原模板正文")
        PromptTemplateRepository.restoreDocument(context, previous)
        AgentMemoryRepository.replaceAll("原现实记忆")
        val imported = EtaBackupDocument(exportedAt = 0, memoryMd = "新现实记忆", promptTemplates = document("新模板正文"),
            conversations = listOf(originalConversation), conversationModes = mapOf("conversation" to ConversationModePreference.FREEDOM),
            roleplay = CharacterBackupData(characters = database.characterDao().characters(),
                assets = listOf(CharacterBackupAsset(blocked.id, memoryMd = "写入失败"))),
        )
        val blocker = File(context.filesDir, "roleplay/${blocked.id}/memory")
        check(blocker.parentFile!!.mkdirs() || blocker.parentFile!!.isDirectory)
        blocker.writeText("阻止创建记忆目录")
        var committed = false
        var failed = false
        try {
            try {
                EtaBackupRepository.import(context, ByteArrayInputStream(json.encodeToString(imported).toByteArray()),
                    onCommitted = { committed = true }, forgetCredentials = {})
            } catch (_: AgentMemoryException) { failed = true }

            assertTrue(failed)
            assertFalse(committed)
            assertEquals(previous, PromptTemplateRepository.snapshot(context))
            PromptTemplateRepository.closeForTests(context)
            assertEquals(previous, PromptTemplateRepository.snapshot(context))
            assertEquals("原现实记忆", AgentMemoryRepository.snapshot().content)
            assertEquals("阻塞角色", CharacterRepository.get(blocked.id)?.card?.name)
            assertEquals(originalModes, ConversationModePreference.captureSnapshot(context))
            assertEquals(ConversationModePreference.NORMAL, ConversationModePreference.getMode(context, "conversation"))
        } finally {
            check(blocker.delete())
            ConversationModePreference.restoreSnapshot(context, outsideModes)
        }
    }
}
