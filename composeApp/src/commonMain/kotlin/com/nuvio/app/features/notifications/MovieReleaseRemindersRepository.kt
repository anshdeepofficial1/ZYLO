package com.nuvio.app.features.notifications

import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.core.time.shiftIsoDate
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MovieReleasePhase
import com.nuvio.app.features.details.MovieReleaseStatus
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.movie_release_notifications_disabled
import nuvio.composeapp.generated.resources.movie_release_notify_day_ott
import nuvio.composeapp.generated.resources.movie_release_notify_day_theatrical
import nuvio.composeapp.generated.resources.movie_release_notify_week_ott
import nuvio.composeapp.generated.resources.movie_release_notify_week_theatrical
import nuvio.composeapp.generated.resources.movie_release_reminder_removed
import nuvio.composeapp.generated.resources.movie_release_reminder_saved
import org.jetbrains.compose.resources.getString
import kotlin.concurrent.Volatile

/**
 * Stores per-profile "remind me" requests for unreleased movies and keeps their WorkManager
 * notifications in sync (1 week before + release day for the next known milestone).
 */
object MovieReleaseRemindersRepository {
    private val log = Logger.withTag("MovieReleaseReminders")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()

    private val _reminderKeys = MutableStateFlow<Set<String>>(emptySet())
    val reminderKeys: StateFlow<Set<String>> = _reminderKeys.asStateFlow()

    @Volatile
    private var hasLoaded = false

