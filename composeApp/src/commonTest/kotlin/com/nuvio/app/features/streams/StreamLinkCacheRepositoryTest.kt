package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StreamLinkCacheRepositoryTest {

    @Test
    fun `movie cache key keeps legacy type and video id shape`() {
        val key = StreamLinkCacheRepository.contentKey(
            type = "movie",
            videoId = "tt123",
        )

        assertEquals("movie|tt123", key)
    }

    @Test
    fun `episode cache key is scoped to parent show and episode`() {
        val firstEpisode = StreamLinkCacheRepository.contentKey(
            type = "series",
            videoId = "video-id",
            parentMetaId = "tt999",
            season = 1,
            episode = 1,
        )
        val secondEpisode = StreamLinkCacheRepository.contentKey(
            type = "series",
            videoId = "video-id",
            parentMetaId = "tt999",
            season = 1,
            episode = 2,
        )

        assertNotEquals(firstEpisode, secondEpisode)
        assertEquals("series|tt999|s1|e1|video-id", firstEpisode)
    }

    @Test
    fun `session local p2p urls are never cached for replay`() {
        // A loopback URL from a previous engine session cannot be reused by a new process.
        assertEquals("", reusablePlaybackUrl("http://127.0.0.1:43210/stream/0.mkv"))
        assertEquals("", reusablePlaybackUrl("http://localhost:8080/video"))
        assertEquals(
            "https://cdn.example/Avengers.Endgame.2019.2160p.mkv",
            reusablePlaybackUrl("https://cdn.example/Avengers.Endgame.2019.2160p.mkv"),
        )
    }

    @Test
    fun `old cached entry keeps torrent identity after its loopback url is dropped`() {
        val legacy = CachedStreamLink(
            url = "http://127.0.0.1:43210/stream/0.mkv",
            streamName = "Torrentio 2160p",
            addonName = "Torrentio",
            addonId = "torrentio",
            cachedAtMs = 1_700_000_000_000L,
            infoHash = "abc123",
            fileIdx = 2,
            playbackConfirmed = true,
        )

        val sanitized = legacy.withoutSessionLocalUrl()

        assertEquals("", sanitized.url)
        assertEquals("abc123", sanitized.infoHash)
        assertEquals(2, sanitized.fileIdx)
        assertTrue(sanitized.playbackConfirmed)
    }
}
