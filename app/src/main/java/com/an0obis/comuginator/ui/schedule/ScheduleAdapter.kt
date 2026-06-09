package com.an0obis.comuginator.ui.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ScheduleItemDto
import com.an0obis.comuginator.ui.base.BaseAdapter

class ScheduleAdapter(
    private val authToken: String,
    private val onDelete: (ScheduleItemDto) -> Unit,
    private val onEdit: (ScheduleItemDto) -> Unit
) : BaseAdapter<ScheduleItemDto, ScheduleAdapter.VH>() {

    class VH(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val ivCard: ImageView = root.findViewById(R.id.ivCard)
        val tvTitle: TextView = root.findViewById(R.id.tvTitle)
        val tvSchedule: TextView = root.findViewById(R.id.tvSchedule)
        val btnMore: Button = root.findViewById(R.id.btnMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false) as ViewGroup
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val card = item.cards.firstOrNull()

        holder.tvTitle.text = card?.label ?: item.id
        holder.tvSchedule.text = formatSchedule(holder.itemView.context, item)

        val imageUrl = card?.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(holder.itemView.context)
                .data(imageUrl)
                .addHeader("Authorization", "Bearer $authToken")
                .target(holder.ivCard)
                .build()
            Coil.imageLoader(holder.itemView.context).enqueue(request)
        } else {
            holder.ivCard.setImageDrawable(null)
        }

        holder.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            val editId = 1
            val deleteId = 2
            popup.menu.add(0, editId, 0, view.context.getString(R.string.edit))
            popup.menu.add(0, deleteId, 1, view.context.getString(R.string.delete))
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    editId -> { onEdit(item); true }
                    deleteId -> { onDelete(item); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun weekdayName(context: android.content.Context, day: Int) = when (day) {
        1 -> context.getString(R.string.weekday_mon)
        2 -> context.getString(R.string.weekday_tue)
        3 -> context.getString(R.string.weekday_wed)
        4 -> context.getString(R.string.weekday_thu)
        5 -> context.getString(R.string.weekday_fri)
        6 -> context.getString(R.string.weekday_sat)
        7 -> context.getString(R.string.weekday_sun)
        else -> "?"
    }

    private fun formatSchedule(context: android.content.Context, item: ScheduleItemDto): String {
        return when (item.mode) {
            "WEEKDAY" -> "${item.weekdays.joinToString(", ") { weekdayName(context, it) }} ${item.time}"
            "DATE" -> "${item.date ?: "-"} ${item.time}"
            else -> item.time
        }
    }
}
