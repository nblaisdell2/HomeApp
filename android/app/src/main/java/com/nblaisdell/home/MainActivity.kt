package com.nblaisdell.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.nblaisdell.home.data.repository.DeviceSetupRepository
import com.nblaisdell.home.data.repository.KeyCheck
import com.nblaisdell.home.ui.navigation.HomeNavHost
import com.nblaisdell.home.ui.theme.HomeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deviceSetup: DeviceSetupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeTheme {
                HomeNavHost()
            }
        }
        if (savedInstanceState == null) handleProvisioningIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleProvisioningIntent(intent)
    }

    /**
     * Lets the PC set the device key over adb (see README / scripts/set-device-key.ps1):
     *   adb shell am start -n <package>/com.nblaisdell.home.MainActivity --es apiKey home_...
     */
    private fun handleProvisioningIntent(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_API_KEY) ?: return
        intent.removeExtra(EXTRA_API_KEY)
        lifecycleScope.launch {
            val message = when (val result = deviceSetup.provisionIfMissing(key)) {
                null -> "This device already has a key. Replace it in Settings."
                is KeyCheck.Accepted -> "Device set up as \"${result.deviceName}\"."
                KeyCheck.Rejected -> "The server rejected that key."
                KeyCheck.NotFormattedLikeAKey -> "That doesn't look like a device key."
                is KeyCheck.Unreachable -> "Key saved; the server couldn't be reached yet."
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val EXTRA_API_KEY = "apiKey"
    }
}
