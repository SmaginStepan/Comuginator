package com.an0obis.comuginator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacMessageListItemDto

class MessageHistoryAdapter(
    private val onRepeatClick: (AacMessageListItemDto) -> Unit
) : RecyclerView.Adapter<MessageHistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<AacMessageListItemDto>()

    fun submitItems(newItems: List<AacMessageListItemDto>) {
        val oldSize = items.size

        if (oldSize > 0) {
            items.clear()
            notifyItemRangeRemoved(0, oldSize)
        } else {
            items.clear()
        }

        items.addAll(newItems)

        if (items.isNotEmpty()) {
            notifyItemRangeInserted(0, items.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCreatedAt: TextView = view.findViewById(R.id.tvCreatedAt)
        private val tvMode: TextView = view.findViewById(R.id.tvMode)
        private val tvSelectedReplyLabel: TextView = view.findViewById(R.id.tvSelectedReplyLabel)

        private val rvSuggestedReplies: RecyclerView = view.findViewById(R.id.rvSuggestedReplies)
        private val rvSelectedReply: RecyclerView = view.findViewById(R.id.rvSelectedReply)

        private val btnRepeat: Button = view.findViewById(R.id.btnRepeat)

        private val suggestedRepliesAdapter = SimpleCardAdapter()
        private val selectedReplyAdapter = SimpleCardAdapter()

        init {
            rvSuggestedReplies.layoutManager =
                LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            rvSuggestedReplies.adapter = suggestedRepliesAdapter
            rvSuggestedReplies.setHasFixedSize(true)

            rvSelectedReply.layoutManager =
                LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            rvSelectedReply.adapter = selectedReplyAdapter
            rvSelectedReply.setHasFixedSize(true)
        }

        fun bind(item: AacMessageListItemDto) {
            val context = itemView.context

            tvCreatedAt.text = item.createdAt

            tvMode.text = when (item.mode) {
                "SEQUENCE" -> context.getString(R.string.compose_mode_sequence)
                else -> context.getString(R.string.compose_mode_normal)
            }

            suggestedRepliesAdapter.submitItems(item.suggestedReplies)

            val selectedReply = item.reply?.reply

            if (selectedReply != null) {
                tvSelectedReplyLabel.text = context.getString(R.string.selected_reply)
                rvSelectedReply.visibility = View.VISIBLE
                selectedReplyAdapter.submitItems(listOf(selectedReply))
            } else {
                tvSelectedReplyLabel.text = context.getString(R.string.reply_empty)
                rvSelectedReply.visibility = View.GONE
                selectedReplyAdapter.submitItems(emptyList())
            }

            btnRepeat.setOnClickListener {
                onRepeatClick(item)
            }
        }
    }
}