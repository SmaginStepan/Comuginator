package com.example.comuginator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.comuginator.R
import com.example.comuginator.api.ArasaacCardDto

class SimpleCardAdapter(
    private val onClick: (ArasaacCardDto) -> Unit
) : RecyclerView.Adapter<SimpleCardAdapter.CardViewHolder>() {

    private val items = mutableListOf<ArasaacCardDto>()

    fun submitItems(newItems: List<ArasaacCardDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCardLabel: TextView = view.findViewById(R.id.tvCardLabel)

        fun bind(item: ArasaacCardDto) {
            tvCardLabel.text = item.label
            itemView.setOnClickListener { onClick(item) }
        }
    }
}