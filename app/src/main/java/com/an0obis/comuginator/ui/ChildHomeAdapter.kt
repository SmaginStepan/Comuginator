package com.an0obis.comuginator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ChildHomeNodeDto

class ChildHomeAdapter(
    private val authToken: String,
    private val isEditorMode: Boolean,
    private val onNodeClick: (ChildHomeNodeDto) -> Unit,
    private val onEditClick: (ChildHomeNodeDto) -> Unit,
    private val onDeleteClick: (ChildHomeNodeDto) -> Unit
) : RecyclerView.Adapter<ChildHomeAdapter.VH>() {

    private val items = mutableListOf<ChildHomeNodeDto>()

    fun submitItems(newItems: List<ChildHomeNodeDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val ivNode: android.widget.ImageView = root.findViewById(R.id.ivNode)
        val tvNodeLabel: android.widget.TextView = root.findViewById(R.id.tvNodeLabel)
        val rootNode: View = root.findViewById(R.id.rootNode)
        val btnEdit: android.widget.Button = root.findViewById(R.id.btnEditNode)
        val btnDelete: android.widget.Button = root.findViewById(R.id.btnDeleteNode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child_home_node, parent, false) as ViewGroup
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = items[position]

        holder.tvNodeLabel.text = node.item?.label ?: node.type

        holder.btnEdit.visibility = if (isEditorMode) View.VISIBLE else View.GONE
        holder.btnDelete.visibility = if (isEditorMode) View.VISIBLE else View.GONE

        val imageUrl = node.item?.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(holder.itemView.context)
                .data(imageUrl)
                .addHeader("Authorization", "Bearer $authToken")
                .target(holder.ivNode)
                .build()

            Coil.imageLoader(holder.itemView.context).enqueue(request)
        } else {
            holder.ivNode.setImageDrawable(null)
        }

        holder.rootNode.setOnClickListener {
            onNodeClick(node)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(node)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(node)
        }
    }
}