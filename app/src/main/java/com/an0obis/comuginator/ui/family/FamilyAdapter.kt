package com.an0obis.comuginator.ui.family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.request.ImageRequest
import com.an0obis.comuginator.R

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
        private val btnUserMore: Button = view.findViewById(R.id.btnUserMore)

        fun bind(item: FamilyListItem.UserHeader) {
            tvUserHeader.text = itemView.context.getString(
                R.string.user_header,
                item.userName,
                item.role
            )

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

            btnUserMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                val renameId = 1
                val avatarId = 2
                popup.menu.add(0, renameId, 0, view.context.getString(R.string.rename))
                popup.menu.add(0, avatarId, 1,view.context.getString(R.string.avatar))

                popup.setOnMenuItemClickListener { btn ->
                    when (btn.itemId) {
                        renameId  -> {
                            onRenameUserClick(item.userId, item.userName)
                            true
                        }
                        avatarId -> {
                            onSetAvatarClick(item.userId)
                            true
                        }
                        else -> false
                    }
                }

                popup.show()
            }
        }
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        private val tvDeviceMeta: TextView = view.findViewById(R.id.tvDeviceMeta)
        private val btnVolume: Button = view.findViewById(R.id.btnVolume)
        private val btnDeviceMore: Button = view.findViewById(R.id.btnDeviceMore)

        fun bind(item: FamilyListItem.DeviceRow) {
            tvDeviceName.text = item.deviceName
            val context = itemView.context
            val battery = item.batteryPercent?.toString()?.plus("%") ?: "?"
            val charging = when (item.isCharging) {
                true -> context.getString(R.string.charging)
                false -> context.getString(R.string.not_charging)
                null -> context.getString(R.string.charging_unknown)
            }
            val volume = item.volumePercent?.toString()?.plus("%") ?: "?"

            val lastSeen = item.lastSeenAt ?: context.getString(R.string.never)

            tvDeviceMeta.text = context.getString(
                R.string.device_meta,
                battery,
                charging,
                volume,
                lastSeen
            )

            val canControlVolume =
                isParentViewer &&
                        item.userRole == "CHILD" &&
                        item.deviceId != myDeviceId

            btnVolume.visibility = if (canControlVolume) View.VISIBLE else View.GONE

            btnVolume.setOnClickListener {
                onVolumeClick(item.deviceId, item.deviceName, item.volumePercent)
            }

            btnDeviceMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                val renameId = 1

                popup.menu.add(0, renameId, 0, view.context.getString(R.string.rename))

                popup.setOnMenuItemClickListener { btn ->
                    when (btn.itemId) {
                        renameId -> {
                            onRenameDeviceClick(item.deviceId, item.deviceName)
                            true
                        }
                        else -> false
                    }
                }

                popup.show()
            }
        }
    }
}