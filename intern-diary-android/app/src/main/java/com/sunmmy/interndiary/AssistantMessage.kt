package com.sunmmy.interndiary

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single item in the AI assistant's chat feed.
 *
 * This is intentionally separate from [ChatMessage] (the diary-capture feed on
 * the home screen): the assistant's plain chat history is local-only and must
 * never mix with diary entry capture, per the AI 助手 spec.
 */
sealed class AssistantMessage {
    abstract val id: Long
    abstract val time: String

    data class UserText(
        override val id: Long,
        override val time: String,
        val content: String,
    ) : AssistantMessage()

    data class Assistant(
        override val id: Long,
        override val time: String,
        val content: String,
    ) : AssistantMessage()

    // A small centered note marking a mode transition (entering/exiting diary
    // edit mode), distinct from a chat bubble.
    data class SystemNote(
        override val id: Long,
        override val time: String,
        val content: String,
    ) : AssistantMessage()

    // A pending/confirmed/cancelled diary-edit preview card. [previewId] is the
    // backend id used to confirm; once resolved it's kept for history but the
    // confirm/cancel actions become inert.
    data class DiaryPreview(
        override val id: Long,
        override val time: String,
        val reply: String,
        val previewId: String,
        val changes: List<ChangeItem>,
        val status: PreviewStatus,
    ) : AssistantMessage()

    data class ChangeItem(
        val target: String,
        val beforeSummary: String,
        val afterSummary: String,
        val beforeContent: String,
        val newContent: String,
    )

    enum class PreviewStatus { PENDING, CONFIRMED, CANCELLED }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("time", time)
        when (val m = this@AssistantMessage) {
            is UserText -> {
                put("type", "user_text")
                put("content", m.content)
            }
            is Assistant -> {
                put("type", "assistant")
                put("content", m.content)
            }
            is SystemNote -> {
                put("type", "system_note")
                put("content", m.content)
            }
            is DiaryPreview -> {
                put("type", "diary_preview")
                put("reply", m.reply)
                put("previewId", m.previewId)
                put("status", m.status.name)
                val arr = JSONArray()
                m.changes.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("target", c.target)
                        put("beforeSummary", c.beforeSummary)
                        put("afterSummary", c.afterSummary)
                        put("beforeContent", c.beforeContent)
                        put("newContent", c.newContent)
                    })
                }
                put("changes", arr)
            }
        }
    }

    companion object {
        fun fromJson(o: JSONObject): AssistantMessage? {
            val id = o.optLong("id")
            val time = o.optString("time")
            return when (o.optString("type")) {
                "user_text" -> UserText(id, time, o.optString("content"))
                "assistant" -> Assistant(id, time, o.optString("content"))
                "system_note" -> SystemNote(id, time, o.optString("content"))
                "diary_preview" -> {
                    val arr = o.optJSONArray("changes") ?: JSONArray()
                    val changes = (0 until arr.length()).map { i ->
                        val c = arr.getJSONObject(i)
                        ChangeItem(
                            c.optString("target"),
                            c.optString("beforeSummary"),
                            c.optString("afterSummary"),
                            c.optString("beforeContent"),
                            c.optString("newContent"),
                        )
                    }
                    val status = runCatching {
                        PreviewStatus.valueOf(o.optString("status", "PENDING"))
                    }.getOrDefault(PreviewStatus.PENDING)
                    DiaryPreview(id, time, o.optString("reply"), o.optString("previewId"), changes, status)
                }
                else -> null
            }
        }

        fun listToJson(items: List<AssistantMessage>): String {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(text: String): List<AssistantMessage> {
            val arr = JSONArray(text)
            val out = ArrayList<AssistantMessage>(arr.length())
            for (i in 0 until arr.length()) {
                fromJson(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
            }
            return out
        }
    }
}
