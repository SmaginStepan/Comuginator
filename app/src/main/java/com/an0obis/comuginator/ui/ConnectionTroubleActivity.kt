package com.an0obis.comuginator.ui

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.an0obis.comuginator.R
import com.an0obis.comuginator.storage.SettingsStore

/**
 * Shown when a request keeps failing after the automatic retries. The user
 * decides: retry once more or switch the whole app into offline mode.
 * Deliberately a plain activity (no BaseActivity role guard / pending checks)
 * so it works identically for parent and child devices.
 */
class ConnectionTroubleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_trouble)

        findViewById<Button>(R.id.btnTryAgain).setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        findViewById<Button>(R.id.btnEnterOffline).setOnClickListener {
            SettingsStore(this).offlineMode = true
            setResult(RESULT_OK)
            finish()
        }
    }
}
