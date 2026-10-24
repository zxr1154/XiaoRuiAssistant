package io.github.mangi.eta.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.ui.components.EtaChoiceChip
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Always present, including an empty draft, conversation loading and an active reply. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ConversationModeBar(
    selectedMode: String,
    enabled: Boolean,
    hasCharacter: Boolean,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("conversation-mode-bar")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "对话模式",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ConversationModePreference.NORMAL to "普通",
                ConversationModePreference.FREEDOM to "破甲",
                ConversationModePreference.ROLEPLAY to "角色扮演",
            ).forEach { (mode, title) ->
                EtaChoiceChip(
                    text = title,
                    selected = selectedMode == mode,
                    enabled = enabled,
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.testTag("conversation-mode-$mode"),
                )
            }
        }
        if (!enabled || !hasCharacter) {
            Text(
                text = if (!enabled) "回复或加载完成后可切换模式" else "点击角色扮演，选择或导入角色卡",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
