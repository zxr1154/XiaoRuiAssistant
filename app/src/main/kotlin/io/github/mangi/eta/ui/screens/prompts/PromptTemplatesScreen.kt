package io.github.mangi.eta.ui.screens.prompts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateBinding
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.repository.PromptTemplateRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaRadioButtonPreference
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaTextButton
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PromptTemplatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    val loaded = remember(context, retry) { runCatching { PromptTemplateRepository.observe(context) } }
    val flow = loaded.getOrNull()
    if (flow == null) {
        MiuixScaffoldPage(title = "提示模板", onBack = onBack) {
            item {
                EtaPreference(title = "无法读取提示模板", summary = loaded.exceptionOrNull()?.message ?: "请重试")
                EtaTextButton("重试", onClick = { retry++ }, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        return
    }
    val document by flow.collectAsState()
    var providers by remember { mutableStateOf<List<ProviderSetting>>(emptyList()) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<PromptTemplate?>(null) }
    var bindingEditor by remember { mutableStateOf<PromptTemplateBinding?>(null) }
    var showBindingEditor by remember { mutableStateOf(false) }
    var deletion by remember { mutableStateOf<PromptTemplate?>(null) }
    var showDefault by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PromptTemplateDocument?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(error) {
        if (error != null) listState.scrollToItem(0)
    }

    LaunchedEffect(retry) {
        try {
            providers = withContext(Dispatchers.IO) { ProviderRepository.allProviders() }
            providerError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            providerError = "服务商列表读取失败，请返回重试"
        }
    }

    fun mutate(action: () -> Unit, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "操作失败，请重试"
            } finally {
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) mutate({
            val text = PromptTemplateRepository.exportDocument(context)
            val output = checkNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开导出文件" }
            output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        }) { Toast.makeText(context, "提示模板已导出", Toast.LENGTH_SHORT).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var validated: PromptTemplateDocument? = null
            mutate({
                // Parsing and validation happen before any repository write or import confirmation.
                validated = PromptTemplateRepository.parseImport(readTemplateImport(context, uri))
            }) { pendingImport = validated }
        }
    }

    editor?.let { draft ->
        BackHandler(enabled = !busy) { editor = null }
        key(draft.id) {
            PromptTemplateEditor(
                template = draft, busy = busy, error = error,
                onBack = { if (!busy) editor = null },
                onSave = { updated -> mutate({ PromptTemplateRepository.saveTemplate(context, updated) }) { editor = null } },
            )
        }
        return
    }
    if (showBindingEditor) {
        BackHandler(enabled = !busy) { showBindingEditor = false }
        PromptBindingEditor(
            binding = bindingEditor, providers = providers, templates = document.templates,
            busy = busy, error = error, onBack = { if (!busy) showBindingEditor = false },
            onSave = { providerId, modelId, templateId ->
                mutate({ PromptTemplateRepository.setBinding(context, providerId, modelId, templateId) }) {
                    showBindingEditor = false
                }
            },
        )
        return
    }

    MiuixScaffoldPage(title = "提示模板", onBack = onBack, listState = listState) {
        item(key = "description") {
            EtaPreference(
                title = "自定义对话风格与规则",
                summary = "助手自动选择顺序：会话选择 → 模型绑定 → 服务商绑定 → 默认模板。角色会话默认保留原有设定，显式选择模板才加入。仅影响下一次请求，不改变工具权限。",
            )
        }
        error?.let { message -> item(key = "error") { PromptError(message) } }
        item(key = "default") {
            EtaPreferenceGroup(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                EtaArrowPreference(
                    title = "默认模板", summary = templateTitle(document, document.defaultTemplateId, "不使用额外模板"),
                    enabled = !busy, onClick = { showDefault = true }, modifier = Modifier.testTag("prompt-default"),
                )
            }
        }
        item(key = "templates-title") { EtaPreferenceGroupTitle("模板（${document.templates.size}）") }
        item(key = "new") {
            EtaTextButton("新建模板", onClick = { error = null; editor = PromptTemplate(UUID.randomUUID().toString(), "", "") },
                enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp).testTag("prompt-new"))
        }
        items(document.templates, key = { "template-${it.id}" }) { template ->
            EtaPreferenceGroup(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                EtaArrowPreference(title = template.name, summary = template.prompt.take(110), enabled = !busy,
                    onClick = { error = null; editor = template }, modifier = Modifier.testTag("prompt-edit-${template.id}"))
                EtaPreferenceDivider()
                EtaSwitchPreference(title = "启用模板", checked = template.enabled, enabled = !busy,
                    onCheckedChange = { value -> mutate({ PromptTemplateRepository.saveTemplate(context, template.copy(enabled = value)) }) },
                    summary = if (template.enabled) null else "已停用，绑定此模板时会跳过它",
                    modifier = Modifier.testTag("prompt-enabled-${template.id}"))
                EtaTextButton("删除模板", onClick = { deletion = template }, enabled = !busy,
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("prompt-delete-${template.id}"))
            }
        }
        item(key = "bindings-title") { EtaPreferenceGroupTitle("服务商与模型绑定") }
        item(key = "binding-new") {
            EtaTextButton("添加绑定", onClick = { bindingEditor = null; showBindingEditor = true; error = null },
                enabled = !busy && providers.isNotEmpty() && document.templates.any { it.enabled },
                modifier = Modifier.padding(horizontal = 16.dp).testTag("prompt-add-binding"))
            if (providerError != null) PromptError(providerError!!)
            else if (providers.isEmpty()) EtaPreference(summary = "请先添加服务商和模型")
            else if (document.templates.none { it.enabled }) EtaPreference(summary = "请先创建并启用一个模板")
        }
        items(document.bindings, key = { "binding-${it.providerId}-${it.modelId.orEmpty()}" }) { binding ->
            val provider = providers.firstOrNull { it.id == binding.providerId }
            EtaPreferenceGroup(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                EtaArrowPreference(title = provider?.name ?: "已移除的服务商",
                    summary = "${binding.modelId ?: "全部模型"} · ${templateTitle(document, binding.templateId)}",
                    enabled = !busy && provider != null, onClick = { bindingEditor = binding; showBindingEditor = true; error = null })
                EtaTextButton("移除绑定", onClick = {
                    mutate({ PromptTemplateRepository.setBinding(context, binding.providerId, binding.modelId, null) })
                }, enabled = !busy, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        item(key = "transfer-title") { EtaPreferenceGroupTitle("导入与导出") }
        item(key = "transfer") {
            EtaPreferenceGroup(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                EtaArrowPreference(title = "导入规则 JSON", summary = "先验证和预览，再选择合并或替换", enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.testTag("prompt-import"))
                EtaPreferenceDivider()
                EtaArrowPreference(title = "导出规则 JSON", summary = "导出模板、默认选择和绑定；不含会话记录与密钥", enabled = !busy,
                    onClick = { exportLauncher.launch("XiaoRui-prompt-templates.json") }, modifier = Modifier.testTag("prompt-export"))
            }
        }
        item(key = "source") {
            EtaArrowPreference(
                title = "设计参考 dsh-purge 1.1.71（YuJunZhiXue）",
                summary = "提示模板与规则管理为小瑞原生实现。查看来源仓库。",
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DSH_SOURCE_URL))) }
                        .onFailure { Toast.makeText(context, "无法打开来源链接", Toast.LENGTH_SHORT).show() }
                },
            )
        }
    }

    if (showDefault) PromptTemplateChoiceDialog(
        title = "默认模板", templates = document.templates, selectedId = document.defaultTemplateId,
        noneTitle = "不使用额外模板", onDismiss = { showDefault = false }, onSelect = { id ->
            showDefault = false
            mutate({ PromptTemplateRepository.setDefault(context, id) })
        },
    )
    deletion?.let { template ->
        EtaWindowDialog(show = true, title = "删除“${template.name}”？", summary = "关联的默认选择与绑定会被清除；已选择此模板的会话改为“不使用”，避免切换到其他模板。",
            onDismissRequest = { if (!busy) deletion = null }) {
            MiuixDialogActions(confirmText = "删除", destructive = true, confirmEnabled = !busy, cancelEnabled = !busy,
                onCancel = { deletion = null }, onConfirm = {
                    mutate({ PromptTemplateRepository.deleteTemplate(context, template.id) }) { deletion = null }
                })
        }
    }
    pendingImport?.let { imported ->
        PromptTemplateImportDialog(imported, busy, error, onDismiss = { pendingImport = null }, onImport = { replace ->
            mutate({ PromptTemplateRepository.importDocument(context, imported, replace) }) { pendingImport = null }
        }, currentDocument = document)
    }
}

