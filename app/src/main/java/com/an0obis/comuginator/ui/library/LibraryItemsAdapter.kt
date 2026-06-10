package com.an0obis.comuginator.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.ui.base.BaseAdapter

class LibraryItemsVH(v: View) : RecyclerView.ViewHolder(v) {
    val ivImage: ImageView = v.findViewById(R.id.ivImage)
    val tvLabel: TextView = v.findViewById(R.id.tvLabel)
    val btnRenameItem: Button = v.findViewById(R.id.btnRenameItem)
    val btnDeleteItem: Button = v.findViewById(R.id.btnDeleteItem)
}

class LibraryItemsAdapter(
    private val authToken: String,
    private val onDeleteItemClick: (AacCardDto) -> Unit,
    private val onRenameItemClick: (AacCardDto) -> Unit = {}
) : BaseAdapter<AacCardDto, LibraryItemsVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryItemsVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_card, parent, false)
        return LibraryItemsVH(view)
    }

    override fun onBindViewHolder(holder: LibraryItemsVH, position: Int) {
        val item = items[position]

        holder.tvLabel.text = item.label
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

        holder.btnRenameItem.setOnClickListener {
            onRenameItemClick(item)
        }

        holder.btnDeleteItem.setOnClickListener {
            onDeleteItemClick(item)
        }
    }
}
