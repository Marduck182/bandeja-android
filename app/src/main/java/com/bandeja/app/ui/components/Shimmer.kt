package com.bandeja.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.bandeja.app.ui.theme.Spacing

/** Placeholder block with a soft moving highlight. Loading is shown as skeletons, not a full-screen spinner. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = androidx.compose.ui.geometry.Offset(progress.value * 1200f - 400f, 0f),
                    end = androidx.compose.ui.geometry.Offset(progress.value * 1200f, 400f),
                ),
            ),
    )
}

@Composable
fun ContentCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShimmerBox(Modifier.size(width = 104.dp, height = 78.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ShimmerBox(Modifier.fillMaxWidth().height(16.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp))
        }
    }
}

@Composable
fun ContentListSkeleton(modifier: Modifier = Modifier, count: Int = 5) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(count) { ContentCardSkeleton() }
    }
}
