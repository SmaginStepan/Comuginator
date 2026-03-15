package com.example.comuginator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val deviceId = UUID.randomUUID().toString()

    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val log = findViewById<TextView>(R.id.log)

        findViewById<Button>(R.id.btnRegister).setOnClickListener {

            scope.launch {
                try {

                    val res = ApiClient.api.register(
                        RegisterRequest(deviceId, "Android Test")
                    )

                    token = res.token

                    runOnUiThread {
                        log.append("REGISTER OK\n")
                        log.append("TOKEN: ${res.token}\n\n")
                    }

                } catch (e: Exception) {
                    runOnUiThread { log.append("ERROR $e\n") }
                }
            }
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {

            val t = token ?: return@setOnClickListener

            scope.launch {

                ApiClient.api.battery(
                    "Bearer $t",
                    BatteryRequest(80, false)
                )

                runOnUiThread {
                    log.append("BATTERY SENT\n\n")
                }
            }
        }

        findViewById<Button>(R.id.btnCommands).setOnClickListener {

            val t = token ?: return@setOnClickListener

            scope.launch {

                val res = ApiClient.api.commands("Bearer $t")

                runOnUiThread {
                    log.append("COMMANDS: ${res.items.size}\n\n")
                }
            }
        }
    }
}