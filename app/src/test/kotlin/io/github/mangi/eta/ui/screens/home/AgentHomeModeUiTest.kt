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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import java.io.File
import org.junit.Assert.assertEquals
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
    private val context get() = RuntimeEnvironment.getApplication<Application>()
    private val emptyState = AgentChatUiState(
        messages = emptyList(), input = "", isStreaming = false, thinkingEnabled = false,
    )

    @Before fun clearPreferences() {
        context.getSharedPreferences("eta_conversation_modes", 0).edit().clear().commit()
    }

    @Test fun freshHomeShowsAllThreeModesInLightTheme() {
        showHome()
        assertAllModesVisible()
        ui.onNodeWithTag("conversation-mode-normal").assertIsSelected()
        screenshot("fresh-home-light")
    }

    @Test fun freshHomeShowsAllThreeModesInDarkTheme() {
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
        ui.onNodeWithTag("conversation-mode-roleplay").performClick()
        ui.runOnIdle { assertEquals(listOf(AgentHomeAction.OpenCharacters), actions) }
        ui.onNodeWithTag("conversation-mode-normal").assertIsSelected()
    }

    @Test fun characterConversationCanSwitchAllThreeModes() {
        showHome(
            state = emptyState.copy(roleplay = RoleplayBinding("card", "{}", "测试角色")),
            conversationId = "character-chat",
        )
        ui.onNodeWithTag("conversation-mode-roleplay").assertIsSelected()
        ui.onNodeWithTag("conversation-mode-normal").performClick().assertIsSelected()
        ui.onNodeWithTag("conversation-mode-freedom").performClick().assertIsSelected()
        ui.onNodeWithTag("conversation-mode-roleplay").performClick().assertIsSelected()
        ui.runOnIdle {
            assertEquals(ConversationModePreference.ROLEPLAY, ConversationModePreference.getMode(context, "character-chat"))
        }
    }

    @Test fun replyKeepsModeControlsVisibleAndDisabled() {
        showHome(state = emptyState.copy(isStreaming = true), conversationId = "reply")
        assertAllModesVisible()
        listOf("normal", "freedom", "roleplay").forEach {
            ui.onNodeWithTag("conversation-mode-$it").assertIsNotEnabled()
        }
    }

    @Test fun loadingKeepsModeControlsVisible() {
        showHome(state = emptyState.copy(isConversationLoaded = false), conversationId = "loading")
        assertAllModesVisible()
        ui.onNodeWithTag("conversation-mode-normal").assertIsNotEnabled()
    }

    private fun showHome(
        dark: Boolean = false,
        state: AgentChatUiState = emptyState,
        conversationId: String? = null,
        onAction: (AgentHomeAction) -> Unit = {},
    ) {
        ui.setContent {
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    AgentHomeScreen(
                        state = state,
                        modelPickerState = AgentModelPickerUiState(),
                        conversationKey = conversationId,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    private fun assertAllModesVisible() {
        listOf("normal", "freedom", "roleplay").forEach {
            ui.onNodeWithTag("conversation-mode-$it").assertIsDisplayed()
        }
    }

    private fun screenshot(name: String) {
        val bitmap = ui.onNodeWithTag("conversation-mode-bar").captureToImage().asAndroidBitmap()
        val file = File("build/reports/mode-ui/$name.png")
        file.parentFile.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
