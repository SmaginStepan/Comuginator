package com.an0obis.comuginator.ui.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ScheduleItemDto
import com.an0obis.comuginator.ui.CardAdapter
import com.an0obis.comuginator.ui.base.BaseAdapter

class ScheduleAdapter(
    private val onDelete: (ScheduleItemDto) -> Unit,
    private val onEdit: (ScheduleItemDto) -> Unit
) : BaseAdapter<ScheduleItemDto, ScheduleAdapter.VH>() {

    class VH(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val rvCard: RecyclerView = root.findViewById(R.id.rvCard)
        val tvTitle: TextView = root.findViewById(R.id.tvTitle)
        val tvSchedule: TextView = root.findViewById(R.id.tvSchedule)
        val btnDelete: ImageButton = root.findViewById(R.id.btnDelete)
        val btnMenu: ImageButton = root.findViewById(R.id.btnMenu)

        val cardAdapter = CardAdapter()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false) as ViewGroup
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val card = item.cards.firstOrNull()

        holder.rvCard.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(
                holder.itemView.context,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
        holder.rvCard.adapter = holder.cardAdapter
        holder.cardAdapter.submitItems(item.cards.take(1))

        holder.tvTitle.text = card?.label ?: item.id
        holder.tvSchedule.text = formatSchedule(item)

        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.btnMenu.setOnClickListener { onEdit(item) }
    }

    private fun weekdayName(day: Int) = when (day) {
        1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"
        5 -> "Fri"; 6 -> "Sat"; 7 -> "Sun"; else -> "?"
    }

    private fun formatSchedule(item: ScheduleItemDto): String {
        return when (item.mode) {
            "WEEKDAY" -> "${item.weekdays.joinToString(", ") { weekdayName(it) }} ${item.time}"
            "DATE" -> "${item.date ?: "-"} ${item.time}"
            else -> item.time
        }
    }
}