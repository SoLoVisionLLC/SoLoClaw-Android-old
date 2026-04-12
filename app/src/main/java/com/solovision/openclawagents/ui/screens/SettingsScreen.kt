package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: AppUiState,
    onBack: () -> Unit,
    onUpdateCartesiaApiKey: (String) -> Unit,
    onUpdateCartesiaModelId: (String) -> Unit,
    onUpdateKokoroEndpoint: (String) -> Unit,
    onUpdateKokoroApiKey: (String) -> Unit,
    onUpdateKokoroModel: (String) -> Unit,
    onUpdateLemonfoxApiKey: (String) -> Unit,
    onUpdateLemonfoxLanguage: (String) -> Unit,
    onUpdateLemonfoxSpeed: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsCard(
                    title = "Cartesia",
                    body = "Shared Cartesia credentials and defaults for room playback and agent voices."
                ) {
                    OutlinedTextField(
                        value = uiState.voiceSettings.cartesiaApiKey,
                        onValueChange = onUpdateCartesiaApiKey,
                        label = { Text("Cartesia API key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.cartesiaModelId,
                        onValueChange = onUpdateCartesiaModelId,
                        label = { Text("Default Cartesia model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            item {
                SettingsCard(
                    title = "Kokoro",
                    body = "Self-hosted Kokoro endpoint and model settings shared across the app."
                ) {
                    OutlinedTextField(
                        value = uiState.voiceSettings.kokoroEndpoint,
                        onValueChange = onUpdateKokoroEndpoint,
                        label = { Text("Kokoro endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.kokoroApiKey,
                        onValueChange = onUpdateKokoroApiKey,
                        label = { Text("Kokoro API key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.kokoroModel,
                        onValueChange = onUpdateKokoroModel,
                        label = { Text("Default Kokoro model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            item {
                SettingsCard(
                    title = "Lemonfox",
                    body = "Shared Lemonfox credentials and defaults for app voice playback."
                ) {
                    OutlinedTextField(
                        value = uiState.voiceSettings.lemonfoxApiKey,
                        onValueChange = onUpdateLemonfoxApiKey,
                        label = { Text("Lemonfox API key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.lemonfoxLanguage,
                        onValueChange = onUpdateLemonfoxLanguage,
                        label = { Text("Default Lemonfox language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.voiceSettings.lemonfoxSpeed,
                        onValueChange = onUpdateLemonfoxSpeed,
                        label = { Text("Default Lemonfox speed") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                content()
            }
        )
    }
}
