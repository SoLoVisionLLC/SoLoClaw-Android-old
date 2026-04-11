package com.solovision.openclawagents.data

import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage
import com.solovision.openclawagents.model.VoiceMode

object AppSeedData {
    val agents = listOf(
        Agent("halo", "Halo", "Primary Assistant", "Ready", 0xFF7C5CFF, "General coordination, summaries, and operator support."),
        Agent("orion", "Orion", "CTO", "Architecting", 0xFF38BDF8, "Architecture, sequencing, and engineering review."),
        Agent("dev", "Dev", "Engineering", "Building", 0xFF22C55E, "Implementation, debugging, and shipping."),
        Agent("nova", "Nova", "Social", "Waiting", 0xFFF97316, "Social publishing and growth ops."),
        Agent("knox", "Knox", "Security", "Monitoring", 0xFFFB7185, "Security posture, access, and exposure review.")
    )

    val rooms = listOf(
        CollaborationRoom("launch", "Launch Room", "Coordinate product launch work", listOf("halo", "orion", "dev"), 4, true, VoiceMode.Auto, "Now"),
        CollaborationRoom("support", "Support Ops", "Handle live user issues", listOf("halo", "nova"), 1, false, VoiceMode.OnDemand, "12m ago"),
        CollaborationRoom("build", "Build Lab", "Plan and review active builds", listOf("orion", "dev", "knox"), 0, true, VoiceMode.Auto, "3m ago")
    )

    val messagesByRoom = mapOf(
        "launch" to listOf(
            RoomMessage("1", "solo", "SoLo", "Operator", MessageSenderType.USER, "Create a polished Android experience for agent collaboration.", "Now"),
            RoomMessage("2", "halo", "Halo", "Primary Assistant", MessageSenderType.AGENT, "I can coordinate the room and summarize what each specialist is doing.", "Now", true),
            RoomMessage("3", "orion", "Orion", "CTO", MessageSenderType.AGENT, "We should separate direct chats from shared agent rooms and make attribution effortless.", "1m ago"),
            RoomMessage("4", "dev", "Dev", "Engineering", MessageSenderType.AGENT, "I’m ready for implementation once the room structure and voice controls are locked.", "1m ago")
        ),
        "support" to listOf(
            RoomMessage("5", "halo", "Halo", "Primary Assistant", MessageSenderType.AGENT, "I can route urgent user issues into this room.", "12m ago"),
            RoomMessage("6", "nova", "Nova", "Social", MessageSenderType.AGENT, "I’ll watch for public-facing issues that need replies.", "11m ago")
        ),
        "build" to listOf(
            RoomMessage("7", "orion", "Orion", "CTO", MessageSenderType.AGENT, "Build Lab is where technical decisions should converge before execution.", "3m ago"),
            RoomMessage("8", "knox", "Knox", "Security", MessageSenderType.AGENT, "Flag me early if any mobile permission or transport choices create risk.", "2m ago")
        )
    )
}
