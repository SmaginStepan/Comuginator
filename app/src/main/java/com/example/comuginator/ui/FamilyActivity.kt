package com.example.comuginator.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.comuginator.R
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.CreateInviteRequest
import com.example.comuginator.api.DeviceDto
import com.example.comuginator.api.FamilyMeResponse
import com.example.comuginator.api.UserDto
import com.example.comuginator.storage.SessionStore
import com.example.comuginator.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FamilyActivity : BaseActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var store: SessionStore

    private lateinit var tvFamily: TextView
    private lateinit var tvMe: TextView
    private lateinit var tvInvite: TextView
    private lateinit var tvUsers: TextView
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
        tvUsers = findViewById(R.id.tvUsers)
        tvStatus = findViewById(R.id.tvStatus)

        btnRefresh = findViewById(R.id.btnRefresh)
        btnInviteParent = findViewById(R.id.btnInviteParent)
        btnInviteChild = findViewById(R.id.btnInviteChild)

        btnRefresh.setOnClickListener { loadFamily() }
        btnInviteParent.setOnClickListener { createInvite("PARENT") }
        btnInviteChild.setOnClickListener { createInvite("CHILD") }

        ensureInitialized()
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
            } catch (e: Exception) {
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
                runOnUiThread {
                    tvStatus.text = "Create invite failed: ${e.message}"
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun renderFamily(response: FamilyMeResponse) {
        val familyName = response.family.name ?: "(no name)"
        tvFamily.text = "Family: $familyName"

        tvMe.text = buildString {
            append("Me:\n")
            append("role=${response.me.role}\n")
            append("userId=${response.me.userId}\n")
            append("deviceId=${response.me.deviceId}")
        }

        tvUsers.text = formatUsers(response.users)
    }

    private fun formatUsers(users: List<UserDto>): String {
        if (users.isEmpty()) return "No users"

        return buildString {
            users.forEach { user ->
                append("• ${user.name ?: "(no name)"} [${user.role}]\n")

                if (user.devices.isEmpty()) {
                    append("   └ no devices\n")
                } else {
                    user.devices.forEachIndexed { index, device ->
                        val prefix = if (index == user.devices.lastIndex) "   └" else "   ├"
                        append(prefix)
                        append(" ")
                        append(formatDevice(device))
                        append("\n")
                    }
                }

                append("\n")
            }
        }.trim()
    }

    private fun formatDevice(device: DeviceDto): String {
        val displayName = device.name ?: device.deviceId
        val battery = device.state?.batteryPercent?.toString() ?: "?"
        val charging = when (device.state?.isCharging) {
            true -> "charging"
            false -> "not charging"
            null -> "charging?"
        }
        val lastSeen = device.lastSeenAt ?: "never"

        return "$displayName | battery=$battery% | $charging | lastSeen=$lastSeen"
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}