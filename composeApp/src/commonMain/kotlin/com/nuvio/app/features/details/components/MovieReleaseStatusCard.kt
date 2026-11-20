package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.time.parseIsoCalendarDate
import com.nuvio.app.features.details.MovieReleasePhase
import com.nuvio.app.features.details.MovieReleaseStatus
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.movie_release_awaiting_ott_body
import nuvio.composeapp.generated.resources.movie_release_awaiting_ott_title
import nuvio.composeapp.generated.resources.movie_release_coming_soon
import nuvio.composeapp.generated.resources.movie_release_countdown_days
import nuvio.composeapp.generated.resources.movie_release_countdown_hours
import nuvio.composeapp.generated.resources.movie_release_in_cinemas
import nuvio.composeapp.generated.resources.movie_release_ott_body
import nuvio.composeapp.generated.resources.movie_release_ott_title
import nuvio.composeapp.generated.resources.movie_release_remind_me
import nuvio.composeapp.generated.resources.movie_release_reminder_set
import nuvio.composeapp.generated.resources.movie_release_theatrical_body
import nuvio.composeapp.generated.resources.movie_release_today
import org.jetbrains.compose.resources.stringResource

private val ShortMonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

internal fun formatMovieReleaseDate(isoDate: String?): String? {
    val date = parseIsoCalendarDate(isoDate) ?: return null
    val year = date.substring(0, 4)
    val month = date.substring(5, 7).toIntOrNull() ?: return date
    val day = date.substring(8, 10).toIntOrNull() ?: return date
    val monthName = ShortMonthNames.getOrElse(month - 1) { return date }
    return "$day $monthName $year"
}

@Composable
internal fun MovieReleaseStatusCard(
    status: MovieReleaseStatus,
    reminderSet: Boolean,
    onReminderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!status.isBlocked) return

    val title = when (status.phase) {
        MovieReleasePhase.Theatrical -> if (status.targetDate != null) {
            stringResource(Res.string.movie_release_in_cinemas)
        } else {
            stringResource(Res.string.movie_release_coming_soon)
        }
        MovieReleasePhase.AwaitingOtt -> stringResource(Res.string.movie_release_awaiting_ott_title)
        MovieReleasePhase.OttScheduled -> stringResource(Res.string.movie_release_ott_title)
        MovieReleasePhase.Released -> return
    }
    val releaseDate = formatMovieReleaseDate(status.targetDate)
    val body = when (status.phase) {
        MovieReleasePhase.Theatrical ->
            releaseDate?.let { stringResource(Res.string.movie_release_theatrical_body, it) }
        MovieReleasePhase.AwaitingOtt -> stringResource(Res.string.movie_release_awaiting_ott_body)
        MovieReleasePhase.OttScheduled ->
            releaseDate?.let { stringResource(Res.string.movie_release_ott_body, it) }
        MovieReleasePhase.Released -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            body?.let { bodyText ->
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ReleaseCountdownText(targetDate = status.targetDate)
        }
        Spacer(modifier = Modifier.width(10.dp))
        FilledTonalButton(
            onClick = onReminderClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = if (reminderSet) {
                    Icons.Default.NotificationsActive
                } else {
                    Icons.Default.NotificationsNone
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (reminderSet) Res.string.movie_release_reminder_set else Res.string.movie_release_remind_me,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ReleaseCountdownText(targetDate: String?) {
    val targetEpochMs = remember(targetDate) {
        targetDate?.let { date ->
            EpisodeReleaseDatePlatform.localDateTimeToEpochMs("${date}T00:00:00.000")
        }
    } ?: return

    var nowEpochMs by remember(targetDate) {
        mutableStateOf(EpisodeReleaseDatePlatform.nowEpochMs())
    }
    LaunchedEffect(targetEpochMs) {
        while (true) {
            nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
            delay(1_000L)
        }
    }

    val remainingMs = (targetEpochMs - nowEpochMs).coerceAtLeast(0L)
    if (remainingMs <= 0L) {
        Text(
            text = stringResource(Res.string.movie_release_today),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }

    val totalMinutes = remainingMs / 60_000L
    val days = totalMinutes / (60L * 24L)
    val hours = (totalMinutes / 60L) % 24L
    val minutes = totalMinutes % 60L
    val seconds = (remainingMs / 1_000L) % 60L
    // Compose resources only support "%n$d"/"%n$s" (no width/zero-pad), so pad here.
    val hoursText = hours.toInt().toString().padStart(2, '0')
    val minutesText = minutes.toInt().toString().padStart(2, '0')
    val secondsText = seconds.toInt().toString().padStart(2, '0')
    val text = if (days > 0L) {
        stringResource(
            Res.string.movie_release_countdown_days,
            days.toInt(),
            hoursText,
            minutesText,
        )
    } else {
        stringResource(
            Res.string.movie_release_countdown_hours,
            hoursText,
            minutesText,
            secondsText,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}
