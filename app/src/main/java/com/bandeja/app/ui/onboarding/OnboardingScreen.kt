package com.bandeja.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.app.R
import com.bandeja.app.ui.components.CategoryBadge
import com.bandeja.app.ui.theme.CategoryAccent
import com.bandeja.app.ui.theme.Spacing
import com.bandeja.app.ui.theme.container
import kotlinx.coroutines.launch

private data class OnboardingPage(val title: Int, val body: Int, val illustration: @Composable () -> Unit)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_1_title, R.string.onboarding_1_body) { StackIllustration() },
    OnboardingPage(R.string.onboarding_2_title, R.string.onboarding_2_body) { ShareIllustration() },
    OnboardingPage(R.string.onboarding_3_title, R.string.onboarding_3_body) { CategoriesIllustration() },
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.End,
        ) {
            // Always present (but invisible on the last page) so the layout does not jump.
            TextButton(onClick = onFinished, enabled = !isLast) {
                Text(
                    stringResource(R.string.onboarding_skip),
                    color = if (isLast) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { page.illustration() }
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PageDots(current = pagerState.currentPage, count = pages.size)
            Button(
                onClick = {
                    if (isLast) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.height(56.dp),
            ) {
                Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
                if (!isLast) {
                    Spacer(Modifier.size(Spacing.sm))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            val width = animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot-width")
            val color = animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot-color",
            )
            Box(
                Modifier
                    .size(width = width.value, height = 8.dp)
                    .clip(CircleShape)
                    .background(color.value),
            )
        }
    }
}

/** Page 1: a small stack of tilted category cards, the idea of "everything in one tray". */
@Composable
private fun StackIllustration() {
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        listOf(
            Triple(CategoryAccent.Coral, "🍔", -10f),
            Triple(CategoryAccent.Sky, "✈️", 6f),
            Triple(CategoryAccent.Violet, "🎮", -2f),
        ).forEachIndexed { index, (accent, emoji, angle) ->
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = accent.container(),
                modifier = Modifier
                    .size(width = 180.dp, height = 120.dp)
                    .padding(top = (index * 6).dp)
                    .rotate(angle),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 48.sp) }
            }
        }
    }
}

/** Page 2: the real gesture: Share → pick Bandeja. */
@Composable
private fun ShareIllustration() {
    val description = stringResource(R.string.onboarding_2_title)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Rounded.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.action_share), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                AppTile("▶️", CategoryAccent.Coral, highlighted = false)
                AppTile("📷", CategoryAccent.Pink, highlighted = false)
                AppTile("📥", CategoryAccent.Indigo, highlighted = true)
                AppTile("🌐", CategoryAccent.Sky, highlighted = false)
            }
        }
    }
}

@Composable
private fun AppTile(emoji: String, accent: CategoryAccent, highlighted: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier
                .size(if (highlighted) 64.dp else 52.dp)
                .clip(CircleShape)
                .background(if (highlighted) MaterialTheme.colorScheme.primary else accent.container()),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = if (highlighted) 30.sp else 24.sp)
        }
        if (highlighted) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Page 3: categories are yours: icon + color. */
@Composable
private fun CategoriesIllustration() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf(
            listOf(CategoryAccent.Emerald to "🏋️", CategoryAccent.Amber to "📚", CategoryAccent.Pink to "💡"),
            listOf(CategoryAccent.Indigo to "💻", CategoryAccent.Teal to "🌱", CategoryAccent.Coral to "🍕"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { (accent, emoji) ->
                    CategoryBadge(icon = emoji, accent = accent, size = 84.dp)
                }
            }
        }
    }
}
