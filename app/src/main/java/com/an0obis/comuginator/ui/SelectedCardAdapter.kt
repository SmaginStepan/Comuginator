package com.an0obis.comuginator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import coil.ImageLoader
import coil.request.ImageRequest
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.base.BaseAdapter

class SelectedCardAdapter(
    private val onClick: (AacCardDto) -> Unit
) : BaseAdapter<AacCardDto, SelectedCardAdapter.CardViewHolder>() {

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

            val sessionStore = SessionStore(itemView.context)

            val requestBuilder = ImageRequest.Builder(itemView.context)
                .data(item.imageUrl)
                .target(ivCardImage)
                .crossfade(true)

            if (sessionStore.isConnected() && item.source == "FAMILY_PHOTO") {
                requestBuilder.addHeader("Authorization", sessionStore.authHeaderOrThrow())
            }

            ImageLoader(itemView.context).enqueue(requestBuilder.build())

            itemView.setOnClickListener { onClick(item) }
        }
    }
}