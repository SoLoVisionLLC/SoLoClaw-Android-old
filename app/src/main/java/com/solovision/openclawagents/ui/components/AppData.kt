package com.solovision.openclawagents.ui.components

import com.solovision.openclawagents.model.Agent
import com.solovision.openclawagents.model.CollaborationRoom
import com.solovision.openclawagents.model.MessageSenderType
import com.solovision.openclawagents.model.RoomMessage

val demoAgents = listOf(
    Agent("halo", "Halo", "Primary Assistant", "Ready", 0xFF7C5CFF, "General coordination and summaries."),
    Agent("orion", "Orion", "CTO", "Architecting", 0xFF38BDF8, "Architecture and sequencing."),
    Agent("dev", "Dev", "Engineering", "Building", 0xFF22C55E, "Implementation and debugging."),
    Agent("nova", "Nova", "Social", "Waiting", 0xFFF97316, "Social publishing and growth ops.")
)

val demoRooms = listOf(
    CollaborationRoom("launch", "Launch Room", "Coordinate product launch work", listOf("halo", "orion", "dev"), 4, true),
    CollaborationRoom("support", "Support Ops", "Handle live user issues", listOf("halo", "nova"), 1, false),
    CollaborationRoom("build", "Build Lab", "Plan and review active builds", listOf("orion", "dev"), 0, true)
)

val demoMessages = listOf(
    RoomMessage("1", "solo", "SoLo", "Operator", MessageSenderType.USER, "Create a polished Android experience for agent collaboration.", "Now"),
    RoomMessage("2", "halo", "Halo", "Primary Assistant", MessageSenderType.AGENT, "I can coordinate the room and summarize what each specialist is doing.", "Now", true),
    RoomMessage("3", "orion", "Orion", "CTO", MessageSenderType.AGENT, "We should separate direct chats from shared agent rooms and make attribution effortless.", "1m ago"),
    RoomMessage("4", "dev", "Dev", "Engineering", MessageSenderType.AGENT, "I’m ready for implementation once the room structure and voice controls are locked.", "1m ago")
)
