package io.github.secnewt.maps.ui

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.maps.R

/** True once every maps host in the manifest is turned on under "Open by default". */
fun linkHostsAllOn(context: Context): Boolean {
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = manager.getDomainVerificationUserState(context.packageName) ?: return false
    return state.hostToStateMap.values.all { it != DomainVerificationUserState.DOMAIN_STATE_NONE }
}

@Composable
fun HomeScreen(
    linksNeedTurningOn: Boolean,
    onTurnOn: () -> Unit,
    onBack: (() -> Unit)? = null,
    practice: Boolean = false,
    onPractice: (Boolean) -> Unit = {},
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                io.github.secnewt.ui.ScreenHeader("Settings", onBack)
            } else {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (linksNeedTurningOn) {
                Text(
                    "Google and Apple Maps links open in their own apps until you turn this on. It's one time.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text("Turn on link opening", style = MaterialTheme.typography.titleLarge)
                }
                Text("On the next screen: Add links → turn on every link.", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Tap a maps link, or Share → Newt Maps. Then pick the map app to open it in.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (onBack != null) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .toggleable(value = practice, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onPractice),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Practice drives", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Directions drive the route by themselves, so you can try navigation without moving.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(checked = practice, onCheckedChange = null)
                }
                Text(
                    "Routes come from your own server on persephone, over Tailscale.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "The map comes from OpenFreeMap, using OpenStreetMap data. It sees which area you look at, not who you are. " +
                        "Search uses OpenStreetMap's Nominatim and sends only what you type.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
