package com.sunmmy.interndiary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Drawer list of archived conversation dates, newest first. Tapping a row loads
 * that day's saved conversation. The current date is tagged so the user can see
 * which day they're viewing.
 */
class HistoryAdapter(
    private val onDateClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.DateHolder>() {

    private val dates = mutableListOf<String>()
    private var currentDate: String = ""
    private var todayDate: String = ""

    fun submit(newDates: List<String>, current: String, today: String) {
        dates.clear()
        dates.addAll(newDates)
        currentDate = current
        todayDate = today
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = dates.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_date, parent, false)
        return DateHolder(view)
    }

    override fun onBindViewHolder(holder: DateHolder, position: Int) {
        val date = dates[position]
        holder.bind(date, date == currentDate, date == todayDate)
        holder.itemView.setOnClickListener { onDateClick(date) }
    }

    class DateHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.textHistoryDate)
        private val tag: TextView = view.findViewById(R.id.textHistoryTag)
        fun bind(date: String, isCurrent: Boolean, isToday: Boolean) {
            dateText.text = date
            itemView.isActivated = isCurrent
            if (isToday) {
                tag.visibility = View.VISIBLE
                tag.setText(R.string.drawer_today)
            } else {
                tag.visibility = View.GONE
            }
        }
    }
}
