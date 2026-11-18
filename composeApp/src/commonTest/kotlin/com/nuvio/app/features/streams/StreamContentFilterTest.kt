package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamContentFilterTest {
    private fun stream(
        name: String? = null,
        title: String? = null,
        description: String? = null,
        url: String? = null,
        filename: String? = null,
    ) = StreamItem(
        name = name,
        title = title,
        description = description,
        url = url,
        addonName = "raw",
        addonId = "raw",
        behaviorHints = StreamBehaviorHints(filename = filename),
    )

    @Test
    fun `rejects spoofed doomsday entry that points at endgame encore`() {
        val spoofed = stream(
            name = "Avengers: Doomsday 2026 SoraStream 2160p",
            title = "Avengers.Endgame.Encore.2026.iMAX.4K.2160p.SDR.JHS.WEB-DL.MULTi.DDP5.1.HEVC.x265",
            url = "https://r2.example/hub/Avengers.Endgame.Encore.2026.iMAX.4K.2160p.mkv?X-Amz-Signature=abc",
            filename = "Avengers.Endgame.Encore.2026.iMAX.4K.2160p.SDR.JHS.WEB-DL.MULTi.DDP5.1.HEVC.x265",
        )

        assertFalse(StreamContentFilter.matchesExpectedTitle(spoofed, "Avengers: Doomsday"))
        assertTrue(StreamContentFilter.isSpoofedRelease(spoofed))
        assertTrue(StreamContentFilter.isBlockedPrint(spoofed))
    }

    @Test
    fun `keeps a real endgame release with a descriptive filename`() {
        val genuine = stream(
            name = "Avengers: Endgame 2019 4KHDHub 2160p",
            title = "Avengers Endgame (2019) 2160p 4K UHD HDR10 BluRay REMUX x265",
            url = "https://cdn.example/Avengers.Endgame.2019.2160p.mkv",
            filename = "Avengers Endgame (2019) 2160p 4K UHD HDR10 BluRay REMUX x265",
        )

        assertTrue(StreamContentFilter.matchesExpectedTitle(genuine, "Avengers: Endgame"))
        assertFalse(StreamContentFilter.isBlockedPrint(genuine))
    }

    @Test
    fun `accepts a real release whose filename is vague but url carries the title`() {
        val genuine = stream(
            name = "Avengers: Endgame 2019 MultiMovies 2160p",
            title = "BluRay x264 Atmos 4K Hindi, English 29.42 GB",
            url = "https://hd.example/extract/?url=https%3A%2F%2Fr2.example%2FAvengers%2520-%2520Endgame%2520%282019%29%25202160p.mkv",
            filename = "BLURAY X264 AVC DDP 5.1 TRUEHD ATMOS DSNP MULTI (29.42 GB)",
        )

        assertTrue(StreamContentFilter.matchesExpectedTitle(genuine, "Avengers: Endgame"))
    }

    @Test
    fun `blocks a spoofed release hidden inside a double encoded extract url`() {
        val spoofed = stream(
            name = "Avengers: Doomsday 2026 MultiMovies 2160p",
            title = "WEB-DL • HEVC • Atmos • 4K • Hindi, English • 5.1 • 22.9 GB",
            // The display label is vague; only the encoded URL carries the fake file name.
            url = "https://hd.example/%7B%22multi%22%3A%22on%22%7D/extract/?index=0" +
                "&url=https%3A%2F%2Fr2.example%2FAvengers.Endgame.Encore.2026.2160p.mkv" +
                "%3FX-Amz-Signature%3Dabc",
            filename = "BLURAY X264 AVC DDP 5.1 TRUEHD ATMOS DSNP MULTI (22.9 GB)",
        )

        assertTrue(StreamContentFilter.isSpoofedRelease(spoofed))
        assertTrue(StreamContentFilter.isBlockedPrint(spoofed))
        assertFalse(StreamContentFilter.matchesExpectedTitle(spoofed, "Avengers: Doomsday"))
    }

    @Test
    fun `blocks cam telesync and screener prints`() {
        val camPrints = listOf(
            "Movie.2024.HDCAM.x264",
            "Movie.2024.HDTS.x264",
            "Movie.2024.Telesync.x264",
            "Movie.2024.PreDVD.x264",
            "Movie.2024.DVDSCR.x264",
            "Movie.2024.CAMRip.x264",
        )

        camPrints.forEach { filename ->
            val candidate = stream(title = filename, filename = filename)
            assertTrue(
                StreamContentFilter.isCamPrint(candidate),
                "expected CAM-family print to be blocked: $filename",
            )
        }
        assertFalse(StreamContentFilter.isCamPrint(stream(filename = "Movie.2024.1080p.BluRay.x264")))
        assertFalse(StreamContentFilter.isCamPrint(stream(filename = "Camila.2024.1080p.WEB-DL")))
    }

    @Test
    fun `detects available print qualities from highest to lowest`() {
        val streams = listOf(
            stream(filename = "Movie.2024.1080p.WEB-DL"),
            stream(filename = "Movie.2024.2160p.UHD.BluRay"),
            stream(filename = "Movie.2024.720p.WEB-DL"),
            stream(filename = "Movie.2024.1440p.WEB-DL"),
            stream(filename = "Movie.2024.CAMRip"),
        )

        assertEquals(
            listOf(StreamPrintQuality.UHD, StreamPrintQuality.QHD, StreamPrintQuality.FHD, StreamPrintQuality.HD),
            StreamContentFilter.availableQualities(streams),
        )
        assertEquals(StreamPrintQuality.UHD, StreamContentFilter.detectQuality(streams[1]))
        assertEquals(StreamPrintQuality.QHD, StreamContentFilter.detectQuality(streams[3]))
    }

    @Test
    fun `parses real size and language labels`() {
        val candidate = stream(
            title = "Movie.2024.2160p.Hindi.English.mkv",
            description = "Hindi, English 22.9 GB",
            filename = "Movie.2024.2160p.Hindi.English.mkv",
        )

        val expectedBytes = (22.9 * 1024.0 * 1024.0 * 1024.0).toLong()
        assertEquals(expectedBytes, StreamContentFilter.parsedSizeBytes("22.9 GB"))
        assertEquals(expectedBytes, StreamContentFilter.parsedSizeBytes(candidate))
        assertEquals("Hindi, English", StreamContentFilter.languageLabel(candidate))
        assertEquals("PixelDrain", StreamContentFilter.sourceLabel(stream(url = "https://pixeldrain.com/u/abc")))
    }

    @Test
    fun `prepareDirectStreams verifies the real release before offering it`() {
        val spoofed = stream(
            name = "Avengers: Doomsday 2026 SoraStream 2160p",
            title = "Avengers.Endgame.Encore.2026.iMAX.4K.2160p",
            url = "https://r2.example/hub/Avengers.Endgame.Encore.2026.mkv",
            filename = "Avengers.Endgame.Encore.2026.iMAX.4K.2160p",
        )
        val genuine = stream(
            name = "Avengers: Endgame 2019 4KHDHub 2160p",
            title = "Avengers.Endgame.2019.IMAX.2160p.DSNP.WEB-DL",
            url = "https://cdn.example/Avengers.Endgame.2019.IMAX.2160p.mkv",
            filename = "Avengers.Endgame.2019.IMAX.2160p.DSNP.WEB-DL",
        )

        assertTrue(
            ZyloStreams.prepareDirectStreams(
                streams = listOf(spoofed),
                expectedTitle = "Avengers: Doomsday",
                verifyTitle = true,
            ).isEmpty(),
        )
        assertEquals(
            1,
            ZyloStreams.prepareDirectStreams(
                streams = listOf(genuine, spoofed.copy(url = "https://cdn.example/other.mkv")),
                expectedTitle = "Avengers: Endgame",
                verifyTitle = true,
            ).size,
        )
    }

    @Test
    fun `recognises session local loopback playback urls`() {
        assertTrue("http://127.0.0.1:1234/stream".isLocalLoopbackPlaybackUrl())
        assertTrue("http://localhost:8080/video".isLocalLoopbackPlaybackUrl())
        assertTrue("http://[::1]:9000/video".isLocalLoopbackPlaybackUrl())
        assertFalse("https://cdn.example/movie.mkv".isLocalLoopbackPlaybackUrl())
        assertFalse("http://127.0.0.1.example.com/video".isLocalLoopbackPlaybackUrl())
    }
}
