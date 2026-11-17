package com.nuvio.app.features.player

import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Human-friendly error translation for playback failures.
 *
 * Converts raw player/engine diagnostics (such as `loading failed`, ffmpeg TCP errors,
 * HTTP 403 responses, or codec decoder crashes) into clear, actionable messages
 * for the user.
 */
object PlaybackErrorMessages {

    fun toUserFriendlyMessageSync(rawMessage: String?): String {
        val raw = rawMessage?.trim().orEmpty()
        if (raw.isBlank()) {
            return "Unable to play this stream. Please try another source."
        }
        val lower = raw.lowercase()
        return when {
            // DNS / Network resolution failures
            "failed to resolve hostname" in lower ||
                "no address associated with hostname" in lower ||
                "unable to connect" in lower ||
                "connection refused" in lower ||
                "connection timed out" in lower ||
                "network is unreachable" in lower ||
                "unknownhostexception" in lower ||
                "connectexception" in lower ||
                "sockettimeoutexception" in lower ||
                "httpexception" in lower -> {
                "Unable to connect to streaming server. Please check your connection or try another source."
            }
            // 403 / 401 / Forbidden / Expired tokens
            "403" in lower || "forbidden" in lower || "401" in lower || "unauthorized" in lower -> {
                "This stream source is currently protected or has expired. Please try another source."
            }
            // Codec / format errors
            "unrecognizedinputformatexception" in lower ||
                "unrecognized file format" in lower ||
                "decoder failure" in lower ||
                "mediacodec" in lower ||
                ("codec" in lower && "not supported" in lower) -> {
                "This video format or codec is not supported by your device."
            }
            // ytdl subprocess / hook errors
            "ytdl_hook" in lower || "youtube-dl failed" in lower || "subprocess failed" in lower -> {
                "Stream loading failed. Please select another source."
            }
            // Generic loading failed / error / source error
            "loading failed" in lower || lower == "error" || "source error" in lower -> {
                "The selected stream is currently unavailable. Please select another source."
            }
            else -> raw
        }
    }

    suspend fun toUserFriendlyMessage(rawMessage: String?): String {
        val raw = rawMessage?.trim().orEmpty()
        if (raw.isBlank()) {
            return runCatching { getString(Res.string.player_error_unable_to_play_stream) }
                .getOrDefault("Unable to play this stream. Please try another source.")
        }
        val lower = raw.lowercase()
        return when {
            "failed to resolve hostname" in lower ||
                "no address associated with hostname" in lower ||
                "unable to connect" in lower ||
                "connection refused" in lower ||
                "connection timed out" in lower ||
                "network is unreachable" in lower ||
                "unknownhostexception" in lower ||
                "connectexception" in lower ||
                "sockettimeoutexception" in lower ||
                "httpexception" in lower -> {
                runCatching { getString(Res.string.player_error_network_connection) }
                    .getOrDefault("Unable to connect to streaming server. Please check your connection or try another source.")
            }
            "403" in lower || "forbidden" in lower || "401" in lower || "unauthorized" in lower -> {
                runCatching { getString(Res.string.player_error_stream_forbidden) }
                    .getOrDefault("This stream source is currently protected or has expired. Please try another source.")
            }
            "unrecognizedinputformatexception" in lower ||
                "unrecognized file format" in lower ||
                "decoder failure" in lower ||
                "mediacodec" in lower ||
                ("codec" in lower && "not supported" in lower) -> {
                runCatching { getString(Res.string.player_error_stream_unsupported_format) }
                    .getOrDefault("This video format or codec is not supported by your device.")
            }
            "ytdl_hook" in lower || "youtube-dl failed" in lower || "subprocess failed" in lower -> {
                runCatching { getString(Res.string.player_error_unable_to_play_stream) }
                    .getOrDefault("Stream loading failed. Please select another source.")
            }
            "loading failed" in lower || lower == "error" || "source error" in lower -> {
                runCatching { getString(Res.string.player_error_stream_unavailable) }
                    .getOrDefault("The selected stream is currently unavailable. Please select another source.")
            }
            else -> raw
        }
    }
}
