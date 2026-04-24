package com.solovision.openclawagents.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.SkillFileEntry
import com.solovision.openclawagents.model.SkillHubEntry
import com.solovision.openclawagents.model.SkillSummary

@Composable
fun SkillsScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onSelectSkill: (String?) -> Unit,
    onToggleHidden: (String) -> Unit,
    onLoadSkillFiles: (String) -> Unit,
    onOpenSkillFile: (SkillFileEntry) -> Unit,
    onUpdateSkillFileContent: (String) -> Unit,
    onSaveSelectedSkillFile: () -> Unit,
    onInstallSkill: (SkillSummary, String) -> Unit,
    onSetSkillEnabled: (SkillSummary, Boolean) -> Unit,
    onUninstallSkill: (SkillSummary) -> Unit,
    onCheckSkill: (SkillSummary) -> Unit,
    onUpdateSkillFromSource: (SkillSummary) -> Unit,
    onBrowseSkillsHub: () -> Unit,
    onSearchSkillsHub: (String) -> Unit,
    onInspectHubSkill: (SkillHubEntry) -> Unit,
    onInstallHubSkill: (SkillHubEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var hubQuery by remember(uiState.skills.lastHubQuery) { mutableStateOf(uiState.skills.lastHubQuery) }
    var filter by remember { mutableStateOf("all") }
    var showHidden by remember { mutableStateOf(false) }

    val allSkills = uiState.skills.skills
    val selectedSkill = allSkills.firstOrNull { it.skillKey == uiState.skills.selectedSkillKey }
    val visibleSkills = allSkills.filter { showHidden || it.skillKey !in uiState.skills.hiddenSkillKeys }
    val filteredSkills = visibleSkills.filter { skill ->
        val matchesQuery = searchQuery.isBlank() ||
            skill.name.contains(searchQuery, ignoreCase = true) ||
            skill.description.contains(searchQuery, ignoreCase = true) ||
            skill.category.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filter) {
            "installed" -> skill.installed
            "available" -> !skill.installed
            "bundled" -> skill.bundled
            "disabled" -> skill.installed && !skill.enabled
            else -> true
        }
        matchesQuery && matchesFilter
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Skills", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Gateway-RPC live control for skill status, install, enable, and update flows, aligned with the SoLoBot dashboard control lane.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (uiState.errorMessage != null || uiState.skills.actionLog != null) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.errorMessage != null) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Text(
                        text = uiState.errorMessage ?: uiState.skills.actionLog.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        color = if (uiState.errorMessage != null) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refresh")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Show hidden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showHidden,
                        onCheckedChange = { showHidden = it }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search skills") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all", "installed", "available", "bundled", "disabled")) { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
        if (uiState.skills.supportsHub) {
            item {
                SkillsHubCard(
                    query = hubQuery,
                    results = uiState.skills.hubResults,
                    isLoading = uiState.skills.isLoadingHub,
                    actingIdentifier = uiState.skills.hubActingIdentifier,
                    onQueryChange = { hubQuery = it },
                    onBrowse = onBrowseSkillsHub,
                    onSearch = { onSearchSkillsHub(hubQuery) },
                    onInspect = onInspectHubSkill,
                    onInstall = onInstallHubSkill
                )
            }
        } else {
            item {
                SkillsEmptyCard("Gateway RPC live control is active. Skill hub browsing needs a Hermes or dashboard companion control API.")
            }
        }
        if (uiState.skills.isLoading && allSkills.isEmpty()) {
            item { SkillsEmptyCard("Loading skill catalog from the gateway...") }
        } else if (filteredSkills.isEmpty()) {
            item { SkillsEmptyCard("No skills match the current search and filters.") }
        } else {
            items(filteredSkills, key = { it.skillKey }) { skill ->
                SkillCard(
                    skill = skill,
                    selected = selectedSkill?.skillKey == skill.skillKey,
                    isHidden = skill.skillKey in uiState.skills.hiddenSkillKeys,
                    acting = uiState.skills.actingSkillKey == skill.skillKey,
                    onSelect = {
                        onSelectSkill(
                            if (selectedSkill?.skillKey == skill.skillKey) null else skill.skillKey
                        )
                    },
                    onToggleHidden = { onToggleHidden(skill.skillKey) },
                    onInstall = {
                        skill.installOptions.firstOrNull()?.let { option ->
                            onInstallSkill(skill, option.id)
                        }
                    },
                    supportsHttpActions = uiState.skills.supportsHttpActions,
                    onToggleEnabled = { onSetSkillEnabled(skill, !skill.enabled) },
                    onUninstall = { onUninstallSkill(skill) },
                    onCheck = { onCheckSkill(skill) },
                    onUpdate = { onUpdateSkillFromSource(skill) }
                )
            }
        }
        selectedSkill?.let { skill ->
            item {
                SkillDetailCard(
                    skill = skill,
                    isHidden = skill.skillKey in uiState.skills.hiddenSkillKeys,
                    acting = uiState.skills.actingSkillKey == skill.skillKey,
                    files = uiState.skills.skillFiles,
                    selectedFilePath = uiState.skills.selectedFilePath,
                    selectedFileContent = uiState.skills.selectedFileContent,
                    isLoadingFiles = uiState.skills.isLoadingFiles,
                    isSavingFile = uiState.skills.isSavingFile,
                    onRefreshFiles = { onLoadSkillFiles(skill.skillKey) },
                    onOpenSkillFile = onOpenSkillFile,
                    onUpdateSkillFileContent = onUpdateSkillFileContent,
                    onSaveSelectedSkillFile = onSaveSelectedSkillFile,
                    onToggleHidden = { onToggleHidden(skill.skillKey) },
                    onInstallSkill = { installId -> onInstallSkill(skill, installId) },
                    supportsHttpActions = uiState.skills.supportsHttpActions,
                    onToggleEnabled = { onSetSkillEnabled(skill, !skill.enabled) },
                    onUninstall = { onUninstallSkill(skill) },
                    onCheck = { onCheckSkill(skill) },
                    onUpdate = { onUpdateSkillFromSource(skill) }
                )
            }
        }
    }
}