@Composable
internal fun PromptTemplateEditor(
    template: PromptTemplate, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (PromptTemplate) -> Unit,
) {
    var name by rememberSaveable(template.id) { mutableStateOf(template.name) }
    var prompt by rememberSaveable(template.id) { mutableStateOf(template.prompt) }
    var enabled by rememberSaveable(template.id) { mutableStateOf(template.enabled) }
    MiuixScaffoldPage(title = if (template.name.isBlank()) "新建模板" else "编辑模板", onBack = onBack,
        modifier = Modifier.imePadding(), actions = {
            EtaTextButton(if (busy) "保存中…" else "保存", onClick = { onSave(template.copy(name = name.trim(), prompt = prompt, enabled = enabled)) },
                enabled = !busy && name.isNotBlank() && prompt.isNotBlank(), modifier = Modifier.testTag("prompt-save"))
        }) {
        item(key = "name") {
            TextField(value = name, onValueChange = { name = it }, label = "模板名称", singleLine = true, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("prompt-name"))
        }
        item(key = "enabled") { EtaSwitchPreference("启用模板", enabled, { enabled = it }, enabled = !busy) }
        item(key = "prompt") {
            TextField(value = prompt, onValueChange = { prompt = it }, label = "提示词正文", minLines = 10, maxLines = 24, enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("prompt-body"))
        }
        item(key = "hint") { EtaPreference(summary = "正文作为额外提示加入下一次请求。角色卡、历史消息和工具执行规则仍由原有模块管理。") }
        error?.let { message -> item(key = "error") { PromptError(message) } }
    }
}

