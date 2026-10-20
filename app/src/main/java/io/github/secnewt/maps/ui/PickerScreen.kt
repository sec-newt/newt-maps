package io.github.secnewt.maps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.maps.handoff.MapApp
import io.github.secnewt.maps.handoff.formatCoord
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.Why
import io.github.secnewt.ui.neonOutline

sealed interface PickerState {
    data class Working(val message: String) : PickerState
    data class Ready(val place: LinkResult.Place, val apps: List<MapApp>) : PickerState
    data class Failed(val why: Why, val canOpenBrowser: Boolean) : PickerState
}

fun whyText(why: Why): String = when (why) {
    Why.NO_LINK -> "No maps link in what was shared"
    Why.NO_LOCATION -> "No location in this link"
    Why.NO_INTERNET -> "No internet"
    Why.NOTHING_FOUND -> "Nothing found for this address"
}

/** "32.749302, -96.681877": what the picker shows and what Copy puts on the clipboard. */
fun coordinates(place: LinkResult.Place): String = "${formatCoord(place.lat)}, ${formatCoord(place.lng)}"

@Composable
fun PickerScreen(
    state: PickerState,
    onOpenIn: (MapApp) -> Unit,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
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
            when (state) {
                is PickerState.Working -> {
                    CircularProgressIndicator()
                    Text(state.message, style = MaterialTheme.typography.headlineSmall)
                }
                is PickerState.Ready -> Ready(state, onOpenIn, onCopy, onOpenBrowser)
                is PickerState.Failed -> Failed(state, onOpenBrowser)
            }
        }
    }
}

@Composable
private fun Ready(state: PickerState.Ready, onOpenIn: (MapApp) -> Unit, onCopy: () -> Unit, onOpenBrowser: () -> Unit) {
    val place = state.place
    Heading(place.name ?: "Dropped pin")
    place.address?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
    Text(coordinates(place), style = MaterialTheme.typography.titleMedium)
    if (state.apps.isEmpty()) {
        Text(
            "No map app is installed. Install Organic Maps, CoMaps or Magic Earth.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    state.apps.forEach { app -> BigButton("Open in ${app.label}", primary = true) { onOpenIn(app) } }
    BigButton("Copy coordinates", onClick = onCopy)
    BigButton("Open in browser", onClick = onOpenBrowser)
}

@Composable
private fun Failed(state: PickerState.Failed, onOpenBrowser: () -> Unit) {
    Heading("Couldn't find it")
    Text(whyText(state.why), style = MaterialTheme.typography.titleLarge)
    if (state.canOpenBrowser) BigButton("Open in browser", primary = true, onClick = onOpenBrowser)
}

@Composable
private fun Heading(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.semantics { heading() },
)

@Composable
private fun BigButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (primary) {
        Button(onClick = onClick, modifier = modifier) { Text(text, style = MaterialTheme.typography.titleLarge) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, border = neonOutline()) {
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
    }
}
