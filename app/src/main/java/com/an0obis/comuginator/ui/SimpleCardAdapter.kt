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

class SimpleCardAdapter(
    private val onClick: (AacCardDto) -> Unit = {},
    private val alphaProvider: ((AacCardDto) -> Float)? = null
) : BaseAdapter<AacCardDto, SimpleCardAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = items[position]
        holder.bind(items[position])
        holder.itemView.alpha =
            alphaProvider?.invoke(item) ?: 1f
    }

    override fun getItemCount(): Int = items.size

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivCardImage: ImageView = view.findViewById(R.id.ivCardImage)
        private val tvCardLabel: TextView = view.findViewById(R.id.tvCardLabel)

        fun bind(item: AacCardDto) {
            tvCardLabel.text = item.label

            if (item.source == "WAIT") {
                ivCardImage.setImageResource(R.drawable.ic_timer_large)
                tvCardLabel.text = item.label
                itemView.setOnClickListener { onClick(item) }
                return
            }

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