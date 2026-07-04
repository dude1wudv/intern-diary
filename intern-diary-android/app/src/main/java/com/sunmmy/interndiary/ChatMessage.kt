package com.sunmmy.interndiary

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single item in the conversation feed shown on the home screen.
 *
 * Messages are persisted per-date by [ConversationStore] as JSON, so the
 * conversation survives date switches and app restarts. Text is stored in full;
 * images are referenced by a local file path (copied into app storage on a
 * best-effort basis) so they can be re-rendered after a restart.
 */
sealed class ChatMessage {
    abstract val id: Long
    abstract val time: String

    // A text note the user sent (POST /api/entries/text).
    data class UserText(
        override val id: Long,
        override val time: String,
        val content: String,
    ) : ChatMessage()

    // One or more images the user picked, optionally with a shared caption
    // (each uploaded via POST /api/entries/image with the caption as note).
    // [localPath] points at a copy inside app storage when persistence
    // succeeded; [uri] is the original content Uri used for immediate display.
    data class UserImage(
        override val id: Long,
        override val time: String,
        val uri: Uri?,
        val localPath: String? = null,
        val caption: String? = null,
    ) : ChatMessage()

    // An assistant-style message: status text or sort/generate progress.
    data class Assistant(
        override val id: Long,
        override val time: String,
        val content: String,
    ) : ChatMessage()

    // A generated diary draft rendered from returned markdown.
    data class DiaryDraft(
        override val id: Long,
        override val time: String,
        val markdown: String,
    ) : ChatMessage()

    // A system status card (today's counts from GET /api/days/{date}).
    data class SystemCard(
        override val id: Long,
        override val time: String,
        val title: String,
        val body: String,
    ) : ChatMessage()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("time", time)
        when (val m = this@ChatMessage) {
            is UserText -> {
                put("type", "user_text")
                put("content", m.content)
            }
            is UserImage -> {
                put("type", "user_image")
                put("uri", m.uri?.toString())
                put("localPath", m.localPath)
                put("caption", m.caption)
            }
            is Assistant -> {
                put("type", "assistant")
                put("content", m.content)
            }
            is DiaryDraft -> {
                put("type", "diary_draft")
                put("markdown", m.markdown)
            }
            is SystemCard -> {
                put("type", "system_card")
                put("title", m.title)
                put("body", m.body)
            }
        }
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMessage? {
            val id = o.optLong("id")
            val time = o.optString("time")
            return when (o.optString("type")) {
                "user_text" -> UserText(id, time, o.optString("content"))
                "user_image" -> {
                    val uriStr = o.optString("uri", "").takeIf { it.isNotEmpty() }
                    val localPath = o.optString("localPath", "").takeIf { it.isNotEmpty() }
                    UserImage(
                        id,
                        time,
                        uriStr?.let { Uri.parse(it) },
                        localPath,
                        o.optString("caption", "").takeIf { it.isNotEmpty() },
                    )
                }
                "assistant" -> Assistant(id, time, o.optString("content"))
                "diary_draft" -> DiaryDraft(id, time, o.optString("markdown"))
                "system_card" -> SystemCard(id, time, o.optString("title"), o.optString("body"))
                else -> null
            }
        }

        fun listToJson(items: List<ChatMessage>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(text: String): List<ChatMessage> {
            val arr = JSONArray(text)
            val out = ArrayList<ChatMessage>(arr.length())
            for (i in 0 until arr.length()) {
                fromJson(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            return out
        }
    }
}
