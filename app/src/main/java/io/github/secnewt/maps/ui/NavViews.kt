package io.github.secnewt.maps.ui

import io.github.secnewt.ui.NewtButton
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtTone
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.secnewt.maps.nav.Turn
import io.github.secnewt.ui.NewtFonts
import io.github.secnewt.ui.neonOutline

/** A found route, before starting: how long, how far, and what it avoids. */
data class RoutePreview(
    val placeName: String,
    val duration: String,
    val distance: String,
    val arrival: String,
    val avoidTolls: Boolean,
    val practice: Boolean,
    val avoidHighways: Boolean = false,
    /** The roads I've marked to avoid, and whether this trip avoids each. */
    val roads: List<RoadChoice> = emptyList(),
)

/** A marked road on the route card's list: its name, and whether this trip avoids it. */
data class RoadChoice(val name: String, val on: Boolean)

/** What the route card's avoid buttons do. Each change finds the route again. */
class AvoidActions(
    val onToggleTolls: () -> Unit = {},
    val onToggleHighways: () -> Unit = {},
    val onSetRoad: (String, Boolean) -> Unit = { _, _ -> },
    val onRemoveRoad: (String) -> Unit = {},
)

/** What to show while driving. */
data class Guidance(
    val turn: Turn,
    /** Distance to the turn: "0.3 mi". */
    val distance: String,
    /** Usually the street to turn onto: "Main Street". */
    val road: String,
    /** The full sentence, for screen readers: "Turn right onto Main Street." */
    val spoken: String,
    /** The turn after this one, if it comes soon: "Then Broadway Boulevard". */
    val then: String?,
    val timeLeft: String,
    val distanceLeft: String,
    val arrival: String,
    val muted: Boolean,
    val rerouting: Boolean,
    val practice: Boolean,
)

/** The arrow icon for each kind of turn. */
fun Turn.icon(): ImageVector = when (this) {
    Turn.STRAIGHT -> NewtIcons.Straight
    Turn.SLIGHT_LEFT -> NewtIcons.TurnSlightLeft
    Turn.LEFT -> NewtIcons.TurnLeft
    Turn.SHARP_LEFT -> NewtIcons.TurnSharpLeft
    Turn.SLIGHT_RIGHT -> NewtIcons.TurnSlightRight
    Turn.RIGHT -> NewtIcons.TurnRight
    Turn.SHARP_RIGHT -> NewtIcons.TurnSharpRight
    Turn.U_TURN -> NewtIcons.UTurnLeft
    Turn.MERGE -> NewtIcons.Merge
    Turn.FORK_LEFT -> NewtIcons.ForkLeft
    Turn.FORK_RIGHT -> NewtIcons.ForkRight
    Turn.RAMP_LEFT -> NewtIcons.RampLeft
    Turn.RAMP_RIGHT -> NewtIcons.RampRight
    Turn.ROUNDABOUT_LEFT -> NewtIcons.RoundaboutLeft
    Turn.ROUNDABOUT_RIGHT -> NewtIcons.RoundaboutRight
    Turn.ARRIVE -> NewtIcons.Arrive
}

/** Green, like the house "go" color, with dark text on it. */
private val Go = Color(0xFF9ECE6A)
private val OnGo = Color(0xFF0B0C10)

/** The card for a found route: time, distance, arrival, tolls, and a big Start. */
@Composable
internal fun PreviewCard(preview: RoutePreview, onStart: () -> Unit, avoid: AvoidActions, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var listOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = neonOutline(2.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        // Start comes before the avoid choices and the card scrolls, so with big text Start
        // is never the part pushed off the screen.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(preview.placeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${preview.duration} · ${preview.distance}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text("Arrive ${preview.arrival}", style = MaterialTheme.typography.titleMedium)
                    if (preview.practice) Text("Practice drive: you won't need to move", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary)
                }
                NavIconButton(Icons.Filled.Close, "Close", onClose)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // The one action that matters here: big, green, with its word.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Go)
                        .clickable(onClickLabel = "Start", role = Role.Button, onClick = onStart)
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(NewtIcons.Start, contentDescription = null, tint = OnGo, modifier = Modifier.size(32.dp))
                    Text("Start", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = OnGo)
                }
            }
            // Words here: no toll or highway icon is clear enough on its own. They wrap onto
            // more rows with big text.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AvoidToggle("No tolls", "tolls", preview.avoidTolls, avoid.onToggleTolls)
                AvoidToggle("No highways", "highways", preview.avoidHighways, avoid.onToggleHighways)
                val avoiding = preview.roads.count { it.on }
                AvoidToggle(
                    if (avoiding == 0) "Avoid roads" else "Avoiding $avoiding",
                    null,
                    avoiding > 0,
                    { listOpen = true },
                )
            }
        }
    }
    if (listOpen) AvoidRoadsDialog(preview.roads, avoid, onClose = { listOpen = false })
}

