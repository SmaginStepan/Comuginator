package com.example.comuginator.ui.family

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

class FamilyAdapter(
    private val isParentViewer: Boolean,
    private val myDeviceId: String,
    private val authToken: String,
    private val onVolumeClick: (deviceId: String, deviceName: String, currentVolumePercent: Int?) -> Unit,
    private val onSendClick: (userId: String, userName: String) -> Unit,
    private val onHistoryClick: (userId: String, userName: String) -> Unit,
    private val onRenameUserClick: (userId: String, userName: String) -> Unit,
    private val onRenameDeviceClick: (deviceId: String, deviceName: String) -> Unit,
    private val onSetAvatarClick: (userId: String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
        private val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        private val tvUserHeader: TextView = view.findViewById(R.id.tvUserHeader)
        private val btnSend: Button = view.findViewById(R.id.btnSend)
        private val btnHistory: Button = view.findViewById(R.id.btnHistory)
        private val btnRenameUser: Button = view.findViewById(R.id.btnRenameUser)
        private val btnSetAvatar: Button = view.findViewById(R.id.btnSetAvatar)

        fun bind(item: FamilyListItem.UserHeader) {
            tvUserHeader.text = "${item.userName} [${item.role}]"

            val url = item.avatarImageUrl
            if (!url.isNullOrBlank()) {
                val request = ImageRequest.Builder(itemView.context)
                    .data(url)
                    .addHeader("Authorization", authToken)
                    .target(ivAvatar)
                    .build()

                itemView.context.imageLoader.enqueue(request)
            } else {
                ivAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            btnSend.setOnClickListener { onSendClick(item.userId, item.userName) }
            btnHistory.setOnClickListener { onHistoryClick(item.userId, item.userName) }

            btnRenameUser.visibility = if (isParentViewer) View.VISIBLE else View.GONE
            btnRenameUser.setOnClickListener { onRenameUserClick(item.userId, item.userName) }

            btnSetAvatar.visibility = if (isParentViewer) View.VISIBLE else View.GONE
            btnSetAvatar.setOnClickListener { onSetAvatarClick(item.userId) }
        }
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        private val tvDeviceMeta: TextView = view.findViewById(R.id.tvDeviceMeta)
        private val btnVolume: Button = view.findViewById(R.id.btnVolume)
        private val btnRenameDevice: Button = view.findViewById(R.id.btnRenameDevice)

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

            btnRenameDevice.visibility = if (isParentViewer) View.VISIBLE else View.GONE
            btnRenameDevice.setOnClickListener { onRenameDeviceClick(item.deviceId, item.deviceName) }
        }
    }
}