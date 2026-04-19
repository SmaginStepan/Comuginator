package com.example.comuginator.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.example.comuginator.R
import com.example.comuginator.api.AacCardDto

class LibraryItemsAdapter(
    private val authToken: String,
    private val onRemoveFromSetClick: (AacCardDto) -> Unit,
    private val onDeleteItemClick: (AacCardDto) -> Unit
) : RecyclerView.Adapter<LibraryItemsAdapter.VH>() {

    private val items = mutableListOf<AacCardDto>()

    fun submit(list: List<AacCardDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivImage = v.findViewById<ImageView>(R.id.ivImage)
        val tvLabel = v.findViewById<TextView>(R.id.tvLabel)
        val tvSource = v.findViewById<TextView>(R.id.tvSource)
        val btnRemoveFromSet = v.findViewById<Button>(R.id.btnRemoveFromSet)
        val btnDeleteItem = v.findViewById<Button>(R.id.btnDeleteItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_card, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvLabel.text = item.label
        holder.tvSource.text = item.source ?: ""
        val url = item.imageUrl

        if (!url.isNullOrBlank()) {
            val request = ImageRequest.Builder(holder.itemView.context)
                .data(url)
                .addHeader("Authorization", authToken)
                .target(holder.ivImage)
                .build()

            holder.ivImage.context.imageLoader.enqueue(request)
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.btnRemoveFromSet.setOnClickListener {
            onRemoveFromSetClick(item)
        }

        holder.btnDeleteItem.setOnClickListener {
            onDeleteItemClick(item)
        }
    }
}