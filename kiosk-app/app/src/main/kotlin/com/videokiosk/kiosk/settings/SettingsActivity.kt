package com.videokiosk.kiosk.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.videokiosk.kiosk.R

/**
 * Settings screen for configuring the signaling server address.
 * Values are persisted in [android.content.SharedPreferences].
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var btnSave: Button

    companion object {
        const val PREFS_NAME = "kiosk_prefs"
        const val PREF_SERVER_IP = "server_ip"
        const val PREF_SERVER_PORT = "server_port"
        const val DEFAULT_IP = "192.168.3.235"
        const val DEFAULT_PORT = "8080"
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_settings)

        etServerIp = findViewById(R.id.et_server_ip)
        etServerPort = findViewById(R.id.et_server_port)
        btnSave = findViewById(R.id.btn_save_settings)

        // Load current settings
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        etServerIp.setText(prefs.getString(PREF_SERVER_IP, DEFAULT_IP))
        etServerPort.setText(prefs.getString(PREF_SERVER_PORT, DEFAULT_PORT))

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---------------------------------------------------------------------------
    // Save logic
    // ---------------------------------------------------------------------------

    private fun saveSettings() {
        val ip = etServerIp.text.toString().trim()
        val port = etServerPort.text.toString().trim()

        if (ip.isBlank()) {
            etServerIp.error = getString(R.string.error_ip_required)
            return
        }

        if (port.isBlank() || port.toIntOrNull() == null) {
            etServerPort.error = getString(R.string.error_port_invalid)
            return
        }

        val portInt = port.toInt()
        if (portInt < 1 || portInt > 65535) {
            etServerPort.error = getString(R.string.error_port_range)
            return
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(PREF_SERVER_IP, ip)
            .putString(PREF_SERVER_PORT, port)
            .apply()

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
