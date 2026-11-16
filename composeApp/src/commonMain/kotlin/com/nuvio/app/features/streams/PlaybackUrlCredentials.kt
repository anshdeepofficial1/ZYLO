package com.nuvio.app.features.streams

import io.ktor.http.decodeURLQueryComponent

private val credentialQueryKeys = setOf(
    "accesskey",
    "accesssignature",
    "accesssig",
    "access_token",
    "accesstoken",
    "auth",
    "authkey",
    "authsig",
    "authsignature",
    "auth_token",
    "authtoken",
    "e",
    "exp",
    "expiration",
    "expire",
    "expires",
    "expiresat",
    "expiresin",
    "expires_in",
    "expiry",
    "hmac",
    "jwt",
    "keypairid",
    "policy",
    "sig",
    "signature",
    "signed",
    "st",
    "t",
    "token",
)

private val credentialKeyFragments = listOf(
    "token",
    "signature",
    "expires",
    "expiry",
)

internal fun String.hasLikelyExpiringPlaybackCredentials(): Boolean =
    hasLikelyExpiringPlaybackCredentials(depth = 0)

private fun String.hasLikelyExpiringPlaybackCredentials(depth: Int): Boolean {
    if (depth > 2) return false
    val query = substringAfter('?', missingDelimiterValue = "")
        .substringBefore('#')
        .takeIf { it.isNotBlank() }
        ?: return false

    return query
        .split('&', ';')
        .any { rawParameter ->
            val rawKey = rawParameter
                .substringBefore('=', missingDelimiterValue = "")
                .trim()
                .lowercase()
            if (rawKey.isBlank()) return@any false

            val compactKey = rawKey
                .replace("-", "")
                .replace("_", "")
                .replace(".", "")

            val directCredential = rawKey in credentialQueryKeys ||
                compactKey in credentialQueryKeys ||
                credentialKeyFragments.any { fragment ->
                    rawKey.contains(fragment) || compactKey.contains(fragment)
                }
            if (directCredential) return@any true

            // Resolver URLs commonly put a signed CDN URL inside `?url=...`. Looking only at
            // the outer key would cache that resolver for 24 hours even though its nested
            // X-Amz/Expires signature lasts minutes. Decode nested values and inspect them too.
            val encodedValue = rawParameter.substringAfter('=', missingDelimiterValue = "")
            if (encodedValue.isBlank()) return@any false
            val decodedValue = runCatching { encodedValue.decodeURLQueryComponent() }.getOrNull()
                ?: return@any false
            decodedValue != encodedValue &&
                decodedValue.hasLikelyExpiringPlaybackCredentials(depth = depth + 1)
        }
}

internal fun String.redactedForLogs(): String = substringBefore('?').substringBefore('#')
