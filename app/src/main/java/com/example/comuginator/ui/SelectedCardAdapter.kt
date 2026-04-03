package com.example.comuginator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.comuginator.R
import com.example.comuginator.api.AacCardDto
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.comuginator.storage.SessionStore

class SelectedCardAdapter(
    private val onClick: (AacCardDto) -> Unit
) : RecyclerView.Adapter<SelectedCardAdapter.CardViewHolder>() {

    private val items = mutableListOf<AacCardDto>()

    fun submitItems(newItems: List<AacCardDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivCardImage: ImageView = view.findViewById(R.id.ivCardImage)
        private val tvCardLabel: TextView = view.findViewById(R.id.tvCardLabel)

        fun bind(item: AacCardDto) {
            tvCardLabel.text = item.label

            val token = SessionStore(itemView.context).token

            val requestBuilder = ImageRequest.Builder(itemView.context)
                .data(item.imageUrl)
                .target(ivCardImage)
                .crossfade(true)

            if (!token.isNullOrBlank() && item.source == "family_photo") {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            ImageLoader(itemView.context).enqueue(requestBuilder.build())

            itemView.setOnClickListener { onClick(item) }
        }
    }
}