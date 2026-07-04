package com.sunmmy.interndiary

import android.content.Context
import java.io.File

data class AssistantSessionMeta(
    val id: String,
    val createdAt: Long,
    val preview: String,
)

/**
 * Local-only persistence for AI assistant chat sessions.
 *
 * Each session is a separate JSON file under `filesDir/assistant/<id>.json`
 * where id = timestamp-millis at creation time. Sessions are entirely separate
 * from [ConversationStore] (the diary-capture feed) and must never merge.
 *
 * Migration: if a legacy flat `history.json` exists from the single-session era
 * it is silently imported as the first session on the next access.
 */
class AssistantHistoryStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "assistant").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("assistant_store", Context.MODE_PRIVATE)

    var currentSessionId: String
        get() {
            var id = prefs.getString(KEY_SESSION_ID, "") ?: ""
            if (id.isEmpty() || !File(dir, "$id.json").exists()) {
                migrateLegacyIfNeeded()
                id = prefs.getString(KEY_SESSION_ID, "") ?: ""
                if (id.isEmpty()) id = createSession()
            }
            return id
        }
        private set(value) {
            prefs.edit().putString(KEY_SESSION_ID, value).apply()
        }

    fun createSession(): String {
        val id = System.currentTimeMillis().toString()
        currentSessionId = id
        return id
    }

    /** Points currentSessionId at an existing session (used when switching from the drawer). */
    fun selectSession(id: String) {
        currentSessionId = id
    }

    fun loadCurrentSession(): List<AssistantMessage> = loadSession(currentSessionId)

    fun saveCurrentSession(messages: List<AssistantMessage>) = saveSession(currentSessionId, messages)

    fun loadSession(id: String): List<AssistantMessage> {
        val f = File(dir, "$id.json")
        if (!f.exists()) return emptyList()
        return try {
            AssistantMessage.listFromJson(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSession(id: String, messages: List<AssistantMessage>) {
        try {
            val f = File(dir, "$id.json")
            if (messages.isEmpty()) {
                f.delete()
                return
            }
            f.writeText(AssistantMessage.listToJson(messages))
        } catch (e: Exception) {
            // best-effort
        }
    }

    fun listSessions(): List<AssistantSessionMeta> {
        val files = dir.listFiles { f -> f.extension == "json" && f.nameWithoutExtension != "legacy" }
            ?: return emptyList()
        return files
            .mapNotNull { f ->
                val id = f.nameWithoutExtension
                val ts = id.toLongOrNull() ?: return@mapNotNull null
                val preview = previewFrom(f)
                AssistantSessionMeta(id, ts, preview)
            }
            .sortedByDescending { it.createdAt }
    }

    fun deleteSession(id: String) {
        File(dir, "$id.json").delete()
        prefs.edit().remove(titleKey(id)).apply()
        if (currentSessionId == id) {
            val remaining = listSessions()
            currentSessionId = remaining.firstOrNull()?.id ?: createSession()
        }
    }

    fun renameSession(id: String, title: String) {
        prefs.edit().putString(titleKey(id), title.trim()).apply()
    }

    private fun previewFrom(file: File): String {
        return try {
            val messages = AssistantMessage.listFromJson(file.readText())
            val id = file.nameWithoutExtension
            prefs.getString(titleKey(id), null)?.takeIf { it.isNotBlank() }
                ?: messages.filterIsInstance<AssistantMessage.UserText>().firstOrNull()?.content?.take(40)
                ?: "空对话"
        } catch (e: Exception) {
            "空对话"
        }
    }

    private fun migrateLegacyIfNeeded() {
        val legacy = File(dir, "history.json")
        if (!legacy.exists()) return
        try {
            val id = System.currentTimeMillis().toString()
            legacy.copyTo(File(dir, "$id.json"), overwrite = true)
            legacy.renameTo(File(dir, "legacy.json.bak"))
            currentSessionId = id
        } catch (e: Exception) {
            legacy.delete()
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "current_session_id"
        private fun titleKey(id: String) = "title_$id"
    }
}
