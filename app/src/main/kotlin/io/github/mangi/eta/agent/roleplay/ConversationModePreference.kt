package io.github.mangi.eta.agent.roleplay

import android.content.Context

/** Conversation-local selection; never changes Android tool permissions. */
internal object ConversationModePreference {
    private const val PREFS = "eta_conversation_modes"
    const val NORMAL = "normal"
    const val FREEDOM = "freedom"
    const val ROLEPLAY = "roleplay"
    private const val DRAFT_MODE = "__draft_mode"

    internal data class Snapshot(val values: Map<String, Any?>)

    @Synchronized
    fun getMode(context: Context, conversationId: String?, hasCharacter: Boolean = false): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(modeKey(conversationId), null)
        if (stored in listOf(NORMAL, FREEDOM, ROLEPLAY)) return stored!!
        // Preserve the previous patch's boolean preference.
        if (conversationId != null && prefs.contains(conversationId)) {
            return if (prefs.getBoolean(conversationId, false)) ROLEPLAY else NORMAL
        }
        return if (hasCharacter) ROLEPLAY else NORMAL
    }

    @Synchronized
    fun setMode(context: Context, conversationId: String?, mode: String) {
        require(mode in listOf(NORMAL, FREEDOM, ROLEPLAY))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(modeKey(conversationId), mode).apply()
    }

    /** Copy the visible draft selection before dispatching the first message. */
    @Synchronized
    fun initializeConversation(context: Context, conversationId: String, hasCharacter: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(modeKey(conversationId)) || prefs.contains(conversationId)) return
        val mode = if (hasCharacter) ROLEPLAY else getMode(context, null).let {
            if (it == ROLEPLAY) NORMAL else it
        }
        setMode(context, conversationId, mode)
    }

    /** Only actual conversations travel in backups; absent entries retain the normal/character default. */
    @Synchronized
    fun exportModes(context: Context, conversationIds: Set<String>): Map<String, String> {
        val values = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        return buildMap {
            conversationIds.forEach { id ->
                val stored = values[modeKey(id)] as? String
                if (stored in setOf(NORMAL, FREEDOM, ROLEPLAY)) put(id, stored!!)
                else (values[id] as? Boolean)?.let { put(id, if (it) ROLEPLAY else NORMAL) }
            }
        }
    }

    /** Validate before opening an editor so a malformed backup cannot change existing preferences. */
    @Synchronized
    fun restoreModes(context: Context, modes: Map<String, String>) {
        require(modes.all { (id, mode) ->
            id.isNotBlank() && id.length <= 256 && id.none { it.isISOControl() } && mode in setOf(NORMAL, FREEDOM, ROLEPLAY)
        }) { "备份中的会话模式无效" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Clear previous conversation choices, including the old boolean representation, while keeping the local draft.
        prefs.all.forEach { (key, value) ->
            if (key != DRAFT_MODE && (key.endsWith("_mode") || value is Boolean)) editor.remove(key)
        }
        modes.forEach { (id, mode) -> editor.putString(modeKey(id), mode) }
        check(editor.commit()) { "会话模式恢复失败" }
    }

    /** Rollback preserves every original typed preference, including legacy and currently unknown entries. */
    @Synchronized
    fun captureSnapshot(context: Context): Snapshot = Snapshot(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        },
    )

    @Synchronized
    fun restoreSnapshot(context: Context, snapshot: Snapshot) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        snapshot.values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    require(value.all { it is String }) { "原会话设置类型无效" }
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                null -> editor.remove(key)
                else -> error("原会话设置类型无效")
            }
        }
        check(editor.commit()) { "原会话模式回滚失败" }
    }

    private fun modeKey(conversationId: String?): String =
        conversationId?.let { it + "_mode" } ?: DRAFT_MODE

    fun isRoleplay(context: Context, conversationId: String): Boolean =
        getMode(context, conversationId) == ROLEPLAY

    /** Custom prompt for the optional, more direct-response mode. */
    const val FREEDOM_PROMPT = """你是 Eta，采用直接、开放的技术讨论风格。优先完整回答合法的技术、文学与创作请求；不添加无关的说教或模板式警告。明确说明不确定性。设备工具操作仍须遵守实际授权与执行规则。"""
}
