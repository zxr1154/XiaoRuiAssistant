package io.github.mangi.eta.ui.screens.characters

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.AgentHomeAction
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.ConversationModeUi
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
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
class RoleplayModuleUiTest {
    @get:Rule val ui = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val story = ConversationSummaryUi("story", "星海的第一封信", "你好", "刚刚", mode = ConversationModeUi.Chat, characterName = "星澜")

    @After fun closeScope() { scope.cancel() }

    @Test fun roleplayLibraryHasItsOwnLandingPageAndQuickActions() {
        showLibrary()
        ui.onNodeWithTag("module-roleplay").assertIsDisplayed().assertIsSelected()
        ui.onNodeWithTag("roleplay-create").assertIsDisplayed()
        ui.onNodeWithTag("roleplay-import").assertIsDisplayed()
        ui.onNodeWithTag("roleplay-persona").assertIsDisplayed()
        screenshot("roleplay-library-light")
    }

    @Test fun roleplayLandingSupportsDarkTheme() {
        showLibrary(dark = true)
        ui.onNodeWithTag("roleplay-module-header").assertIsDisplayed()
        screenshot("roleplay-library-dark")
    }

    @Test fun storiesTabResumesTheSelectedStory() {
        val opened = mutableListOf<String>()
        showLibrary(onStory = opened::add)
        ui.onNodeWithTag("roleplay-stories-tab").performClick().assertIsSelected()
        ui.onNodeWithTag("roleplay-story-story").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(listOf("story"), opened) }
        screenshot("roleplay-stories")
    }

    @Test fun assistantTabReturnsToTheAssistantWorkspace() {
        var returns = 0
        showLibrary(onAssistant = { returns++ })
        ui.onNodeWithTag("module-assistant").performClick()
        ui.runOnIdle { assertEquals(1, returns) }
    }

    @Test fun roleplayChatUsesCharacterIdentityWithoutAssistantModeControls() {
        val actions = mutableListOf<AgentHomeAction>()
        ui.setContent {
            MiuixTheme {
                RoleplayChatScreen(
                    state = AgentChatUiState(emptyList(), input = "", isStreaming = false, thinkingEnabled = false,
                        roleplay = RoleplayBinding("card", "{}", "星澜")),
                    modelPickerState = AgentModelPickerUiState(), conversationKey = "story", onAction = actions::add,
                )
            }
        }
        ui.onNodeWithTag("roleplay-chat-header").assertIsDisplayed()
        ui.onNodeWithTag("module-roleplay").assertIsSelected()
        ui.onNodeWithTag("conversation-mode-normal").assertDoesNotExist()
        ui.onNodeWithTag("conversation-mode-freedom").assertDoesNotExist()
        ui.onNodeWithText("角色库", useUnmergedTree = false).performClick()
        ui.runOnIdle { assertEquals(listOf(AgentHomeAction.OpenCharacters), actions) }
        screenshot("roleplay-chat")
    }

    private fun showLibrary(dark: Boolean = false, onStory: (String) -> Unit = {}, onAssistant: () -> Unit = {}) {
        val store = CharacterLibraryStore(RuntimeEnvironment.getApplication(), scope)
        ui.setContent {
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    CharacterLibraryScreen(store, {}, onAssistant, listOf(story), onStory)
                }
            }
        }
        ui.runOnIdle { store.loadLibrary() }
        ui.waitUntil(15_000) { !store.busy && store.characters.isNotEmpty() }
    }

    private fun screenshot(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/reports/mode-ui/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
