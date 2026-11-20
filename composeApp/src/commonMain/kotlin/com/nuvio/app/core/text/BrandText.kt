package com.nuvio.app.core.text

/**
 * Replaces old upstream branding in names that come from addons, servers, or manifests.
 * Only use this for text shown to the user; request URLs and ids must stay untouched.
 */
fun sanitizeBrandLabel(value: String): String {
    if (value.isBlank()) return value
    var result = value
    result = result.replace("Nuvoio", "ZYLO")
    result = result.replace("NUVIO", "ZYLO")
    result = result.replace("Nuvio", "ZYLO")
    result = result.replace("nuvio", "ZYLO")
    return result
}
