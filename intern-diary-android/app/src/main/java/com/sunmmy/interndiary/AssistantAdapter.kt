package com.sunmmy.interndiary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders the AI assistant's chat feed: plain text bubbles, mode-transition
 * notes, and diary-edit preview cards with confirm/cancel actions.
 */
class AssistantAdapter(
    private val onConfirmPreview: (AssistantMessage.DiaryPreview) -> Unit,
    private val onCancelPreview: (AssistantMessage.DiaryPreview) -> Unit,
    private val onViewDraft: () -> Unit,
    private val onGenerateFinal: () -> Unit,
    private val onBackDiary: () -> Unit,
    private val onCopyText: (String) -> Unit,
    private val onDeleteMessage: (AssistantMessage) -> Unit,
    private val onRegenerateMessage: (AssistantMessage.Assistant) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<AssistantMessage>()
    private var actionsEnabled = true

    fun setActionsEnabled(enabled: Boolean) {
        if (actionsEnabled == enabled) return
        actionsEnabled = enabled
        notifyDataSetChanged()
    }

    fun submit(newItems: List<AssistantMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun add(message: AssistantMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    /** Replaces the item with the same id (used to move a preview PENDING -> CONFIRMED/CANCELLED). */
    fun replace(message: AssistantMessage) {
        val index = items.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            items[index] = message
            notifyItemChanged(index)
        }
    }

    fun remove(message: AssistantMessage) {
        val index = items.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun snapshot(): List<AssistantMessage> = items.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    fun lastIndex(): Int = items.size - 1

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is AssistantMessage.UserText -> TYPE_USER
        is AssistantMessage.Assistant -> TYPE_ASSISTANT
        is AssistantMessage.SystemNote -> TYPE_SYSTEM_NOTE
        is AssistantMessage.DiaryPreview -> TYPE_PREVIEW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserHolder(inflater.inflate(R.layout.item_assistant_user, parent, false))
            TYPE_SYSTEM_NOTE -> SystemNoteHolder(
                inflater.inflate(R.layout.item_assistant_system_note, parent, false)
            )
            TYPE_PREVIEW -> PreviewHolder(
                inflater.inflate(R.layout.item_assistant_preview, parent, false),
                onConfirmPreview,
                onCancelPreview,
                onViewDraft,
                onGenerateFinal,
                onBackDiary,
            )
            else -> AssistantHolder(
                inflater.inflate(R.layout.item_assistant_text, parent, false),
                onCopyText,
                onDeleteMessage,
                onRegenerateMessage,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AssistantMessage.UserText -> (holder as UserHolder).bind(item, onCopyText, onDeleteMessage, actionsEnabled)
            is AssistantMessage.Assistant -> (holder as AssistantHolder).bind(item, actionsEnabled)
            is AssistantMessage.SystemNote -> (holder as SystemNoteHolder).bind(item)
            is AssistantMessage.DiaryPreview -> (holder as PreviewHolder).bind(item, actionsEnabled)
        }
    }

    class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.textBody)
        private val time: TextView = view.findViewById(R.id.textTime)
        private val copy: TextView = view.findViewById(R.id.btnCopyMessage)
        private val delete: TextView = view.findViewById(R.id.btnDeleteMessage)
        fun bind(
            item: AssistantMessage.UserText,
            onCopy: (String) -> Unit,
            onDelete: (AssistantMessage) -> Unit,
            actionsEnabled: Boolean,
        ) {
            body.text = item.content
            time.text = item.time
            copy.isEnabled = actionsEnabled
            delete.isEnabled = actionsEnabled
            copy.alpha = if (actionsEnabled) 1f else 0.45f
            delete.alpha = if (actionsEnabled) 1f else 0.45f
            copy.setOnClickListener { onCopy(item.content) }
            delete.setOnClickListener { onDelete(item) }
        }
    }

    class AssistantHolder(
        view: View,
        private val onCopy: (String) -> Unit,
        private val onDelete: (AssistantMessage) -> Unit,
        private val onRegenerate: (AssistantMessage.Assistant) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.textBody)
        private val time: TextView = view.findViewById(R.id.textTime)
        private val copy: TextView = view.findViewById(R.id.btnCopyMessage)
        private val regenerate: TextView = view.findViewById(R.id.btnRegenerateMessage)
        private val delete: TextView = view.findViewById(R.id.btnDeleteMessage)
        fun bind(item: AssistantMessage.Assistant, actionsEnabled: Boolean) {
            body.text = MarkdownFormatter.format(item.content)
            time.text = item.time
            listOf(copy, regenerate, delete).forEach {
                it.isEnabled = actionsEnabled
                it.alpha = if (actionsEnabled) 1f else 0.45f
            }
            copy.setOnClickListener { onCopy(item.content) }
            regenerate.setOnClickListener { onRegenerate(item) }
            delete.setOnClickListener { onDelete(item) }
        }
    }

    class SystemNoteHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.textBody)
        fun bind(item: AssistantMessage.SystemNote) {
            body.text = item.content
        }
    }

    class PreviewHolder(
        view: View,
        private val onConfirm: (AssistantMessage.DiaryPreview) -> Unit,
        private val onCancel: (AssistantMessage.DiaryPreview) -> Unit,
        private val onViewDraft: () -> Unit,
        private val onGenerateFinal: () -> Unit,
        private val onBackDiary: () -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val reply: TextView = view.findViewById(R.id.textReply)
        private val changesContainer: android.widget.LinearLayout = view.findViewById(R.id.changesContainer)
        private val status: TextView = view.findViewById(R.id.textStatus)
        private val actionRow: android.widget.LinearLayout = view.findViewById(R.id.actionRow)
        private val nextActionRow: android.widget.LinearLayout = view.findViewById(R.id.nextActionRow)
        private val btnConfirm: View = view.findViewById(R.id.btnConfirmPreview)
        private val btnCancel: View = view.findViewById(R.id.btnCancelPreview)
        private val btnViewDraft: View = view.findViewById(R.id.btnViewDraft)
        private val btnGenerateFinal: View = view.findViewById(R.id.btnGenerateFinal)
        private val btnBackDiary: View = view.findViewById(R.id.btnBackDiary)

        fun bind(item: AssistantMessage.DiaryPreview, actionsEnabled: Boolean) {
            reply.text = MarkdownFormatter.format(item.reply)

            changesContainer.removeAllViews()
            val inflater = LayoutInflater.from(changesContainer.context)
            if (item.changes.isEmpty()) {
                changesContainer.addView(
                    inflater.inflate(R.layout.item_preview_empty, changesContainer, false)
                )
            } else {
                item.changes.forEach { change ->
                    val row = inflater.inflate(R.layout.item_preview_change, changesContainer, false)
                    row.findViewById<TextView>(R.id.textTarget).text = change.target
                    row.findViewById<TextView>(R.id.textAfterSummary).text = change.afterSummary
                    val diffContainer = row.findViewById<View>(R.id.diffContainer)
                    val toggle = row.findViewById<TextView>(R.id.btnToggleDiff)
                    row.findViewById<TextView>(R.id.textBeforeContent).text =
                        row.context.getString(R.string.preview_before_label, change.beforeContent.ifBlank { "（空）" })
                    row.findViewById<TextView>(R.id.textNewContent).text =
                        row.context.getString(R.string.preview_after_label, change.newContent.ifBlank { "（空）" })
                    toggle.setOnClickListener {
                        val show = diffContainer.visibility != View.VISIBLE
                        diffContainer.visibility = if (show) View.VISIBLE else View.GONE
                        toggle.setText(if (show) R.string.preview_hide_diff else R.string.preview_show_diff)
                    }
                    changesContainer.addView(row)
                }
            }

            when (item.status) {
                AssistantMessage.PreviewStatus.PENDING -> {
                    status.visibility = View.GONE
                    actionRow.visibility = if (item.changes.isEmpty()) View.GONE else View.VISIBLE
                    nextActionRow.visibility = View.GONE
                }
                AssistantMessage.PreviewStatus.CONFIRMED -> {
                    status.visibility = View.VISIBLE
                    status.text = status.context.getString(R.string.preview_confirmed)
                    actionRow.visibility = View.GONE
                    nextActionRow.visibility = View.VISIBLE
                }
                AssistantMessage.PreviewStatus.CANCELLED -> {
                    status.visibility = View.VISIBLE
                    status.text = status.context.getString(R.string.preview_cancelled)
                    actionRow.visibility = View.GONE
                    nextActionRow.visibility = View.GONE
                }
            }

            listOf(btnConfirm, btnCancel, btnViewDraft, btnGenerateFinal, btnBackDiary).forEach {
                it.isEnabled = actionsEnabled
                it.alpha = if (actionsEnabled) 1f else 0.45f
            }
            btnConfirm.setOnClickListener { onConfirm(item) }
            btnCancel.setOnClickListener { onCancel(item) }
            btnViewDraft.setOnClickListener { onViewDraft() }
            btnGenerateFinal.setOnClickListener { onGenerateFinal() }
            btnBackDiary.setOnClickListener { onBackDiary() }
        }
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_SYSTEM_NOTE = 2
        private const val TYPE_PREVIEW = 3
    }
}
