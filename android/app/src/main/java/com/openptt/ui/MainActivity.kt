package com.openptt.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.openptt.ui.navigation.NavGraph
import com.openptt.ui.theme.OpenPTTTheme
import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.openptt.data.local.TokenManager
import com.openptt.data.local.SettingsDataStore
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val requestPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            // Permissions handled
        }

        val permissionsToRequest = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }

        // Request to ignore battery optimizations so the app doesn't get killed in the background
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                // Ignore if device doesn't support this intent
            }
        }

        val hasServerUrl = runBlocking { settingsDataStore.serverUrlFlow.first().isNotEmpty() }
        val isLoggedIn = tokenManager.isLoggedIn

        val startRoute = if (hasServerUrl) {
            if (isLoggedIn) com.openptt.ui.navigation.Routes.HOME else com.openptt.ui.navigation.Routes.LOGIN
        } else {
            com.openptt.ui.navigation.Routes.SERVER_SETUP
        }

        setContent {
            val theme by settingsDataStore.themeFlow.collectAsState(initial = "system")
            val darkTheme = when (theme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            OpenPTTTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(startDestination = startRoute)
                }
            }
        }
    }
}