@Composable
private fun PromptBindingEditor(
    binding: PromptTemplateBinding?, providers: List<ProviderSetting>, templates: List<PromptTemplate>,
    busy: Boolean, error: String?, onBack: () -> Unit, onSave: (String, String?, String) -> Unit,
) {
    var providerId by remember(binding) { mutableStateOf(binding?.providerId ?: providers.firstOrNull()?.id) }
    var modelId by remember(binding) { mutableStateOf(binding?.modelId) }
    var templateId by remember(binding) { mutableStateOf(binding?.templateId ?: templates.firstOrNull { it.enabled }?.id) }
    var picker by remember { mutableStateOf<String?>(null) }
    val provider = providers.firstOrNull { it.id == providerId }
    MiuixScaffoldPage(title = if (binding == null) "添加绑定" else "编辑绑定", onBack = onBack) {
        item {
            EtaPreferenceGroup(Modifier.padding(16.dp)) {
                EtaArrowPreference(title = "服务商", summary = provider?.name ?: "请选择", enabled = !busy && binding == null, onClick = { picker = "provider" })
                EtaPreferenceDivider()
                EtaArrowPreference(title = "模型", summary = modelId ?: "此服务商的全部模型", enabled = !busy && provider != null && binding == null,
                    onClick = { picker = "model" })
                EtaPreferenceDivider()
                EtaArrowPreference(title = "模板", summary = templates.firstOrNull { it.id == templateId }?.name ?: "请选择", enabled = !busy,
                    onClick = { picker = "template" })
            }
        }
        error?.let { message -> item { PromptError(message) } }
        item {
            EtaTextButton("保存绑定", onClick = { onSave(providerId!!, modelId, templateId!!) },
                enabled = !busy && provider != null && templateId != null && templates.any { it.id == templateId && it.enabled },
                modifier = Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
    when (picker) {
        "provider" -> PromptChoiceDialog("选择服务商", providers.map { PromptChoice(it.id, it.name) }, providerId, { picker = null }) {
            providerId = it; modelId = null; picker = null
        }
        "model" -> PromptChoiceDialog("选择模型", listOf(PromptChoice(null, "全部模型")) + provider?.models.orEmpty().map {
            PromptChoice(it.modelId, it.displayName, it.modelId)
        }, modelId, { picker = null }) { modelId = it; picker = null }
        "template" -> PromptTemplateChoiceDialog("选择模板", templates, templateId, onDismiss = { picker = null },
            onSelect = { templateId = it; picker = null })
    }
}

@Composable
internal fun PromptTemplateImportDialog(
    document: PromptTemplateDocument, busy: Boolean, error: String?, onDismiss: () -> Unit, onImport: (Boolean) -> Unit,
    currentDocument: PromptTemplateDocument? = null,
) {
    var replace by remember(document) { mutableStateOf(false) }
    EtaWindowDialog(show = true, title = "导入预览", onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("验证通过：${document.templates.size} 个模板，${document.bindings.size} 条绑定。")
            Text(document.templates.take(8).joinToString("、") { it.name }, modifier = Modifier.padding(vertical = 8.dp))
            currentDocument?.let { current ->
                val overlaps = document.templates.count { imported -> current.templates.any { it.id == imported.id } }
                if (overlaps > 0) Text("其中 $overlaps 个模板与现有 ID 相同，将覆盖原内容。")
            }
            EtaRadioButtonPreference("合并到现有规则", !replace, { replace = false }, enabled = !busy,
                summary = "同 ID 模板及同服务商/模型绑定由导入值覆盖，其余规则保留；文件指定的默认模板会被采用。",
                modifier = Modifier.testTag("prompt-import-merge"))
            EtaRadioButtonPreference("替换全部模板规则", replace, { replace = true }, enabled = !busy,
                summary = "替换模板、绑定和默认项；会话停用选择保留，引用被移除模板的会话改为“不使用”。此操作不可撤销。",
                modifier = Modifier.testTag("prompt-import-replace"))
            error?.let { PromptError(it) }
        }
        MiuixDialogActions(confirmText = if (busy) "导入中…" else if (replace) "确认替换" else "确认合并",
            onCancel = onDismiss, onConfirm = { onImport(replace) }, confirmEnabled = !busy, cancelEnabled = !busy,
            destructive = replace, modifier = Modifier.padding(top = 12.dp).testTag("prompt-import-confirm"))
    }
}

internal data class PromptChoice(val id: String?, val title: String, val summary: String? = null)

@Composable
internal fun PromptChoiceDialog(
    title: String, choices: List<PromptChoice>, selectedId: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit,
) {
    EtaWindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { choice ->
                EtaRadioButtonPreference(choice.title, choice.id == selectedId, { onSelect(choice.id) }, summary = choice.summary,
                    modifier = Modifier.testTag("prompt-choice-${choice.id ?: "automatic"}"))
            }
        }
        EtaTextButton("取消", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
internal fun PromptTemplateChoiceDialog(
    title: String, templates: List<PromptTemplate>, selectedId: String?, noneTitle: String? = null,
    onDismiss: () -> Unit, onSelect: (String?) -> Unit,
) = PromptChoiceDialog(title,
    (noneTitle?.let { listOf(PromptChoice(null, it)) } ?: emptyList()) + templates.filter { it.enabled }.map { PromptChoice(it.id, it.name) },
    selectedId, onDismiss, onSelect)

@Composable
private fun PromptError(message: String) = Text(message, color = MiuixTheme.colorScheme.error,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("prompt-error"))

private fun templateTitle(document: PromptTemplateDocument, id: String?, noneTitle: String = "模板已移除"): String =
    document.templates.firstOrNull { it.id == id }?.let { it.name + if (it.enabled) "" else "（已停用）" } ?: noneTitle

private fun readTemplateImport(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("无法读取导入文件")
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = stream.read(buffer)
            if (size < 0) break
            require(output.size() + size <= MAX_IMPORT_BYTES) { "规则文件超过 4 MiB，请拆分后导入" }
            output.write(buffer, 0, size)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024
private const val DSH_SOURCE_URL = "https://github.com/YuJunZhiXue/dsh-purge"
