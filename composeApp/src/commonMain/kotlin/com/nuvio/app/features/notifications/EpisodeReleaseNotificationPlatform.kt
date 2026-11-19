package com.nuvio.app.features.notifications

internal expect object EpisodeReleaseNotificationPlatform {
    suspend fun notificationsAuthorized(): Boolean
    suspend fun requestAuthorization(): Boolean
    suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>)
    suspend fun clearScheduledEpisodeReleaseNotifications()
    suspend fun scheduleMovieReleaseReminders(requests: List<EpisodeReleaseNotificationRequest>)
    suspend fun clearScheduledMovieReleaseReminders()
    suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest)
}