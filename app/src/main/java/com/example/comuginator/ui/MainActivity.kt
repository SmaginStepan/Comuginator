package com.example.comuginator.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.comuginator.R
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.CreateFamilyRequest
import com.example.comuginator.api.JoinFamilyRequest
import com.example.comuginator.service.ConnectionService
import com.example.comuginator.storage.SessionStore
import com.example.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : BaseActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var store: SessionStore

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

    private lateinit var stableDeviceId: String

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

        stableDeviceId = store.deviceId ?: UUID.randomUUID().toString().also {
            store.deviceId = it
        }

        layoutChoice.visibility = View.GONE
        layoutJoin.visibility = View.GONE

        if (!store.userName.isNullOrBlank()) {
            etUserName.setText(store.userName)
        }

        val savedDeviceName = store.deviceName
        if (!savedDeviceName.isNullOrBlank()) {
            etDeviceName.setText(savedDeviceName)
        } else {
            etDeviceName.setText("${android.os.Build.BRAND} ${android.os.Build.MODEL}")
        }

        btnNext.setOnClickListener {
            val userName = etUserName.text.toString().trim()
            val deviceName = etDeviceName.text.toString().trim()

            if (userName.isBlank()) {
                tvStatus.text = "User name is required"
                return@setOnClickListener
            }

            if (deviceName.isBlank()) {
                tvStatus.text = "Device name is required"
                return@setOnClickListener
            }

            store.userName = userName
            store.deviceName = deviceName

            layoutChoice.visibility = View.VISIBLE
            tvStatus.text = "Choose: create family group or join family group"
        }

        btnShowJoin.setOnClickListener {
            layoutJoin.visibility =
                if (layoutJoin.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnCreateFamily.setOnClickListener {
            createFamily()
        }

        btnJoinFamily.setOnClickListener {
            joinFamily()
        }

        ensureInitialized()
    }

    override fun onInitialized() {
        openFamilyScreen()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnCreateFamily.isEnabled = enabled
        btnShowJoin.isEnabled = enabled
        btnJoinFamily.isEnabled = enabled
    }

    private fun openFamilyScreen() {
        startActivity(Intent(this, FamilyActivity::class.java))
        finish()
    }

    private fun createFamily() {
        val userName = etUserName.text.toString().trim()
        val deviceName = etDeviceName.text.toString().trim()

        if (userName.isBlank()) {
            tvStatus.text = "User name is required"
            return
        }

        if (deviceName.isBlank()) {
            tvStatus.text = "Device name is required"
            return
        }

        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Creating family..."
                    setButtonsEnabled(false)
                }

                val response = ApiClient.api.createFamily(
                    CreateFamilyRequest(
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

                runOnUiThread {
                    tvStatus.text =
                        "Family created.\nFamily ID: ${response.familyId}\nRole: ${response.role}\nDevice ID: ${response.deviceId}"
                    setButtonsEnabled(true)
                    openFamilyScreen()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Create family failed: ${e.message}"
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
            tvStatus.text = "User name is required"
            return
        }

        if (deviceName.isBlank()) {
            tvStatus.text = "Device name is required"
            return
        }

        if (code.isBlank()) {
            tvStatus.text = "Invite code is required"
            return
        }

        scope.launch {
            try {
                runOnUiThread {
                    tvStatus.text = "Joining family..."
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

                runOnUiThread {
                    tvStatus.text =
                        "Joined family.\nFamily ID: ${response.familyId}\nRole: ${response.role}\nUser created: ${response.userCreated}"
                    setButtonsEnabled(true)
                    openFamilyScreen()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Join family failed: ${e.message}"
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