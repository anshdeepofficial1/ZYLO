package com.nuvio.app.features.notifications

internal expect object MovieReleaseRemindersStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
