package io.github.mangi.eta.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

@Composable
internal fun ConversationModeBar(
    selectedMode: String,
    enabled: Boolean,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabledReason: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).testTag("conversation-mode-bar")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "对话风格", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            listOf(ConversationModePreference.NORMAL to "普通", ConversationModePreference.FREEDOM to "破甲").forEach { (mode, title) ->
                EtaChoiceChip(if (selectedMode == mode) "✓ $title" else title, selectedMode == mode, { onModeSelected(mode) },
                    modifier = Modifier.testTag("conversation-mode-$mode"), enabled = enabled)
            }
        }
        if (!enabled || selectedMode == ConversationModePreference.FREEDOM) {
            Text(
                text = if (!enabled) disabledReason ?: "当前暂不可切换，完成当前操作后重试。"
                    else "当前选择：破甲；模板只用于后续请求。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp).testTag("conversation-mode-status"),
            )
        }
    }
}
