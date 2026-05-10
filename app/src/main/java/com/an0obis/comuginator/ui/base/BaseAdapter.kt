package com.an0obis.comuginator.ui.base

import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<TItem, TViewHolder : RecyclerView.ViewHolder>: RecyclerView.Adapter<TViewHolder>() {

    protected val items = mutableListOf<TItem>()

    override fun getItemCount(): Int = items.size

    fun readItems(): List<TItem> {
        return items.toList()
    }

    fun submitItems(newItems: List<TItem>) {
        val oldSize = items.size

        if (oldSize > 0) {
            items.clear()
            notifyItemRangeRemoved(0, oldSize)
        } else {
            items.clear()
        }

        items.addAll(newItems)

        if (items.isNotEmpty()) {
            notifyItemRangeInserted(0, items.size)
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return

        val item = items.removeAt(from)
        items.add(to, item)

        notifyItemMoved(from, to)
    }

}