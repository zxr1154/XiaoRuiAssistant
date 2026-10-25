package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.AppModule
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The two workspaces stay one tap apart without mixing conversation modes. */
@Composable
internal fun HomeModuleTabs(
    selected: AppModule,
    onAssistant: () -> Unit,
    onRoleplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .selectableGroup().padding(4.dp).testTag("home-modules"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppModule.entries.forEach { module ->
            val active = module == selected
            val color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (active) MiuixTheme.colorScheme.surface else Color.Transparent)
                    .selectable(active, role = Role.Tab, onClick = if (module == AppModule.Assistant) onAssistant else onRoleplay)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .testTag(if (module == AppModule.Assistant) "module-assistant" else "module-roleplay"),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (module == AppModule.Assistant) Icons.Rounded.AutoAwesome else Icons.Rounded.TheaterComedy,
                    contentDescription = null, tint = color, modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (module == AppModule.Assistant) "智能助手" else "角色扮演",
                    style = MiuixTheme.textStyles.body2, color = color,
                )
            }
        }
    }
}
