package com.solovision.openclawagents.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.solovision.openclawagents.AgentAvatarCatalog

@Composable
fun AgentAvatar(
    key: String?,
    label: String,
    accent: Long? = null,
    size: Dp,
    rounded: Boolean = false
) {
    val resId = AgentAvatarCatalog.drawableResIdForKey(key)
    val fallbackColor = accent?.let(::Color) ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val shape = if (rounded) RoundedCornerShape(size * 0.28f) else CircleShape

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        if (resId != null) {
            Image(
                painter = painterResource(resId),
                contentDescription = label,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = label.trim().take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AgentAvatar(
    key: String?,
    label: String,
    accent: Long? = null,
    size: Dp = 40.dp
) {
    AgentAvatar(
        key = key,
        label = label,
        accent = accent,
        size = size,
        rounded = false
    )
}
