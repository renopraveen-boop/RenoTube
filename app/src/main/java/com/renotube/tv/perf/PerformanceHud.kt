package com.renotube.tv.perf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renotube.tv.ui.theme.YouTubeRed

@Composable
fun PerformanceHud(
    modifier: Modifier = Modifier
) {
    val metrics by PerformanceTracker.metrics.collectAsState()

    AnimatedVisibility(
        visible = metrics.isHudVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xE6141418))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(8.dp))
                .clickable { PerformanceTracker.toggleHud() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = "Performance HUD",
                tint = YouTubeRed,
                modifier = Modifier.size(14.dp)
            )

            // API Latency
            Text(
                text = "API: ${if (metrics.lastApiLatencyMs > 0) "${metrics.lastApiLatencyMs}ms" else "--"}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (metrics.isApiSuccess) Color(0xFF66FF66) else Color(0xFFFF6666)
            )

            Text(text = "•", fontSize = 10.sp, color = Color(0x60FFFFFF))

            // Stream Start Latency
            Text(
                text = "Stream: ${if (metrics.streamStartLatencyMs > 0) "${metrics.streamStartLatencyMs}ms" else "Ready"}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (metrics.streamStartLatencyMs in 1..800) Color(0xFF66FF66) else Color(0xFFFFCC00)
            )

            Text(text = "•", fontSize = 10.sp, color = Color(0x60FFFFFF))

            // Buffer Health
            Text(
                text = "Buf: ${String.format("%.1f", metrics.bufferHealthSec)}s",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = if (metrics.bufferHealthSec >= 5f) Color(0xFF66FF66) else Color(0xFFFF9900)
            )

            Text(text = "•", fontSize = 10.sp, color = Color(0x60FFFFFF))

            // RAM Usage
            Text(
                text = "RAM: ${metrics.heapUsedMb}MB",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCCCCCC)
            )
        }
    }
}
