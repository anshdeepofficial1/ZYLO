package com.nuvio.app.features.downloads

internal expect object DownloadPreferencesStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
