package com.nuvio.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals

class TmdbReleaseRegionPriorityTest {
    @Test
    fun `movie release dates prefer India before locale fallbacks`() {
        assertEquals(
            listOf("IN", "US", "GB"),
            preferredMovieReleaseRegions("en-US"),
        )
    }
}
