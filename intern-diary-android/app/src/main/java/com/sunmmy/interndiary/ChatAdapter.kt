package com.sunmmy.interndiary

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import java.io.File

/**
 * Renders the conversation feed as chat-style bubbles / cards.
 *
 * View types map 1:1 to the ChatMessage subtypes.
 */
class ChatAdapter(
    private val onCopyDraft: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun submit(newItems: List<ChatMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun add(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    fun snapshot(): List<ChatMessage> = items.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    fun lastIndex(): Int = items.size - 1

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ChatMessage.UserText -> TYPE_USER_TEXT
        is ChatMessage.UserImage -> TYPE_USER_IMAGE
        is ChatMessage.Assistant -> TYPE_ASSISTANT
        is ChatMessage.DiaryDraft -> TYPE_DRAFT
        is ChatMessage.SystemCard -> TYPE_SYSTEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER_TEXT -> UserTextHolder(
                inflater.inflate(R.layout.item_message_user, parent, false)
            )
            TYPE_USER_IMAGE -> UserImageHolder(
                inflater.inflate(R.layout.item_message_image, parent, false)
            )
            TYPE_SYSTEM -> SystemHolder(
                inflater.inflate(R.layout.item_message_system, parent, false)
            )
            TYPE_DRAFT -> DraftHolder(
                inflater.inflate(R.layout.item_message_draft, parent, false),
                onCopyDraft
            )
            else -> AssistantHolder(
                inflater.inflate(R.layout.item_message_assistant, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatMessage.UserText -> (holder as UserTextHolder).bind(item)
            is ChatMessage.UserImage -> (holder as UserImageHolder).bind(item)
            is ChatMessage.Assistant -> (holder as AssistantHolder).bind(item)
            is ChatMessage.DiaryDraft -> (holder as DraftHolder).bind(item)
            is ChatMessage.SystemCard -> (holder as SystemHolder).bind(item)
        }
    }

    class UserTextHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.textBody)
        private val time: TextView = view.findViewById(R.id.textTime)
        fun bind(item: ChatMessage.UserText) {
            body.text = item.content
            time.text = item.time
        }
    }

    class UserImageHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb: ImageView = view.findViewById(R.id.imageThumb)
        private val caption: TextView = view.findViewById(R.id.textCaption)
        private val time: TextView = view.findViewById(R.id.textTime)
        fun bind(item: ChatMessage.UserImage) {
            // Prefer the persisted local copy (survives restarts); fall back to
            // the original content Uri for freshly-picked images.
            val localFile = item.localPath?.let { File(it) }?.takeIf { it.exists() }
            val image = when {
                localFile != null -> Uri.fromFile(localFile)
                item.uri != null -> item.uri
                else -> null
            }
            if (image != null) {
                thumb.load(image) {
                    placeholder(R.drawable.ic_gallery)
                    error(R.drawable.ic_gallery)
                    crossfade(false)
                }
            } else {
                thumb.setImageResource(R.drawable.ic_gallery)
            }
            if (item.caption.isNullOrBlank()) {
                caption.visibility = View.GONE
            } else {
                caption.visibility = View.VISIBLE
                caption.text = item.caption
            }
            time.text = item.time
        }
    }

    class AssistantHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.textBody)
        private val time: TextView = view.findViewById(R.id.textTime)
        fun bind(item: ChatMessage.Assistant) {
            body.text = item.content
            time.text = item.time
        }
    }

    class DraftHolder(
        view: View,
        private val onCopyDraft: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.textBody)
        private val time: TextView = view.findViewById(R.id.textTime)
        private val copy: TextView = view.findViewById(R.id.btnCopyDraft)
        fun bind(item: ChatMessage.DiaryDraft) {
            body.text = MarkdownFormatter.format(item.markdown)
            time.text = item.time
            copy.setOnClickListener { onCopyDraft(item.markdown) }
        }
    }

    class SystemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.textTitle)
        private val body: TextView = view.findViewById(R.id.textBody)
        fun bind(item: ChatMessage.SystemCard) {
            title.text = item.title
            body.text = item.body
        }
    }

    companion object {
        private const val TYPE_USER_TEXT = 0
        private const val TYPE_USER_IMAGE = 1
        private const val TYPE_ASSISTANT = 2
        private const val TYPE_SYSTEM = 3
        private const val TYPE_DRAFT = 4
    }
}
