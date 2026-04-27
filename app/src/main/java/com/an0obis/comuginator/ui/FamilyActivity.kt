package com.an0obis.comuginator.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CreateCommandRequest
import com.an0obis.comuginator.api.CreateInviteRequest
import com.an0obis.comuginator.api.FamilyMeResponse
import com.an0obis.comuginator.api.UserDto
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.api.UpdateNameRequest
import com.an0obis.comuginator.ui.family.FamilyAdapter
import com.an0obis.comuginator.ui.family.FamilyListItem
import com.an0obis.comuginator.api.UpdateMyAvatarRequest
import android.app.Activity
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import com.an0obis.comuginator.service.CommandSyncScheduler

class FamilyActivity : BaseActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var openingIncomingMessageId: String? = null
    private lateinit var store: SessionStore

    private lateinit var tvFamily: TextView
    private lateinit var tvInvite: TextView
    private lateinit var rvFamily: RecyclerView
    private lateinit var familyAdapter: FamilyAdapter
    private var currentMeRole: String = ""
    private var currentMyDeviceId: String = ""
    private lateinit var tvStatus: TextView
    private lateinit var btnFamilyAdd: Button
    private lateinit var btnFamilyMore: Button
    private lateinit var btnLibrary: Button
    private lateinit var btnChildHome: Button
    private var pendingAvatarUserId: String? = null

    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val userId = pendingAvatarUserId
            pendingAvatarUserId = null

            if (result.resultCode == Activity.RESULT_OK) {
                val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                if (!userId.isNullOrBlank() && !itemId.isNullOrBlank()) {
                    updateUserAvatar(userId, itemId)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family)

        store = SessionStore(this)

        tvFamily = findViewById(R.id.tvFamily)
        tvInvite = findViewById(R.id.tvInvite)
        rvFamily = findViewById(R.id.rvFamily)
        btnLibrary = findViewById(R.id.btnLibrary)

        btnLibrary.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        btnChildHome = findViewById(R.id.btnChildHome)
        btnChildHome.setOnClickListener {
            val intent = Intent(this, ChildHomeActivity::class.java)
            intent.putExtra(ChildHomeActivity.EXTRA_EDITOR_MODE, true)
            startActivity(intent)
        }

        familyAdapter = FamilyAdapter(
            isParentViewer = currentMeRole == "PARENT",
            myDeviceId = currentMyDeviceId,
            authToken = authHeaderOrThrow(),
            onVolumeClick = { deviceId, deviceName, currentVolumePercent ->
                showSetVolumeDialog(deviceId, deviceName, currentVolumePercent)
            },
            onSendClick = { userId, userName ->
                openComposeMessageScreen(userId, userName)
            },
            onHistoryClick = { userId, userName ->
                openMessageHistoryScreen(userId, userName)
            },
            onRenameUserClick = { userId, userName ->
                showRenameDialog(
                    title = "Rename user",
                    initialValue = userName,
                    onApply = { newName -> updateUserName(userId, newName) }
                )
            },
            onRenameDeviceClick = { deviceId, deviceName ->
                showRenameDialog(
                    title = "Rename device",
                    initialValue = deviceName,
                    onApply = { newName -> updateDeviceName(deviceId, newName) }
                )
            },
            onSetAvatarClick = { userId ->
                showChooseAvatarDialog(userId)
            }
        )
        btnFamilyAdd = findViewById(R.id.btnFamilyAdd)
        btnFamilyAdd.setOnClickListener { view ->

            val popup = PopupMenu(view.context, view)
            popup.menu.add("Parent")
            popup.menu.add("Child")
            popup.setOnMenuItemClickListener { btn ->
                when (btn.title.toString()) {
                    "Parent" -> {
                        createInvite("PARENT")
                        true
                    }
                    "Child" -> {
                        createInvite("CHILD")
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
        btnFamilyMore = findViewById(R.id.btnFamilyMore)
        btnFamilyMore.setOnClickListener {view ->

            val popup = PopupMenu(view.context, view)
            popup.menu.add("Rename")
            popup.menu.add("Refresh")
            popup.menu.add("Send heartbeat")
            popup.menu.add("Settings")

            popup.setOnMenuItemClickListener { btn ->
                when (btn.title.toString()) {
                    "Rename" -> {
                        showRenameDialog(
                            title = "Rename family",
                            initialValue = tvFamily.text.toString().removePrefix("Family: ").trim(),
                            onApply = { newName -> updateFamilyName(newName) }
                        )
                        true
                    }
                    "Settings" -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }
                    "Refresh" -> {
                        loadFamily()
                        true
                    }
                    "Send heartbeat" -> {
                        sendHeartbeat()
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }

        rvFamily.layoutManager = LinearLayoutManager(this)
        rvFamily.adapter = familyAdapter
        tvStatus = findViewById(R.id.tvStatus)

        ensureInitialized()
    }

    private fun showRenameDialog(
        title: String,
        initialValue: String,
        onApply: (String) -> Unit
    ) {
        val editText = android.widget.EditText(this).apply {
            setText(initialValue)
            setSelection(text.length)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val value = editText.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    onApply(value)
                }
            }
            .show()
    }

    private fun updateFamilyName(name: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Updating family name..."
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateMyFamily(
                    auth = authHeaderOrThrow(),
                    body = UpdateNameRequest(name = name)
                )

                runOnUiThread {
                    tvStatus.text = "Family name updated"
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Update family failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun updateUserName(userId: String, name: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Updating user name..."
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateUserName(
                    auth = authHeaderOrThrow(),
                    userId = userId,
                    body = UpdateNameRequest(name)
                )

                runOnUiThread {
                    tvStatus.text = "User name updated"
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Update user failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun updateDeviceName(deviceId: String, name: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Updating device name..."
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateDeviceName(
                    auth = authHeaderOrThrow(),
                    deviceId = deviceId,
                    body = UpdateNameRequest(name)
                )

                runOnUiThread {
                    tvStatus.text = "Device name updated"
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Update device failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun openComposeMessageScreen(targetUserId: String, targetUserName: String) {
        val intent = Intent(this, ComposeMessageActivity::class.java).apply {
            putExtra("targetUserId", targetUserId)
            putExtra("targetUserName", targetUserName)
        }
        startActivity(intent)
    }

    override fun onInitialized() {
        loadFamily()
    }

    private fun sendHeartbeat() {
        CommandSyncScheduler.enqueueImmediate(this, "manual_test")
    }

    private fun authHeaderOrThrow(): String {
        val token = store.token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnFamilyAdd.isEnabled = enabled
        btnFamilyMore.isEnabled = enabled
    }

    private fun loadFamily() {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Loading family..."
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.getMyFamily(authHeaderOrThrow())

                runOnUiThread {
                    renderFamily(response)
                    tvStatus.text = "Family loaded"
                    setButtonsEnabled(true)
                }

                checkPendingIncomingMessages()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Load family failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun createInvite(role: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Creating $role invite..."
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.createInvite(
                    auth = authHeaderOrThrow(),
                    body = CreateInviteRequest(
                        role = role,
                        expiresInMinutes = 60
                    )
                )

                runOnUiThread {
                    tvInvite.text = buildString {
                        append("Invite code: ${response.code}\n")
                        append("Role: $role\n")
                        append("Expires: ${response.expiresAt}")
                    }
                    tvStatus.text = "Invite created"
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Create invite failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun mapFamilyItems(users: List<UserDto>): List<FamilyListItem> {
        val out = mutableListOf<FamilyListItem>()

        for (user in users) {
            out += FamilyListItem.UserHeader(
                userId = user.id,
                userName = user.name ?: "(no name)",
                role = user.role,
                avatarImageUrl = user.avatarImageUrl
            )

            for (device in user.devices) {
                out += FamilyListItem.DeviceRow(
                    userId = user.id,
                    userRole = user.role,
                    deviceId = device.deviceId,
                    deviceName = device.name ?: device.deviceId,
                    batteryPercent = device.state?.batteryPercent,
                    isCharging = device.state?.isCharging,
                    lastSeenAt = device.lastSeenAt,
                    volumePercent = device.state?.volumePercent
                )
            }
        }

        return out
    }

    private fun renderFamily(response: FamilyMeResponse) {
        val familyName = response.family.name ?: "(no name)"
        tvFamily.text = "Family: $familyName"

        currentMeRole = response.me.role
        currentMyDeviceId = response.me.deviceId

        tvStatus.text = buildString {
            append("Me:\n")
            append("role=${response.me.role}\n")
            append("userId=${response.me.userId}\n")
            append("deviceId=${response.me.deviceId}")
        }

        familyAdapter = FamilyAdapter(
            isParentViewer = currentMeRole == "PARENT",
            myDeviceId = currentMyDeviceId,
            authToken = authHeaderOrThrow(),
            onVolumeClick = { deviceId, deviceName, currentVolumePercent ->
                showSetVolumeDialog(deviceId, deviceName, currentVolumePercent)
            },
            onSendClick = { userId, userName ->
                openComposeMessageScreen(userId, userName)
            },
            onHistoryClick = { userId, userName ->
                openMessageHistoryScreen(userId, userName)
            },
            onRenameUserClick = { userId, userName ->
                showRenameDialog(
                    title = "Rename user",
                    initialValue = userName,
                    onApply = { newName -> updateUserName(userId, newName) }
                )
            },
            onRenameDeviceClick = { deviceId, deviceName ->
                showRenameDialog(
                    title = "Rename device",
                    initialValue = deviceName,
                    onApply = { newName -> updateDeviceName(deviceId, newName) }
                )
            },
            onSetAvatarClick = { userId ->
                showChooseAvatarDialog(userId)
            }
        )

        rvFamily.adapter = familyAdapter
        familyAdapter.submitItems(mapFamilyItems(response.users))
    }

    private fun openMessageHistoryScreen(targetUserId: String, targetUserName: String) {
        val intent = Intent(this, UserMessageHistoryActivity::class.java).apply {
            putExtra("targetUserId", targetUserId)
            putExtra("targetUserName", targetUserName)
        }
        startActivity(intent)
    }

    private fun sendSetVolumeCommand(deviceId: String, volumePercent: Int) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Sending volume command..."
                    setButtonsEnabled(false)
                }

                ApiClient.api.createCommand(
                    auth = authHeaderOrThrow(),
                    deviceId = deviceId,
                    body = CreateCommandRequest(
                        type = "set_volume",
                        payload = mapOf("volumePercent" to volumePercent)
                    )
                )

                runOnUiThread {
                    tvStatus.text = "Volume command sent: $volumePercent%"
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Failed to send volume command: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun showChooseAvatarDialog(userId: String) {
        pendingAvatarUserId = userId

        val intent = LibraryItemPickerActivity.createIntent(
            context = this,
            targetMode = LibraryItemPickerActivity.TargetMode.USER_AVATAR
        )

        avatarPickerLauncher.launch(intent)
    }

    private fun updateUserAvatar(userId: String, avatarItemId: String?) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Updating avatar..."
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateUserAvatar(
                    auth = authHeaderOrThrow(),
                    userId = userId,
                    body = UpdateMyAvatarRequest(
                        avatarItemId = avatarItemId
                    )
                )

                runOnUiThread {
                    tvStatus.text = "Avatar updated"
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Update avatar failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun showSetVolumeDialog(
        deviceId: String,
        deviceName: String,
        currentVolumePercent: Int?
    ) {
        val initialValue = (currentVolumePercent ?: 50).coerceIn(0, 100)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }

        val valueText = TextView(this).apply {
            text = "Volume: $initialValue%"
            textSize = 16f
        }

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = initialValue

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = "Volume: $progress%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        container.addView(valueText)
        container.addView(seekBar)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set volume: $deviceName")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ ->
                sendSetVolumeCommand(deviceId, seekBar.progress)
            }
            .show()
    }

    private fun checkPendingIncomingMessages() {
        scope.launch {
            try {
                val auth = authHeaderOrThrow()

                val pending = ApiClient.api.getPendingCommands(auth)
                val inbox = ApiClient.api.getAacMessages(auth = auth, scope = "inbox")

                val pendingMap = pending.items
                    .asSequence()
                    .filter { it.status == "queued" && it.type == "aac_message_available" }
                    .mapNotNull { cmd ->
                        val messageId = cmd.payload["messageId"] as? String
                        if (messageId != null) messageId to cmd.id else null
                    }
                    .toMap()

                val preferred = inbox.items.firstOrNull { msg ->
                    msg.reply == null && msg.suggestedReplies.isNotEmpty()
                }

                val fallback = inbox.items.firstOrNull { msg ->
                    msg.reply == null &&
                            msg.suggestedReplies.isEmpty() &&
                            pendingMap.containsKey(msg.id)
                }

                val target = preferred ?: fallback ?: return@launch

                if (openingIncomingMessageId == target.id) return@launch
                openingIncomingMessageId = target.id

                val commandId = pendingMap[target.id].orEmpty()

                runOnUiThread {
                    openIncomingMessage(target.id, commandId)
                }
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = "Check incoming message failed: ${e.message}"
                }
            }
        }
    }

    private fun openIncomingMessage(messageId: String, commandId: String) {
        val intent = Intent(this, IncomingMessageActivity::class.java).apply {
            putExtra(IncomingMessageActivity.EXTRA_MESSAGE_ID, messageId)
            putExtra(IncomingMessageActivity.EXTRA_COMMAND_ID, commandId)
        }
        startActivity(intent)
    }
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}