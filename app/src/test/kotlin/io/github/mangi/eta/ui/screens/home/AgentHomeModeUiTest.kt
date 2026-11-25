package io.github.mangi.eta.ui.screens.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateBinding
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.repository.PromptTemplateRepository
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "zh-rCN-w390dp-h844dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentHomeModeUiTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private val emptyState = AgentChatUiState(
        messages = emptyList(), input = "", isStreaming = false, thinkingEnabled = false,
    )

    @Before fun clearPreferences() {
        context.getSharedPreferences("eta_conversation_modes", 0).edit().clear().commit()
        context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, 0).edit().clear().commit()
        PromptTemplateRepository.closeForTests(context)
    }

    @After fun closeTemplatePreferences() {
        context.getSharedPreferences(PromptTemplateRepository.PREFERENCES_NAME, 0).edit().clear().commit()
        PromptTemplateRepository.closeForTests(context)
    }

    @Test fun freshHomeShowsSeparateModulesInLightTheme() {
        showHome()
        assertAllModesVisible()
        ui.onNodeWithTag("conversation-mode-normal").assertIsSelected()
        screenshot("fresh-home-light")
    }

    @Test fun freshHomeShowsSeparateModulesInDarkTheme() {
        showHome(dark = true)
        assertAllModesVisible()
        screenshot("fresh-home-dark")
    }

    @Test fun firstMessageKeepsTheModeChosenBeforeAConversationExists() {
        var conversationId by mutableStateOf<String?>(null)
        ui.setContent {
            MiuixTheme {
                AgentHomeScreen(
                    state = emptyState,
                    modelPickerState = AgentModelPickerUiState(),
                    conversationKey = conversationId,
                    onAction = {},
                )
            }
        }
        ui.onNodeWithTag("conversation-mode-freedom").performClick().assertIsSelected()
        ui.runOnIdle {
            assertEquals(ConversationModePreference.FREEDOM, ConversationModePreference.getMode(context, null))
            ConversationModePreference.initializeConversation(context, "first-message")
            conversationId = "first-message"
        }
        ui.onNodeWithTag("conversation-mode-freedom").assertIsSelected()
        ui.onNodeWithTag("conversation-mode-normal").performClick().assertIsSelected()
        ui.runOnIdle {
            assertEquals(ConversationModePreference.NORMAL, ConversationModePreference.getMode(context, "first-message"))
        }
    }

    @Test fun roleplayWithoutACardOpensCharacterLibrary() {
        val actions = mutableListOf<AgentHomeAction>()
        showHome(onAction = actions::add)
        ui.onNodeWithTag("module-roleplay").performClick()
        ui.runOnIdle { assertEquals(listOf(AgentHomeAction.OpenCharacters), actions) }
        ui.onNodeWithTag("conversation-mode-normal").assertIsSelected()
    }

    @Test fun selectingFreedomShowsTheActualModelBoundTemplateAndNormalHidesIt() {
        val template = PromptTemplate("technical", "技术交流", "先给出可验证的技术结论。")
        PromptTemplateRepository.importDocument(context, PromptTemplateDocument(
            templates = listOf(template),
            bindings = listOf(PromptTemplateBinding("provider-main", "ds-v4pro", template.id)),
        ), replace = true)
        val picker = AgentModelPickerUiState(selectedModel = AgentModelOptionUi(
            id = "local-database-row", providerId = "provider-main", providerName = "测试服务商",
            providerSourceType = "custom", modelId = "ds-v4pro", displayName = "DS V4 Pro", contextWindow = null,
        ))
        showHome(modelPickerState = picker)
        ui.onNodeWithTag("prompt-template-select").assertDoesNotExist()
        ui.onNodeWithTag("conversation-mode-freedom").performClick().assertIsSelected().assertTextEquals("✓ 破甲")
        ui.onNodeWithTag("conversation-mode-status").assertTextContains("当前选择：破甲", substring = true)
        ui.onNodeWithTag("prompt-template-select").assertIsDisplayed().assertTextContains("技术交流", substring = true)
        ui.onNodeWithTag("prompt-template-status").assertTextContains("模型绑定 · ds-v4pro", substring = true)
        screenshot("freedom-template-state")
        ui.onNodeWithTag("conversation-mode-normal").performClick().assertIsSelected()
        ui.onNodeWithTag("prompt-template-select").assertDoesNotExist()
    }

    @Test fun roleplayModuleEntryStaysAvailableWhileAssistantIsReplying() {
        val actions = mutableListOf<AgentHomeAction>()
        showHome(state = emptyState.copy(isStreaming = true), onAction = actions::add)
        ui.onNodeWithTag("module-roleplay").performClick()
        ui.runOnIdle { assertEquals(listOf(AgentHomeAction.OpenCharacters), actions) }
    }

    @Test fun replyKeepsModeControlsVisibleAndDisabled() {
        showHome(state = emptyState.copy(isStreaming = true), conversationId = "reply")
        assertAllModesVisible()
        listOf("normal", "freedom").forEach {
            ui.onNodeWithTag("conversation-mode-$it").assertIsNotEnabled()
        }
        ui.onNodeWithTag("conversation-mode-status").assertTextContains("正在生成回复", substring = true)
    }

    @Test fun loadingKeepsModeControlsVisible() {
        showHome(state = emptyState.copy(isConversationLoaded = false), conversationId = "loading")
        assertAllModesVisible()
        ui.onNodeWithTag("conversation-mode-normal").assertIsNotEnabled()
        ui.onNodeWithTag("conversation-mode-status").assertTextContains("正在加载会话", substring = true)
    }

    private fun showHome(
        dark: Boolean = false,
        state: AgentChatUiState = emptyState,
        conversationId: String? = null,
        onAction: (AgentHomeAction) -> Unit = {},
        modelPickerState: AgentModelPickerUiState = AgentModelPickerUiState(),
    ) {
        ui.setContent {
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    AgentHomeScreen(
                        state = state,
                        modelPickerState = modelPickerState,
                        conversationKey = conversationId,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    private fun assertAllModesVisible() {
        listOf("normal", "freedom").forEach {
            ui.onNodeWithTag("conversation-mode-$it").assertIsDisplayed()
        }
        ui.onNodeWithTag("module-assistant").assertIsDisplayed().assertIsSelected()
        ui.onNodeWithTag("module-roleplay").assertIsDisplayed()
        ui.onNodeWithTag("conversation-mode-roleplay").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/reports/mode-ui/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
