package com.an0obis.comuginator.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import coil.Coil
import coil.request.ImageRequest
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.ui.base.BaseAdapter

class ChildHomeAdapter(
    private val authToken: String,
    private var isEditorMode: Boolean,
    private val onNodeClick: (ChildHomeNodeDto) -> Unit,
    private val onRenameClick: (ChildHomeNodeDto) -> Unit,
    private val onEditClick: (ChildHomeNodeDto) -> Unit,
    private val onDeleteClick: (ChildHomeNodeDto) -> Unit,
    private val onToggleVisibilityClick: (ChildHomeNodeDto) -> Unit
) : BaseAdapter<ChildHomeNodeDto, ChildHomeAdapter.VH>() {

    private var onlyVisibleNodeId: String? = null

    fun setEditorMode(value: Boolean) {
        if (isEditorMode == value) return
        isEditorMode = value
        notifyItemRangeChanged(0, itemCount)
    }

    fun showOnlyNode(nodeId: String?) {
        onlyVisibleNodeId = nodeId
        notifyItemRangeChanged(0, itemCount)
    }

    fun updateNodeVisibility(nodeId: String, isVisible: Boolean) {
        val index = items.indexOfFirst { it.id == nodeId }
        if (index == -1) return

        val old = items[index]
        items[index] = old.copy(isVisible = isVisible)

        notifyItemChanged(index)
    }

    class VH(root: ViewGroup) : RecyclerView.ViewHolder(root) {
        val ivNode: android.widget.ImageView = root.findViewById(R.id.ivNode)
        val tvNodeLabel: android.widget.TextView = root.findViewById(R.id.tvNodeLabel)
        val rootNode: View = root.findViewById(R.id.rootNode)
        val btnChildMore: Button = root.findViewById(R.id.btnChildMore)
        val btnToggleVisibility: Button = itemView.findViewById(R.id.btnToggleVisibility)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child_home_node, parent, false) as ViewGroup
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = items[position]

        holder.tvNodeLabel.text =
            node.labelOverride ?: node.item?.label ?: node.type
        holder.btnChildMore.visibility = if (isEditorMode) View.VISIBLE else View.GONE
        val shouldHideByBlink = onlyVisibleNodeId != null && onlyVisibleNodeId != node.id
        val nodeAlpha = if (isEditorMode && !node.isVisible) 0.35f else 1f
        Log.d("ChildHomeAdapter", "onBindViewHolder: $position $nodeAlpha")

        holder.itemView.clearAnimation()
        holder.rootNode.clearAnimation()

        holder.itemView.visibility = if (shouldHideByBlink) View.INVISIBLE else View.VISIBLE
        holder.itemView.alpha = 1f
        holder.rootNode.alpha = nodeAlpha

        holder.btnChildMore.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            val renameId = 1
            val editId = 2
            val deleteId = 3
            popup.menu.add(0, renameId, 0, view.context.getString(R.string.rename))
            popup.menu.add(0, editId, 0, view.context.getString(R.string.edit))
            popup.menu.add(0, deleteId, 1,view.context.getString(R.string.delete))

            popup.setOnMenuItemClickListener { btn ->
                when (btn.itemId) {
                    renameId  -> {
                        onRenameClick(node)
                        true
                    }
                    editId  -> {
                        onEditClick(node)
                        true
                    }
                    deleteId -> {
                        onDeleteClick(node)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

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
        holder.btnToggleVisibility.visibility =
            if (isEditorMode) View.VISIBLE else View.GONE

        holder.btnToggleVisibility.text =
            holder.itemView.context.getString(
                if (node.isVisible) R.string.hide else R.string.show
            )

        holder.btnToggleVisibility.setOnClickListener {
            onToggleVisibilityClick(node)
        }
    }
}