package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Parses user-entered time strings into milliseconds.
 * Supports:
 * - "HH:MM:SS" (e.g. "1:23:45")
 * - "MM:SS" (e.g. "12:34")
 * - "M" or "MM" (e.g. "45" -> 45 minutes)
 */
fun parseJumpTimestampToMs(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":").map { it.trim().toLongOrNull() }
    if (parts.any { it == null || it < 0 }) return null
    return when (parts.size) {
        1 -> (parts[0] ?: 0L) * 60_000L
        2 -> ((parts[0] ?: 0L) * 60 + (parts[1] ?: 0L)) * 1_000L
        3 -> ((parts[0] ?: 0L) * 3600 + (parts[1] ?: 0L) * 60 + (parts[2] ?: 0L)) * 1_000L
        else -> null
    }
}

fun formatMsToTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}

@Composable
fun JumpToTimeDialog(
    currentPositionMs: Long,
    durationMs: Long,
    onDismiss: () -> Unit,
    onJump: (Long) -> Unit,
) {
    var timeInput by remember { mutableStateOf(formatMsToTimestamp(currentPositionMs)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun submitJump() {
        val targetMs = parseJumpTimestampToMs(timeInput)
        if (targetMs == null) {
            errorMessage = "Enter valid time (e.g. 1:25:00 or 15:30)"
            return
        }
        if (durationMs > 0 && targetMs > durationMs) {
            errorMessage = "Time exceeds duration (${formatMsToTimestamp(durationMs)})"
            return
        }
        val safeMs = if (durationMs > 0) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        onJump(safeMs)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFF1E1E24),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Jump to Time",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (durationMs > 0) {
                    Text(
                        text = "Duration: ${formatMsToTimestamp(durationMs)} • Current: ${formatMsToTimestamp(currentPositionMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time input field
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = {
                        timeInput = it
                        errorMessage = null
                    },
                    label = { Text("Timestamp (HH:MM:SS or MM:SS)", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitJump() }),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick jump chips
                Text(
                    text = "Quick Jump",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val quickJumps = listOf(
                        "Start" to 0L,
                        "+5m" to (currentPositionMs + 5 * 60_000L),
                        "+10m" to (currentPositionMs + 10 * 60_000L),
                        "+30m" to (currentPositionMs + 30 * 60_000L),
                    )
                    quickJumps.forEach { (label, target) ->
                        val safeTarget = if (durationMs > 0) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    timeInput = formatMsToTimestamp(safeTarget)
                                    errorMessage = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White.copy(alpha = 0.7f),
                        ),
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { submitJump() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Jump", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
