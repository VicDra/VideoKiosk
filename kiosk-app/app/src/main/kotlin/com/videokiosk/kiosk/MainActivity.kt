package com.videokiosk.kiosk

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.videokiosk.kiosk.model.CallState
import com.videokiosk.kiosk.settings.SettingsActivity
import com.videokiosk.kiosk.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Single-activity host for the kiosk application.
 * Hosts the NavHostFragment and observes [CallState] to drive navigation.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val PREF_SERVER_IP = "server_ip"
        private const val PREF_SERVER_PORT = "server_port"
        private const val DEFAULT_SERVER_IP = "192.168.1.100"
        private const val DEFAULT_SERVER_PORT = "8080"
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up NavController from the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Read server address from SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val serverIp = prefs.getString(PREF_SERVER_IP, DEFAULT_SERVER_IP)!!
        val serverPort = prefs.getString(PREF_SERVER_PORT, DEFAULT_SERVER_PORT)!!
        val serverUrl = "ws://$serverIp:$serverPort"

        // Initialize the ViewModel with the server URL
        viewModel.initialize(serverUrl)

        // Observe call state and navigate to the appropriate fragment
        observeCallState()
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    private fun observeCallState() {
        lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is CallState.Idle -> {
                        // Navigate back to waiting screen if not already there
                        val currentDest = navController.currentDestination?.id
                        if (currentDest != R.id.waitingFragment) {
                            navController.navigate(R.id.waitingFragment)
                        }
                    }

                    is CallState.Calling,
                    is CallState.Queued -> {
                        val currentDest = navController.currentDestination?.id
                        if (currentDest != R.id.queueFragment) {
                            navController.navigate(R.id.queueFragment)
                        }
                    }

                    is CallState.InCall -> {
                        val currentDest = navController.currentDestination?.id
                        if (currentDest != R.id.callFragment) {
                            navController.navigate(R.id.callFragment)
                        }
                    }

                    is CallState.Error -> {
                        // TODO: show error dialog or Snackbar
                        // Navigate back to waiting screen after error
                        navController.navigate(R.id.waitingFragment)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Options menu (for accessing Settings)
    // ---------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
