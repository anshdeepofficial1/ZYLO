package com.nuvio.app.features.home

import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeCatalogDefinitionTest {
    private val definition = HomeCatalogDefinition(
        key = "addon:movie:popular",
        defaultTitle = "Popular - Movie",
        catalogName = "Popular",
        addonName = "Addon",
        manifestUrl = "https://example.com/manifest.json",
        type = "movie",
        catalogId = "popular",
        supportsPagination = true,
        descriptorSignature = "signature",
    )

    @Test
    fun `shows the type suffix by default`() {
        assertEquals("Popular - Movie", definition.titleFor(showCatalogType = true))
    }

    @Test
    fun `omits the type suffix when disabled`() {
        assertEquals("Popular", definition.titleFor(showCatalogType = false))
    }

    @Test
    fun `addon refresh signature tracks unresolved terminal and loaded manifest states`() {
        val pendingAddon = ManagedAddon(
            manifestUrl = "https://example.test/manifest.json",
            isRefreshing = true,
        )
        val failedAddon = pendingAddon.copy(
            isRefreshing = false,
            errorMessage = "Timed out",
        )
        val loadedAddon = pendingAddon.copy(
            manifest = AddonManifest(
                id = "addon",
                name = "Addon",
                description = "",
                version = "1.0.0",
                resources = listOf(AddonResource(name = "meta", types = listOf("movie"))),
                types = listOf("movie"),
                catalogs = emptyList(),
                transportUrl = pendingAddon.manifestUrl,
            ),
            isRefreshing = false,
        )

        val pendingSignature = buildAddonCatalogRefreshSignature(listOf(pendingAddon))
        val failedSignature = buildAddonCatalogRefreshSignature(listOf(failedAddon))
        val loadedSignature = buildAddonCatalogRefreshSignature(listOf(loadedAddon))

        assertTrue(pendingSignature.isNotEmpty())
        assertNotEquals(pendingSignature, failedSignature)
        assertNotEquals(pendingSignature, loadedSignature)
        assertNotEquals(failedSignature, loadedSignature)
    }

    @Test
    fun `home starts with trending then spotlight and regional cinema`() {
        fun catalog(id: String, name: String, type: String = "movie") = definition.copy(
            key = "test:$type:$id",
            defaultTitle = name,
            catalogName = name,
            type = type,
            catalogId = id,
        )
        val spotlight = catalog(SpotlightCatalog.CATALOG_ID, "New & Returning")
        val regional = listOf(
            catalog("zylo_bollywood", "Bollywood & Hindi Hits"),
            catalog("zylo_hollywood", "Hollywood Movies"),
            catalog("zylo_south", "South Indian Cinema"),
            catalog("zylo_punjabi", "Punjabi Cinema"),
        )
        val ids = orderHomeCatalogDefinitions(
            addonDefinitions = listOf(
                catalog("trending-series", "Trending Series", type = "series"),
                catalog("popular", "Popular Movies"),
                catalog("trending-movies", "Trending Movies"),
            ),
            spotlightDefinition = spotlight,
            regionalDefinitions = regional,
        ).map(HomeCatalogDefinition::catalogId)

        assertEquals(
            listOf(
                "trending-movies",
                "trending-series",
                SpotlightCatalog.CATALOG_ID,
                "zylo_bollywood",
                "zylo_hollywood",
                "zylo_south",
                "zylo_punjabi",
            ),
            ids.take(7),
        )
    }
}
