package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZyloStreamsTest {
    @Test
    fun buildsMovieAndEpisodeDiscoveryIds() {
        assertEquals(
            listOf("tt0133093", "tmdb:603"),
            ZyloStreams.buildQueryIds(false, "603", "tt0133093", "603", 1, 1),
        )
        assertEquals(
            listOf("tt0903747:2:3", "tmdb:1396:2:3"),
            ZyloStreams.buildQueryIds(true, "1396", "tt0903747", "1396", 2, 3),
        )
    }

    @Test
    fun keepsOnlyDirectMediaAndPrefersCompatibleFiles() {
        val streams = listOf(
            StreamItem(
                title = "4K HEVC 40 GB",
                url = "https://cdn.example/large.mkv",
                addonName = "raw",
                addonId = "raw",
            ),
            StreamItem(
                title = "1080p x264 AAC 2 GB",
                url = "https://cdn.example/fast.mp4",
                externalUrl = "https://embed.example/player",
                addonName = "raw",
                addonId = "raw",
                behaviorHints = StreamBehaviorHints(filename = "movie.1080p.x264.aac.mp4"),
            ),
            StreamItem(
                title = "HTML only",
                externalUrl = "https://embed.example/player",
                addonName = "raw",
                addonId = "raw",
            ),
        )

        val prepared = ZyloStreams.prepareDirectStreams(streams)

        assertEquals(2, prepared.size)
        assertEquals("https://cdn.example/large.mkv", prepared.first().url)
        assertNull(prepared.first().externalUrl)
        assertEquals("ZYLO Direct", prepared.first().addonName)
        assertTrue(prepared.first().behaviorHints.bingeGroup.orEmpty().startsWith("zylo_direct|"))
    }
}
