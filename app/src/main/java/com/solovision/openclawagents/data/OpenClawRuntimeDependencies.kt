package com.solovision.openclawagents.data

import android.content.Context

data class OpenClawRuntimeDependencies(
    val repository: OpenClawRepository,
    val missionControlService: MissionControlService?
)

fun buildOpenClawRuntimeDependencies(context: Context): OpenClawRuntimeDependencies {
    val gatewayUrl = "wss://gateway.solobot.cloud"
    val sessionKey = "agent:orion:main"
    return runCatching {
        val transport = GatewayRpcOpenClawTransport(
            context = context,
            config = OpenClawBackendConfig(
                gatewayUrl = gatewayUrl,
                sessionKey = sessionKey,
                apiKey = "19ca7975c4842989d999110a09569394b203ef14916a4f08187f3e1482197633"
            )
        )
        OpenClawRuntimeDependencies(
            repository = RealOpenClawRepository(transport),
            missionControlService = MissionControlService(transport)
        )
    }.getOrElse {
        OpenClawRuntimeDependencies(
            repository = FakeOpenClawRepository(),
            missionControlService = null
        )
    }
}
