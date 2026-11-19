package com.nuvio.app.features.details

import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.time.daysUntilEpisodeRelease
import com.nuvio.app.core.time.parseEpisodeReleaseLocalDate
import com.nuvio.app.features.home.isUnreleased

/**
 * Release window of a movie, used to keep playback/download locked until the movie is actually
 * available to stream (OTT release), not merely out in theaters.
 */
enum class MovieReleasePhase {
    /** Theatrical release is still ahead. */
    Theatrical,

    /** Theatrical run has started/ended but no OTT date is known yet. */
    AwaitingOtt,

    /** OTT release date is known and still in the future. */
    OttScheduled,

    /** OTT release day has arrived (or the movie is old enough to be safely considered out). */
    Released,
}

data class MovieReleaseStatus(
    val phase: MovieReleasePhase,
    /** Next milestone date used for the countdown and reminders. */
    val targetDate: String? = null,
    val theatricalDate: String? = null,
    val ottDate: String? = null,
    val daysUntilTarget: Int? = null,
) {
    val isReleased: Boolean get() = phase == MovieReleasePhase.Released
    val isBlocked: Boolean get() = !isReleased
}

/**
 * How long after the theatrical release we keep waiting for an OTT date before assuming a movie
 * without digital release metadata is already available. This only matters for titles whose
 * provider/TMDB data has no digital release entry; without it older catalog movies would stay
 * locked forever.
 */
private const val OttFallbackWindowDays = 120

/**
 * Resolves the current release phase for a movie. Returns null for series (episode gating is
 * handled separately) and for titles that are not movies.
 */
internal fun MetaDetails.movieReleaseStatus(
    nowEpochMs: Long = EpisodeReleaseDatePlatform.nowEpochMs(),
    todayIsoDate: String = EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(nowEpochMs).orEmpty(),
): MovieReleaseStatus? {
    if (!type.equals("movie", ignoreCase = true) && !type.equals("film", ignoreCase = true)) return null
    // Movies can carry flat embedded video entries; only real episode lists mean "series".
    if (videos.any { (it.season ?: 0) > 0 || (it.episode ?: 0) > 0 }) return null

    val ottDate = parseEpisodeReleaseLocalDate(ottReleaseDate)
    val explicitTheatricalDate = parseEpisodeReleaseLocalDate(theatricalReleaseDate)
    // Provider meta usually only exposes one generic release date. When that is all we have, the
    // movie is treated as available on that date instead of waiting months for OTT metadata.
    val fallbackReleaseDate = parseEpisodeReleaseLocalDate(releaseDate)
        ?: parseEpisodeReleaseLocalDate(releaseInfo)
    val theatricalDate = explicitTheatricalDate ?: fallbackReleaseDate

    // An explicitly announced theatrical window is shown first, even when the OTT date is
    // already known: the countdown follows the next milestone the viewer can actually reach.
    if (explicitTheatricalDate != null) {
        val theatricalDays = daysUntilEpisodeRelease(todayIsoDate, explicitTheatricalDate)
        if (theatricalDays != null && theatricalDays > 0) {
            return MovieReleaseStatus(
                phase = MovieReleasePhase.Theatrical,
                targetDate = explicitTheatricalDate,
                theatricalDate = explicitTheatricalDate,
                ottDate = ottDate,
                daysUntilTarget = theatricalDays,
            )
        }
    }

    if (ottDate != null) {
        val days = daysUntilEpisodeRelease(todayIsoDate, ottDate)
        return if (days != null && days > 0) {
            MovieReleaseStatus(
                phase = MovieReleasePhase.OttScheduled,
                targetDate = ottDate,
                theatricalDate = theatricalDate,
                ottDate = ottDate,
                daysUntilTarget = days,
            )
        } else {
            MovieReleaseStatus(
                phase = MovieReleasePhase.Released,
                theatricalDate = theatricalDate,
                ottDate = ottDate,
                daysUntilTarget = days,
            )
        }
    }

    if (theatricalDate != null) {
        val days = daysUntilEpisodeRelease(todayIsoDate, theatricalDate)
        return when {
            days == null -> MovieReleaseStatus(phase = MovieReleasePhase.Released)
            days > 0 -> MovieReleaseStatus(
                phase = MovieReleasePhase.Theatrical,
                targetDate = theatricalDate,
                theatricalDate = theatricalDate,
                daysUntilTarget = days,
            )
            days > -OttFallbackWindowDays && explicitTheatricalDate != null -> MovieReleaseStatus(
                phase = MovieReleasePhase.AwaitingOtt,
                theatricalDate = theatricalDate,
                daysUntilTarget = days,
            )
            else -> MovieReleaseStatus(
                phase = MovieReleasePhase.Released,
                theatricalDate = theatricalDate,
                daysUntilTarget = days,
            )
        }
    }

    return if (isUnreleased(nowEpochMs = nowEpochMs, todayIsoDate = todayIsoDate)) {
        MovieReleaseStatus(phase = MovieReleasePhase.Theatrical)
    } else {
        MovieReleaseStatus(phase = MovieReleasePhase.Released)
    }
}
