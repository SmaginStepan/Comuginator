package com.an0obis.comuginator.ui.conversation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.dispose
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient

/**
 * Shows [ConvCell]s as compact picture cards. Used for the library grid, the
 * horizontal line of composed cards and the ARASAAC search results; only the
 * item layout differs.
 */
class ConversationCardAdapter(
    private val authToken: String,
    @LayoutRes private val layoutRes: Int,
    private val onClick: (cell: ConvCell, position: Int) -> Unit
) : RecyclerView.Adapter<ConversationCardAdapter.VH>() {

    private var items: List<ConvCell> = emptyList()

    fun submit(newItems: List<ConvCell>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(root: View) : RecyclerView.ViewHolder(root) {
        val image: ImageView = root.findViewById(R.id.ivCard)
        val label: TextView = root.findViewById(R.id.tvCardLabel)
        val folderBadge: View? = root.findViewById(R.id.ivFolderBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(layoutRes, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cell = items[position]

        holder.label.text = cell.label
        holder.itemView.isActivated = cell.isFolder   // selects the folder background
        holder.folderBadge?.visibility = if (cell.isFolder) View.VISIBLE else View.GONE

        val url = cell.imageUrl
        if (url.isNullOrBlank()) {
            holder.image.dispose()
            holder.image.setImageDrawable(null)
        } else {
            val request = ImageRequest.Builder(holder.itemView.context)
                .data(url)
                .apply {
                    // Never send our token to third-party hosts (ARASAAC).
                    if (ApiClient.isProtectedImageUrl(url)) {
                        addHeader("Authorization", "Bearer $authToken")
                    }
                }
                .target(holder.image)
                .build()
            Coil.imageLoader(holder.itemView.context).enqueue(request)
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(items[pos], pos)
        }
    }
}
