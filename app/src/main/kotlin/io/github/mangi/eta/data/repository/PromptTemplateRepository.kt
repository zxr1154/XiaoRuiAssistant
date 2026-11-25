package io.github.mangi.eta.data.repository

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.annotation.VisibleForTesting
import io.github.mangi.eta.agent.roleplay.ConversationModePreference
import io.github.mangi.eta.data.model.PromptTemplate
import io.github.mangi.eta.data.model.PromptTemplateBinding
import io.github.mangi.eta.data.model.PromptTemplateDocument
import io.github.mangi.eta.data.model.PromptTemplateSelection
import java.io.StringReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** All operations are synchronous; callers perform disk work on an I/O dispatcher. */
internal object PromptTemplateRepository {
    const val DRAFT_CONVERSATION_ID = "__draft__"
    const val BUILTIN_DIRECT_ID = "builtin-direct"
    internal const val PREFERENCES_NAME = "xiaorui_prompt_templates"
    internal const val DOCUMENT_KEY = "document_v1"
    const val MAX_DOCUMENT_BYTES = 4 * 1024 * 1024
    const val MAX_PROMPT_BYTES = 64 * 1024
    private const val MAX_TEMPLATES = 100
    private const val MAX_BINDINGS = 1_000
    private const val MAX_CONVERSATIONS = 10_000
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    private val states = mutableMapOf<String, MutableStateFlow<PromptTemplateDocument>>()
    private val lock = Any()

    fun snapshot(context: Context): PromptTemplateDocument = synchronized(lock) { state(context).value }

    fun observe(context: Context): StateFlow<PromptTemplateDocument> = synchronized(lock) {
        state(context).asStateFlow()
    }

    fun saveTemplate(context: Context, template: PromptTemplate) = update(context) { current ->
        val templates = current.templates.toMutableList()
        val index = templates.indexOfFirst { it.id == template.id }
        if (index < 0) templates += template else templates[index] = template
        current.copy(templates = templates)
    }

    fun deleteTemplate(context: Context, id: String) = update(context) { current ->
        current.copy(
            templates = current.templates.filterNot { it.id == id },
            bindings = current.bindings.filterNot { it.templateId == id },
            defaultTemplateId = current.defaultTemplateId?.takeUnless { it == id },
            conversationSelections = current.conversationSelections.mapValues { (_, selection) ->
                if (selection.templateId == id) PromptTemplateSelection(disabled = true) else selection
            },
        )
    }

    fun setDefault(context: Context, id: String?) = update(context) { it.copy(defaultTemplateId = id) }

    fun setBinding(context: Context, providerId: String, modelId: String?, templateId: String?) = update(context) { current ->
        requireBoundedLabel(providerId, "服务商标识", 512)
        modelId?.let { requireBoundedLabel(it, "模型名称", 512) }
        current.copy(bindings = current.bindings.filterNot { it.providerId == providerId && it.modelId == modelId } +
            listOfNotNull(templateId?.let { PromptTemplateBinding(providerId, modelId, it) }))
    }

    fun conversationSelection(context: Context, conversationId: String?): PromptTemplateSelection =
        snapshot(context).conversationSelections[conversationKey(conversationId)] ?: PromptTemplateSelection()

    fun setConversationSelection(context: Context, conversationId: String?, selection: PromptTemplateSelection) = update(context) { current ->
        val key = conversationKey(conversationId)
        current.copy(conversationSelections = current.conversationSelections + (key to selection))
    }

    /** Preserve an existing conversation's explicit choice if initialization is retried. */
    fun initializeConversation(context: Context, id: String) = update(context) { current ->
        require(id != DRAFT_CONVERSATION_ID) { "会话标识无效" }
        val key = conversationKey(id)
        if (key in current.conversationSelections) current else current.copy(
            conversationSelections = current.conversationSelections +
                (key to (current.conversationSelections[DRAFT_CONVERSATION_ID] ?: PromptTemplateSelection())),
        )
    }

    /** Parse only: malformed or incompatible documents never mutate the saved configuration. */
    fun parseImport(text: String): PromptTemplateDocument {
        require(text.length <= MAX_DOCUMENT_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) {
            "提示模板文件超过 4 MiB 限制"
        }
        try {
            validateJsonShape(text)
            val root = json.parseToJsonElement(text) as? JsonObject ?: error("提示模板必须是 JSON 对象")
            val format = root["format"] as? JsonPrimitive
            val version = root["schemaVersion"] as? JsonPrimitive
            require(format?.isString == true && format.content == PromptTemplateDocument.FORMAT) { "这不是小瑞提示模板文件" }
            require(version != null && !version.isString && version.intOrNull == PromptTemplateDocument.SCHEMA_VERSION) {
                "不支持的提示模板版本"
            }
            require("templates" in root) { "提示模板文件缺少模板列表" }
            return validate(json.decodeFromString<PromptTemplateDocument>(text))
        } catch (failure: Exception) {
            throw IllegalArgumentException("提示模板文件无效，请检查格式、版本和引用", failure)
        }
    }

    fun importDocument(context: Context, document: PromptTemplateDocument, replace: Boolean = false) {
        val incoming = validate(document)
        update(context) { current ->
            if (replace) {
                val incomingIds = incoming.templates.mapTo(mutableSetOf()) { it.id }
                incoming.copy(conversationSelections = current.conversationSelections.mapValues { (_, selection) ->
                    if (selection.templateId != null && selection.templateId !in incomingIds) {
                        PromptTemplateSelection(disabled = true)
                    } else selection
                })
            } else {
                val incomingIds = incoming.templates.mapTo(mutableSetOf()) { it.id }
                val incomingBindings = incoming.bindings.mapTo(mutableSetOf()) { it.providerId to it.modelId }
                current.copy(
                    templates = current.templates.filterNot { it.id in incomingIds } + incoming.templates,
                    bindings = current.bindings.filterNot { (it.providerId to it.modelId) in incomingBindings } + incoming.bindings,
                    defaultTemplateId = incoming.defaultTemplateId ?: current.defaultTemplateId,
                )
            }
        }
    }

