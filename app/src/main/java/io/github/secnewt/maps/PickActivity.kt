package io.github.secnewt.maps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.secnewt.maps.handoff.MapApps
import io.github.secnewt.maps.link.LinkPipeline
import io.github.secnewt.maps.link.LinkReader
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.Outcome
import io.github.secnewt.maps.ui.PickerScreen
import io.github.secnewt.maps.ui.PickerState
import io.github.secnewt.maps.ui.coordinates
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opened by a maps link, a geo: link, or Share → Newt Maps. */
class PickActivity : ComponentActivity() {

    private val pipeline = LinkPipeline()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent.dataString
        }.orEmpty()
        val link = LinkReader.firstLink(shared)
        val apps = MapApps(this)
        setContent {
            NewtTheme {
                var state by remember { mutableStateOf<PickerState>(PickerState.Working("Reading link…")) }
                LaunchedEffect(shared) {
                    state = when (val outcome = withContext(Dispatchers.IO) { pipeline.run(shared) }) {
                        is Outcome.Found -> PickerState.Ready(outcome.place, apps.installed())
                        is Outcome.NotFound -> PickerState.Failed(
                            outcome.why,
                            canOpenBrowser = outcome.link != null && !outcome.link.startsWith("geo:"),
                            searchText = outcome.searchText,
                            apps = if (outcome.searchText != null) apps.installed() else emptyList(),
                        )
                    }
                }
                val place = (state as? PickerState.Ready)?.place
                PickerScreen(
                    state = state,
                    onOpenIn = { app ->
                        place?.let {
                            startActivity(apps.handoff(it, app))
                            finish()
                        }
                    },
                    onCopy = { place?.let(::copy) },
                    onOpenBrowser = { link?.let(::openInBrowser) },
                    onSearchIn = { app ->
                        (state as? PickerState.Failed)?.searchText?.let {
                            startActivity(apps.search(it, app))
                            finish()
                        }
                    },
                )
            }
        }
    }

    private fun copy(place: LinkResult.Place) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Coordinates", coordinates(place)))
    }

    private fun openInBrowser(link: String) {
        // A chooser, so the link doesn't bounce straight back into Newt Maps.
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(link)), "Open in browser"))
    }
}
