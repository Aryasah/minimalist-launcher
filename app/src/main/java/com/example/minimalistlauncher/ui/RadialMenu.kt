package com.example.minimalistlauncher.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.zIndex

/**
 * Data model for an action that will appear in radial menu.
 */
data class RadialAction(
    val id: String,
    val icon: @Composable () -> Unit,
    val contentDescription: String,
    val onClick: () -> Unit
)

/**
 * RadialMenu - a central circular button that expands its actions radially.
 *
 * Added params:
 *  - contentPadding: padding applied around the whole menu (keeps it away from edges)
 *  - navigationBarsSafe: when true applies navigationBarsPadding() to avoid clipping on phones with nav bars/gestures
 */
@Composable
fun RadialMenu(
    actions: List<RadialAction>,
    modifier: Modifier = Modifier,
    radiusDp: Dp = 100.dp,
    startAngleDegrees: Float = -135f,
    sweepAngleDegrees: Float = 90f,
    centerButtonSize: Dp = 56.dp,
    centerButtonColor: Color = MaterialTheme.colorScheme.primary,
    centerIconColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(12.dp), // default small padding so the center button never rests on the edge
    navigationBarsSafe: Boolean = true, // apply navigationBarsPadding() by default
    onOpenedChanged: ((Boolean) -> Unit)? = null
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radiusDp.toPx() }
    val centerSizePx = with(density) { centerButtonSize.toPx() }

    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // track global progress from 0..1 used for staggering ease
    val globalAnim = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        onOpenedChanged?.invoke(expanded)
        if (expanded) {
            globalAnim.snapTo(0f)
            globalAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        } else {
            globalAnim.animateTo(
                0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    // center container to position items around
    var outerModifier = modifier
        .wrapContentSize(align = Alignment.BottomEnd)
        // apply default content padding so the control is not flush to the screen edge
        .padding(contentPadding)

    // respect navigation bars if requested
    if (navigationBarsSafe) outerModifier = outerModifier.navigationBarsPadding()

    Box(modifier = outerModifier, contentAlignment = Alignment.BottomEnd) {
        // Action buttons (draw them first so center button sits on top)
        actions.forEachIndexed { index, action ->
            val count = actions.size
            val angleDeg = when {
                count == 1 -> startAngleDegrees + sweepAngleDegrees / 2f
                else -> startAngleDegrees + (sweepAngleDegrees * index.toFloat() / (count - 1).coerceAtLeast(
                    1
                ))
            }
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val stagger = 0.12f * index
            val childProgress =
                ((globalAnim.value - stagger) / (1f - 0.12f * (count - 1))).coerceIn(0f, 1f)
            val eased = FastOutSlowInEasing.transform(childProgress)

            // ... earlier code remains the same ...

// compute position
            val dx = (cos(angleRad) * radiusPx * eased).toFloat()
            val dy = (sin(angleRad) * radiusPx * eased).toFloat()

// scale & alpha
            val scale = 0.6f + 0.4f * eased
            val alpha = 0.4f + 0.6f * eased

// IMPORTANT: do NOT negate dx/dy here — use the values directly.
// When anchored at BottomEnd, negative dx -> left, negative dy -> up (what we want).
            val offsetX = with(density) { dx.toDp() }
            val offsetY = with(density) { dy.toDp() }

// action button UI (give it higher zIndex when expanded so it is above center button)
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(centerButtonSize)
                    .scale(scale)
                    .padding(4.dp)
                    .zIndex(if (expanded) 2f else 1f),   // <-- ensure actions are visible on top when expanded
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    tonalElevation = 6.dp,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
                    modifier = Modifier
                        .size(centerButtonSize)
                        .clickable {
                            scope.launch {
                                try {
                                    action.onClick()
                                    delay(120)
                                } catch (t: Throwable) {
                                    t.printStackTrace()
                                } finally {
                                    expanded = false
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        action.icon()
                    }
                }
            }


            // Center button
            FloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = centerButtonColor,
                contentColor = centerIconColor,
                modifier = Modifier
                    .size(centerButtonSize)
                    .padding(4.dp)
                    .zIndex(1f)
            ) {
                if (expanded) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close radial menu"
                    )
                } else {
                    Icon(
                        painter = rememberVectorPainter(Icons.Default.ArrowBack),
                        contentDescription = "Open radial menu",
                        tint = centerIconColor
                    )
                }
            }
        }
    }
}

