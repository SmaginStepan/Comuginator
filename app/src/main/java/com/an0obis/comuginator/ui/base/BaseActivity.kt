package com.an0obis.comuginator.ui.base

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.an0obis.comuginator.service.CommandSyncScheduler
import com.an0obis.comuginator.service.FcmTokenSyncScheduler
import com.an0obis.comuginator.service.PowerConnectionReceiver
import com.an0obis.comuginator.service.TelemetryScheduler
import com.an0obis.comuginator.storage.FcmTokenStore
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.ChildHomeActivity
import com.an0obis.comuginator.ui.IncomingMessageActivity
import com.an0obis.comuginator.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.HttpException
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.an0obis.comuginator.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class BaseActivity: AppCompatActivity() {
    protected lateinit var store: SessionStore
    private var initialized = false
    private var pendingIncomingCheckRunning = false

    protected var redirectedByRoleGuard: Boolean = false
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppLanguage()
        super.onCreate(savedInstanceState)

        store = SessionStore(this)

        if (store.role == "CHILD" && shouldForceChildHome()) {
            redirectedByRoleGuard = true

            startActivity(
                Intent(this, ChildHomeActivity::class.java).apply {
                    putExtra(ChildHomeActivity.EXTRA_EDITOR_MODE, false)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )

            finish()
            return
        }
    }

    private fun applyAppLanguage() {
        val lang = SessionStore(this).appLanguage

        val locales = if (lang.isNullOrBlank() || lang == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }

        AppCompatDelegate.setApplicationLocales(locales)
    }

    protected fun getArasaacLang(): String {
        val lang = SessionStore(this).appLanguage
            ?.takeIf { it != "system" }
            ?: java.util.Locale.getDefault().language

        return when (lang.lowercase()) {
            "es" -> "es"
            "ru" -> "ru"
            else -> "en"
        }
    }
    protected fun ensureInitialized() {
        if (initialized) return
        if (!ensureNotificationsPermission()) return

        initialized = true

        val existingToken = store.token
        if (!existingToken.isNullOrBlank()) {
            TelemetryScheduler.ensurePeriodic(applicationContext)
            TelemetryScheduler.enqueueImmediate(applicationContext, "app_start")

            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        FcmTokenStore(applicationContext).savePendingToken(token)
                        FcmTokenSyncScheduler.enqueueImmediate(applicationContext, "app_start")
                    }
                }

            registerReceiver(
                PowerConnectionReceiver(),
                IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
            )

            CommandSyncScheduler.enqueueImmediate(applicationContext, "app_start")
            if (store.role == "CHILD" && shouldForceChildHome()) {
                startActivity(
                    Intent(this, ChildHomeActivity::class.java).apply {
                        putExtra(ChildHomeActivity.EXTRA_EDITOR_MODE, false)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
                finish()
                return
            }

            onInitialized()
            return
        }
    }

    private fun shouldForceChildHome(): Boolean {
        return this !is ChildHomeActivity &&
                this !is IncomingMessageActivity &&
                this !is MainActivity
    }
    open fun onInitialized() {
        // if session and token is ready
    }

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")

            if (granted) ensureInitialized()
            else openNotificationSettings()
        }

    private fun ensureNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return false
        }
        return true
    }

    protected fun handleUnauthorized(e: Exception): Boolean {
        if (e is HttpException && e.code() == 401) {
            val store = SessionStore(this)
            store.clear()

            startActivity(Intent(this, MainActivity::class.java))
            finish()

            return true
        }
        return false
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        }

        startActivity(intent)
    }

    private fun shouldCheckPendingIncomingMessages(): Boolean {
        if (this is IncomingMessageActivity) return false

        val auth = store.authHeader()
        if (auth.isNullOrBlank()) return false

        return true
    }

    private fun checkPendingIncomingMessages() {
        if (!shouldCheckPendingIncomingMessages()) return
        if (pendingIncomingCheckRunning) return

        pendingIncomingCheckRunning = true

        lifecycleScope.launch {
            try {
                val auth = store.authHeaderOrThrow()

                val result = withContext(Dispatchers.IO) {
                    val pending = ApiClient.api.getPendingCommands(auth)
                    val allMessages = ApiClient.api.getAacMessages(auth = auth, scope = "all")

                    val pendingMessageMap = pending.items
                        .asSequence()
                        .filter { it.status == "queued" && it.type == "aac_message_available" }
                        .mapNotNull { cmd ->
                            val messageId = cmd.payload["messageId"] as? String
                            if (messageId != null) messageId to cmd.id else null
                        }
                        .toMap()

                    val pendingReplyMap = pending.items
                        .asSequence()
                        .filter { it.status == "queued" && it.type == "aac_reply_available" }
                        .mapNotNull { cmd ->
                            val messageId = cmd.payload["messageId"] as? String
                            if (messageId != null) messageId to cmd.id else null
                        }
                        .toMap()

                    val incomingToAnswer = allMessages.items.firstOrNull { msg ->
                        msg.toUserId == store.userId &&
                                msg.reply == null &&
                                msg.suggestedReplies.isNotEmpty()
                    }

                    val repliedToMyMessage = allMessages.items.firstOrNull { msg ->
                        msg.fromUserId == store.userId &&
                                msg.reply != null &&
                                pendingReplyMap.containsKey(msg.id)
                    }

                    when {
                        incomingToAnswer != null -> Triple(
                            incomingToAnswer.id,
                            pendingMessageMap[incomingToAnswer.id].orEmpty(),
                            IncomingMessageActivity.MODE_MESSAGE
                        )

                        repliedToMyMessage != null -> Triple(
                            repliedToMyMessage.id,
                            pendingReplyMap[repliedToMyMessage.id].orEmpty(),
                            IncomingMessageActivity.MODE_REPLY
                        )

                        else -> null
                    }
                }

                val target = result ?: return@launch
                val messageId = target.first
                val commandId = target.second
                val mode = target.third

                openIncomingMessage(messageId, commandId, mode)
            } catch (e: Exception) {
                handleUnauthorized(e)
            } finally {
                pendingIncomingCheckRunning = false
            }
        }
    }

    private fun openIncomingMessage(messageId: String, commandId: String, mode: String) {
        startActivity(
            Intent(this, IncomingMessageActivity::class.java).apply {
                putExtra(IncomingMessageActivity.EXTRA_MESSAGE_ID, messageId)
                putExtra(IncomingMessageActivity.EXTRA_COMMAND_ID, commandId)
                putExtra(IncomingMessageActivity.EXTRA_MODE, mode)
            }
        )
    }

    protected fun loadProtectedImage(url: String?, imageView: ImageView) {
        if (url.isNullOrBlank()) return

        val request = ImageRequest.Builder(this)
            .data(url)
            .addHeader("Authorization", store.authHeaderOrThrow())
            .target(imageView)
            .build()

        ImageLoader(this).enqueue(request)
    }

    override fun onResume() {
        super.onResume()

        if (initialized) {
            checkPendingIncomingMessages()
        }
    }
}