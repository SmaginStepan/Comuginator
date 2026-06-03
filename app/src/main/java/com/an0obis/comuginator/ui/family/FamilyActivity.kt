package com.an0obis.comuginator.ui.family

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.UserDto
import com.an0obis.comuginator.service.ACTION_INVITE_USED
import com.an0obis.comuginator.service.EXTRA_INVITE_ID
import com.an0obis.comuginator.ui.messaging.ComposeMessageActivity
import com.an0obis.comuginator.ui.MainActivity
import com.an0obis.comuginator.ui.SettingsActivity
import com.an0obis.comuginator.ui.messaging.UserMessageHistoryActivity
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.ui.childhome.ChildHomeActivity
import com.an0obis.comuginator.ui.library.LibraryActivity
import com.an0obis.comuginator.ui.library.LibraryItemPickerActivity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FamilyActivity : BaseActivity() {

    private val viewModel: FamilyViewModel by viewModels()

    private lateinit var tvFamily: TextView
    private lateinit var tvInvite: TextView
    private lateinit var ivInviteQr: ImageView
    private lateinit var rvFamily: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var btnFamilyAdd: Button
    private lateinit var btnFamilyMore: Button

    private var familyAdapter: FamilyAdapter? = null
    private var adapterMeRole: String = ""
    private var adapterMyDeviceId: String = ""
    private var pendingAvatarUserId: String? = null

    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val userId = pendingAvatarUserId
            pendingAvatarUserId = null
            if (result.resultCode == RESULT_OK) {
                val itemId = LibraryItemPickerActivity.parseResultItemId(result.data)
                if (!userId.isNullOrBlank() && !itemId.isNullOrBlank()) {
                    viewModel.updateUserAvatar(userId, itemId)
                }
            }
        }

    private val inviteUsedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.onInviteUsed(intent?.getStringExtra(EXTRA_INVITE_ID))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectedByRoleGuard) return
        setContentView(R.layout.activity_family)

        tvFamily = findViewById(R.id.tvFamily)
        tvInvite = findViewById(R.id.tvInvite)
        ivInviteQr = findViewById(R.id.ivInviteQr)
        rvFamily = findViewById(R.id.rvFamily)
        tvStatus = findViewById(R.id.tvStatus)
        btnFamilyAdd = findViewById(R.id.btnFamilyAdd)
        btnFamilyMore = findViewById(R.id.btnFamilyMore)

        tvInvite.visibility = View.GONE
        ivInviteQr.visibility = View.GONE

        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
        findViewById<Button>(R.id.btnChildHome).setOnClickListener {
            startActivity(
                Intent(this, ChildHomeActivity::class.java).apply {
                    putExtra(ChildHomeActivity.EXTRA_EDITOR_MODE, true)
                }
            )
        }

        btnFamilyAdd.setOnClickListener { view ->
            val parentId = 1; val childId = 2
            PopupMenu(view.context, view).apply {
                menu.add(0, parentId, 0, getString(R.string.role_parent))
                menu.add(0, childId,  1, getString(R.string.role_child))
                setOnMenuItemClickListener { btn ->
                    when (btn.itemId) {
                        parentId -> { viewModel.createInvite("PARENT"); true }
                        childId  -> { viewModel.createInvite("CHILD");  true }
                        else     -> false
                    }
                }
                show()
            }
        }

        btnFamilyMore.setOnClickListener { view ->
            val renameId = 1; val refreshId = 2; val heartBeatId = 3
            val settingsId = 4; val deleteFamilyId = 5
            PopupMenu(view.context, view).apply {
                menu.add(0, renameId,       0, getString(R.string.rename))
                menu.add(0, refreshId,      1, getString(R.string.refresh))
                menu.add(0, heartBeatId,    2, getString(R.string.send_heartbeat))
                menu.add(0, settingsId,     3, getString(R.string.settings))
                menu.add(0, deleteFamilyId, 4, getString(R.string.delete_family))
                setOnMenuItemClickListener { btn ->
                    when (btn.itemId) {
                        renameId -> {
                            showRenameDialog(
                                title = getString(R.string.rename_family),
                                initialValue = tvFamily.text.toString()
                                    .removePrefix("Family: ").trim(),
                                onApply = viewModel::updateFamilyName
                            )
                            true
                        }
                        refreshId      -> { viewModel.loadFamily();     true }
                        heartBeatId    -> { viewModel.sendHeartbeat();  true }
                        settingsId     -> {
                            startActivity(Intent(this@FamilyActivity, SettingsActivity::class.java))
                            true
                        }
                        deleteFamilyId -> { confirmDeleteFamily();       true }
                        else           -> false
                    }
                }
                show()
            }
        }

        rvFamily.layoutManager = LinearLayoutManager(this)

        bindState()
        ensureInitialized()
    }

    override fun onInitialized() {
        viewModel.loadFamily()
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
        if (!usedInviteId.isNullOrBlank()) {
            viewModel.onInviteUsed(usedInviteId)
            store.clearLastUsedInviteId()
        }
    }

    override fun onStop() {
        unregisterReceiver(inviteUsedReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.startRefreshLoop()
    }

    override fun onPause() {
        viewModel.stopRefreshLoop()
        super.onPause()
    }

    // ── State binding ─────────────────────────────────────────────────────────

    private fun formatDateTime(value: String): String {
        return try {
            Instant.parse(value)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        } catch (_: Exception) {
            value
        }
    }

    private fun bindState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val response = state.familyResponse ?: return@collect
                        renderFamily(response.family.name, response.me.role,
                            response.me.deviceId, response.users, state.optimisticVolumes)
                    }
                }
                launch {
                    viewModel.statusText.collect { text ->
                        tvStatus.text = text
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        btnFamilyAdd.isEnabled = !loading
                        btnFamilyMore.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.inviteDisplay.collect { invite ->
                        if (invite == null) {
                            tvInvite.text = ""
                            tvInvite.visibility = View.GONE
                            ivInviteQr.setImageDrawable(null)
                            ivInviteQr.visibility = View.GONE
                        } else {
                            tvInvite.text = buildString {
                                append(getString(R.string.invite_code_result, invite.code))
                                append("\n")
                                append(getString(R.string.role_result, invite.role))
                                append("\n")
                                append(
                                    getString(
                                        R.string.expires_result,
                                        formatDateTime(invite.expiresAt)
                                    )
                                )
                            }
                            tvInvite.visibility = View.VISIBLE
                            ivInviteQr.setImageBitmap(
                                createQrBitmap("comuginator://join?code=${invite.code}")
                            )
                            ivInviteQr.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            FamilyEvent.NavigateToMain -> {
                                startActivity(Intent(this@FamilyActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderFamily(
        familyName: String?,
        meRole: String,
        meDeviceId: String,
        users: List<UserDto>,
        optimisticVolumes: Map<String, Int>
    ) {
        tvFamily.text = getString(R.string.family_prefix, familyName ?: getString(R.string.no_name))

        if (familyAdapter == null || adapterMeRole != meRole || adapterMyDeviceId != meDeviceId) {
            adapterMeRole = meRole
            adapterMyDeviceId = meDeviceId
            familyAdapter = FamilyAdapter(
                isParentViewer = meRole == "PARENT",
                myDeviceId = meDeviceId,
                authToken = store.authHeaderOrThrow(),
                onVolumeClick = { deviceId, deviceName, currentVolume ->
                    showSetVolumeDialog(deviceId, deviceName, currentVolume)
                },
                onSendClick = ::openComposeMessageScreen,
                onHistoryClick = ::openMessageHistoryScreen,
                onRenameUserClick = { userId, userName ->
                    showRenameDialog(
                        title = getString(R.string.rename_user),
                        initialValue = userName,
                        onApply = { newName -> viewModel.updateUserName(userId, newName) }
                    )
                },
                onRenameDeviceClick = { deviceId, deviceName ->
                    showRenameDialog(
                        title = getString(R.string.rename_device),
                        initialValue = deviceName,
                        onApply = { newName -> viewModel.updateDeviceName(deviceId, newName) }
                    )
                },
                onSetAvatarClick = ::showChooseAvatarDialog,
                onDeleteDeviceClick = ::confirmDeleteDevice,
                onDeleteUserClick = ::confirmDeleteUser
            )
            val listState = rvFamily.layoutManager?.onSaveInstanceState()
            rvFamily.adapter = familyAdapter
            rvFamily.post { rvFamily.layoutManager?.onRestoreInstanceState(listState) }
        }

        familyAdapter!!.submitItems(viewModel.mapFamilyItems(users, optimisticVolumes))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showRenameDialog(title: String, initialValue: String, onApply: (String) -> Unit) {
        val editText = EditText(this).apply {
            setText(initialValue)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val value = editText.text?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) onApply(value)
            }
            .show()
    }

    private fun showSetVolumeDialog(deviceId: String, deviceName: String, currentVolumePercent: Int?) {
        val initial = (currentVolumePercent ?: 50).coerceIn(0, 100)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        val valueText = TextView(this).apply {
            text = getString(R.string.volume_value, initial)
            textSize = 16f
        }
        val seekBar = SeekBar(this).apply {
            max = 100
            progress = initial
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = getString(R.string.volume_value, progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        container.addView(valueText)
        container.addView(seekBar)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_volume) + deviceName)
            .setView(container)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                viewModel.sendSetVolumeCommand(deviceId, seekBar.progress)
            }
            .show()
    }

    private fun showChooseAvatarDialog(userId: String) {
        pendingAvatarUserId = userId
        avatarPickerLauncher.launch(
            LibraryItemPickerActivity.createIntent(
                context = this,
                targetMode = LibraryItemPickerActivity.TargetMode.USER_AVATAR
            )
        )
    }

    // ── Confirm-delete dialogs ────────────────────────────────────────────────

    private fun confirmDeleteUser(userId: String, userName: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_user)
            .setMessage(getString(R.string.delete_user_confirm, userName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteUser(userId) }
            .show()
    }

    private fun confirmDeleteDevice(deviceId: String, deviceName: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_device)
            .setMessage(getString(R.string.delete_device_confirm, deviceName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteDevice(deviceId) }
            .show()
    }

    private fun confirmDeleteFamily() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_family)
            .setMessage(R.string.delete_family_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteFamily() }
            .show()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun openComposeMessageScreen(targetUserId: String, targetUserName: String) {
        startActivity(Intent(this, ComposeMessageActivity::class.java).apply {
            putExtra("targetUserId", targetUserId)
            putExtra("targetUserName", targetUserName)
        })
    }

    private fun openMessageHistoryScreen(targetUserId: String, targetUserName: String) {
        startActivity(Intent(this, UserMessageHistoryActivity::class.java).apply {
            putExtra("targetUserId", targetUserId)
            putExtra("targetUserName", targetUserName)
        })
    }
}