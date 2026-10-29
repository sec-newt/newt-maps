package io.github.secnewt.maps.ui

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

/** A found route, before starting: how long, how far, and the toll choice. */
data class RoutePreview(
    val placeName: String,
    val duration: String,
    val distance: String,
    val arrival: String,
    val avoidTolls: Boolean,
    val practice: Boolean,
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
internal fun PreviewCard(preview: RoutePreview, onStart: () -> Unit, onToggleTolls: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = neonOutline(2.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                NavIconButton(NewtIcons.Toll, "Avoid tolls", onToggleTolls, on = preview.avoidTolls)
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
        }
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
                Text(guidance.then, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${guidance.timeLeft} · ${guidance.distanceLeft}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (guidance.practice) "Arrive ${guidance.arrival} · practice" else "Arrive ${guidance.arrival}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NavIconButton(if (guidance.muted) NewtIcons.Muted else NewtIcons.Voice, "Voice", onMute, on = !guidance.muted)
            NavIconButton(Icons.Filled.Close, "Stop navigation", onStop, danger = true)
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
