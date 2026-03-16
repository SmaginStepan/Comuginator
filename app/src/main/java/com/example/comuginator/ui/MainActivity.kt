package com.example.comuginator.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.comuginator.R
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.CreateFamilyRequest
import com.example.comuginator.api.JoinFamilyRequest
import com.example.comuginator.storage.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var store: SessionStore

    private lateinit var etUserName: EditText
    private lateinit var etDeviceName: EditText
    private lateinit var etInviteCode: EditText
    private lateinit var layoutChoice: LinearLayout
    private lateinit var layoutJoin: LinearLayout
    private lateinit var tvStatus: TextView

    private lateinit var btnNext: Button
    private lateinit var btnCreateFamily: Button
    private lateinit var btnShowJoin: Button
    private lateinit var btnJoinFamily: Button

    private lateinit var stableDeviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = SessionStore(this)
        stableDeviceId = getOrCreateStableDeviceId()

        etUserName = findViewById(R.id.etUserName)
        etDeviceName = findViewById(R.id.etDeviceName)
        etInviteCode = findViewById(R.id.etInviteCode)
        layoutChoice = findViewById(R.id.layoutChoice)
        layoutJoin = findViewById(R.id.layoutJoin)
        tvStatus = findViewById(R.id.tvStatus)

        btnNext = findViewById(R.id.btnNext)
        btnCreateFamily = findViewById(R.id.btnCreateFamily)
        btnShowJoin = findViewById(R.id.btnShowJoin)
        btnJoinFamily = findViewById(R.id.btnJoinFamily)

        val defaultDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        etUserName.setText(store.userName ?: "")
        etDeviceName.setText(store.deviceName ?: defaultDeviceName)

        btnNext.setOnClickListener {
            val userName = etUserName.text.toString().trim()
            val deviceName = etDeviceName.text.toString().trim()

            if (userName.isBlank()) {
                tvStatus.text = "Enter user name"
                return@setOnClickListener
            }

            if (deviceName.isBlank()) {
                tvStatus.text = "Enter device name"
                return@setOnClickListener
            }

            store.userName = userName
            store.deviceName = deviceName

            layoutChoice.visibility = View.VISIBLE
            tvStatus.text = "Choose what to do next"
        }

        btnShowJoin.setOnClickListener {
            layoutJoin.visibility = View.VISIBLE
            tvStatus.text = "Enter invite code"
        }

        btnCreateFamily.setOnClickListener {
            createFamily()
        }

        btnJoinFamily.setOnClickListener {
            joinFamily()
        }

        val existingToken = store.token
        if (!existingToken.isNullOrBlank()) {
            tvStatus.text = "Already connected. Token exists for device $stableDeviceId"
            layoutChoice.visibility = View.VISIBLE
        }
    }

    private fun createFamily() {
        val userName = etUserName.text.toString().trim()
        val deviceName = etDeviceName.text.toString().trim()

        if (userName.isBlank() || deviceName.isBlank()) {
            runOnUiThread { tvStatus.text = "Fill in user name and device name" }
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
                        deviceId = stableDeviceId,
                        familyName = "$userName family"
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
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Create family failed: $e"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun joinFamily() {
        val userName = etUserName.text.toString().trim()
        val deviceName = etDeviceName.text.toString().trim()
        val code = etInviteCode.text.toString().trim().uppercase()

        if (userName.isBlank() || deviceName.isBlank()) {
            runOnUiThread { tvStatus.text = "Fill in user name and device name" }
            return
        }

        if (code.isBlank()) {
            runOnUiThread { tvStatus.text = "Enter invite code" }
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
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Join family failed: $e"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnCreateFamily.isEnabled = enabled
        btnShowJoin.isEnabled = enabled
        btnJoinFamily.isEnabled = enabled
    }

    private fun getOrCreateStableDeviceId(): String {
        val existing = store.deviceId
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        store.deviceId = newId
        return newId
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}