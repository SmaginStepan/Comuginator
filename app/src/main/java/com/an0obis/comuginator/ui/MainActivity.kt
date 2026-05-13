package com.an0obis.comuginator.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CreateFamilyRequest
import com.an0obis.comuginator.api.JoinFamilyRequest
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.core.view.isVisible
import com.an0obis.comuginator.ui.family.FamilyActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : BaseActivity() {

    private companion object {
        const val STATE_CHOICE_VISIBLE = "choice_visible"
        const val STATE_JOIN_VISIBLE = "join_visible"
        const val STATE_STATUS = "status"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var etUserName: EditText
    private lateinit var etDeviceName: EditText
    private lateinit var etInviteCode: EditText

    private lateinit var btnNext: Button
    private lateinit var btnCreateFamily: Button
    private lateinit var btnShowJoin: Button
    private lateinit var btnJoinFamily: Button

    private lateinit var tvStatus: TextView
    private lateinit var layoutChoice: LinearLayout
    private lateinit var layoutJoin: LinearLayout

    private lateinit var btnScanQr: Button

    private lateinit var stableDeviceId: String

    private val qrScanLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val content = result.contents ?: return@registerForActivityResult
            val code = parseInviteCodeFromQr(content)

            if (code == null) {
                tvStatus.text = getString(R.string.invalid_qr_code)
                return@registerForActivityResult
            }

            etInviteCode.setText(code)
            layoutJoin.isVisible = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = SessionStore(this)

        etUserName = findViewById(R.id.etUserName)
        etDeviceName = findViewById(R.id.etDeviceName)
        etInviteCode = findViewById(R.id.etInviteCode)

        btnNext = findViewById(R.id.btnNext)
        btnCreateFamily = findViewById(R.id.btnCreateFamily)
        btnShowJoin = findViewById(R.id.btnShowJoin)
        btnJoinFamily = findViewById(R.id.btnJoinFamily)

        tvStatus = findViewById(R.id.tvStatus)
        layoutChoice = findViewById(R.id.layoutChoice)
        layoutJoin = findViewById(R.id.layoutJoin)
        btnScanQr = findViewById(R.id.btnScanQr)

        stableDeviceId = store.deviceId ?: UUID.randomUUID().toString().also {
            store.deviceId = it
        }

        if (savedInstanceState == null) {
            layoutChoice.visibility = View.GONE
            layoutJoin.visibility = View.GONE
        } else {
            layoutChoice.isVisible = savedInstanceState.getBoolean(STATE_CHOICE_VISIBLE, false)
            layoutJoin.isVisible = savedInstanceState.getBoolean(STATE_JOIN_VISIBLE, false)
            tvStatus.text = savedInstanceState.getString(STATE_STATUS).orEmpty()
        }

        if (!store.userName.isNullOrBlank()) {
            etUserName.setText(store.userName)
        }

        val savedDeviceName = store.deviceName
        if (!savedDeviceName.isNullOrBlank()) {
            etDeviceName.setText(savedDeviceName)
        } else {
            etDeviceName.setText(
                getString(R.string.default_device_name, android.os.Build.BRAND, android.os.Build.MODEL)
            )
        }

        btnNext.setOnClickListener {
            val userName = etUserName.text.toString().trim()
            val deviceName = etDeviceName.text.toString().trim()

            if (userName.isBlank()) {
                tvStatus.text = getString(R.string.user_name_required)
                return@setOnClickListener
            }

            if (deviceName.isBlank()) {
                tvStatus.text = getString(R.string.device_name_required)
                return@setOnClickListener
            }

            store.userName = userName
            store.deviceName = deviceName

            layoutChoice.visibility = View.VISIBLE
            tvStatus.text = getString(R.string.choose_create_or_join)
        }

        btnShowJoin.setOnClickListener {
            layoutJoin.isVisible = !layoutJoin.isVisible
        }

        btnCreateFamily.setOnClickListener {
            showCreateFamilyDialog()
        }

        btnJoinFamily.setOnClickListener {
            joinFamily()
        }

        btnScanQr.setOnClickListener {
            qrScanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(getString(R.string.scan_qr_code))
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        }

        ensureInitialized()
    }

    override fun onInitialized() {
        openFamilyScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_CHOICE_VISIBLE, layoutChoice.isVisible)
        outState.putBoolean(STATE_JOIN_VISIBLE, layoutJoin.isVisible)
        outState.putString(STATE_STATUS, tvStatus.text?.toString().orEmpty())
    }

    private fun parseInviteCodeFromQr(content: String): String? {
        val raw = content.trim()

        if (raw.startsWith("comuginator://join?code=")) {
            return raw.substringAfter("code=")
                .substringBefore("&")
                .trim()
                .uppercase()
                .takeIf { it.isNotBlank() }
        }

        return raw.uppercase().takeIf { it.matches(Regex("[A-Z0-9]{4,20}")) }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnCreateFamily.isEnabled = enabled
        btnShowJoin.isEnabled = enabled
        btnJoinFamily.isEnabled = enabled
        btnScanQr.isEnabled = enabled
    }

    private fun openFamilyScreen() {
        startActivity(Intent(this, FamilyActivity::class.java))
        finish()
    }

    private fun showCreateFamilyDialog() {
        val inputLayout = TextInputLayout(this).apply {
            setPadding(48, 24, 48, 0)
            hint = getString(R.string.family_name)
        }

        val input = TextInputEditText(inputLayout.context).apply {
            setText("")
            setSingleLine(true)
        }

        inputLayout.addView(input)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_family)
            .setMessage(R.string.enter_family_name)
            .setView(inputLayout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                val familyName = input.text?.toString()?.trim().orEmpty().ifBlank { null }
                createFamily(familyName)
            }
            .show()
    }

    private fun createFamily(familyName: String?) {
        val userName = etUserName.text.toString().trim()
        val deviceName = etDeviceName.text.toString().trim()

        if (userName.isBlank()) {
            tvStatus.text = getString(R.string.user_name_required)
            return
        }

        if (deviceName.isBlank()) {
            tvStatus.text = getString(R.string.device_name_required)
            return
        }

        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.creating_family)
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.createFamily(
                    CreateFamilyRequest(
                        userName = userName,
                        deviceName = deviceName,
                        deviceId = stableDeviceId,
                        familyName = familyName
                    )
                )

                store.token = response.token
                store.familyId = response.familyId
                store.userId = response.userId
                store.deviceId = response.deviceId
                store.userName = userName
                store.deviceName = deviceName
                store.role = response.role

                runOnUiThread {
                    tvStatus.text = getString(
                        R.string.family_created_details,
                        response.familyId,
                        response.role,
                        response.deviceId
                    )
                    setButtonsEnabled(true)
                    openFamilyScreen()
                }
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.create_family_failed, e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun joinFamily() {
        val userName = etUserName.text.toString().trim()
        val deviceName = etDeviceName.text.toString().trim()
        val code = etInviteCode.text.toString().trim().uppercase()

        if (userName.isBlank()) {
            tvStatus.text = getString(R.string.user_name_required)
            return
        }

        if (deviceName.isBlank()) {
            tvStatus.text = getString(R.string.device_name_required)
            return
        }

        if (code.isBlank()) {
            tvStatus.text = getString(R.string.invite_code_required)
            return
        }

        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = getString(R.string.joining_family)
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.joinFamily(
                    JoinFamilyRequest(
                        code = code,
                        userName = userName,
                        deviceName = deviceName,
                        deviceId = stableDeviceId
                    )
                )

                store.token = response.token
                store.familyId = response.familyId
                store.userId = response.userId
                store.deviceId = response.deviceId
                store.userName = userName
                store.deviceName = deviceName
                store.role = response.role

                runOnUiThread {
                    tvStatus.text = getString(
                        R.string.joined_family_details,
                        response.familyId,
                        response.role,
                        response.userCreated.toString()
                    )
                    setButtonsEnabled(true)
                    openFamilyScreen()
                }
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                runOnUiThread {
                    tvStatus.text = getString(R.string.join_family_failed, e.message)
                    setButtonsEnabled(true)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}