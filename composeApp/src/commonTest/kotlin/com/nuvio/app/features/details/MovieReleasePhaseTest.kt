package com.nuvio.app.features.details

import com.nuvio.app.core.time.parseEpisodeReleaseEpochMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovieReleasePhaseTest {
    private val nowEpochMs = requireNotNull(parseEpisodeReleaseEpochMs("2026-05-06T12:00:00Z"))
    private val today = "2026-05-06"

    @Test
    fun `future theatrical release is in theatrical phase`() {
        val status = movie(theatricalReleaseDate = "2026-06-15").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.Theatrical, status?.phase)
        assertEquals("2026-06-15", status?.targetDate)
        assertEquals(40, status?.daysUntilTarget)
        assertTrue(status?.isBlocked == true)
    }

    @Test
    fun `theatrical release that passed without ott date awaits ott`() {
        val status = movie(theatricalReleaseDate = "2026-04-01").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.AwaitingOtt, status?.phase)
        assertNull(status?.targetDate)
        assertTrue(status?.isBlocked == true)
    }

    @Test
    fun `upcoming theatrical window is shown before a known ott date`() {
        val status = movie(
            theatricalReleaseDate = "2026-06-01",
            ottReleaseDate = "2026-07-20",
        ).movieReleaseStatus(nowEpochMs = nowEpochMs, todayIsoDate = today)

        assertEquals(MovieReleasePhase.Theatrical, status?.phase)
        assertEquals("2026-06-01", status?.targetDate)
        assertEquals(26, status?.daysUntilTarget)
    }

    @Test
    fun `future ott date schedules streaming countdown`() {
        val status = movie(
            theatricalReleaseDate = "2026-03-01",
            ottReleaseDate = "2026-05-20",
        ).movieReleaseStatus(nowEpochMs = nowEpochMs, todayIsoDate = today)

        assertEquals(MovieReleasePhase.OttScheduled, status?.phase)
        assertEquals("2026-05-20", status?.targetDate)
        assertEquals(14, status?.daysUntilTarget)
        assertTrue(status?.isBlocked == true)
    }

    @Test
    fun `ott release day unlocks playback`() {
        val status = movie(ottReleaseDate = "2026-05-06").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.Released, status?.phase)
        assertFalse(status?.isBlocked == true)
    }

    @Test
    fun `past ott date unlocks playback`() {
        val status = movie(ottReleaseDate = "2026-04-01").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.Released, status?.phase)
        assertTrue(status?.isReleased == true)
    }

    @Test
    fun `old theatrical release without ott metadata is treated as released`() {
        val status = movie(theatricalReleaseDate = "2025-10-01").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.Released, status?.phase)
    }

    @Test
    fun `passed generic release date without ott metadata is treated as released`() {
        val status = movie(releaseDate = "2026-04-01").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.Released, status?.phase)
        assertFalse(status?.isBlocked == true)
    }

    @Test
    fun `future primary release date falls back to theatrical phase`() {
        val status = movie(releaseDate = "2026-07-01").movieReleaseStatus(
            nowEpochMs = nowEpochMs,
            todayIsoDate = today,
        )

        assertEquals(MovieReleasePhase.Theatrical, status?.phase)
        assertEquals("2026-07-01", status?.targetDate)
    }

    @Test
    fun `movie with flat embedded video entries is still gated`() {
        val status = MetaDetails(
            id = "movie-embedded",
            type = "movie",
            name = "Embedded",
            ottReleaseDate = "2026-06-01",
            videos = listOf(MetaVideo(id = "movie-embedded", title = "Embedded")),
        ).movieReleaseStatus(nowEpochMs = nowEpochMs, todayIsoDate = today)

        assertEquals(MovieReleasePhase.OttScheduled, status?.phase)
        assertTrue(status?.isBlocked == true)
    }

    @Test
    fun `series are not gated by movie release phases`() {
        val status = MetaDetails(
            id = "series-1",
            type = "series",
            name = "Series",
            ottReleaseDate = "2026-06-01",
        ).movieReleaseStatus(nowEpochMs = nowEpochMs, todayIsoDate = today)

        assertNull(status)
    }

    @Test
    fun `missing dates keep released titles playable`() {
        val status = movie().movieReleaseStatus(nowEpochMs = nowEpochMs, todayIsoDate = today)

        assertEquals(MovieReleasePhase.Released, status?.phase)
    }

    private fun movie(
        releaseDate: String? = null,
        theatricalReleaseDate: String? = null,
        ottReleaseDate: String? = null,
    ): MetaDetails = MetaDetails(
        id = "movie-1",
        type = "movie",
        name = "Movie",
        releaseDate = releaseDate,
        theatricalReleaseDate = theatricalReleaseDate,
        ottReleaseDate = ottReleaseDate,
    )
}
