package io.github.mangi.eta.ui.screens.prompts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.repository.PromptTemplateRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.ui.components.EtaPreference
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "zh-rCN-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PromptTemplatesUiTest {
    @get:Rule val ui = createComposeRule()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val template = PromptTemplate("creative", "故事写作", "采用自然的叙述，保留人物的说话风格。")

    @Before fun setup() {
        clearTemplates()
        ProviderRepository.init(context)
        PromptTemplateRepository.importDocument(context, PromptTemplateDocument(templates = listOf(template)), replace = true)
    }

    @After fun close() {
        clearTemplates()
        EtaDatabase.closeForTests()
    }

    @Test fun editingAndSavingUpdatesTheStoredTemplateAndList() {
        ui.setContent { MiuixTheme { PromptTemplatesScreen(onBack = {}) } }
        ui.waitForIdle()
        screenshot("template-list")

        ui.onNodeWithTag("prompt-edit-creative").performClick()
        ui.onNodeWithTag("prompt-name").performTextReplacement("简洁技术交流")
        ui.onNodeWithTag("prompt-body").performTextReplacement("先给出可执行结论，再说明证据与不确定性。")
        screenshot("template-editor")
        ui.onNodeWithTag("prompt-save").performClick()

        ui.waitUntil(10_000) {
            PromptTemplateRepository.snapshot(context).templates.single().name == "简洁技术交流"
        }
        ui.waitForIdle()
        ui.onNodeWithText("简洁技术交流").assertExists()
        assertEquals("先给出可执行结论，再说明证据与不确定性。", PromptTemplateRepository.snapshot(context).templates.single().prompt)
    }

    @Test fun conversationSelectionIsPersistedWithoutChangingAnotherConversation() {
        ui.setContent {
            MiuixTheme {
                Column(Modifier.fillMaxSize()) {
                    EtaPreference(title = "当前会话", summary = "本会话选择的模板只影响后续请求。")
                    PromptTemplateBar(conversationId = "story-1", enabled = true, onManage = {})
                }
            }
        }
        ui.onNodeWithTag("prompt-template-select").performClick()
        ui.onNodeWithTag("prompt-choice-template:creative").performClick()
        ui.waitUntil(10_000) { PromptTemplateRepository.conversationSelection(context, "story-1").templateId == template.id }
        ui.waitForIdle()
        ui.onNodeWithText("提示模板：故事写作").assertExists()
        screenshot("conversation-selection")
        assertEquals(null, PromptTemplateRepository.conversationSelection(context, "story-2").templateId)

        ui.onNodeWithTag("prompt-template-select").performClick()
        ui.onNodeWithTag("prompt-choice-disabled").performClick()
        ui.waitUntil(10_000) { PromptTemplateRepository.conversationSelection(context, "story-1").disabled }
        assertTrue(PromptTemplateRepository.conversationSelection(context, "story-1").disabled)
    }

    @Test fun invalidSafImportShowsAnErrorWithoutChangingSavedRules() {
        val source = File(context.cacheDir, "invalid-prompt-rules.json").apply { writeText("{\"schemaVersion\":999,\"templates\":[]}") }
        val original = PromptTemplateRepository.snapshot(context)
        val preferences = context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val saved = preferences.getString(PromptTemplateRepository.DOCUMENT_KEY, null)
        val registryOwner = importRegistry(Uri.fromFile(source))
        ui.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                MiuixTheme { PromptTemplatesScreen(onBack = {}) }
            }
        }
        ui.onNode(hasScrollAction()).performScrollToNode(hasTestTag("prompt-import"))
        ui.onNodeWithTag("prompt-import").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("prompt-error").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(original, PromptTemplateRepository.snapshot(context))
        assertEquals(saved, preferences.getString(PromptTemplateRepository.DOCUMENT_KEY, null))
        ui.onNodeWithText("导入预览").assertDoesNotExist()
    }

    @Test fun validSafImportWaitsForPreviewConfirmationBeforeWriting() {
        val imported = PromptTemplateDocument(templates = listOf(PromptTemplate("imported", "新模板", "保留用户的叙事视角。")))
        val source = File(context.cacheDir, "valid-prompt-rules.json").apply { writeText(Json { encodeDefaults = true }.encodeToString(imported)) }
        val original = PromptTemplateRepository.snapshot(context)
        val registryOwner = importRegistry(Uri.fromFile(source))
        ui.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                MiuixTheme { PromptTemplatesScreen(onBack = {}) }
            }
        }
        ui.onNode(hasScrollAction()).performScrollToNode(hasTestTag("prompt-import"))
        ui.onNodeWithTag("prompt-import").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("prompt-import-merge").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(original, PromptTemplateRepository.snapshot(context))
        ui.onNodeWithText("确认合并").performClick()
        ui.waitUntil(10_000) { PromptTemplateRepository.snapshot(context).templates.any { it.id == "imported" } }
        assertTrue(PromptTemplateRepository.snapshot(context).templates.any { it.id == template.id })
    }

    private fun importRegistry(uri: Uri): ActivityResultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
            }
        }
    }

    private fun clearTemplates() {
        context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        PromptTemplateRepository.closeForTests(context)
    }

    private fun screenshot(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val output = File("build/reports/prompt-templates/$name.png")
        output.parentFile.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
