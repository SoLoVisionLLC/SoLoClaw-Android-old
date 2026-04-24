package com.solovision.openclawagents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object AgentAvatarCatalog {
    fun drawableResIdForKey(key: String?): Int? {
        val normalized = key
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "")
            .orEmpty()
        if (normalized.isBlank()) return null

        return when (normalized) {
            "atlas" -> R.drawable.avatar_atlas
            "chip" -> R.drawable.avatar_chip
            "dev", "developer", "engineering" -> R.drawable.avatar_dev
            "elon" -> R.drawable.avatar_elon
            "forge" -> R.drawable.avatar_forge
            "halo", "main" -> R.drawable.avatar_halo
            "haven" -> R.drawable.avatar_haven
            "knox", "security" -> R.drawable.avatar_knox
            "ledger" -> R.drawable.avatar_ledger
            "luma" -> R.drawable.avatar_luma
            "nova" -> R.drawable.avatar_nova
            "orion", "cto" -> R.drawable.avatar_orion
            "quill" -> R.drawable.avatar_quill
            "sentinel" -> R.drawable.avatar_sentinel
            "snip" -> R.drawable.avatar_snip
            "solo", "operator" -> R.drawable.avatar_solo
            "sterling" -> R.drawable.avatar_sterling
            "vector" -> R.drawable.avatar_vector
            else -> null
        }
    }

    fun bitmapForKey(context: Context, key: String?): Bitmap? {
        val resId = drawableResIdForKey(key) ?: return null
        return BitmapFactory.decodeResource(context.resources, resId)
    }
}
