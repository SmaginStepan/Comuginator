package com.an0obis.comuginator.ui.family

import android.content.Context
import android.util.Log
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
import com.an0obis.comuginator.ui.base.BaseAdapter
import com.an0obis.comuginator.util.TimeFormat
import java.text.DateFormat
import java.util.Calendar

class FamilyAdapter(
    private val isParentViewer: Boolean,
    private val myDeviceId: String,
    private val authToken: String,
    private val onVolumeClick: (deviceId: String, deviceName: String, currentVolumePercent: Int?) -> Unit,
    private val onSendClick: (userId: String, userName: String) -> Unit,
    private val onHistoryClick: (userId: String, userName: String) -> Unit,
    private val onRenameUserClick: (userId: String, userName: String) -> Unit,
    private val onRenameDeviceClick: (deviceId: String, deviceName: String) -> Unit,
    private val onDeleteDeviceClick: (deviceId: String, deviceName: String) -> Unit,
    private val onSetAvatarClick: (userId: String) -> Unit,
    private val onDeleteUserClick: (userId: String, userName: String) -> Unit
) : BaseAdapter<FamilyListItem, RecyclerView.ViewHolder>() {


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
            fun getRole(role: String): String {
                return when (role) {
                    "PARENT" -> itemView.context.getString(R.string.role_parent)
                    "CHILD" -> itemView.context.getString(R.string.role_child)
                    else -> { "" }
                }
            }
            tvUserHeader.text = itemView.context.getString(
                R.string.user_header,
                item.userName,
                getRole(item.role)
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
                val deleteUserId = 3
                popup.menu.add(0, renameId, 0, view.context.getString(R.string.rename))
                popup.menu.add(0, avatarId, 1,view.context.getString(R.string.avatar))
                popup.menu.add(0, deleteUserId, 2,view.context.getString(R.string.delete_user))

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
                        deleteUserId -> {
                            onDeleteUserClick(item.userId, item.userName)
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

        private fun formatLastSeen(iso: String?, context: Context): String {
            if (iso.isNullOrBlank()) {
                return context.getString(R.string.never_seen)
            }

            return try {
                val date = TimeFormat.parseUtc(iso) ?: return iso

                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    time = date
                }

                val timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT)
                val dateFormatter =
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

                when {
                    isSameDay(now, target) -> {
                        context.getString(
                            R.string.seen_today_at,
                            timeFormatter.format(date)
                        )
                    }

                    isYesterday(now, target) -> {
                        context.getString(
                            R.string.seen_yesterday_at,
                            timeFormatter.format(date)
                        )
                    }

                    else -> {
                        context.getString(
                            R.string.seen_at_date,
                            dateFormatter.format(date)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("FamilyAdapter", "failed",e)
                iso
            }
        }

        private fun isSameDay(a: Calendar, b: Calendar): Boolean {
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }

        private fun isYesterday(now: Calendar, target: Calendar): Boolean {
            val yesterday = now.clone() as Calendar
            yesterday.add(Calendar.DAY_OF_YEAR, -1)

            return isSameDay(yesterday, target)
        }

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

            val lastSeen = formatLastSeen(item.lastSeenAt, context)

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
                val deleteId = 2

                popup.menu.add(0, renameId, 0, view.context.getString(R.string.rename))
                popup.menu.add(0, deleteId, 1, view.context.getString(R.string.delete))

                popup.setOnMenuItemClickListener { btn ->
                    when (btn.itemId) {
                        renameId -> {
                            onRenameDeviceClick(item.deviceId, item.deviceName)
                            true
                        }
                        deleteId -> {
                            onDeleteDeviceClick(item.deviceId, item.deviceName)
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