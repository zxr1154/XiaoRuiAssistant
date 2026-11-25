package io.github.mangi.eta.ui.screens.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.mangi.eta.agent.prompt.PromptTemplateResolver
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.PromptTemplateSelection
import io.github.mangi.eta.data.repository.PromptTemplateRepository
import io.github.mangi.eta.ui.components.EtaTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A conversation-local override; automatic follows model/provider/default bindings. */
@Composable
internal fun PromptTemplateBar(
    conversationId: String?, enabled: Boolean, onManage: () -> Unit, roleplay: Boolean = false,
    providerId: String? = null, modelId: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(context) { runCatching { PromptTemplateRepository.observe(context) }.getOrNull() }
    var choosing by remember(conversationId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember(conversationId) { mutableStateOf<String?>(null) }
    if (flow == null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            EtaTextButton("提示模板不可用 · 管理", onClick = onManage, modifier = Modifier.testTag("prompt-template-manage"))
        }
        return
    }
    val document by flow.collectAsState()
    val conversationKey = conversationId ?: PromptTemplateRepository.DRAFT_CONVERSATION_ID
    val selection = document.conversationSelections[conversationKey] ?: PromptTemplateSelection()
    val preview = remember(document, conversationKey, providerId, modelId, roleplay) {
        templatePreview(document, conversationKey, selection, providerId, modelId, roleplay)
    }
    val selected = when {
        selection.disabled -> "disabled"
        selection.templateId != null -> "template:${selection.templateId}"
        else -> null
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            EtaTextButton("提示模板：${preview.label}", onClick = { choosing = true }, enabled = enabled && !saving,
                modifier = Modifier.weight(1f, fill = false).testTag("prompt-template-select"))
            EtaTextButton("管理", onClick = onManage, enabled = !saving, modifier = Modifier.testTag("prompt-template-manage"))
        }
        Text(preview.detail, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp).testTag("prompt-template-status"))
    }
    if (choosing) PromptChoiceDialog(
        title = "本会话提示模板",
        enabled = !saving,
        summary = if (saving) "正在保存，完成后即可继续对话…" else saveError,
        choices = listOf(
            PromptChoice(null, "自动", if (roleplay) "保持角色原有设定，不添加助手模板" else "按模型、服务商和默认规则选择"),
            PromptChoice("disabled", "不使用", "本会话不添加额外提示模板"),
        ) + document.templates.filter { it.enabled }.map { PromptChoice("template:${it.id}", it.name) },
        selectedId = selected, onDismiss = { if (!saving) choosing = false }, onSelect = { id ->
            if (saving) return@PromptChoiceDialog
            val updated = when (id) {
                null -> PromptTemplateSelection()
                "disabled" -> PromptTemplateSelection(disabled = true)
                else -> PromptTemplateSelection(templateId = id.removePrefix("template:"))
            }
            saving = true
            saveError = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { PromptTemplateRepository.setConversationSelection(context, conversationId, updated) }
                    // Keep the modal over the composer until the committed selection is visible to Runtime.
                    choosing = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    saveError = "未保存，仍使用原选择。${failure.message ?: "请重试或取消。"}"
                } finally {
                    saving = false
                }
            }
        },
    )
}

private data class TemplatePreview(val label: String, val detail: String)

