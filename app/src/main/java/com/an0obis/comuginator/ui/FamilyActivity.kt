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
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import com.an0obis.comuginator.service.CommandSyncScheduler
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.an0obis.comuginator.service.ACTION_INVITE_USED
import com.an0obis.comuginator.service.EXTRA_INVITE_ID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class FamilyActivity : BaseActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var familyRefreshJob: Job? = null
    private lateinit var tvFamily: TextView
    private lateinit var tvInvite: TextView
    private lateinit var ivInviteQr: ImageView
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

    private var shownInviteId: String? = null

    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val userId = pendingAvatarUserId
            pendingAvatarUserId = null

            if (result.resultCode == RESULT_OK) {
                val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                if (!userId.isNullOrBlank() && !itemId.isNullOrBlank()) {
                    updateUserAvatar(userId, itemId)
                }
            }
        }

    private val inviteUsedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val inviteId = intent?.getStringExtra(EXTRA_INVITE_ID)
            onInviteUsed(inviteId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return
        setContentView(R.layout.activity_family)

        store = SessionStore(this)

        tvFamily = findViewById(R.id.tvFamily)
        tvInvite = findViewById(R.id.tvInvite)
        ivInviteQr = findViewById(R.id.ivInviteQr)
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
                    title = getString(R.string.rename_user),
                    initialValue = userName,
                    onApply = { newName -> updateUserName(userId, newName) }
                )
            },
            onRenameDeviceClick = { deviceId, deviceName ->
                showRenameDialog(
                    title = getString(R.string.rename_device),
                    initialValue = deviceName,
                    onApply = { newName -> updateDeviceName(deviceId, newName) }
                )
            },
            onSetAvatarClick = { userId ->
                showChooseAvatarDialog(userId)
            },
            onDeleteDeviceClick = { deviceId, deviceName ->
                confirmDeleteDevice(deviceId, deviceName)
            }
        )
        btnFamilyAdd = findViewById(R.id.btnFamilyAdd)
        btnFamilyAdd.setOnClickListener { view ->

            val parentId = 1
            val childId = 2
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, parentId, 0, getString(R.string.role_parent))
            popup.menu.add(0, childId, 1, getString(R.string.role_child))

            popup.setOnMenuItemClickListener { btn ->

                when (btn.itemId) {
                    parentId -> {
                        createInvite("PARENT")
                        true
                    }
                    childId  -> {
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
            val renameId = 1
            val refreshId = 2
            val heartBeatId = 3
            val settingsId = 4

            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, renameId, 0, getString(R.string.rename))
            popup.menu.add(0, refreshId, 1, getString(R.string.refresh))
            popup.menu.add(0, heartBeatId, 2, getString(R.string.send_heartbeat))
            popup.menu.add(0, settingsId, 3, getString(R.string.settings))

            popup.setOnMenuItemClickListener { btn ->
                when (btn.itemId) {
                    renameId -> {
                        showRenameDialog(
                            title = getString(R.string.rename_family),
                            initialValue = tvFamily.text.toString().removePrefix("Family: ").trim(),
                            onApply = { newName -> updateFamilyName(newName) }
                        )
                        true
                    }
                    settingsId -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }
                    refreshId -> {
                        loadFamily()
                        true
                    }
                    heartBeatId -> {
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

        tvInvite.visibility = View.GONE
        ivInviteQr.visibility = View.GONE

        ensureInitialized()
    }

    override fun onStart() {
        super.onStart()

        ContextCompat.registerReceiver(
            this,
            inviteUsedReceiver,
            IntentFilter(ACTION_INVITE_USED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val usedInviteId = store.lastUsedInviteId
        if (usedInviteId == shownInviteId) {
            clearInviteUi()
            store.clearLastUsedInviteId()
        }
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
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
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
                    tvStatus.text = getString(R.string.updating_family_name)
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateMyFamily(
                    auth = authHeaderOrThrow(),
                    body = UpdateNameRequest(name = name)
                )

                runOnUiThread {
                    tvStatus.text = getString(R.string.family_name_updated)
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.update_family_failed, e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun updateUserName(userId: String, name: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.updating_user_name)
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateUserName(
                    auth = authHeaderOrThrow(),
                    userId = userId,
                    body = UpdateNameRequest(name)
                )

                runOnUiThread {
                    tvStatus.text = getString(R.string.user_name_updated)
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.update_user_failed, e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun confirmDeleteDevice(deviceId: String, deviceName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_device)
            .setMessage(getString(R.string.delete_device_confirm, deviceName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteDevice(deviceId)
            }
            .show()
    }

    private fun deleteDevice(deviceId: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.deleting_device)
                    setButtonsEnabled(false)
                }

                ApiClient.api.deleteDevice(
                    auth = authHeaderOrThrow(),
                    deviceId = deviceId
                )

                runOnUiThread {
                    tvStatus.text = getString(R.string.device_deleted)
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.delete_device_failed, e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun updateDeviceName(deviceId: String, name: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.updating_device_name)
                    setButtonsEnabled(false)
                }

                ApiClient.api.updateDeviceName(
                    auth = authHeaderOrThrow(),
                    deviceId = deviceId,
                    body = UpdateNameRequest(name)
                )

                runOnUiThread {
                    tvStatus.text = getString(R.string.device_name_updated)
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.update_device_failed, e.message)
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
        return store.authHeader() ?: error("No token in SessionStore")
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnFamilyAdd.isEnabled = enabled
        btnFamilyMore.isEnabled = enabled
    }

    private fun loadFamily() {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.loading_family)
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.getMyFamily(authHeaderOrThrow())
                store.role = response.me.role

                runOnUiThread {
                    renderFamily(response)
                    tvStatus.text = getString(R.string.family_loaded)
                    setButtonsEnabled(true)
                }

            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.load_family_failed, e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun createInvite(role: String) {
        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.creating_invite,role)
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.createInvite(
                    auth = authHeaderOrThrow(),
                    body = CreateInviteRequest(
                        role = role,
                        expiresInMinutes = 60
                    )
                )
                val qrContent = "comuginator://join?code=${response.code}"
                val qrBitmap = createQrBitmap(qrContent)
                shownInviteId = response.inviteId

                runOnUiThread {
                    shownInviteId = response.inviteId

                    tvInvite.text = buildString {
                        append(getString(R.string.invite_code_result, response.code))
                        append(getString(R.string.role_result, role))
                        append(getString(R.string.expires_result, response.expiresAt))
                    }

                    tvInvite.visibility = View.VISIBLE

                    ivInviteQr.setImageBitmap(qrBitmap)
                    ivInviteQr.visibility = View.VISIBLE

                    tvStatus.text = getString(R.string.invite_created)
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.create_invite_failed,e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun clearInviteUi() {
        shownInviteId = null

        tvInvite.text = ""
        tvInvite.visibility = View.GONE

        ivInviteQr.setImageDrawable(null)
        ivInviteQr.visibility = View.GONE
    }

    private fun onInviteUsed(inviteId: String?) {
        if (inviteId.isNullOrBlank()) return

        if (inviteId == shownInviteId) {
            clearInviteUi()
        }
    }

    private fun mapFamilyItems(users: List<UserDto>): List<FamilyListItem> {
        val out = mutableListOf<FamilyListItem>()

        for (user in users) {
            out += FamilyListItem.UserHeader(
                userId = user.id,
                userName = user.name ?: getString(R.string.no_name),
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
        val familyName = response.family.name ?: getString(R.string.no_name)
        tvFamily.text = getString(R.string.family_prefix, familyName)

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
                    title = getString(R.string.rename_user),
                    initialValue = userName,
                    onApply = { newName -> updateUserName(userId, newName) }
                )
            },
            onRenameDeviceClick = { deviceId, deviceName ->
                showRenameDialog(
                    title = getString(R.string.rename_device),
                    initialValue = deviceName,
                    onApply = { newName -> updateDeviceName(deviceId, newName) }
                )
            },
            onSetAvatarClick = { userId ->
                showChooseAvatarDialog(userId)
            },
            onDeleteDeviceClick = { deviceId, deviceName ->
                confirmDeleteDevice(deviceId, deviceName)
            },
        )

        val listState = rvFamily.layoutManager?.onSaveInstanceState()

        rvFamily.adapter = familyAdapter
        familyAdapter.submitItems(mapFamilyItems(response.users))

        rvFamily.post {
            rvFamily.layoutManager?.onRestoreInstanceState(listState)
        }
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
                    tvStatus.text = getString(R.string.sending_volume_command)
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
                    tvStatus.text = getString(R.string.volume_command_sent,volumePercent)
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = getString(R.string.failed_send_volume_command,e.message)
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
                    tvStatus.text = getString(R.string.updating_avatar)
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
                    tvStatus.text = getString(R.string.avatar_updated)
                }

                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.update_avatar_failed, e.message)
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
            text =  getString(R.string.volume_value, initialValue)
            textSize = 16f
        }

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = initialValue

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = getString(R.string.volume_value, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        container.addView(valueText)
        container.addView(seekBar)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_volume) + deviceName)
            .setView(container)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                sendSetVolumeCommand(deviceId, seekBar.progress)
            }
            .show()
    }

    private fun createQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)

        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap[x, y] = if (bits[x, y]) Color.BLACK else Color.WHITE
            }
        }

        return bitmap
    }

    override fun onResume() {
        super.onResume()
        startFamilyRefreshLoop()
    }

    override fun onPause() {
        stopFamilyRefreshLoop()
        super.onPause()
    }

    private fun startFamilyRefreshLoop() {
        familyRefreshJob?.cancel()

        familyRefreshJob = scope.launch {
            while (true) {
                loadFamily()
                delay(30_000)
            }
        }
    }

    private fun stopFamilyRefreshLoop() {
        familyRefreshJob?.cancel()
        familyRefreshJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        familyRefreshJob?.cancel()
        scope.cancel()
    }
}