@Composable
private fun SkillsHubCard(
    query: String,
    results: List<SkillHubEntry>,
    isLoading: Boolean,
    actingIdentifier: String?,
    onQueryChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onInspect: (SkillHubEntry) -> Unit,
    onInstall: (SkillHubEntry) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Skills Hub", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Browse and install structured hub entries like Hermes mission control.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search hub") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBrowse, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(if (isLoading && query.isBlank()) "Loading..." else "Browse")
                }
                OutlinedButton(onClick = onSearch, enabled = !isLoading) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(if (isLoading && query.isNotBlank()) "Searching..." else "Search")
                }
            }
            if (isLoading && results.isEmpty()) {
                SkillsEmptyCard("Loading hub entries...")
            } else if (results.isEmpty()) {
                SkillsEmptyCard("No hub results yet. Browse or search to load installable skills.")
            } else {
                results.take(12).forEach { entry ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    entry.source.takeIf { it.isNotBlank() },
                                    entry.trustLevel.takeIf { it.isNotBlank() },
                                    entry.repo
                                ).joinToString(" - "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                entry.description.ifBlank { entry.identifier },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onInspect(entry) },
                                    enabled = actingIdentifier != entry.identifier
                                ) {
                                    Text(if (actingIdentifier == entry.identifier) "Working..." else "Inspect")
                                }
                                OutlinedButton(
                                    onClick = { onInstall(entry) },
                                    enabled = actingIdentifier != entry.identifier
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Text(if (actingIdentifier == entry.identifier) "Installing..." else "Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillSummary,
    selected: Boolean,
    isHidden: Boolean,
    acting: Boolean,
    onSelect: () -> Unit,
    onToggleHidden: () -> Unit,
    onInstall: () -> Unit,
    supportsHttpActions: Boolean,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(skill.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            skill.category.takeIf { it.isNotBlank() },
                            skill.source.takeIf { it.isNotBlank() },
                            if (isHidden) "Hidden on mobile" else null
                        ).joinToString(" - "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = skillBadge(skill),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        !skill.installed -> MaterialTheme.colorScheme.onSurfaceVariant
                        !skill.enabled -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(
                skill.description.ifBlank { "No description available." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!skill.primaryEnv.isNullOrBlank() || !skill.assignedAgent.isNullOrBlank()) {
                Text(
                    listOfNotNull(
                        skill.primaryEnv?.let { "Env: $it" },
                        skill.assignedAgent?.let { "Agent: $it" }
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onToggleHidden) {
                    Text(if (isHidden) "Unhide" else "Hide")
                }
                if (!skill.installed && skill.installOptions.isNotEmpty()) {
                    OutlinedButton(onClick = onInstall, enabled = !acting) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Text(if (acting) "Installing..." else skill.installOptions.first().label)
                    }
                }
                if (skill.installed && supportsHttpActions) {
                    OutlinedButton(onClick = onCheck, enabled = !acting) {
                        Text("Check")
                    }
                    OutlinedButton(onClick = onUpdate, enabled = !acting) {
                        Text("Update")
                    }
                    OutlinedButton(onClick = onToggleEnabled, enabled = !acting) {
                        Text(if (skill.enabled) "Disable" else "Enable")
                    }
                }
                if (skill.installed && skill.canUninstall && supportsHttpActions) {
                    OutlinedButton(onClick = onUninstall, enabled = !acting) {
                        Text("Uninstall")
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillDetailCard(
    skill: SkillSummary,
    isHidden: Boolean,
    acting: Boolean,
    files: List<SkillFileEntry>,
    selectedFilePath: String?,
    selectedFileContent: String,
    isLoadingFiles: Boolean,
    isSavingFile: Boolean,
    onRefreshFiles: () -> Unit,
    onOpenSkillFile: (SkillFileEntry) -> Unit,
    onUpdateSkillFileContent: (String) -> Unit,
    onSaveSelectedSkillFile: () -> Unit,
    onToggleHidden: () -> Unit,
    onInstallSkill: (String) -> Unit,
    supportsHttpActions: Boolean,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(skill.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(
                        skill.category.takeIf { it.isNotBlank() },
                        skill.source.takeIf { it.isNotBlank() },
                        skill.path.takeIf { it.isNotBlank() }
                    ).joinToString(" - "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                skill.description.ifBlank { "No description available." },
                style = MaterialTheme.typography.bodyMedium
            )
            if (skill.hasRequirements()) {
                SkillRequirementsCard(skill)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onToggleHidden) {
                    Text(if (isHidden) "Unhide on mobile" else "Hide on mobile")
                }
                if (!skill.installed) {
                    skill.installOptions.forEach { option ->
                        OutlinedButton(
                            onClick = { onInstallSkill(option.id) },
                            enabled = !acting
                        ) {
                            Text(if (acting) "Installing..." else option.label)
                        }
                    }
                } else if (supportsHttpActions) {
                    OutlinedButton(onClick = onCheck, enabled = !acting) {
                        Text("Check skill")
                    }
                    OutlinedButton(onClick = onUpdate, enabled = !acting) {
                        Text("Update from source")
                    }
                    OutlinedButton(onClick = onToggleEnabled, enabled = !acting) {
                        Text(if (skill.enabled) "Disable skill" else "Enable skill")
                    }
                    if (skill.canUninstall) {
                        OutlinedButton(onClick = onUninstall, enabled = !acting) {
                            Text("Uninstall")
                        }
                    }
                }
            }
            if (skill.installed && supportsHttpActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onRefreshFiles) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Refresh Files")
                    }
                    Text(
                        text = if (selectedFilePath == null) {
                            "Select a file to inspect or edit."
                        } else {
                            selectedFilePath
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoadingFiles && files.isEmpty()) {
                    SkillsEmptyCard("Loading skill files...")
                } else if (files.isEmpty()) {
                    SkillsEmptyCard("No editable files were returned for this skill.")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(files, key = { it.relativePath }) { file ->
                            FilterChip(
                                selected = file.relativePath == selectedFilePath,
                                onClick = { onOpenSkillFile(file) },
                                label = { Text(file.name) }
                            )
                        }
                    }
                }
                if (selectedFilePath != null) {
                    OutlinedTextField(
                        value = selectedFileContent,
                        onValueChange = onUpdateSkillFileContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        label = { Text("File Contents") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    OutlinedButton(
                        onClick = onSaveSelectedSkillFile,
                        enabled = !isSavingFile
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(if (isSavingFile) "Saving..." else "Save File")
                    }
                }
            } else if (skill.installed) {
                SkillsEmptyCard("This deployment is running in gateway-RPC mode. Skill files, uninstall, and source-maintenance actions need a companion control API.")
            }
        }
    }
}

@Composable
private fun SkillRequirementsCard(skill: SkillSummary) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Readiness", style = MaterialTheme.typography.titleMedium)
            if (skill.blockedByAllowlist) {
                Text(
                    "This skill is blocked by the current allowlist.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            missingSummaryLines(skill).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SkillsEmptyCard(message: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun skillBadge(skill: SkillSummary): String {
    return when {
        !skill.installed -> "Available"
        !skill.enabled -> "Disabled"
        skill.bundled -> "Bundled"
        else -> "Installed"
    }
}

private fun SkillSummary.hasRequirements(): Boolean {
    return blockedByAllowlist ||
        missing.bins.isNotEmpty() ||
        missing.anyBins.isNotEmpty() ||
        missing.env.isNotEmpty() ||
        missing.config.isNotEmpty() ||
        missing.os.isNotEmpty()
}

private fun missingSummaryLines(skill: SkillSummary): List<String> {
    return buildList {
        if (skill.missing.bins.isNotEmpty()) add("Missing binaries: ${skill.missing.bins.joinToString()}")
        if (skill.missing.anyBins.isNotEmpty()) add("Need one of: ${skill.missing.anyBins.joinToString()}")
        if (skill.missing.env.isNotEmpty()) add("Missing env vars: ${skill.missing.env.joinToString()}")
        if (skill.missing.config.isNotEmpty()) add("Missing config: ${skill.missing.config.joinToString()}")
        if (skill.missing.os.isNotEmpty()) add("Unsupported OS: ${skill.missing.os.joinToString()}")
        if (isEmpty()) add("No additional requirements reported.")
    }
}
