package com.nuvio.app.features.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Shows the real download sources returned for the selected title/episode. Each row comes from an
 * actual direct stream; the server is probed for size, resume support and response speed before
 * this list is displayed.
 */
@Composable
fun DownloadOptionsDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    loadOptions: suspend () -> List<DownloadSourceOption>,
    onConfirm: (DownloadSourceOption) -> Unit,
) {
    val prefs by DownloadPreferencesRepository.preferences.collectAsState()
    var options by remember { mutableStateOf<List<DownloadSourceOption>>(emptyList()) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var includeSubtitles by remember { mutableStateOf(prefs.includeSubtitles) }

    LaunchedEffect(reloadToken) {
        isLoading = true
        loadError = null
        options = emptyList()
        runCatching { loadOptions() }
            .onSuccess { loaded ->
                options = loaded
                if (selectedKey == null || loaded.none { it.selectionKey == selectedKey }) {
                    selectedKey = loaded.firstOrNull()?.selectionKey
                }
            }
            .onFailure { error ->
                loadError = error.message?.takeIf { it.isNotBlank() } ?: "Could not load download sources"
            }
        isLoading = false
    }

    val selectedOption = options.firstOrNull { it.selectionKey == selectedKey }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (!subtitle.isNullOrBlank()) "$title • $subtitle" else title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

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
                                    text = "Finding real sources…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Checking quality, size, speed and resume support",
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
                                text = "No downloadable source found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = loadError
                                    ?: "CAM prints are hidden. A direct MP4/MKV source is required for offline download.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { reloadToken++ }) {
                                Text("Try again")
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = "Available sources (${options.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(options, key = { it.selectionKey }) { option ->
                                DownloadSourceRow(
                                    option = option,
                                    selected = option.selectionKey == selectedKey,
                                    onClick = { selectedKey = option.selectionKey },
                                )
                            }
                        }
                    }
                }

                if (options.isNotEmpty() && !isLoading) {
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
                            text = "Download subtitles when available",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val choice = selectedOption ?: return@Button
                            DownloadPreferencesRepository.updatePreferences(
                                includeSubtitles = includeSubtitles,
                            )
                            onConfirm(choice)
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
                        Text("Download")
                    }
                }
            }
        }
    }
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
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.sourceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadTag(
                    text = option.qualityLabel,
                    emphasized = true,
                )
                if (selected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = buildString {
                    append(option.sizeLabel)
                    option.languageLabel?.let {
                        append(" • ")
                        append(it)
                    }
                },
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
