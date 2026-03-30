package com.example.comuginator.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.comuginator.R
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.CreateCommandRequest
import com.example.comuginator.api.CreateInviteRequest
import com.example.comuginator.api.FamilyMeResponse
import com.example.comuginator.api.UserDto
import com.example.comuginator.storage.SessionStore
import com.example.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.comuginator.ui.family.FamilyAdapter
import com.example.comuginator.ui.family.FamilyListItem

class FamilyActivity : BaseActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var openingIncomingMessageId: String? = null
    private lateinit var store: SessionStore

    private lateinit var tvFamily: TextView
    private lateinit var tvMe: TextView
    private lateinit var tvInvite: TextView
    private lateinit var rvFamily: RecyclerView
    private lateinit var familyAdapter: FamilyAdapter
    private var currentMeRole: String = ""
    private var currentMyDeviceId: String = ""
    private lateinit var tvStatus: TextView

    private lateinit var btnRefresh: Button
    private lateinit var btnInviteParent: Button
    private lateinit var btnInviteChild: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family)

        store = SessionStore(this)

        tvFamily = findViewById(R.id.tvFamily)
        tvMe = findViewById(R.id.tvMe)
        tvInvite = findViewById(R.id.tvInvite)
        rvFamily = findViewById(R.id.rvFamily)

        familyAdapter = FamilyAdapter(
            isParentViewer = currentMeRole == "PARENT",
            myDeviceId = currentMyDeviceId,
            onVolumeClick = { deviceId, deviceName, currentVolumePercent ->
                showSetVolumeDialog(deviceId, deviceName, currentVolumePercent)
            },
            onSendClick = { userId, userName ->
                openComposeMessageScreen(userId, userName)
            }
        )

        rvFamily.layoutManager = LinearLayoutManager(this)
        rvFamily.adapter = familyAdapter
        tvStatus = findViewById(R.id.tvStatus)

        btnRefresh = findViewById(R.id.btnRefresh)
        btnInviteParent = findViewById(R.id.btnInviteParent)
        btnInviteChild = findViewById(R.id.btnInviteChild)

        btnRefresh.setOnClickListener { loadFamily() }
        btnInviteParent.setOnClickListener { createInvite("PARENT") }
        btnInviteChild.setOnClickListener { createInvite("CHILD") }

        ensureInitialized()
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

    private fun authHeaderOrThrow(): String {
        val token = store.token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnRefresh.isEnabled = enabled
        btnInviteParent.isEnabled = enabled
        btnInviteChild.isEnabled = enabled
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
                role = user.role
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

        tvMe.text = buildString {
            append("Me:\n")
            append("role=${response.me.role}\n")
            append("userId=${response.me.userId}\n")
            append("deviceId=${response.me.deviceId}")
        }

        familyAdapter = FamilyAdapter(
            isParentViewer = currentMeRole == "PARENT",
            myDeviceId = currentMyDeviceId,
            onVolumeClick = { deviceId, deviceName, currentVolumePercent ->
                showSetVolumeDialog(deviceId, deviceName, currentVolumePercent)
            },
            onSendClick = { userId, userName ->
                openComposeMessageScreen(userId, userName)
            }
        )

        rvFamily.adapter = familyAdapter
        familyAdapter.submitItems(mapFamilyItems(response.users))
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
                val pending = ApiClient.api.getPendingCommands(authHeaderOrThrow())

                Log.d("AAC", "pending commands: ${pending.items}")
                pending.items.forEach {
                    Log.d("AAC", "cmd: ${it.type} ${it.payload}")
                }

                val cmd = pending.items.firstOrNull { item ->
                    item.status == "queued" &&
                            item.type == "aac_message_available" &&
                            item.payload["messageId"] is String
                } ?: return@launch

                val messageId = cmd.payload["messageId"] as? String ?: return@launch

                if (openingIncomingMessageId == messageId) return@launch
                openingIncomingMessageId = messageId

                runOnUiThread {
                    openIncomingMessage(messageId, cmd.id)
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