package com.nuvio.app.features.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.downloads_dialog_cancel
import nuvio.composeapp.generated.resources.downloads_dialog_checking_sources
import nuvio.composeapp.generated.resources.downloads_dialog_default_language
import nuvio.composeapp.generated.resources.downloads_dialog_download
import nuvio.composeapp.generated.resources.downloads_dialog_episode_title
import nuvio.composeapp.generated.resources.downloads_dialog_finding_sources
import nuvio.composeapp.generated.resources.downloads_dialog_language
import nuvio.composeapp.generated.resources.downloads_dialog_movie_title
import nuvio.composeapp.generated.resources.downloads_dialog_no_source_body
import nuvio.composeapp.generated.resources.downloads_dialog_no_source_title
import nuvio.composeapp.generated.resources.downloads_dialog_quality
import nuvio.composeapp.generated.resources.downloads_dialog_remember
import nuvio.composeapp.generated.resources.downloads_dialog_remember_hint
import nuvio.composeapp.generated.resources.downloads_dialog_selected
import nuvio.composeapp.generated.resources.downloads_dialog_selected_summary
import nuvio.composeapp.generated.resources.downloads_dialog_server
import nuvio.composeapp.generated.resources.downloads_dialog_size
import nuvio.composeapp.generated.resources.downloads_dialog_subtitles
import nuvio.composeapp.generated.resources.downloads_dialog_try_again
import org.jetbrains.compose.resources.stringResource

/**
 * Download sheet shown before a real download starts. It follows the same flow as a dedicated
 * downloader: server -> language -> quality, with the exact size/quality that was discovered from
 * the stream metadata or the server's own response headers.
 */
data class DownloadSelection(
    val option: DownloadSourceOption,
    val includeSubtitles: Boolean,
)

