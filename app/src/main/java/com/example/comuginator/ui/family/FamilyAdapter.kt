package com.example.comuginator.ui.family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.comuginator.R


class FamilyAdapter(
    private val isParentViewer: Boolean,
    private val myDeviceId: String,
    private val onVolumeClick: (deviceId: String, deviceName: String, currentVolumePercent: Int?) -> Unit,
    private val onSendClick: (userId: String, userName: String) -> Unit,
    private val onHistoryClick: (userId: String, userName: String) -> Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FamilyListItem>()

    fun submitItems(newItems: List<FamilyListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FamilyListItem.UserHeader -> 0
            is FamilyListItem.DeviceRow -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val view = inflater.inflate(R.layout.item_family_user_header, parent, false)
                UserHeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_family_device, parent, false)
                DeviceViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FamilyListItem.UserHeader -> (holder as UserHeaderViewHolder).bind(item)
            is FamilyListItem.DeviceRow -> (holder as DeviceViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class UserHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvUserHeader: TextView = view.findViewById(R.id.tvUserHeader)
        private val btnSend: Button = view.findViewById(R.id.btnSend)

        private val btnHistory: Button = view.findViewById(R.id.btnHistory)

        fun bind(item: FamilyListItem.UserHeader) {
            tvUserHeader.text = "${item.userName} [${item.role}]"
            btnSend.setOnClickListener { onSendClick(item.userId, item.userName) }
            btnHistory.setOnClickListener { onHistoryClick(item.userId, item.userName) }
        }
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        private val tvDeviceMeta: TextView = view.findViewById(R.id.tvDeviceMeta)
        private val btnVolume: Button = view.findViewById(R.id.btnVolume)

        fun bind(item: FamilyListItem.DeviceRow) {
            tvDeviceName.text = item.deviceName

            val battery = item.batteryPercent?.toString()?.plus("%") ?: "?"
            val charging = when (item.isCharging) {
                true -> "charging"
                false -> "not charging"
                null -> "charging?"
            }
            val lastSeen = item.lastSeenAt ?: "never"
            val volume = item.volumePercent?.toString()?.plus("%") ?: "?"

            tvDeviceMeta.text = "Battery: $battery · $charging · Volume: $volume · Last seen: $lastSeen"

            val canControlVolume =
                isParentViewer &&
                        item.userRole == "CHILD" &&
                        item.deviceId != myDeviceId

            btnVolume.visibility = if (canControlVolume) View.VISIBLE else View.GONE

            btnVolume.setOnClickListener {
                onVolumeClick(item.deviceId, item.deviceName, item.volumePercent)
            }
        }
    }
}