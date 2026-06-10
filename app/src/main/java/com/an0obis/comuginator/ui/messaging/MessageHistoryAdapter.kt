package com.an0obis.comuginator.ui.messaging

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacMessageListItemDto
import com.an0obis.comuginator.ui.CardAdapter
import com.an0obis.comuginator.util.TimeFormat

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

        private val tvSuggestedRepliesLabel: TextView =
            view.findViewById(R.id.tvSuggestedRepliesLabel)

        private val rvSuggestedReplies: RecyclerView = view.findViewById(R.id.rvSuggestedReplies)
        private val rvSelectedReply: RecyclerView = view.findViewById(R.id.rvSelectedReply)

        private val btnRepeat: Button = view.findViewById(R.id.btnRepeat)

        private val suggestedRepliesAdapter = CardAdapter()
        private val selectedReplyAdapter = CardAdapter()

        private val rvMessage: RecyclerView =
            view.findViewById(R.id.rvMessage)

        private val tvMessageLabel: TextView =
            view.findViewById(R.id.tvMessageLabel)

        private val messageAdapter = CardAdapter()

        init {
            rvSuggestedReplies.layoutManager =
                LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            rvSuggestedReplies.adapter = suggestedRepliesAdapter
            rvSuggestedReplies.setHasFixedSize(true)

            rvSelectedReply.layoutManager =
                LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            rvSelectedReply.adapter = selectedReplyAdapter
            rvSelectedReply.setHasFixedSize(true)
            rvMessage.layoutManager =
                LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)

            rvMessage.adapter = messageAdapter
        }

        fun bind(item: AacMessageListItemDto) {
            val context = itemView.context
            val hasSuggestedReplies = item.suggestedReplies.isNotEmpty()
            val isRequestOnly = !hasSuggestedReplies

            tvCreatedAt.text = TimeFormat.dateTime(item.createdAt)

            if (isRequestOnly) {
                tvMode.visibility = View.GONE
            } else {
                tvMode.visibility = View.VISIBLE
                tvMode.text = when (item.mode) {
                    "SEQUENCE" -> context.getString(R.string.compose_mode_sequence)
                    else -> context.getString(R.string.compose_mode_normal)
                }
            }

            messageAdapter.submitItems(item.message)

            rvMessage.visibility =
                if (isRequestOnly && item.message.isNotEmpty()) View.VISIBLE else View.GONE

            if (isRequestOnly && item.message.isNotEmpty()) {
                tvMessageLabel.text = context.getString(R.string.request)
                tvMessageLabel.visibility = View.VISIBLE
            } else {
                tvMessageLabel.visibility = View.GONE
            }

            suggestedRepliesAdapter.submitItems(
                item.suggestedReplies.map { it.toCardDto() }
            )

            tvSuggestedRepliesLabel.visibility =
                if (hasSuggestedReplies) View.VISIBLE else View.GONE

            rvSuggestedReplies.visibility =
                if (hasSuggestedReplies) View.VISIBLE else View.GONE

            val selectedReply = item.reply?.reply

            if (hasSuggestedReplies && selectedReply != null) {
                tvSelectedReplyLabel.text = context.getString(R.string.selected_reply)
                tvSelectedReplyLabel.visibility = View.VISIBLE
                rvSelectedReply.visibility = View.VISIBLE
                selectedReplyAdapter.submitItems(selectedReply)
            } else {
                tvSelectedReplyLabel.visibility = View.GONE
                rvSelectedReply.visibility = View.GONE
                selectedReplyAdapter.submitItems(emptyList())
            }

            btnRepeat.visibility =
                if (hasSuggestedReplies) View.VISIBLE else View.GONE

            btnRepeat.setOnClickListener {
                onRepeatClick(item)
            }
        }
    }
}