/** The roads I've marked: a switch each for this trip, and a way to forget one. */
@Composable
private fun AvoidRoadsDialog(roads: List<RoadChoice>, avoid: AvoidActions, onClose: () -> Unit) {
    var forgetting by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Roads to avoid", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (roads.isEmpty()) {
                    Text("None yet. Press and hold a road on the map to avoid it there.", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Turn a road off to drive on it this trip. Press and hold a road on the map to add one.", style = MaterialTheme.typography.bodyLarge)
                }
                roads.forEach { road ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { AvoidToggle(road.name, road.name, road.on) { avoid.onSetRoad(road.name, !road.on) } }
                        NavIconButton(Icons.Filled.Delete, "Forget ${road.name}", { forgetting = road.name }, danger = true)
                    }
                }
            }
        },
        confirmButton = { NewtPrimaryButton("Done", onClick = onClose) },
    )
    forgetting?.let { name ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Forget $name?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Routes can use it again. You can mark it again any time.", style = MaterialTheme.typography.titleMedium) },
            confirmButton = { NewtPrimaryButton("Forget", onClick = { avoid.onRemoveRoad(name); forgetting = null }, tone = NewtTone.Danger) },
            dismissButton = { NewtButton("Keep", onClick = { forgetting = null }) },
        )
    }
}

/**
 * A choice on the route card, filled with the gradient when on. Tapping a switch ([what]
 * set: "tolls") finds the route again; without [what] it's a button that opens something.
 */
@Composable
private fun AvoidToggle(label: String, what: String?, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fill = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .then(if (on) Modifier.background(fill) else Modifier.background(MaterialTheme.colorScheme.surface))
            .border(neonOutline(2.dp), shape)
            .then(
                if (what != null) {
                    Modifier.clickable(onClickLabel = if (on) "Allow $what" else "Avoid $what", role = Role.Switch, onClick = onClick)
                        .semantics { stateDescription = if (on) "On" else "Off" }
                } else {
                    Modifier.clickable(onClickLabel = "Choose roads to avoid", role = Role.Button, onClick = onClick)
                },
            )
            .padding(horizontal = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}

/** The big banner at the top while driving: the arrow, how far, and the street. */
@Composable
internal fun TurnBanner(guidance: Guidance, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = neonOutline(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                // One sentence for screen readers, said again whenever the turn changes.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "In ${guidance.distance}: ${guidance.spoken}"
                    liveRegion = LiveRegionMode.Polite
                },
            ) {
                Icon(guidance.turn.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
                Column(Modifier.weight(1f)) {
                    Text(guidance.distance, style = MaterialTheme.typography.displaySmall, fontFamily = NewtFonts.Mono, fontWeight = FontWeight.Bold)
                    Text(guidance.road, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (guidance.rerouting) {
                Text("Finding a new route…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            } else if (guidance.then != null) {
                // The next turn on its own lifted line, so it never competes with this one.
                Text(
                    guidance.then,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The strip at the bottom while driving: time and distance left, voice, and stop. */
@Composable
internal fun TripBar(guidance: Guidance, onMute: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = neonOutline(2.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(guidance.timeLeft, style = MaterialTheme.typography.headlineMedium, fontFamily = NewtFonts.Mono, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(guidance.distanceLeft, "arrive ${guidance.arrival}", if (guidance.practice) "practice" else null).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                if (guidance.muted) {
                    NewtButton("Voice off", onClick = onMute, icon = NewtIcons.Muted, large = true, modifier = Modifier.weight(1f).fillMaxHeight())
                } else {
                    NewtPrimaryButton("Voice on", onClick = onMute, icon = NewtIcons.Voice, large = true, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                NewtPrimaryButton(
                    "End",
                    onClick = onStop,
                    icon = Icons.Filled.Close,
                    tone = NewtTone.Danger,
                    large = true,
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "Stop navigation" },
                )
            }
        }
    }
}

/**
 * A 56dp icon button in the house outline. [on] fills it with the blue-to-purple gradient
 * and says "On" to screen readers; [danger] makes it red.
 */
@Composable
private fun NavIconButton(icon: ImageVector, description: String, onClick: () -> Unit, on: Boolean? = null, danger: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    val fill = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .then(if (on == true) Modifier.background(fill) else Modifier.background(MaterialTheme.colorScheme.surface))
            .border(if (danger) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error) else neonOutline(2.dp), shape)
            .clickable(onClickLabel = description, role = if (on != null) Role.Switch else Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (on != null) stateDescription = if (on) "On" else "Off"
            },
    ) {
        Icon(
            icon, contentDescription = null, modifier = Modifier.size(28.dp),
            tint = when {
                on == true -> MaterialTheme.colorScheme.onPrimary
                danger -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}
