package io.github.mangi.eta.ui.screens.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FileUpload
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.HomeModuleTabs
import io.github.mangi.eta.ui.model.AppModule
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoleplayQuickAction("创建角色", Icons.Rounded.Add, enabled, onCreate,
                    Modifier.weight(1f).testTag("roleplay-create"))
                RoleplayQuickAction("导入角色卡", Icons.Rounded.FileUpload, enabled, onImport,
                    Modifier.weight(1f).testTag("roleplay-import"))
                RoleplayQuickAction("我的人设", Icons.Rounded.Face, enabled, onPersona,
                    Modifier.weight(1f).testTag("roleplay-persona"))
            }
        }
    }
}

@Composable
private fun RoleplayQuickAction(text: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.disabledOnSurface
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface, maxLines = 1)
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