private const val DefaultLanguageKey = "default"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    loadOptions: suspend () -> List<DownloadSourceOption>,
    onConfirm: (DownloadSelection) -> Unit,
) {
    LaunchedEffect(Unit) {
        DownloadPreferencesRepository.ensureLoaded()
    }
    val prefs by DownloadPreferencesRepository.preferences.collectAsState()
    val noSourceTitle = stringResource(Res.string.downloads_dialog_no_source_title)

    var options by remember { mutableStateOf<List<DownloadSourceOption>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var includeSubtitles by remember { mutableStateOf(prefs.includeSubtitles) }
    var rememberChoice by remember { mutableStateOf(prefs.rememberChoice) }

    LaunchedEffect(prefs.includeSubtitles, prefs.rememberChoice) {
        includeSubtitles = prefs.includeSubtitles
        rememberChoice = prefs.rememberChoice
    }

    LaunchedEffect(reloadToken) {
        isLoading = true
        loadError = null
        options = emptyList()
        selectedServer = null
        selectedLanguage = null
        selectedKey = null
        runCatching { loadOptions() }
            .onSuccess { loaded ->
                options = loaded
                val initial = initialSelection(loaded, prefs)
                val server = initial?.option?.sourceLabel ?: loaded.firstOrNull()?.sourceLabel
                val language = initial?.option?.let(::languageKey)
                    ?: server?.let { firstLanguage(loaded, it) }
                selectedServer = server
                selectedLanguage = language
                selectedKey = initial?.option?.selectionKey
                    ?: bestOption(loaded, server, language, prefs, useRemembered = false)?.selectionKey
            }
            .onFailure { error ->
                loadError = error.message?.takeIf { it.isNotBlank() } ?: noSourceTitle
            }
        isLoading = false
    }

    val servers = remember(options) { options.map { it.sourceLabel }.distinct() }
    val languages = remember(options, selectedServer) {
        selectedServer?.let { languagesFor(options, it) }.orEmpty()
    }
    val visibleOptions = remember(options, selectedServer, selectedLanguage) {
        options
            .filter { it.sourceLabel == selectedServer && languageKey(it) == selectedLanguage }
            .sortedWith(
                compareByDescending<DownloadSourceOption> { it.qualityRank }
                    .thenByDescending { it.sizeBytes ?: 0L },
            )
    }
    val selectedOption = options.firstOrNull { it.selectionKey == selectedKey }

    fun selectServer(server: String) {
        val language = if (rememberChoice && prefs.preferredLanguage in languagesFor(options, server)) {
            prefs.preferredLanguage
        } else {
            firstLanguage(options, server)
        }
        selectedServer = server
        selectedLanguage = language
        selectedKey = bestOption(options, server, language, prefs, useRemembered = rememberChoice)?.selectionKey
    }

    fun selectLanguage(language: String) {
        selectedLanguage = language
        selectedKey = bestOption(options, selectedServer, language, prefs, useRemembered = rememberChoice)?.selectionKey
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (!subtitle.isNullOrBlank()) {
                                    Res.string.downloads_dialog_episode_title
                                } else {
                                    Res.string.downloads_dialog_movie_title
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (!subtitle.isNullOrBlank()) "$title • $subtitle" else title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Column {
                                Text(
                                    text = stringResource(Res.string.downloads_dialog_finding_sources),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(Res.string.downloads_dialog_checking_sources),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    options.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = noSourceTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = loadError ?: stringResource(Res.string.downloads_dialog_no_source_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { reloadToken++ }) {
                                Text(stringResource(Res.string.downloads_dialog_try_again))
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (servers.size > 1) {
                                SectionLabel(stringResource(Res.string.downloads_dialog_server))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    servers.forEach { server ->
                                        FilterChip(
                                            selected = server == selectedServer,
                                            onClick = { selectServer(server) },
                                            label = {
                                                Text(
                                                    text = server,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            if (languages.size > 1) {
                                SectionLabel(stringResource(Res.string.downloads_dialog_language))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    languages.forEach { language ->
                                        FilterChip(
                                            selected = language == selectedLanguage,
                                            onClick = { selectLanguage(language) },
                                            label = { Text(languageLabel(language)) },
                                        )
                                    }
                                }
                            }

                            SectionLabel(stringResource(Res.string.downloads_dialog_quality))
                            visibleOptions.forEach { option ->
                                DownloadSourceRow(
                                    option = option,
                                    selected = option.selectionKey == selectedKey,
                                    onClick = { selectedKey = option.selectionKey },
                                )
                            }

                            selectedOption?.let { option ->
                                Text(
                                    text = stringResource(
                                        Res.string.downloads_dialog_selected_summary,
                                        option.sourceLabel,
                                        option.qualityLabel,
                                        languageLabel(languageKey(option)),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { includeSubtitles = !includeSubtitles },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = includeSubtitles,
                                    onCheckedChange = { includeSubtitles = it },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(Res.string.downloads_dialog_subtitles),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { rememberChoice = !rememberChoice },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = rememberChoice,
                                    onCheckedChange = { rememberChoice = it },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(Res.string.downloads_dialog_remember),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (rememberChoice) {
                                        Text(
                                            text = stringResource(Res.string.downloads_dialog_remember_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.downloads_dialog_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val choice = selectedOption ?: return@Button
                            if (rememberChoice) {
                                DownloadPreferencesRepository.updatePreferences(
                                    quality = if (choice.qualityLabel.equals("Auto", ignoreCase = true)) {
                                        "ask"
                                    } else {
                                        choice.qualityLabel
                                    },
                                    server = choice.sourceLabel,
                                    language = languageKey(choice),
                                    includeSubtitles = includeSubtitles,
                                    rememberChoice = true,
                                )
                            } else {
                                DownloadPreferencesRepository.updatePreferences(
                                    includeSubtitles = includeSubtitles,
                                    rememberChoice = false,
                                )
                            }
                            onConfirm(DownloadSelection(choice, includeSubtitles))
                        },
                        enabled = selectedOption != null && !isLoading,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.downloads_dialog_download))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun languageLabel(language: String): String =
    if (language == DefaultLanguageKey) {
        stringResource(Res.string.downloads_dialog_default_language)
    } else {
        language
    }

private fun languageKey(option: DownloadSourceOption): String =
    option.languageLabel?.takeIf { it.isNotBlank() } ?: DefaultLanguageKey

private fun languagesFor(
    options: List<DownloadSourceOption>,
    server: String,
): List<String> = options
    .filter { it.sourceLabel == server }
    .map(::languageKey)
    .distinct()

private fun firstLanguage(
    options: List<DownloadSourceOption>,
    server: String,
): String? = languagesFor(options, server).firstOrNull()

private fun bestOption(
    options: List<DownloadSourceOption>,
    server: String?,
    language: String?,
    prefs: DownloadPreferences,
    useRemembered: Boolean,
): DownloadSourceOption? {
    val filtered = options.filter {
        it.sourceLabel == server && languageKey(it) == language
    }
    if (filtered.isEmpty()) return null
    if (useRemembered && prefs.preferredQuality != "ask") {
        filtered.firstOrNull {
            it.qualityLabel.equals(prefs.preferredQuality, ignoreCase = true)
        }?.let { return it }
    }
    return filtered.maxWithOrNull(
        compareBy<DownloadSourceOption> { it.qualityRank }
            .thenBy { it.resumable == true }
            .thenBy { it.sizeBytes ?: 0L },
    )
}

private data class InitialSelection(val option: DownloadSourceOption)

private fun initialSelection(
    options: List<DownloadSourceOption>,
    prefs: DownloadPreferences,
): InitialSelection? {
    if (options.isEmpty()) return null
    if (!prefs.rememberChoice) return null
    val server = options.firstOrNull {
        it.sourceLabel.equals(prefs.preferredServer, ignoreCase = true)
    }?.sourceLabel ?: return null
    val languages = languagesFor(options, server)
    val language = prefs.preferredLanguage.takeIf { it in languages } ?: languages.firstOrNull()
    val option = bestOption(options, server, language, prefs, useRemembered = true)
    return option?.let(::InitialSelection)
}

@Composable
private fun DownloadSourceRow(
    option: DownloadSourceOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.qualityLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DownloadTag(text = languageLabel(languageKey(option)))
                }
                Text(
                    text = "${stringResource(Res.string.downloads_dialog_size)}: ${option.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadTag(text = "${option.speedLabel} response")
                    DownloadTag(
                        text = option.resumeLabel,
                        highlighted = option.resumable == true,
                    )
                }
            }
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(Res.string.downloads_dialog_selected),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DownloadTag(
    text: String,
    emphasized: Boolean = false,
    highlighted: Boolean = false,
) {
    val containerColor = when {
        emphasized -> MaterialTheme.colorScheme.primary
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        emphasized -> MaterialTheme.colorScheme.onPrimary
        highlighted -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