    /** A full application backup restores local conversation choices as well as shared rules. */
    fun restoreDocument(context: Context, document: PromptTemplateDocument) {
        val restored = validate(document)
        update(context) { restored }
    }

    /** Share templates and model bindings without leaking local conversation identifiers. */
    fun exportDocument(context: Context): String = json.encodeToString(snapshot(context).copy(conversationSelections = emptyMap()))

    /** Full backups use this validator before any database or file replacement occurs. */
    fun validate(document: PromptTemplateDocument): PromptTemplateDocument {
        require(document.format == PromptTemplateDocument.FORMAT && document.schemaVersion == PromptTemplateDocument.SCHEMA_VERSION) {
            "不支持的提示模板格式或版本"
        }
        require(document.templates.size <= MAX_TEMPLATES) { "提示模板不能超过 100 个" }
        require(document.bindings.size <= MAX_BINDINGS) { "提示模板绑定不能超过 1000 个" }
        require(document.conversationSelections.size <= MAX_CONVERSATIONS) { "提示模板会话选择过多" }
        val ids = mutableSetOf<String>()
        document.templates.forEach { template ->
            require(template.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) && ids.add(template.id)) { "提示模板 ID 重复或无效" }
            requireBoundedLabel(template.name, "模板名称", 256)
            require(template.prompt.isNotBlank() && template.prompt.length <= MAX_PROMPT_BYTES &&
                template.prompt.toByteArray(Charsets.UTF_8).size <= MAX_PROMPT_BYTES) { "模板正文为空或超过 64 KiB" }
        }
        require(document.defaultTemplateId == null || document.defaultTemplateId in ids) { "默认提示模板不存在" }
        val bindings = mutableSetOf<Pair<String, String?>>()
        document.bindings.forEach { binding ->
            requireBoundedLabel(binding.providerId, "服务商标识", 512)
            binding.modelId?.let { requireBoundedLabel(it, "模型名称", 512) }
            require(bindings.add(binding.providerId to binding.modelId)) { "提示模板模型绑定重复" }
            require(binding.templateId in ids) { "绑定的提示模板不存在" }
        }
        document.conversationSelections.forEach { (id, selection) ->
            requireBoundedLabel(id, "会话标识", 256)
            require(!(selection.disabled && selection.templateId != null)) { "停用的会话不能同时指定提示模板" }
            require(selection.templateId == null || selection.templateId in ids) { "会话选择的提示模板不存在" }
        }
        val safe = document.copy(templates = document.templates.toList(), bindings = document.bindings.toList(),
            conversationSelections = document.conversationSelections.toMap())
        require(json.encodeToString(safe).toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES) { "提示模板文件超过 4 MiB 限制" }
        return safe
    }

    private fun update(context: Context, transform: (PromptTemplateDocument) -> PromptTemplateDocument) = synchronized(lock) {
        val state = state(context)
        val next = validate(transform(state.value))
        if (next != state.value) {
            val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            check(preferences.edit().putString(DOCUMENT_KEY, json.encodeToString(next)).commit()) { "提示模板保存失败，原配置尚未更新" }
            state.value = next
        }
    }

    private fun state(context: Context): MutableStateFlow<PromptTemplateDocument> {
        val appContext = context.applicationContext
        val key = appContext.filesDir.canonicalPath
        return states.getOrPut(key) {
            val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val encoded = preferences.getString(DOCUMENT_KEY, null)
            val document = if (encoded == null) PromptTemplateDocument(templates = listOf(
                PromptTemplate(BUILTIN_DIRECT_ID, "直接交流", ConversationModePreference.FREEDOM_PROMPT),
            ), defaultTemplateId = BUILTIN_DIRECT_ID) else try { parseImport(encoded) } catch (failure: Exception) {
                throw IllegalStateException("已保存的提示模板无法读取，请先导出或修复原配置", failure)
            }
            MutableStateFlow(document)
        }
    }

    private fun conversationKey(id: String?): String = (id ?: DRAFT_CONVERSATION_ID).also {
        requireBoundedLabel(it, "会话标识", 256)
    }

    private fun requireBoundedLabel(value: String, label: String, maxChars: Int) {
        require(value.isNotBlank() && value.length <= maxChars && value.none { it.isISOControl() }) { "$label 为空、过长或包含控制字符" }
    }

    /** Android's reader lets us reject duplicate JSON keys before Kotlin serialization collapses them. */
    private fun validateJsonShape(text: String) {
        JsonReader(StringReader(text)).use { reader ->
            fun value(depth: Int) {
                require(depth <= 16) { "提示模板 JSON 嵌套过深" }
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        val names = mutableSetOf<String>()
                        while (reader.hasNext()) {
                            require(names.add(reader.nextName())) { "提示模板 JSON 字段重复" }
                            value(depth + 1)
                        }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        while (reader.hasNext()) value(depth + 1)
                        reader.endArray()
                    }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("提示模板 JSON 格式无效")
                }
            }
            value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "提示模板 JSON 末尾存在额外内容" }
        }
    }

    @VisibleForTesting
    internal fun closeForTests(context: Context) = synchronized(lock) { states.remove(context.applicationContext.filesDir.canonicalPath); Unit }
}
