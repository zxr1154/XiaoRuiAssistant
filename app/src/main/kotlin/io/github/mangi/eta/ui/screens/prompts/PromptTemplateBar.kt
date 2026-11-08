package io.github.mangi.eta.ui.screens.prompts

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.PromptTemplateSelection
import io.github.mangi.eta.data.repository.PromptTemplateRepository
import io.github.mangi.eta.ui.components.EtaTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A conversation-local override; automatic follows model/provider/default bindings. */
@Composable
internal fun PromptTemplateBar(conversationId: String?, enabled: Boolean, onManage: () -> Unit, roleplay: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(context) { runCatching { PromptTemplateRepository.observe(context) }.getOrNull() }
    var choosing by remember(conversationId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    if (flow == null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            EtaTextButton("提示模板不可用 · 管理", onClick = onManage, modifier = Modifier.testTag("prompt-template-manage"))
        }
        return
    }
    val document by flow.collectAsState()
    val selection = remember(document, conversationId) { PromptTemplateRepository.conversationSelection(context, conversationId) }
    val selected = when {
        selection.disabled -> "disabled"
        selection.templateId != null -> "template:${selection.templateId}"
        else -> null
    }
    val label = when {
        selection.disabled -> "不使用"
        selection.templateId != null -> document.templates.firstOrNull { it.id == selection.templateId }
            ?.let { it.name + if (it.enabled) "" else "（已停用）" } ?: "模板已移除"
        else -> if (roleplay) "不使用（角色默认）" else "自动"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        EtaTextButton("提示模板：$label", onClick = { choosing = true }, enabled = enabled && !saving,
            modifier = Modifier.weight(1f, fill = false).testTag("prompt-template-select"))
        EtaTextButton("管理", onClick = onManage, modifier = Modifier.testTag("prompt-template-manage"))
    }
    if (choosing) PromptChoiceDialog(
        title = "本会话提示模板",
        choices = listOf(
            PromptChoice(null, "自动", if (roleplay) "保持角色原有设定，不添加助手模板" else "按模型、服务商和默认规则选择"),
            PromptChoice("disabled", "不使用", "本会话不添加额外提示模板"),
        ) + document.templates.filter { it.enabled }.map { PromptChoice("template:${it.id}", it.name) },
        selectedId = selected, onDismiss = { choosing = false }, onSelect = { id ->
            choosing = false
            val updated = when (id) {
                null -> PromptTemplateSelection()
                "disabled" -> PromptTemplateSelection(disabled = true)
                else -> PromptTemplateSelection(templateId = id.removePrefix("template:"))
            }
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { PromptTemplateRepository.setConversationSelection(context, conversationId, updated) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Toast.makeText(context, failure.message ?: "无法保存模板选择", Toast.LENGTH_LONG).show()
                } finally {
                    saving = false
                }
            }
        },
    )
}