    private var remindersByKey: Map<String, StoredMovieReleaseReminder> = emptyMap()

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
        scope.launch { syncSchedules() }
    }

    fun onProfileChanged() {
        loadFromDisk()
        scope.launch { syncSchedules() }
    }

    fun clearLocalState() {
        hasLoaded = false
        remindersByKey = emptyMap()
        publishKeys()
        scope.launch {
            runCatching { MovieReleaseRemindersStorage.savePayload("") }
            runCatching { EpisodeReleaseNotificationPlatform.clearScheduledMovieReleaseReminders() }
        }
    }

    fun isReminderSet(contentId: String): Boolean =
        reminderKey(contentId) in remindersByKey

    fun toggleReminder(meta: MetaDetails, status: MovieReleaseStatus) {
        ensureLoaded()
        if (!status.isBlocked) return
        val key = reminderKey(meta.id)
        if (key in remindersByKey) {
            removeReminder(meta.id)
        } else {
            addReminder(meta, status)
        }
    }

    /**
     * Refreshes a stored reminder with the latest release dates. Called when the details screen
     * loads the movie, so a newly announced OTT date replaces the old theatrical milestone.
     */
    fun updateFromMeta(meta: MetaDetails, status: MovieReleaseStatus) {
        ensureLoaded()
        val key = reminderKey(meta.id)
        val existing = remindersByKey[key] ?: return

        if (status.isReleased) {
            remindersByKey = remindersByKey - key
            persist()
            scope.launch { syncSchedules() }
            return
        }

        val updated = existing.copy(
            title = meta.name.ifBlank { existing.title },
            contentType = meta.type.ifBlank { existing.contentType },
            backdropUrl = meta.background ?: meta.poster ?: existing.backdropUrl,
            theatricalReleaseDate = status.theatricalDate ?: existing.theatricalReleaseDate,
            ottReleaseDate = status.ottDate ?: existing.ottReleaseDate,
            targetDate = status.targetDate,
            phase = status.phase.name,
        )
        if (updated == existing) return
        remindersByKey = remindersByKey + (key to updated)
        persist()
        scope.launch { syncSchedules() }
    }

    private fun addReminder(meta: MetaDetails, status: MovieReleaseStatus) {
        scope.launch {
            val granted = runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }
                .onFailure { error ->
                    log.w { "Failed to request notification permission for movie reminder: ${error.message}" }
                }
                .getOrDefault(false)
            if (!granted) {
                NuvioToastController.show(getString(Res.string.movie_release_notifications_disabled))
                return@launch
            }

            val key = reminderKey(meta.id)
            val reminder = StoredMovieReleaseReminder(
                contentId = meta.id,
                contentType = meta.type.ifBlank { "movie" },
                title = meta.name.ifBlank { meta.id },
                backdropUrl = meta.background ?: meta.poster,
                theatricalReleaseDate = status.theatricalDate,
                ottReleaseDate = status.ottDate,
                targetDate = status.targetDate,
                phase = status.phase.name,
            )
            remindersByKey = remindersByKey + (key to reminder)
            persist()
            syncSchedules()
            NuvioToastController.show(getString(Res.string.movie_release_reminder_saved, reminder.title))
        }
    }

    private fun removeReminder(contentId: String) {
        val key = reminderKey(contentId)
        if (key !in remindersByKey) return
        remindersByKey = remindersByKey - key
        persist()
        scope.launch {
            syncSchedules()
            NuvioToastController.show(getString(Res.string.movie_release_reminder_removed))
        }
    }

    private suspend fun syncSchedules() {
        mutex.withLock {
            if (!hasLoaded) return
            val requests = buildRequests()
            runCatching {
                if (requests.isEmpty()) {
                    EpisodeReleaseNotificationPlatform.clearScheduledMovieReleaseReminders()
                } else {
                    EpisodeReleaseNotificationPlatform.scheduleMovieReleaseReminders(requests)
                }
            }.onFailure { error ->
                log.w { "Failed to schedule movie release reminders: ${error.message}" }
            }
        }
    }

    private suspend fun buildRequests(): List<EpisodeReleaseNotificationRequest> {
        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        val requests = mutableListOf<EpisodeReleaseNotificationRequest>()

        remindersByKey.values.forEach { reminder ->
            val targetDate = reminder.targetDate ?: return@forEach
            if (targetDate < todayIsoDate) return@forEach

            requests += buildRequest(reminder, targetDate, isReleaseDay = true)
            val weekBefore = shiftIsoDate(targetDate, -7)
            if (weekBefore != null && weekBefore >= todayIsoDate) {
                requests += buildRequest(reminder, weekBefore, isReleaseDay = false)
            }
        }

        return requests
    }

    private suspend fun buildRequest(
        reminder: StoredMovieReleaseReminder,
        triggerDate: String,
        isReleaseDay: Boolean,
    ): EpisodeReleaseNotificationRequest {
        val phase = MovieReleasePhase.entries.firstOrNull {
            it.name.equals(reminder.phase, ignoreCase = true)
        } ?: MovieReleasePhase.Theatrical
        val bodyRes = when (phase) {
            MovieReleasePhase.OttScheduled, MovieReleasePhase.Released ->
                if (isReleaseDay) Res.string.movie_release_notify_day_ott else Res.string.movie_release_notify_week_ott
            MovieReleasePhase.Theatrical, MovieReleasePhase.AwaitingOtt ->
                if (isReleaseDay) Res.string.movie_release_notify_day_theatrical else Res.string.movie_release_notify_week_theatrical
        }
        return EpisodeReleaseNotificationRequest(
            requestId = "movie-release-${ProfileRepository.activeProfileId}-" +
                "${kotlin.math.abs(reminder.contentId.hashCode())}-" +
                "${if (isReleaseDay) "day" else "week"}-$triggerDate",
            notificationTitle = reminder.title,
            notificationBody = getString(bodyRes),
            releaseDateIso = triggerDate,
            deepLinkUrl = buildMetaDeepLinkUrl(type = reminder.contentType, id = reminder.contentId),
            backdropUrl = reminder.backdropUrl,
        )
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val rawPayload = MovieReleaseRemindersStorage.loadPayload().orEmpty().trim()
        val stored = rawPayload.takeIf { it.isNotEmpty() }
            ?.let { payload ->
                runCatching {
                    json.decodeFromString<StoredMovieReleaseRemindersPayload>(payload)
                }.onFailure { error ->
                    log.w { "Failed to decode movie release reminders payload: ${error.message}" }
                }.getOrNull()
            }

        remindersByKey = stored?.reminders.orEmpty()
            .associateBy { reminder -> reminderKey(reminder.contentId) }
        publishKeys()
    }

    private fun persist() {
        publishKeys()
        MovieReleaseRemindersStorage.savePayload(
            json.encodeToString(
                StoredMovieReleaseRemindersPayload(
                    reminders = remindersByKey.values.sortedBy { it.contentId },
                ),
            ),
        )
    }

    private fun publishKeys() {
        // Expose raw content ids so UI code can check membership with the meta id directly.
        _reminderKeys.value = remindersByKey.values.mapTo(mutableSetOf()) { it.contentId }
    }

    private fun reminderKey(contentId: String): String = "movie:${contentId.trim()}"
}

@Serializable
private data class StoredMovieReleaseRemindersPayload(
    val reminders: List<StoredMovieReleaseReminder> = emptyList(),
)

@Serializable
internal data class StoredMovieReleaseReminder(
    val contentId: String,
    val contentType: String = "movie",
    val title: String,
    val backdropUrl: String? = null,
    val theatricalReleaseDate: String? = null,
    val ottReleaseDate: String? = null,
    val targetDate: String? = null,
    val phase: String = "Theatrical",
)
