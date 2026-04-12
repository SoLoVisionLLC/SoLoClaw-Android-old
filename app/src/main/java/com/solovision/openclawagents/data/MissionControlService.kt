package com.solovision.openclawagents.data

import com.solovision.openclawagents.model.CronDelivery
import com.solovision.openclawagents.model.CronDraft
import com.solovision.openclawagents.model.CronJob
import com.solovision.openclawagents.model.CronJobRun
import com.solovision.openclawagents.model.CronPayload
import com.solovision.openclawagents.model.CronSchedule
import com.solovision.openclawagents.model.MissionControlCapabilities
import com.solovision.openclawagents.model.SkillFileEntry
import com.solovision.openclawagents.model.SkillHubEntry
import com.solovision.openclawagents.model.SkillInstallOption
import com.solovision.openclawagents.model.SkillMissingState
import com.solovision.openclawagents.model.SkillSummary
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class MissionControlService(
    private val transport: GatewayRpcOpenClawTransport
) {

    suspend fun detectCapabilities(): MissionControlCapabilities {
        return MissionControlCapabilities(
            // Android is intentionally running in gateway-RPC control mode.
            // Hermes-style HTTP control endpoints may exist on separate companion
            // services, but they are not part of the guaranteed live-control lane.
            supportsCronDelete = false,
            supportsSkillHttpActions = false,
            supportsSkillHub = false
        )
    }

    suspend fun listCronJobs(): List<CronJob> {
        val result = transport.requestGatewayMethod(
            method = "cron.list",
            params = mapOf("includeDisabled" to true)
        )
        val jobs = result["jobs"] as? List<*> ?: emptyList<Any>()
        return jobs.mapNotNull(::parseCronJob)
    }

    suspend fun listCronRuns(jobId: String, limit: Int = 24): List<CronJobRun> {
        val result = transport.requestGatewayMethod(
            method = "cron.runs",
            params = mapOf("jobId" to jobId, "limit" to limit)
        )
        val runs = result["entries"] as? List<*>
            ?: result["runs"] as? List<*>
            ?: emptyList<Any>()
        return runs.mapNotNull(::parseCronRun)
    }

    suspend fun createCronJob(draft: CronDraft) {
        transport.requestGatewayMethod(
            method = "cron.add",
            params = buildCronJobPayload(draft)
        )
    }

    suspend fun updateCronJob(draft: CronDraft) {
        val jobId = draft.id ?: error("Cron job id is required for updates")
        transport.requestGatewayMethod(
            method = "cron.update",
            params = mapOf(
                "jobId" to jobId,
                "patch" to buildCronJobPatch(draft)
            )
        )
    }

    suspend fun setCronEnabled(jobId: String, enabled: Boolean) {
        transport.requestGatewayMethod(
            method = "cron.update",
            params = mapOf(
                "jobId" to jobId,
                "patch" to mapOf("enabled" to enabled)
            )
        )
    }

    suspend fun runCronJob(jobId: String) {
        transport.requestGatewayMethod(
            method = "cron.run",
            params = mapOf("jobId" to jobId, "mode" to "force")
        )
    }

    suspend fun deleteCronJob(jobId: String) {
        runCatching {
            transport.requestHttpJson(
                path = "/api/cron-jobs/${encodeSegment(jobId)}",
                method = "DELETE"
            )
        }.onSuccess {
            return
        }

        runCatching {
            transport.requestGatewayMethod(
                method = "cron.delete",
                params = mapOf("jobId" to jobId)
            )
        }.onSuccess {
            return
        }

        transport.requestGatewayMethod(
            method = "cron.remove",
            params = mapOf("jobId" to jobId)
        )
    }

    suspend fun listSkills(): List<SkillSummary> {
        val result = runCatching {
            transport.requestGatewayMethod("skills.status")
        }.getOrElse {
            transport.requestGatewayMethod("skills.list")
        }

        val skills = result["skills"] as? List<*> ?: emptyList<Any>()
        return skills.mapNotNull(::parseSkill)
    }

    suspend fun installSkill(skillName: String, installId: String): String {
        val result = transport.requestGatewayMethod(
            method = "skills.install",
            params = mapOf("name" to skillName, "installId" to installId)
        )
        return formatGatewayCommandResult(result, "Install request finished.")
    }

    suspend fun setSkillEnabled(skillKey: String, enabled: Boolean) {
        transport.requestGatewayMethod(
            method = "skills.update",
            params = mapOf("skillKey" to skillKey, "enabled" to enabled)
        )
    }

    suspend fun updateSkillEnv(skillKey: String, env: Map<String, String>) {
        transport.requestGatewayMethod(
            method = "skills.update",
            params = mapOf("skillKey" to skillKey, "env" to env)
        )
    }

    suspend fun listSkillFiles(skillKey: String): List<SkillFileEntry> {
        val payload = transport.requestHttpJson("/api/skills/${encodeSegment(skillKey)}/files")
        val root = JSONObject(payload)
        val files = root.optJSONArray("files") ?: JSONArray()
        return buildList {
            repeat(files.length()) { index ->
                val file = files.optJSONObject(index) ?: return@repeat
                add(
                    SkillFileEntry(
                        name = file.optString("name"),
                        relativePath = file.optString("relative_path")
                            .ifBlank { file.optString("relativePath") },
                        size = file.optLong("size", 0L),
                        modifiedAt = file.optString("mtime")
                    )
                )
            }
        }
    }

    suspend fun readSkillFile(skillKey: String, relativePath: String): String {
        val payload = transport.requestHttpJson(
            "/api/skills/${encodeSegment(skillKey)}/files/${encodePath(relativePath)}"
        )
        return JSONObject(payload).optString("content")
    }

    suspend fun saveSkillFile(skillKey: String, relativePath: String, content: String) {
        transport.requestHttpJson(
            path = "/api/skills/${encodeSegment(skillKey)}/files/${encodePath(relativePath)}",
            method = "PUT",
            body = JSONObject().put("content", content).toString()
        )
    }

    suspend fun uninstallSkill(skillKey: String) {
        transport.requestHttpJson(
            path = "/api/skills/${encodeSegment(skillKey)}",
            method = "DELETE"
        )
    }

    suspend fun checkSkill(skillName: String): String {
        val payload = transport.requestHttpJson(
            path = "/api/skills/${encodeSegment(skillName)}/check",
            method = "POST",
            body = JSONObject().toString()
        )
        return formatHttpCommandResult(payload, "Skill check finished.")
    }

    suspend fun updateSkill(skillName: String): String {
        val payload = transport.requestHttpJson(
            path = "/api/skills/${encodeSegment(skillName)}/update",
            method = "POST",
            body = JSONObject().toString()
        )
        return formatHttpCommandResult(payload, "Skill update finished.")
    }

    suspend fun browseSkillsHub(page: Int = 1, size: Int = 20, source: String = "all"): List<SkillHubEntry> {
        val payload = transport.requestHttpJson(
            path = "/api/skills/hub/browse-structured",
            method = "POST",
            body = JSONObject()
                .put("page", page)
                .put("size", size)
                .put("source", source)
                .toString()
        )
        return parseHubEntries(payload)
    }

    suspend fun searchSkillsHub(query: String): List<SkillHubEntry> {
        val payload = transport.requestHttpJson(
            path = "/api/skills/hub/search-structured",
            method = "POST",
            body = JSONObject()
                .put("query", query)
                .toString()
        )
        return parseHubEntries(payload)
    }

    suspend fun inspectSkillHub(identifier: String): String {
        val payload = transport.requestHttpJson(
            path = "/api/skills/hub/inspect",
            method = "POST",
            body = JSONObject()
                .put("identifier", identifier)
                .toString()
        )
        return formatHttpCommandResult(payload, "Hub inspect finished.")
    }

    suspend fun installSkillHub(identifier: String): String {
        val payload = transport.requestHttpJson(
            path = "/api/skills/hub/install",
            method = "POST",
            body = JSONObject()
                .put("identifier", identifier)
                .toString()
        )
        return formatHttpCommandResult(payload, "Hub install finished.")
    }

    private fun buildCronJobPayload(draft: CronDraft): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "name" to draft.name.trim(),
            "schedule" to mapOf(
                "kind" to "cron",
                "expr" to draft.scheduleExpr.trim(),
                "tz" to java.util.TimeZone.getDefault().id
            ),
            "sessionTarget" to draft.sessionTarget.trim().ifBlank { "main" },
            "wakeMode" to "now",
            "payload" to buildCronPayloadMap(draft)
        )
        draft.agentId?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["agentId"] = it }
        return payload
    }

    private fun buildCronJobPatch(draft: CronDraft): Map<String, Any?> {
        val patch = linkedMapOf<String, Any?>(
            "name" to draft.name.trim(),
            "enabled" to draft.enabled,
            "schedule" to mapOf(
                "kind" to "cron",
                "expr" to draft.scheduleExpr.trim(),
                "tz" to java.util.TimeZone.getDefault().id
            ),
            "sessionTarget" to draft.sessionTarget.trim().ifBlank { "main" },
            "payload" to buildCronPayloadMap(draft),
            "delivery" to buildCronDeliveryMap(draft)
        )
        patch["agentId"] = draft.agentId?.trim()?.takeIf { it.isNotEmpty() }
        return patch
    }

    private fun buildCronPayloadMap(draft: CronDraft): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>(
            "kind" to "systemEvent",
            "text" to draft.command.trim()
        )
        draft.model?.trim()?.takeIf { it.isNotEmpty() }?.let { payload["model"] = it }
        return payload
    }

    private fun buildCronDeliveryMap(draft: CronDraft): Map<String, Any?>? {
        val mode = draft.deliveryMode.trim().ifBlank { "none" }
        if (mode == "none") return mapOf("mode" to "none")

        val delivery = linkedMapOf<String, Any?>("mode" to mode)
        draft.deliveryChannel?.trim()?.takeIf { it.isNotEmpty() }?.let { delivery["channel"] = it }
        draft.deliveryTarget?.trim()?.takeIf { it.isNotEmpty() }?.let { delivery["to"] = it }
        return delivery
    }

    private fun parseCronJob(raw: Any?): CronJob? {
        val job = raw as? Map<*, *> ?: return null
        val id = job["id"]?.toString()?.trim().orEmpty()
        if (id.isBlank()) return null

        val scheduleMap = job["schedule"] as? Map<*, *>
        val scheduleExpr = scheduleMap?.get("expr")?.toString()
            ?: job["cron"]?.toString()
            ?: job["schedule"]?.takeIf { it !is Map<*, *> }?.toString()
            ?: ""
        val scheduleKind = scheduleMap?.get("kind")?.toString() ?: "cron"
        val timezone = scheduleMap?.get("tz")?.toString()

        val payloadMap = job["payload"] as? Map<*, *>
        val deliveryMap = job["delivery"] as? Map<*, *>
        val stateMap = job["state"] as? Map<*, *>

        return CronJob(
            id = id,
            name = job["name"]?.toString()?.ifBlank { id } ?: id,
            schedule = CronSchedule(
                kind = scheduleKind,
                expr = scheduleExpr,
                timezone = timezone
            ),
            enabled = (job["enabled"] as? Boolean) ?: true,
            agentId = job["agentId"]?.toString() ?: job["agent_id"]?.toString(),
            sessionTarget = job["sessionTarget"]?.toString()
                ?: job["session_target"]?.toString()
                ?: "main",
            payload = CronPayload(
                kind = payloadMap?.get("kind")?.toString() ?: "systemEvent",
                text = payloadMap?.get("text")?.toString()
                    ?: payloadMap?.get("message")?.toString()
                    ?: job["command"]?.toString()
                    ?: "",
                model = payloadMap?.get("model")?.toString()
            ),
            delivery = deliveryMap?.let {
                CronDelivery(
                    mode = it["mode"]?.toString() ?: "none",
                    channel = it["channel"]?.toString(),
                    target = it["to"]?.toString()
                )
            },
            lastRunAt = stateMap?.get("lastRunAtMs")?.toString()
                ?: job["lastRunAtMs"]?.toString()
                ?: job["last_run"]?.toString()
                ?: job["lastRun"]?.toString(),
            nextRunAt = stateMap?.get("nextRunAtMs")?.toString()
                ?: job["nextRunAtMs"]?.toString()
                ?: job["next_run"]?.toString()
                ?: job["nextRun"]?.toString(),
            lastStatus = stateMap?.get("lastRunStatus")?.toString()
                ?: stateMap?.get("lastStatus")?.toString()
                ?: job["lastStatus"]?.toString()
                ?: job["last_run_status"]?.toString(),
            consecutiveErrors = (stateMap?.get("consecutiveErrors") as? Number)?.toInt()
                ?: (job["consecutive_errors"] as? Number)?.toInt()
                ?: 0,
            lastError = stateMap?.get("lastError")?.toString()
                ?: job["last_error"]?.toString()
        )
    }

    private fun parseCronRun(raw: Any?): CronJobRun? {
        val run = raw as? Map<*, *> ?: return null
        val id = run["id"]?.toString()?.trim().orEmpty()
        if (id.isBlank()) return null
        return CronJobRun(
            id = id,
            status = run["status"]?.toString() ?: if ((run["success"] as? Boolean) == true) "success" else "unknown",
            success = (run["success"] as? Boolean) ?: false,
            startedAt = run["started_at"]?.toString() ?: run["runAt"]?.toString() ?: run["ts"]?.toString(),
            completedAt = run["completed_at"]?.toString(),
            output = run["output"]?.toString(),
            error = run["error"]?.toString() ?: run["summary"]?.toString(),
            durationMs = (run["duration_ms"] as? Number)?.toLong(),
            sessionKey = run["session_key"]?.toString()
        )
    }

    private fun parseSkill(raw: Any?): SkillSummary? {
        val skill = raw as? Map<*, *> ?: return null
        val name = skill["name"]?.toString()
            ?: skill["id"]?.toString()
            ?: skill["skillKey"]?.toString()
            ?: return null
        val missing = skill["missing"] as? Map<*, *>
        val installOptions = (skill["install"] as? List<*>).orEmpty().mapNotNull { option ->
            val install = option as? Map<*, *> ?: return@mapNotNull null
            val id = install["id"]?.toString()?.trim().orEmpty()
            if (id.isBlank()) return@mapNotNull null
            SkillInstallOption(
                id = id,
                label = install["label"]?.toString()?.ifBlank { "Install" } ?: "Install"
            )
        }

        val disabled = (skill["disabled"] as? Boolean) ?: false
        val enabled = (skill["enabled"] as? Boolean) ?: !disabled

        return SkillSummary(
            name = name,
            skillKey = skill["skillKey"]?.toString()?.ifBlank { name } ?: name,
            description = skill["description"]?.toString().orEmpty(),
            category = skill["category"]?.toString()?.ifBlank { "General" } ?: "General",
            path = skill["path"]?.toString().orEmpty(),
            source = skill["source"]?.toString().orEmpty(),
            bundled = (skill["bundled"] as? Boolean) ?: false,
            canUninstall = !(skill["bundled"] as? Boolean ?: false),
            enabled = enabled,
            installed = (skill["installed"] as? Boolean) ?: false,
            eligible = skill["eligible"] as? Boolean,
            blockedByAllowlist = (skill["blockedByAllowlist"] as? Boolean) ?: false,
            primaryEnv = skill["primaryEnv"]?.toString(),
            assignedAgent = skill["assignedAgent"]?.toString(),
            installOptions = installOptions,
            missing = SkillMissingState(
                bins = (missing?.get("bins") as? List<*>).orEmpty().mapNotNull { it?.toString() },
                anyBins = (missing?.get("anyBins") as? List<*>).orEmpty().mapNotNull { it?.toString() },
                env = (missing?.get("env") as? List<*>).orEmpty().mapNotNull { it?.toString() },
                config = (missing?.get("config") as? List<*>).orEmpty().mapNotNull { it?.toString() },
                os = (missing?.get("os") as? List<*>).orEmpty().mapNotNull { it?.toString() }
            )
        )
    }

    private fun parseHubEntries(payload: String): List<SkillHubEntry> {
        val trimmed = payload.trim()
        val items = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.isBlank() -> JSONArray()
            else -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        }

        return buildList {
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val identifier = item.optString("identifier").trim()
                if (identifier.isBlank()) return@repeat
                add(
                    SkillHubEntry(
                        name = item.optString("name").ifBlank { identifier },
                        description = item.optString("description"),
                        source = item.optString("source"),
                        identifier = identifier,
                        trustLevel = item.optString("trust_level"),
                        repo = item.optString("repo").ifBlank { null },
                        path = item.optString("path").ifBlank { null },
                        tags = item.optJSONArray("tags").toStringList()
                    )
                )
            }
        }
    }

    private fun formatGatewayCommandResult(
        result: Map<String, Any?>,
        emptyFallback: String
    ): String {
        return listOfNotNull(
            result["message"]?.toString()?.takeIf { it.isNotBlank() },
            result["command"]?.toString()?.takeIf { it.isNotBlank() }?.let { "COMMAND:\n$it" },
            result["stdout"]?.toString()?.takeIf { it.isNotBlank() }?.let { "STDOUT:\n$it" },
            result["stderr"]?.toString()?.takeIf { it.isNotBlank() }?.let { "STDERR:\n$it" },
            (result["warnings"] as? List<*>)?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }?.let {
                "WARNINGS:\n- ${it.joinToString("\n- ")}"
            }
        ).joinToString("\n\n").ifBlank { emptyFallback }
    }

    private fun formatHttpCommandResult(payload: String, emptyFallback: String): String {
        val root = JSONObject(payload.ifBlank { "{}" })
        return listOfNotNull(
            root.optString("message").ifBlank { null },
            root.optString("command").ifBlank { null }?.let { "COMMAND:\n$it" },
            root.optString("stdout").ifBlank { null }?.let { "STDOUT:\n$it" },
            root.optString("stderr").ifBlank { null }?.let { "STDERR:\n$it" },
            root.optJSONArray("warnings")?.toStringList()?.takeIf { it.isNotEmpty() }?.let {
                "WARNINGS:\n- ${it.joinToString("\n- ")}"
            }
        ).joinToString("\n\n").ifBlank { emptyFallback }
    }

    private fun encodeSegment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun encodePath(path: String): String {
        return path.split('/').joinToString("/") { segment -> encodeSegment(segment) }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        repeat(length()) { index ->
            opt(index)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
