package com.nuvio.app.features.streams

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches which real print qualities exist for a movie so the details screen can show them
 * without resolving the same source list on every recomposition.
 *
 * Only facts from an actual provider response are stored; an empty result is retried sooner
 * because it can also mean the provider was temporarily unreachable.
 */
internal object StreamQualityRepository {
    private const val freshTtlMs = 10 * 60 * 1000L
    private const val emptyTtlMs = 45 * 1000L

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()

    suspend fun availableQualities(
        type: String,
        videoId: String,
        parentMetaId: String? = null,
    ): List<StreamPrintQuality> {
        if (!type.equals("movie", ignoreCase = true)) return emptyList()

        val key = "${type.lowercase()}|${parentMetaId ?: videoId}|$videoId"
        val now = epochMs()
        mutex.withLock { cache[key] }
            ?.takeIf { entry -> now - entry.cachedAtMs < entry.ttlMs }
            ?.let { return it.qualities }

        val group = ZyloStreams.resolveDirectStreams(
            type = type,
            videoId = videoId,
            parentMetaId = parentMetaId,
        )
        val qualities = StreamContentFilter.availableQualities(group.streams)
        mutex.withLock {
            cache[key] = CacheEntry(
                qualities = qualities,
                cachedAtMs = epochMs(),
                ttlMs = if (qualities.isEmpty()) emptyTtlMs else freshTtlMs,
            )
        }
        return qualities
    }

    private data class CacheEntry(
        val qualities: List<StreamPrintQuality>,
        val cachedAtMs: Long,
        val ttlMs: Long,
    )
}
