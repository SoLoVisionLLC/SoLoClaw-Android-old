package com.solovision.openclawagents.data

import android.content.Context
import android.util.Log

data class OpenClawRuntimeDependencies(
    val repository: OpenClawRepository,
    val missionControlService: MissionControlService?
)

fun buildOpenClawRuntimeDependencies(context: Context): OpenClawRuntimeDependencies {
    val gatewayUrl = resolveGatewayUrl(context)
    val sessionKey = resolveSessionKey(context)
    val apiKey = resolveApiKey(context)
    return runCatching {
        val transport = GatewayRpcOpenClawTransport(
            context = context,
            config = OpenClawBackendConfig(
                gatewayUrl = gatewayUrl,
                sessionKey = sessionKey,
                apiKey = apiKey
            )
        )
        OpenClawRuntimeDependencies(
            repository = RealOpenClawRepository(transport),
            missionControlService = MissionControlService(transport)
        )
    }.getOrElse { error ->
        Log.e("OpenClawRuntime", "Failed to initialize real repository, falling back to fake", error)
        OpenClawRuntimeDependencies(
            repository = FakeOpenClawRepository(),
            missionControlService = null
        )
    }
}

private fun resolveGatewayUrl(context: Context): String {
    val prefs = context.getSharedPreferences("openclaw_gateway", Context.MODE_PRIVATE)
    return prefs.getString("gateway_url", null)
        ?: "wss://gateway.solobot.cloud"
}

private fun resolveSessionKey(context: Context): String {
    val prefs = context.getSharedPreferences("openclaw_gateway", Context.MODE_PRIVATE)
    return prefs.getString("session_key", null)
        ?: "agent:orion:main"
}

private fun resolveApiKey(context: Context): String? {
    val prefs = context.getSharedPreferences("openclaw_gateway", Context.MODE_PRIVATE)
    return prefs.getString("api_key", null)
        ?: "19ca7975c4842989d999110a09569394b203ef14916a4f08187f3e1482197633"
}
