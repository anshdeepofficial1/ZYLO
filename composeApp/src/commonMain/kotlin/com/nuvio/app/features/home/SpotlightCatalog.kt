package com.nuvio.app.features.home

import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.details.MetaDetails
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.spotlight_encore_description
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Small editorial row for titles that are real and freshly available but often missing from
 * third-party catalogs. Entries resolve their artwork and overview from TMDB at runtime, while
 * the id stays stable so playback, refetch and downloads can all find the same title again.
 */
object SpotlightCatalog {
    const val CATALOG_KEY = "zylo:spotlight"
    const val CATALOG_ID = "zylo_spotlight"

    data class Entry(
        val metaId: String,
        val type: String,
        val fallbackName: String,
        val fallbackYear: String?,
        val displayName: String? = null,
        val displayYear: String? = null,
        val releaseDate: String? = null,
        val theatricalReleaseDate: String? = null,
        val ottReleaseDate: String? = null,
        val descriptionResource: StringResource? = null,
    )

    val entries: List<Entry> = listOf(
        Entry(
            metaId = "tmdb:299534:encore",
            type = "movie",
            fallbackName = "Avengers Endgame: Encore",
            fallbackYear = "2026",
            displayName = "Avengers Endgame: Encore",
            displayYear = "2026",
            releaseDate = "2026-09-25",
            theatricalReleaseDate = "2026-09-25",
            ottReleaseDate = "2026-11-13",
            descriptionResource = Res.string.spotlight_encore_description,
        ),
        Entry(
            metaId = "tmdb:969681",
            type = "movie",
            fallbackName = "Spider-Man: Brand New Day",
            fallbackYear = "2026",
        ),
    )

    suspend fun previews(): List<MetaPreview> = entries.map { entry ->
        val tmdbId = entry.metaId
            .removePrefix("tmdb:")
            .substringBefore(':')
            .toIntOrNull()
        val remote = tmdbId?.let { id ->
            runCatching { TmdbCollectionSourceResolver.fetchPreview(entry.type, id) }.getOrNull()
        }
        applyPreview(remote ?: fallbackPreview(entry), entry)
    }

    suspend fun search(query: String): List<MetaPreview> {
        val tokens = query.normalizedSearchTokens()
        if (tokens.isEmpty()) return emptyList()
        val matchingIds = entries
            .filter { entry ->
                val candidateTokens = listOfNotNull(entry.displayName, entry.fallbackName)
                    .flatMap { it.normalizedSearchTokens() }
                    .toSet()
                tokens.all(candidateTokens::contains)
            }
            .mapTo(mutableSetOf(), Entry::metaId)
        if (matchingIds.isEmpty()) return emptyList()
        return previews().filter { preview -> preview.id in matchingIds }
    }

    fun hasMatch(query: String): Boolean {
        val tokens = query.normalizedSearchTokens()
        if (tokens.isEmpty()) return false
        return entries.any { entry ->
            val candidateTokens = listOfNotNull(entry.displayName, entry.fallbackName)
                .flatMap { it.normalizedSearchTokens() }
                .toSet()
            tokens.all(candidateTokens::contains)
        }
    }

    suspend fun applyMeta(meta: MetaDetails, type: String, requestedId: String): MetaDetails {
        val entry = entryFor(type = type, requestedId = requestedId) ?: return meta
        val description = entry.descriptionResource
            ?.let { resource -> runCatching { getString(resource) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        return meta.copy(
            id = entry.metaId,
            name = entry.displayName ?: meta.name,
            releaseInfo = entry.displayYear ?: meta.releaseInfo,
            releaseDate = entry.releaseDate ?: meta.releaseDate,
            theatricalReleaseDate = entry.theatricalReleaseDate ?: meta.theatricalReleaseDate,
            ottReleaseDate = entry.ottReleaseDate ?: meta.ottReleaseDate,
            description = if (description == null) {
                meta.description
            } else {
                listOfNotNull(description, meta.description?.takeIf { it.isNotBlank() })
                    .joinToString(separator = "\n\n")
            },
        )
    }

    private fun entryFor(type: String, requestedId: String): Entry? =
        entries.firstOrNull { entry ->
            entry.type.equals(type, ignoreCase = true) && entry.metaId == requestedId
        }

    private fun fallbackPreview(entry: Entry): MetaPreview = MetaPreview(
        id = entry.metaId,
        type = entry.type,
        name = entry.displayName ?: entry.fallbackName,
        releaseInfo = entry.displayYear ?: entry.fallbackYear,
        rawReleaseDate = entry.releaseDate,
    )

    private fun applyPreview(preview: MetaPreview, entry: Entry): MetaPreview = preview.copy(
        id = entry.metaId,
        type = entry.type,
        name = entry.displayName ?: preview.name,
        releaseInfo = entry.displayYear ?: preview.releaseInfo,
        rawReleaseDate = entry.releaseDate ?: preview.rawReleaseDate,
    )

    private fun String.normalizedSearchTokens(): List<String> =
        lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
}
