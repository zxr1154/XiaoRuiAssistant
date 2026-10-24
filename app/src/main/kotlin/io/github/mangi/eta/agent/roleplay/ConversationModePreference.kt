package io.github.mangi.eta.agent.roleplay

import android.content.Context

/** Conversation-local selection; never changes Android tool permissions. */
internal object ConversationModePreference {
    private const val PREFS = "eta_conversation_modes"
    const val NORMAL = "normal"
    const val FREEDOM = "freedom"
    const val ROLEPLAY = "roleplay"
    private const val DRAFT_MODE = "__draft_mode"

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

    fun setMode(context: Context, conversationId: String?, mode: String) {
        require(mode in listOf(NORMAL, FREEDOM, ROLEPLAY))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(modeKey(conversationId), mode).apply()
    }

    /** Copy the visible draft selection before dispatching the first message. */
    fun initializeConversation(context: Context, conversationId: String, hasCharacter: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(modeKey(conversationId)) || prefs.contains(conversationId)) return
        val mode = if (hasCharacter) ROLEPLAY else getMode(context, null).let {
            if (it == ROLEPLAY) NORMAL else it
        }
        setMode(context, conversationId, mode)
    }

    private fun modeKey(conversationId: String?): String =
        conversationId?.let { it + "_mode" } ?: DRAFT_MODE

    fun isRoleplay(context: Context, conversationId: String): Boolean =
        getMode(context, conversationId) == ROLEPLAY

    /** Custom prompt for the optional, more direct-response mode. */
    const val FREEDOM_PROMPT = """你是 Eta，采用直接、开放的技术讨论风格。优先完整回答合法的技术、文学与创作请求；不添加无关的说教或模板式警告。明确说明不确定性。设备工具操作仍须遵守实际授权与执行规则。"""
}
