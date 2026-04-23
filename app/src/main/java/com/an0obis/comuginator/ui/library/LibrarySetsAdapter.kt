package com.an0obis.comuginator.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.LibrarySetDto

class LibrarySetsAdapter(
    private val authToken: String,
    private val onClick: (LibrarySetDto) -> Unit
) : RecyclerView.Adapter<LibrarySetsAdapter.VH>() {

    private val items = mutableListOf<LibrarySetDto>()

    fun submit(list: List<LibrarySetDto>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivCover = v.findViewById<ImageView>(R.id.ivCover)
        val tvName = v.findViewById<TextView>(R.id.tvName)
        val tvCount = v.findViewById<TextView>(R.id.tvCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_set, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvName.text = item.name
        holder.tvCount.text = "${item.itemsCount} items"

        val url = item.cover?.imageUrl

        if (!url.isNullOrBlank()) {
            val request = ImageRequest.Builder(holder.itemView.context)
                .data(url)
                .addHeader("Authorization", authToken)
                .target(holder.ivCover)
                .build()

            holder.ivCover.context.imageLoader.enqueue(request)
        } else {
            holder.ivCover.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}