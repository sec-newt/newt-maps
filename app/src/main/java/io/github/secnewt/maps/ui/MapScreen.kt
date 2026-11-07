package io.github.secnewt.maps.ui

import io.github.secnewt.ui.NewtButton
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtTone
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import io.github.secnewt.maps.search.SearchHit
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.neonFieldColors
import io.github.secnewt.ui.neonOutline

/** What the card at the bottom of the map is showing. */
sealed interface MapCard {
    /** A street I tapped: its name in big type, so I don't have to read it off the map. */
    data class Street(val name: String, val kind: String?, val ref: String?) : MapCard

    /** A place a search or link found. */
    data class Place(val name: String, val lat: Double, val lng: Double, val address: String? = null) : MapCard

    /** Everything a search found, nearest first-ish; tap one to see it. */
    data class Results(val query: String, val hits: List<SearchHit>, val shortenedTo: String? = null) : MapCard

    /** Something went wrong or needs saying ("Couldn't find that"). */
    data class Note(val text: String) : MapCard
}

/**
 * The map, full screen, with as little on top of it as possible: a search box, and big icon
 * buttons for zoom and "where am I". Tapping a street shows its name in a card at the
 * bottom, in large type. Every icon has a name for screen readers.
 *
 * [map] is the map itself (MapLibre on the phone, a stand-in in tests).
 */
@Composable
fun MapScreen(
    map: @Composable () -> Unit,
    card: MapCard?,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    onSettings: () -> Unit,
    onCloseCard: () -> Unit,
    onShare: (MapCard.Place) -> Unit = {},
    onCopy: (MapCard.Place) -> Unit = {},
    onOpenElsewhere: (MapCard.Place) -> Unit = {},
    onDirections: (MapCard.Place) -> Unit = {},
    onPick: (SearchHit) -> Unit = {},
    /** A found route waiting for Start; replaces the card. */
    preview: RoutePreview? = null,
    onStart: () -> Unit = {},
    onToggleTolls: () -> Unit = {},
    onClosePreview: () -> Unit = {},
    /** While driving: the banner and trip bar replace everything else. */
    guidance: Guidance? = null,
    onMute: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        map()
        if (guidance != null) {
            TurnBanner(guidance, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
            TripBar(guidance, onMute, onStop, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
            return@Box
        }
        // The search box on top and the buttons and card at the bottom share one column, so a
        // tall card (big text, a long result list) shrinks and scrolls instead of pushing the
        // buttons up over the search box.
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
        ) {
            SearchBar(searching, onSearch, onSettings, Modifier)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.End,
                // Full width, so the buttons sit on the right even with no card open.
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(top = 12.dp),
            ) {
                MapButton(Icons.Filled.Add, "Zoom in", onZoomIn)
                MapButton(NewtIcons.ZoomOut, "Zoom out", onZoomOut)
                MapButton(NewtIcons.MyLocation, "Where am I", onMyLocation)
                if (preview != null) {
                    PreviewCard(preview, onStart, onToggleTolls, onClosePreview)
                } else if (card != null) {
                    CardView(card, onCloseCard, onShare, onCopy, onOpenElsewhere, onDirections, onPick, Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

@Composable
private fun SearchBar(searching: Boolean, onSearch: (String) -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val go = { if (text.isNotBlank()) { focus.clearFocus(); onSearch(text.trim()) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(if (searching) "Searching…" else "Search for a place") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            colors = neonFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go() }),
            trailingIcon = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clickable(onClickLabel = "Search", role = Role.Button) { go() }
                        .semantics { contentDescription = "Search" },
                ) { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
        )
        MapButton(Icons.Filled.Settings, "Settings", onSettings)
    }
}

/** A big square icon button with a solid back, so it reads over any part of the map. */
@Composable
private fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Int = 64) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(neonOutline(2.dp), shape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun CardView(
    card: MapCard,
    onClose: () -> Unit,
    onShare: (MapCard.Place) -> Unit,
    onCopy: (MapCard.Place) -> Unit,
    onOpenElsewhere: (MapCard.Place) -> Unit,
    onDirections: (MapCard.Place) -> Unit,
    onPick: (SearchHit) -> Unit,
    modifier: Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = neonOutline(2.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (card) {
                        is MapCard.Street -> {
                            Text(card.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                            val details = listOfNotNull(card.kind, card.ref).joinToString(" · ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is MapCard.Place -> {
                            Text(card.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                            if (!card.address.isNullOrEmpty()) Text(card.address, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "%.5f, %.5f".format(card.lat, card.lng),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = NewtFonts.Mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is MapCard.Note -> Text(card.text, style = MaterialTheme.typography.titleLarge)
                        is MapCard.Results -> Text(resultsTitle(card), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    }
                }
                MapButton(Icons.Filled.Close, "Close", onClose, size = 56)
            }
            if (card is MapCard.Results) {
                // Scrolls when there are more rows than fit (big text), leaving the top of the map visible.
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                ) {
                    card.hits.forEach { hit -> ResultRow(hit, onPick) }
                }
            }
            if (card is MapCard.Place) {
                // Directions is the one big green button; the rest say what they do.
                NewtPrimaryButton(
                    "Directions",
                    onClick = { onDirections(card) },
                    icon = NewtIcons.Directions,
                    tone = NewtTone.Good,
                    large = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewtButton("Share", onClick = { onShare(card) }, icon = Icons.Filled.Share)
                    NewtButton("Copy", onClick = { onCopy(card) }, icon = NewtIcons.Copy, modifier = Modifier.semantics { contentDescription = "Copy coordinates" })
                    NewtButton("Open in", onClick = { onOpenElsewhere(card) }, icon = NewtIcons.OpenElsewhere, modifier = Modifier.semantics { contentDescription = "Open in another map app" })
                }
            }
        }
    }
}

internal fun resultsTitle(card: MapCard.Results): String {
    val count = if (card.hits.size == 1) "1 place" else "${card.hits.size} places"
    return if (card.shortenedTo != null) {
        "Nothing for \"${card.query}\". $count for \"${card.shortenedTo}\":"
    } else {
        "$count for \"${card.query}\""
    }
}

/** "0.3 mi", "4.2 mi", "25 mi". */
internal fun milesText(miles: Double): String = when {
    miles < 0.1 -> "< 0.1 mi"
    miles < 10 -> "%.1f mi".format(miles)
    else -> "%.0f mi".format(miles)
}

/** One search result: a big row with the name, the street and town, and how far. */
@Composable
private fun ResultRow(hit: SearchHit, onPick: (SearchHit) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val distance = hit.miles?.let(::milesText)
    val spoken = listOfNotNull(hit.name, hit.address.ifEmpty { null }, distance?.replace("mi", "miles")).joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(neonOutline(2.dp), shape)
            .clickable(onClickLabel = "Show on the map", role = Role.Button) { onPick(hit) }
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(hit.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (hit.address.isNotEmpty()) Text(hit.address, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (distance != null) Text(distance, style = MaterialTheme.typography.titleMedium, fontFamily = NewtFonts.Mono, color = MaterialTheme.colorScheme.primary)
    }
}
