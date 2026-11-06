package io.github.secnewt.maps.ui

import io.github.secnewt.ui.NewtSteps
import io.github.secnewt.ui.NewtStep
import io.github.secnewt.ui.NewtStepState
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtSettingsCard
import io.github.secnewt.ui.NewtSwitchRow
import io.github.secnewt.ui.NewtInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
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

/**
 * Setup and settings: numbered steps (a map app, link opening, try it), what happens after,
 * and in Settings the practice-drive switch. [mapApp] is the first map app found, if any.
 */
@Composable
fun HomeScreen(
    linksNeedTurningOn: Boolean,
    onTurnOn: () -> Unit,
    onBack: (() -> Unit)? = null,
    practice: Boolean = false,
    onPractice: (Boolean) -> Unit = {},
    mapApp: String? = null,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) {
                io.github.secnewt.ui.ScreenHeader("Settings", onBack)
            } else {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text("Open Google and Apple Maps links in the map app you pick.", style = MaterialTheme.typography.bodyLarge)
            val hasApp = mapApp != null
            NewtSteps(
                listOf(
                    NewtStep(
                        "Install a map app",
                        if (hasApp) NewtStepState.Done else NewtStepState.Now,
                        hint = if (hasApp) "$mapApp found" else "Install Organic Maps, CoMaps or Magic Earth",
                    ),
                    NewtStep(
                        "Turn on link opening",
                        when {
                            !linksNeedTurningOn -> NewtStepState.Done
                            hasApp -> NewtStepState.Now
                            else -> NewtStepState.ToDo
                        },
                        hint = if (linksNeedTurningOn) "One time. Turn on every link on the next screen." else "On",
                        action = { NewtPrimaryButton("Turn on", onClick = onTurnOn, modifier = Modifier.fillMaxWidth()) },
                    ),
                    NewtStep(
                        "Try it",
                        if (hasApp && !linksNeedTurningOn) NewtStepState.Now else NewtStepState.ToDo,
                        hint = "Tap any maps link, or Share → Newt Maps",
                    ),
                ),
            )
            NewtSettingsCard("Then it does this", icon = Icons.Filled.Place) {
                Text(
                    "maps.google.com/… → opens in ${mapApp ?: "your map app"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            if (onBack != null) {
                NewtSettingsCard("Driving", icon = Icons.Filled.Settings) {
                    NewtSwitchRow("Practice drives", practice, onPractice, hint = "Drives the route by itself, so you can try it")
                    NewtInfo(
                        "Routes come from your own server on persephone, over Tailscale. The map comes from OpenFreeMap, " +
                            "using OpenStreetMap data; it sees which area you look at, not who you are. Search uses " +
                            "OpenStreetMap's Nominatim and sends only what you type.",
                        label = "Where maps and routes come from",
                    )
                }
            }
        }
    }
}
