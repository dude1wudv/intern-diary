package com.sunmmy.interndiary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AssistantSessionAdapter(
    private val onSessionClick: (String) -> Unit,
    private val onRenameSession: (String) -> Unit,
    private val onDeleteSession: (String) -> Unit,
) : RecyclerView.Adapter<AssistantSessionAdapter.SessionHolder>() {

    private val sessions = mutableListOf<AssistantSessionMeta>()
    private var currentId: String = ""

    fun submit(newSessions: List<AssistantSessionMeta>, current: String) {
        sessions.clear()
        sessions.addAll(newSessions)
        currentId = current
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sessions.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assistant_session, parent, false)
        return SessionHolder(view)
    }

    override fun onBindViewHolder(holder: SessionHolder, position: Int) {
        val session = sessions[position]
        holder.bind(session, session.id == currentId)
        holder.itemView.setOnClickListener { onSessionClick(session.id) }
        holder.itemView.findViewById<View>(R.id.btnRenameSession).setOnClickListener { onRenameSession(session.id) }
        holder.itemView.findViewById<View>(R.id.btnDeleteSession).setOnClickListener { onDeleteSession(session.id) }
    }

    class SessionHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val preview: TextView = view.findViewById(R.id.textSessionPreview)
        private val time: TextView = view.findViewById(R.id.textSessionTime)
        private val tag: TextView = view.findViewById(R.id.textSessionTag)

        fun bind(meta: AssistantSessionMeta, isCurrent: Boolean) {
            preview.text = meta.preview.ifEmpty { "空对话" }
            time.text = formatTime(meta.createdAt)
            itemView.isActivated = isCurrent
            tag.visibility = if (isCurrent) View.VISIBLE else View.GONE
            if (isCurrent) tag.setText(R.string.drawer_session_current)
        }

        private fun formatTime(millis: Long): String {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            return sdf.format(Date(millis))
        }
    }
}
