package io.github.mangi.eta.ui.screens.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.HomeModuleTabs
import io.github.mangi.eta.ui.model.AppModule
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RoleplayModuleHeader(
    characterCount: Int,
    storyCount: Int,
    enabled: Boolean,
    onAssistant: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onPersona: () -> Unit,
) {
    Column(Modifier.testTag("roleplay-module-header")) {
        HomeModuleTabs(AppModule.Roleplay, onAssistant, onRoleplay = {})
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("让故事继续", style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface)
            Text(
                "选择一个角色，开启属于你的对话。角色设定、人设和剧情记忆都在这里。",
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                "$characterCount 个角色 · $storyCount 段剧情",
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("创建角色", onClick = onCreate, enabled = enabled, modifier = Modifier.testTag("roleplay-create"))
                TextButton("导入角色卡", onClick = onImport, enabled = enabled, modifier = Modifier.testTag("roleplay-import"))
                TextButton("我的人设", onClick = onPersona, enabled = enabled, modifier = Modifier.testTag("roleplay-persona"))
            }
        }
    }
}

@Composable
internal fun RoleplayStoryRow(story: ConversationSummaryUi, onOpen: (String) -> Unit) {
    EtaPreferenceGroup(modifier = Modifier.padding(bottom = 8.dp)) {
        EtaArrowPreference(
            title = story.title,
            summary = "${story.characterName.orEmpty()} · ${story.timeLabel}",
            onClick = { onOpen(story.id) },
            modifier = Modifier.testTag("roleplay-story-${story.id}"),
        )
    }
}
