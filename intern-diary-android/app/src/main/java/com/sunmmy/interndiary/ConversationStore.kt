package com.sunmmy.interndiary

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Local, per-date persistence for the conversation feed.
 *
 * Each date's messages are stored as a JSON file under
 * `filesDir/conversations/<date>.json`. This lets the user switch dates from
 * the drawer (or the date picker) without losing chat context, and restores the
 * feed after an app restart.
 *
 * Images are copied into `filesDir/images/` best-effort so they can be
 * re-rendered later; if copying fails the message still persists with its text.
 * Nothing here touches the backend contract.
 */
class ConversationStore(context: Context) {

    private val appContext = context.applicationContext
    private val convDir = File(appContext.filesDir, "conversations").apply { mkdirs() }
    private val imageDir = File(appContext.filesDir, "images").apply { mkdirs() }

    private fun fileFor(date: String) = File(convDir, "$date.json")

    /** Returns the saved messages for [date], or an empty list if none. */
    fun load(date: String): List<ChatMessage> {
        val f = fileForOrNull(date) ?: return emptyList()
        return try {
            ChatMessage.listFromJson(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fileForOrNull(date: String): File? =
        fileFor(date).takeIf { it.exists() }

    /** Persists [messages] for [date]. An empty list deletes the archive entry. */
    fun save(date: String, messages: List<ChatMessage>) {
        try {
            if (messages.isEmpty()) {
                fileFor(date).delete()
                return
            }
            fileFor(date).writeText(ChatMessage.listToJson(messages))
        } catch (e: Exception) {
            // Persistence is best-effort; a failed write must not crash the UI.
        }
    }

    /** Dates that have a saved conversation, newest first. */
    fun archivedDates(): List<String> {
        val files = convDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files
            .map { it.nameWithoutExtension }
            .sortedDescending()
    }

    /** True if [date] has any saved messages. */
    fun hasConversation(date: String): Boolean = fileFor(date).exists()

    /**
     * Copies the image behind [uri] into app storage and returns the absolute
     * path, or null on failure. Safe to call off the main thread.
     */
    fun persistImage(uri: Uri, suggestedName: String): String? {
        return try {
            val dest = File(imageDir, "${System.currentTimeMillis()}_$suggestedName")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
