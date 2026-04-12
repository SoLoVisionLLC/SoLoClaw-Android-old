package com.solovision.openclawagents.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.AppUiState
import com.solovision.openclawagents.model.CronDraft
import com.solovision.openclawagents.model.CronJob
import com.solovision.openclawagents.model.CronJobRun
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun CronScreen(
    uiState: AppUiState,
    onRefresh: () -> Unit,
    onSelectJob: (String?) -> Unit,
    onRefreshRuns: (String?) -> Unit,
    onToggleEnabled: (CronJob, Boolean) -> Unit,
    onRunJob: (CronJob) -> Unit,
    onDeleteJob: (CronJob) -> Unit,
    onCreateJob: (CronDraft) -> Unit,
    onUpdateJob: (CronDraft) -> Unit,
    onClearActionMessage: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var editingDraft by remember { mutableStateOf<CronDraft?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDeleteJob by remember { mutableStateOf<CronJob?>(null) }

    val selectedJob = uiState.cron.jobs.firstOrNull { it.id == uiState.cron.selectedJobId }
    val filteredJobs = uiState.cron.jobs.filter { job ->
        val matchesQuery = searchQuery.isBlank() ||
            job.name.contains(searchQuery, ignoreCase = true) ||
            job.payload.text.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filter) {
            "enabled" -> job.enabled
            "disabled" -> !job.enabled
            "failing" -> job.consecutiveErrors > 0
            "healthy" -> job.enabled && job.consecutiveErrors == 0
            else -> true
        }
        matchesQuery && matchesFilter
    }

    if (showEditor) {
        CronEditorDialog(
            draft = editingDraft ?: CronDraft(),
            agents = uiState.agents,
            onDismiss = { showEditor = false },
            onSave = { draft ->
                if (draft.id == null) onCreateJob(draft) else onUpdateJob(draft)
                showEditor = false
            }
        )
    }

    if (uiState.cron.supportsDelete) {
        pendingDeleteJob?.let { job ->
        AlertDialog(
            onDismissRequest = { pendingDeleteJob = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteJob(job)
                        pendingDeleteJob = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteJob = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete cron job?") },
            text = { Text("This will remove ${job.name} if the current backend supports cron deletion.") }
        )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cron Jobs", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Gateway-RPC live control for scheduled automation, aligned with the SoLoBot dashboard control lane.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (uiState.cron.actionMessage != null || uiState.errorMessage != null) {
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            uiState.errorMessage ?: uiState.cron.actionMessage.orEmpty(),
                            color = if (uiState.errorMessage != null) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        if (uiState.cron.actionMessage != null) {
                            TextButton(onClick = onClearActionMessage) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refresh")
                }
                OutlinedButton(
                    onClick = {
                        editingDraft = CronDraft()
                        showEditor = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add Job")
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search jobs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all", "enabled", "disabled", "failing", "healthy").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
        if (uiState.cron.isLoading && uiState.cron.jobs.isEmpty()) {
            item { CronEmptyCard("Loading cron jobs from the gateway...") }
        } else if (filteredJobs.isEmpty()) {
            item { CronEmptyCard("No cron jobs match the current filters.") }
        } else {
            items(filteredJobs, key = { it.id }) { job ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CronJobCard(
                        job = job,
                        selected = selectedJob?.id == job.id,
                        running = uiState.cron.runningJobId == job.id,
                        agent = uiState.agents.firstOrNull { it.id.equals(job.agentId, ignoreCase = true) },
                        onSelect = {
                            onSelectJob(if (selectedJob?.id == job.id) null else job.id)
                            if (selectedJob?.id != job.id) onRefreshRuns(job.id)
                        }
                    )
                    if (selectedJob?.id == job.id) {
                        CronJobDetailsCard(
                            job = job,
                            runs = uiState.cron.runsByJobId[job.id].orEmpty(),
                            isLoading = uiState.cron.isLoadingRuns,
                            running = uiState.cron.runningJobId == job.id,
                            onRefresh = { onRefreshRuns(job.id) },
                            onToggleEnabled = { onToggleEnabled(job, !job.enabled) },
                            onRun = { onRunJob(job) },
                            onEdit = {
                                editingDraft = job.toDraft()
                                showEditor = true
                            },
                            onDelete = if (uiState.cron.supportsDelete) {
                                { pendingDeleteJob = job }
                            } else {
                                null
                            },
                            onCopy = {
                                clipboardManager.setText(
                                    AnnotatedString(buildCronExportJson(job, uiState.cron.runsByJobId[job.id].orEmpty()))
                                )
                            },
                            onShare = {
                                val exportPayload = buildCronExportJson(job, uiState.cron.runsByJobId[job.id].orEmpty())
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "${job.name} cron job")
                                    putExtra(Intent.EXTRA_TEXT, exportPayload)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share cron job"))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CronEmptyCard(message: String) {
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

@Composable
private fun CronJobCard(
    job: CronJob,
    selected: Boolean,
    running: Boolean,
    agent: Agent?,
    onSelect: () -> Unit,
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(job.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(agent?.name, job.sessionTarget).joinToString(" | ").ifBlank { "No agent target" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (job.enabled) {
                        if (job.consecutiveErrors > 0) "Failing" else "Enabled"
                    } else {
                        "Paused"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!job.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (job.consecutiveErrors > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(
                job.payload.text.ifBlank { "No prompt configured." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    cronScheduleDescription(job.schedule.expr),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (running) "Running now" else "Tap for details",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!job.lastError.isNullOrBlank()) {
                Text(
                    "Latest error: ${job.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CronJobDetailsCard(
    job: CronJob,
    runs: List<CronJobRun>,
    isLoading: Boolean,
    running: Boolean,
    onRefresh: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Job Details", style = MaterialTheme.typography.titleLarge)
                    Text(job.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                OutlinedButton(onClick = onToggleEnabled) {
                    Text(if (job.enabled) "Pause" else "Enable")
                }
                OutlinedButton(onClick = onRun, enabled = !running) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(if (running) "Running..." else "Run Now")
                }
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
                onDelete?.let { deleteAction ->
                    OutlinedButton(onClick = deleteAction) {
                        Text("Delete")
                    }
                }
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refresh Runs")
                }
                OutlinedButton(onClick = onCopy) {
                    Text("Copy JSON")
                }
                OutlinedButton(onClick = onShare) {
                    Text("Share JSON")
                }
            }
            Text(
                cronScheduleDescription(job.schedule.expr),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Next: ${job.nextRunAt ?: "Unknown"} | Last: ${job.lastRunAt ?: "Never"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                job.payload.text.ifBlank { "No prompt configured." },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!job.lastError.isNullOrBlank()) {
                Text(
                    "Latest error: ${job.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text("Recent Runs", style = MaterialTheme.typography.titleMedium)
            if (isLoading) {
                Text("Loading timeline...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (runs.isEmpty()) {
                Text("No recent runs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    runs.forEach { run ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "${run.status.replaceFirstChar { it.uppercase() }} - ${run.startedAt ?: "Unknown time"}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (!run.output.isNullOrBlank()) {
                                    Text(run.output, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!run.error.isNullOrBlank()) {
                                    Text(
                                        run.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildCronExportJson(job: CronJob, runs: List<CronJobRun>): String {
    val jobJson = JSONObject()
        .put("id", job.id)
        .put("name", job.name)
        .put("enabled", job.enabled)
        .put("agentId", job.agentId)
        .put("sessionTarget", job.sessionTarget)
        .put(
            "schedule",
            JSONObject()
                .put("kind", job.schedule.kind)
                .put("expr", job.schedule.expr)
                .put("timezone", job.schedule.timezone)
        )
        .put(
            "payload",
            JSONObject()
                .put("kind", job.payload.kind)
                .put("text", job.payload.text)
                .put("model", job.payload.model)
        )
        .put(
            "delivery",
            job.delivery?.let {
                JSONObject()
                    .put("mode", it.mode)
                    .put("channel", it.channel)
                    .put("target", it.target)
            }
        )
        .put("lastRunAt", job.lastRunAt)
        .put("nextRunAt", job.nextRunAt)
        .put("lastStatus", job.lastStatus)
        .put("consecutiveErrors", job.consecutiveErrors)
        .put("lastError", job.lastError)

    val runsJson = JSONArray().apply {
        runs.forEach { run ->
            put(
                JSONObject()
                    .put("id", run.id)
                    .put("status", run.status)
                    .put("success", run.success)
                    .put("startedAt", run.startedAt)
                    .put("completedAt", run.completedAt)
                    .put("output", run.output)
                    .put("error", run.error)
                    .put("durationMs", run.durationMs)
                    .put("sessionKey", run.sessionKey)
            )
        }
    }

    return JSONObject()
        .put("job", jobJson)
        .put("runs", runsJson)
        .toString(2)
}

@Composable
private fun CronEditorDialog(
    draft: CronDraft,
    agents: List<Agent>,
    onDismiss: () -> Unit,
    onSave: (CronDraft) -> Unit
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var command by remember(draft.id) { mutableStateOf(draft.command) }
    var scheduleType by remember(draft.id) { mutableStateOf(parseScheduleType(draft.scheduleExpr)) }
    var dailyTime by remember(draft.id) { mutableStateOf(parseScheduleTime(draft.scheduleExpr)) }
    var weeklyDay by remember(draft.id) { mutableStateOf(parseScheduleDay(draft.scheduleExpr)) }
    var monthlyDay by remember(draft.id) { mutableStateOf(parseMonthlyDay(draft.scheduleExpr)) }
    var minutesInterval by remember(draft.id) { mutableStateOf(parseMinutesInterval(draft.scheduleExpr)) }
    var agentId by remember(draft.id) { mutableStateOf(draft.agentId.orEmpty()) }
    var sessionTarget by remember(draft.id) { mutableStateOf(draft.sessionTarget) }
    var model by remember(draft.id) { mutableStateOf(draft.model.orEmpty()) }
    var deliveryMode by remember(draft.id) { mutableStateOf(draft.deliveryMode) }
    var deliveryChannel by remember(draft.id) { mutableStateOf(draft.deliveryChannel.orEmpty()) }
    var deliveryTarget by remember(draft.id) { mutableStateOf(draft.deliveryTarget.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CronDraft(
                            id = draft.id,
                            name = name.trim(),
                            scheduleExpr = buildScheduleExpression(
                                type = scheduleType,
                                dailyTime = dailyTime,
                                weeklyDay = weeklyDay,
                                monthlyDay = monthlyDay,
                                minutesInterval = minutesInterval
                            ),
                            command = command.trim(),
                            agentId = agentId.ifBlank { null },
                            sessionTarget = sessionTarget,
                            enabled = draft.enabled,
                            model = model.ifBlank { null },
                            deliveryMode = deliveryMode,
                            deliveryChannel = deliveryChannel.ifBlank { null },
                            deliveryTarget = deliveryTarget.ifBlank { null }
                        )
                    )
                },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) {
                Text(if (draft.id == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(if (draft.id == null) "Add Cron Job" else "Edit Cron Job", style = MaterialTheme.typography.titleLarge)
                }
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("Prompt / command") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                }
                item {
                    OutlinedTextField(value = scheduleType, onValueChange = { scheduleType = it.lowercase() }, label = { Text("Schedule type") }, supportingText = { Text("Use daily, weekly, monthly, hourly, or minutes") }, modifier = Modifier.fillMaxWidth())
                }
                if (scheduleType == "daily" || scheduleType == "weekly" || scheduleType == "monthly") {
                    item {
                        OutlinedTextField(value = dailyTime, onValueChange = { dailyTime = it }, label = { Text("Time (HH:MM)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (scheduleType == "weekly") {
                    item {
                        OutlinedTextField(value = weeklyDay, onValueChange = { weeklyDay = it }, label = { Text("Day of week (0-6)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (scheduleType == "monthly") {
                    item {
                        OutlinedTextField(value = monthlyDay, onValueChange = { monthlyDay = it }, label = { Text("Day of month") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (scheduleType == "minutes") {
                    item {
                        OutlinedTextField(value = minutesInterval, onValueChange = { minutesInterval = it }, label = { Text("Every N minutes") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    OutlinedTextField(
                        value = agentId,
                        onValueChange = { agentId = it },
                        label = { Text("Agent ID") },
                        supportingText = {
                            Text("Known agents: ${agents.joinToString { it.id }}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(value = sessionTarget, onValueChange = { sessionTarget = it }, label = { Text("Session target") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = deliveryMode, onValueChange = { deliveryMode = it }, label = { Text("Delivery mode") }, modifier = Modifier.fillMaxWidth())
                }
                if (deliveryMode != "none") {
                    item {
                        OutlinedTextField(value = deliveryChannel, onValueChange = { deliveryChannel = it }, label = { Text("Delivery channel") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = deliveryTarget, onValueChange = { deliveryTarget = it }, label = { Text("Delivery target") }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model override") }, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Text(
                        "Cron expression preview: ${buildScheduleExpression(scheduleType, dailyTime, weeklyDay, monthlyDay, minutesInterval)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

private fun cronScheduleDescription(expr: String): String {
    val parts = expr.trim().split(Regex("\\s+"))
    if (parts.size != 5) return expr
    val minute = parts[0]
    val hour = parts[1]
    val dayOfMonth = parts[2]
    val dayOfWeek = parts[4]
    return when {
        minute.startsWith("*/") -> "Every ${minute.removePrefix("*/")} minutes"
        minute == "0" && hour == "*" -> "Every hour"
        dayOfMonth == "*" && dayOfWeek == "*" -> "Daily at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
        dayOfMonth == "*" -> "Weekly on ${dayOfWeek} at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
        else -> "Monthly on day $dayOfMonth at ${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
    }
}

private fun CronJob.toDraft(): CronDraft {
    return CronDraft(
        id = id,
        name = name,
        scheduleExpr = schedule.expr,
        command = payload.text,
        agentId = agentId,
        sessionTarget = sessionTarget,
        enabled = enabled,
        model = payload.model,
        deliveryMode = delivery?.mode ?: "none",
        deliveryChannel = delivery?.channel,
        deliveryTarget = delivery?.target
    )
}

private fun parseScheduleType(expr: String): String {
    val parts = expr.trim().split(Regex("\\s+"))
    if (parts.size != 5) return "daily"
    return when {
        parts[0].startsWith("*/") -> "minutes"
        parts[0] == "0" && parts[1] == "*" -> "hourly"
        parts[2] != "*" -> "monthly"
        parts[4] != "*" -> "weekly"
        else -> "daily"
    }
}

private fun parseScheduleTime(expr: String): String {
    val parts = expr.trim().split(Regex("\\s+"))
    if (parts.size < 2) return "09:00"
    val hour = parts[1].takeIf { it != "*" } ?: "09"
    val minute = parts[0].takeIf { it != "*" && !it.startsWith("*/") } ?: "00"
    return "${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
}

private fun parseScheduleDay(expr: String): String = expr.trim().split(Regex("\\s+")).getOrNull(4) ?: "1"

private fun parseMonthlyDay(expr: String): String = expr.trim().split(Regex("\\s+")).getOrNull(2) ?: "1"

private fun parseMinutesInterval(expr: String): String {
    val minute = expr.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    return minute.removePrefix("*/").ifBlank { "15" }
}

private fun buildScheduleExpression(
    type: String,
    dailyTime: String,
    weeklyDay: String,
    monthlyDay: String,
    minutesInterval: String
): String {
    val safeTime = dailyTime.split(":").let { parts ->
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        hour to minute
    }
    return when (type.lowercase()) {
        "minutes" -> "*/${minutesInterval.toIntOrNull()?.coerceIn(1, 59) ?: 15} * * * *"
        "hourly" -> "0 * * * *"
        "weekly" -> "${safeTime.second} ${safeTime.first} * * ${weeklyDay.toIntOrNull()?.coerceIn(0, 6) ?: 1}"
        "monthly" -> "${safeTime.second} ${safeTime.first} ${monthlyDay.toIntOrNull()?.coerceIn(1, 31) ?: 1} * *"
        else -> "${safeTime.second} ${safeTime.first} * * *"
    }
}
