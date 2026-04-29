package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.TalkPhase

@Composable
fun VoiceScreen(
    uiState: AppUiState,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onSetTalkEnabled: (Boolean) -> Unit,
    onStartManualMic: () -> Unit,
    onStopManualMic: () -> Unit,
    onUpdateSpeechLocale: (String) -> Unit,
    onUpdateSilenceTimeoutMs: (Int) -> Unit,
    onSetInterruptOnSpeech: (Boolean) -> Unit
) {
    val talk = uiState.talkMode
    val settings = uiState.voiceSettings
    val localeDraft = remember(settings.speechLocale) { mutableStateOf(settings.speechLocale) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Voice", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Manual Mic sends one turn. Talk keeps listening, waits for silence, asks OpenClaw, then speaks through Gateway talk.speak.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Talk Mode", fontWeight = FontWeight.SemiBold)
                        Text(talk.statusMessage, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = talk.talkEnabled,
                        onCheckedChange = { enabled ->
                            if (!hasMicPermission) onRequestMicPermission() else onSetTalkEnabled(enabled)
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(talk.phase.label()) })
                    AssistChip(onClick = {}, label = { Text(talk.providerStatus) })
                }

                if (talk.errorMessage != null) {
                    Text(talk.errorMessage, color = MaterialTheme.colorScheme.error)
                }

                if (talk.lastTranscript.isNotBlank()) {
                    Text("Heard: ${talk.lastTranscript}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Manual Mic", fontWeight = FontWeight.SemiBold)
                        Text("Mutually exclusive with Talk Mode; stops when app leaves the foreground.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !talk.manualMicActive,
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission() else onStartManualMic()
                        }
                    ) { Text("Start Mic") }
                    Button(
                        enabled = talk.manualMicActive,
                        onClick = onStopManualMic
                    ) { Text("Stop Mic") }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Talk Settings", fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = localeDraft.value,
                    onValueChange = {
                        localeDraft.value = it
                        onUpdateSpeechLocale(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Speech locale") },
                    placeholder = { Text("Device default, or en-US") },
                    singleLine = true
                )

                Text("Silence timeout: ${settings.silenceTimeoutMs}ms")
                Slider(
                    value = settings.silenceTimeoutMs.toFloat(),
                    onValueChange = { onUpdateSilenceTimeoutMs(it.toInt()) },
                    valueRange = 300f..3000f,
                    steps = 26
                )
                Text("OpenClaw Android default is ~700ms unless config overrides it.", style = MaterialTheme.typography.bodySmall)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Interrupt on speech", fontWeight = FontWeight.SemiBold)
                        Text("Stops playback when you start talking.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = settings.interruptOnSpeech, onCheckedChange = onSetInterruptOnSpeech)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun TalkPhase.label(): String = when (this) {
    TalkPhase.Idle -> "Idle"
    TalkPhase.Connecting -> "Connecting"
    TalkPhase.Listening -> "Listening"
    TalkPhase.Thinking -> "Thinking / Asking OpenClaw"
    TalkPhase.Speaking -> "Speaking"
    TalkPhase.Error -> "Error"
}
