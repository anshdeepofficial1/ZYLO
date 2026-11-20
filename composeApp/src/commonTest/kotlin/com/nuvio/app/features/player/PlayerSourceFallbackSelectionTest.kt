package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySource
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamQualityPreference
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSourceFallbackSelectionTest {
    @Test
    fun `fallback keeps identity quality and audio preferences`() {
        val wrongTitle4k = stream(
            url = "https://cdn.example/wrong.movie.2024.2160p.english.mkv",
            filename = "Wrong.Movie.2024.2160p.English.mkv",
        )
        val correctEnglish1080p = stream(
            url = "https://cdn.example/movie.alpha.2024.1080p.english.mkv",
            filename = "Movie.Alpha.2024.1080p.English.mkv",
        )
        val correctHindi1080p = stream(
            url = "https://cdn.example/movie.alpha.2024.1080p.hindi.mkv",
            filename = "Movie.Alpha.2024.1080p.Hindi.mkv",
        )

        val selected = nextSourceFallbackCandidate(
            streams = listOf(wrongTitle4k, correctEnglish1080p, correctHindi1080p),
            settings = PlayerSettingsUiState(
                streamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM,
                streamAutoPlaySource = StreamAutoPlaySource.ALL_SOURCES,
                preferredAudioLanguage = "hi",
                streamPreferredQuality = StreamQualityPreference.FHD_1080P,
            ),
            triedKeys = emptySet(),
            failedAddonId = null,
            failedInfoHash = null,
            installedAddonNames = setOf("Test Addon"),
            debridEnabled = false,
            activeResolverProviderId = null,
            expectedTitle = "Movie Alpha",
            expectedYear = 2024,
        )

        assertEquals(correctHindi1080p, selected)
    }

    private fun stream(url: String, filename: String): StreamItem = StreamItem(
        name = filename,
        title = filename,
        url = url,
        addonName = "Test Addon",
        addonId = "test-addon",
        behaviorHints = StreamBehaviorHints(filename = filename),
    )
}
