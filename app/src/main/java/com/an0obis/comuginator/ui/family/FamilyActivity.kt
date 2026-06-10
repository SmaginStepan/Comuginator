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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import com.an0obis.comuginator.ui.schedule.ScheduleActivity
import com.an0obis.comuginator.util.TimeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
class FamilyActivity : BaseActivity() {

    private val viewModel: FamilyViewModel by viewModels()

    private lateinit var tvFamily: TextView
    private lateinit var tvInvite: TextView
    private lateinit var ivInviteQr: ImageView
    private lateinit var rvFamily: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var btnFamilyAdd: Button
    private lateinit var btnFamilyMore: Button

    private lateinit var btnSchedule: Button

    private var familyAdapter: FamilyAdapter? = null
    private var adapterMeRole: String = ""
    private var adapterMyDeviceId: String = ""
    private var pendingAvatarUserId: String? = null

    private val joinFamilyQrLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val content = result.contents ?: return@registerForActivityResult
            val code = parseInviteCode(content)
            if (code != null) {
                viewModel.joinAnotherFamily(code)
            } else {
                viewModel.setStatus(getString(R.string.invalid_qr_code))
            }
        }

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
        btnSchedule = findViewById(R.id.btnSchedule)

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

        findViewById<Button>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
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
            val joinAnotherId = 6; val switchFamilyId = 7
            PopupMenu(view.context, view).apply {
                menu.add(0, renameId,       0, getString(R.string.rename))
                menu.add(0, refreshId,      1, getString(R.string.refresh))
                menu.add(0, heartBeatId,    2, getString(R.string.send_heartbeat))
                menu.add(0, settingsId,     3, getString(R.string.settings))
                menu.add(0, joinAnotherId,  4, getString(R.string.join_another_family))
                if (viewModel.getFamilies().size > 1) {
                    menu.add(0, switchFamilyId, 5, getString(R.string.switch_family))
                }
                menu.add(0, deleteFamilyId, 6, getString(R.string.delete_family))
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
                        refreshId      -> { viewModel.loadFamily();          true }
                        heartBeatId    -> { viewModel.sendHeartbeat();       true }
                        settingsId     -> {
                            startActivity(Intent(this@FamilyActivity, SettingsActivity::class.java))
                            true
                        }
                        joinAnotherId  -> { showJoinAnotherFamilyDialog();   true }
                        switchFamilyId -> { showSwitchFamilyDialog();        true }
                        deleteFamilyId -> { confirmDeleteFamily();            true }
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

    private fun formatDateTime(value: String): String = TimeFormat.dateTime(value)

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
                            FamilyEvent.FamilySwitched -> {
                                familyAdapter = null // force adapter rebuild for new role
                            }
                            is FamilyEvent.ShowToast -> {
                                Toast.makeText(this@FamilyActivity, event.message, Toast.LENGTH_LONG).show()
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

        btnSchedule.isVisible = meRole == "PARENT"

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

    // ── Multi-family dialogs ──────────────────────────────────────────────────

    private fun parseInviteCode(content: String): String? {
        val raw = content.trim()
        if (raw.startsWith("comuginator://join?code=")) {
            return raw.substringAfter("code=").substringBefore("&").trim().uppercase()
                .takeIf { it.isNotBlank() }
        }
        return raw.uppercase().takeIf { it.matches(Regex("[A-Z0-9]{4,20}")) }
    }

    private fun showJoinAnotherFamilyDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etCode = EditText(this).apply {
            hint = getString(R.string.invite_code_required).removeSuffix(" is required")
                .ifBlank { "Invite code" }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val btnScan = Button(this).apply {
            text = getString(R.string.scan_qr_code)
            setOnClickListener {
                joinFamilyQrLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt(getString(R.string.scan_qr_code))
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            }
        }
        container.addView(etCode)
        container.addView(btnScan)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.join_another_family))
            .setView(container)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.join)) { _, _ ->
                val code = etCode.text?.toString()?.trim().orEmpty()
                if (code.isNotEmpty()) viewModel.joinAnotherFamily(code)
            }
            .show()
    }

    private fun showSwitchFamilyDialog() {
        val families = viewModel.getFamilies()
        val activeFamilyId = store.familyId
        val labels = families.map { entry ->
            val display = entry.name?.takeIf { it.isNotEmpty() } ?: entry.familyId
            if (entry.familyId == activeFamilyId) "▶ $display" else display
        }.toTypedArray()
        val currentIndex = families.indexOfFirst { it.familyId == activeFamilyId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.switch_family))
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selected = families[which]
                if (selected.familyId != activeFamilyId) {
                    if (selected.role == "CHILD") {
                        confirmSwitchToChildFamily(selected.familyId)
                    } else {
                        viewModel.setActiveFamily(selected.familyId)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmSwitchToChildFamily(familyId: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.switch_family))
            .setMessage(getString(R.string.switch_to_child_family_warning))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                viewModel.setActiveFamily(familyId)
            }
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