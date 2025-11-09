package com.example.minimalistlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minimalistlauncher.FocusViewModel

/**
 * FocusScreen (fixed): make sure Compose-only values (colors, typography) are captured
 * before calling Canvas { ... } because the Canvas draw lambda is not a @Composable context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    vm: FocusViewModel = viewModel(),
    onClose: () -> Unit
) {
    val isActive by vm.isActive.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val dur by vm.durationSec.collectAsState()
    val rem by vm.remainingSec.collectAsState()

    // read composable-only values upfront
    val surfaceBg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val titleStyle = MaterialTheme.typography.titleLarge
    val bodyStyle = MaterialTheme.typography.bodyMedium

    val progress = remember(dur, rem) {
        if (dur <= 0) 0f else (dur - rem).toFloat() / dur.toFloat()
    }
    val animatedProgress by animateFloatAsState(targetValue = progress)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceBg),
        color = surfaceBg
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Close",
                            tint = onBg
                        )
                    }

                    Text(text = "Focus", style = titleStyle, color = onBg)

                    // placeholder box to balance header
                    Box(modifier = Modifier.size(48.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // big ring + timer
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(20.dp)) {
                    val circleDp = 220.dp

                    // Canvas drawScope cannot call composable APIs — pass plain values in.
                    Canvas(modifier = Modifier.size(circleDp)) {
                        val stroke = 14f
                        // background ring
                        drawArc(
                            color = onBg.copy(alpha = 0.12f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        // progress arc
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }

                    // Center time
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val minutes = (rem / 60).toString().padStart(2, '0')
                        val seconds = (rem % 60).toString().padStart(2, '0')
                        Text("$minutes:$seconds", style = titleStyle, color = onBg)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isActive) if (isRunning) "Running" else "Paused" else "Ready",
                            style = bodyStyle,
                            color = onBg.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Controls: Play/Pause and Stop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!isActive) {
                                vm.start(25 * 60, type = "pomodoro")
                            } else {
                                if (isRunning) vm.pause() else vm.resume()
                            }
                        },
                        text = { Text(if (!isActive) "Start" else if (isRunning) "Pause" else "Resume") },
                        icon = {
                            Icon(
                                if (!isActive) Icons.Default.PlayArrow else if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        }
                    )

                    ExtendedFloatingActionButton(
                        onClick = { vm.stop() },
                        text = { Text("Stop") },
                        icon = { Icon(Icons.Default.Stop, contentDescription = null) },
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Presets row
                val presets = listOf(25 * 60, 50 * 60, 15 * 60)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    presets.forEach { s ->
                        val label = when (s) {
                            25 * 60 -> "25m"
                            50 * 60 -> "50m"
                            15 * 60 -> "15m"
                            else -> "${(s / 60)}m"
                        }
                        TextButton(onClick = { vm.start(s, type = "preset") }) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
