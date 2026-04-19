package com.example.comuginator.ui.library

import com.example.comuginator.api.AacCardDto
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.example.comuginator.R

class LibraryPickerItemsAdapter(
    private val authToken: String,
    private val onClick: (AacCardDto) -> Unit
) : RecyclerView.Adapter<LibraryPickerItemsAdapter.VH>() {

    private val items = mutableListOf<AacCardDto>()

    fun submit(list: List<AacCardDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivImage = v.findViewById<ImageView>(R.id.ivImage)
        val tvLabel = v.findViewById<TextView>(R.id.tvLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_picker_card, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvLabel.text = item.label

        val url = item.imageUrl
        if (!url.isNullOrBlank()) {
            val request = ImageRequest.Builder(holder.itemView.context)
                .data(url)
                .addHeader("Authorization", authToken)
                .target(holder.ivImage)
                .build()
            holder.itemView.context.imageLoader.enqueue(request)
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}