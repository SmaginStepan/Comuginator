package com.example.comuginator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.comuginator.R
import com.example.comuginator.api.AacMessageListItemDto

class MessageHistoryAdapter(
    private val onRepeatClick: (AacMessageListItemDto) -> Unit
) : RecyclerView.Adapter<MessageHistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<AacMessageListItemDto>()

    fun submitItems(newItems: List<AacMessageListItemDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
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
        private val tvFrom: TextView = view.findViewById(R.id.tvFrom)
        private val tvCreatedAt: TextView = view.findViewById(R.id.tvCreatedAt)
        private val tvReply: TextView = view.findViewById(R.id.tvReply)
        private val rvCards: RecyclerView = view.findViewById(R.id.rvCards)
        private val btnRepeat: Button = view.findViewById(R.id.btnRepeat)

        private val cardsAdapter = SimpleCardAdapter { }

        init {
            rvCards.layoutManager =
                LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            rvCards.adapter = cardsAdapter
            rvCards.setHasFixedSize(true)
        }

        fun bind(item: AacMessageListItemDto) {
            val fromName = item.fromUser?.name ?: item.fromUserId
            tvFrom.text = "From: $fromName"
            tvCreatedAt.text = item.createdAt

            cardsAdapter.submitItems(item.message)

            tvReply.text = if (item.reply != null) {
                "Reply: ${item.reply.reply.label}"
            } else {
                "Reply: —"
            }

            btnRepeat.setOnClickListener {
                onRepeatClick(item)
            }
        }
    }
}