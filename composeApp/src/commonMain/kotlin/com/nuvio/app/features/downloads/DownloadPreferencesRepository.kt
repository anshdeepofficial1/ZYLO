package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DownloadPreferences(
    val preferredQuality: String = "ask", // "ask", "4K", "2K", "1080p", "720p", "480p"
    val preferredServer: String = "auto",
    val preferredLanguage: String = "default",
    val includeSubtitles: Boolean = true,
    val rememberChoice: Boolean = false,
)

object DownloadPreferencesRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _preferences = MutableStateFlow(DownloadPreferences())
    val preferences: StateFlow<DownloadPreferences> = _preferences.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val payload = DownloadPreferencesStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return
        runCatching {
            json.decodeFromString<DownloadPreferences>(payload)
        }.onSuccess { stored ->
            _preferences.value = stored
        }
    }

    fun onProfileChanged() {
        hasLoaded = false
        _preferences.value = DownloadPreferences()
        ensureLoaded()
    }

    fun clearLocalState() {
        hasLoaded = false
        _preferences.value = DownloadPreferences()
    }

    fun updatePreferences(
        quality: String? = null,
        server: String? = null,
        language: String? = null,
        includeSubtitles: Boolean? = null,
        rememberChoice: Boolean? = null,
    ) {
        ensureLoaded()
        _preferences.update { current ->
            current.copy(
                preferredQuality = quality ?: current.preferredQuality,
                preferredServer = server ?: current.preferredServer,
                preferredLanguage = language ?: current.preferredLanguage,
                includeSubtitles = includeSubtitles ?: current.includeSubtitles,
                rememberChoice = rememberChoice ?: current.rememberChoice,
            )
        }
        persist()
    }

    fun resetRememberedChoice() {
        ensureLoaded()
        _preferences.update { current ->
            current.copy(
                rememberChoice = false,
                preferredQuality = "ask",
                preferredServer = "auto",
                preferredLanguage = "default",
            )
        }
        persist()
    }

    private fun persist() {
        runCatching {
            DownloadPreferencesStorage.savePayload(json.encodeToString(_preferences.value))
        }
    }
}
