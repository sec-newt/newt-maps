package io.github.secnewt.maps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.secnewt.maps.ui.HomeScreen
import io.github.secnewt.maps.ui.linkHostsAllOn
import io.github.secnewt.ui.NewtTheme

class MainActivity : ComponentActivity() {
    private val needsTurningOn = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewtTheme {
                HomeScreen(linksNeedTurningOn = needsTurningOn.value, onTurnOn = {
                    startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$packageName")))
                })
            }
        }
    }

    // Checked on every return, since the user comes back here from Settings.
    override fun onResume() {
        super.onResume()
        needsTurningOn.value = !linkHostsAllOn(this)
    }
}