private fun templatePreview(
    document: PromptTemplateDocument,
    conversationKey: String,
    selection: PromptTemplateSelection,
    providerId: String?,
    modelId: String?,
    roleplay: Boolean,
): TemplatePreview {
    val explicit = document.templates.firstOrNull { it.id == selection.templateId }
    val selectedLabel = explicit?.let { it.name + if (it.enabled) "" else "（已停用）" }
    // No model binding is previewed until both identifiers match an actual selectable request model.
    if (providerId.isNullOrBlank() || modelId.isNullOrBlank()) {
        return when {
            selection.disabled -> TemplatePreview("不使用", "先选择模型；本会话已关闭额外模板。")
            selection.templateId != null -> TemplatePreview(selectedLabel ?: "模板已移除", when {
                explicit == null -> "先选择模型；当前选择的模板已移除，后续请求不添加模板。"
                !explicit.enabled -> "先选择模型；当前选择的模板已停用，后续请求不添加模板。"
                else -> "先选择模型；当前选择“${explicit.name}”，仅用于后续请求。"
            })
            roleplay -> TemplatePreview("不使用（角色默认）", "先选择模型；角色默认沿用角色卡，不添加助手模板。")
            else -> {
                val candidate = document.templates.firstOrNull { it.id == document.defaultTemplateId }
                TemplatePreview("自动 · 待选模型", "先选择模型；" +
                    (candidate?.let { "默认候选为“${it.name}”${if (it.enabled) "" else "（已停用）"}，" } ?: "尚未设置默认候选，") +
                    "选定后再解析模型绑定。")
            }
        }
    }
    val resolution = PromptTemplateResolver.resolve(
        document = document,
        mode = if (roleplay) ConversationModePreference.ROLEPLAY else ConversationModePreference.FREEDOM,
        providerId = providerId,
        modelId = modelId,
        conversationId = conversationKey,
    )
    val source = when (resolution.source) {
        PromptTemplateResolver.Source.CONVERSATION -> "会话选择"
        PromptTemplateResolver.Source.MODEL -> "模型绑定"
        PromptTemplateResolver.Source.PROVIDER -> "服务商绑定"
        PromptTemplateResolver.Source.DEFAULT -> "默认模板"
        PromptTemplateResolver.Source.NONE -> "当前设置"
    }
    val chosenId = when (resolution.source) {
        PromptTemplateResolver.Source.CONVERSATION -> selection.templateId
        PromptTemplateResolver.Source.MODEL -> document.bindings.firstOrNull { it.providerId == providerId && it.modelId == modelId }?.templateId
        PromptTemplateResolver.Source.PROVIDER -> document.bindings.firstOrNull { it.providerId == providerId && it.modelId == null }?.templateId
        PromptTemplateResolver.Source.DEFAULT -> document.defaultTemplateId
        PromptTemplateResolver.Source.NONE -> null
    }
    val chosenTemplate = document.templates.firstOrNull { it.id == chosenId }
    val appliedName = chosenTemplate?.name
    val label = when {
        selection.disabled -> "不使用"
        selection.templateId != null -> selectedLabel ?: "模板已移除"
        appliedName != null -> "自动 · $appliedName" + if (resolution.reason == PromptTemplateResolver.Reason.DISABLED) "（已停用）" else ""
        resolution.reason == PromptTemplateResolver.Reason.ROLEPLAY_INHERITS_NONE -> "不使用（角色默认）"
        else -> "自动 · 未使用"
    }
    val detail = when (resolution.reason) {
        PromptTemplateResolver.Reason.APPLIED -> "下次请求将使用“$appliedName” · $source · $modelId"
        PromptTemplateResolver.Reason.CONVERSATION_DISABLED -> "下次请求不添加模板：本会话已选择“不使用”。"
        PromptTemplateResolver.Reason.ROLEPLAY_INHERITS_NONE -> "下次请求沿用角色卡；角色默认不添加助手模板。"
        PromptTemplateResolver.Reason.NO_SELECTION -> "下次请求不添加模板：没有匹配的绑定或默认模板。"
        PromptTemplateResolver.Reason.DISABLED -> "下次请求不添加模板：${source}所选模板已停用。"
        PromptTemplateResolver.Reason.MISSING -> "下次请求不添加模板：${source}引用的模板已移除。"
        PromptTemplateResolver.Reason.EMPTY -> "下次请求不添加模板：${source}所选模板正文为空。"
        PromptTemplateResolver.Reason.MODE_DISABLED -> "当前为普通模式，后续请求不添加额外模板。"
    }
    return TemplatePreview(label, detail)
}
