package com.example.minimalistlauncher.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minimalistlauncher.FocusViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.minimalistlauncher.FontManager
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.airbnb.lottie.compose.* // optional; safe if dependency added
import com.airbnb.lottie.compose.*
import com.example.minimalistlauncher.R


/**
 * Calm, minimal full-screen FocusScreen.
 * - Large central ring inside rounded frosted card
 * - Minimal actions: Start / Pause|Resume and Stop
 * - DND toggle (permission flow) + optional BGM and Lottie breathing animation
 *
 * Make sure to show this as a full-screen Surface (not a dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    vm: FocusViewModel = viewModel(),
    onClose: () -> Unit,
    enableLottieDefault: Boolean = false
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // ViewModel state
    val isActive by vm.isActive.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val durationSec by vm.durationSec.collectAsState()
    val remainingSec by vm.remainingSec.collectAsState()

    // UI-only toggles
    var bgmEnabled by remember { mutableStateOf(false) }
    var dndEnabled by remember { mutableStateOf(false) }
    var useLottie by remember { mutableStateOf(enableLottieDefault) }

    // Media player holder (optional BGM)
    val mpHolder = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(bgmEnabled) {
        if (bgmEnabled) {
            // resource name: res/raw/focus_bgm.mp3 (optional)
            val resId = ctx.resources.getIdentifier("focus_bgm", "raw", ctx.packageName)
            if (resId != 0) {
                val mp = MediaPlayer.create(ctx, resId)
                mp?.isLooping = true
                try { mp?.start() } catch (_: Exception) { }
                mpHolder.value = mp
            }
        } else {
            mpHolder.value?.let { try { it.stop(); it.release() } catch (_: Exception) {} ; mpHolder.value = null }
        }
        onDispose {
            mpHolder.value?.let { try { it.stop(); it.release() } catch (_: Exception) {} ; mpHolder.value = null }
        }
    }

    // compute progress float 0..1
    val progress = remember(durationSec, remainingSec) {
        if (durationSec <= 0) 0f else (durationSec - remainingSec).coerceAtLeast(0) / durationSec.toFloat()
    }
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(durationMillis = 400))

    // subtle breathing scale using infiniteTransition (used for card if Lottie disabled)
    val breathTransition = rememberInfiniteTransition()
    val breathScale by breathTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    // DND helpers
    fun hasDndPermission(): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.isNotificationPolicyAccessGranted else true
    }
    fun requestDndPermission() {
        // open system settings where user can grant DND access to the app
        val i = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ctx.startActivity(i)
    }
    fun setDnd(enabled: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!hasDndPermission()) {
            requestDndPermission()
            return
        }
        try {
            if (enabled) {
                // set minimal interruptions (requires granted access)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        } catch (t: Throwable) {
            // don't crash; permission or OEM restrictions might block it
            t.printStackTrace()
        }
    }

    // Optional Lottie: try loading a bundled lottie JSON resource name "breath.json" in res/raw or res/raw/lottie/...
    // You need to add dependency: implementation "com.airbnb.android:lottie-compose:<version>"
    // I'll show how to add in the notes below.

            @Composable
            fun LottieBreath(modifier: Modifier = Modifier) {
                // breath.json in res/raw
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.breath))
                // play forever
                val progress by animateLottieCompositionAsState(
                    composition,
                    iterations = LottieConstants.IterateForever,
                    isPlaying = true
                )
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = modifier
                )
            }


    // Format mm:ss
    fun fmt(sec: Int) = String.format("%02d:%02d", sec / 60, sec % 60)

    // Colors: reduced contrast
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f)
    val cardBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.06f)
    val primary = MaterialTheme.colorScheme.primary

    Surface(modifier = Modifier.fillMaxSize(), color = bg) {
        Box(modifier = Modifier.fillMaxSize()) {
            // soft vertical gradient background to reduce contrast
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.98f))))
            )

            // Full-screen content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: small close + DND toggle
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onClose() }) {
                        Icon(painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel), contentDescription = "Close", tint = onBg)
                    }

                    // DND toggle compact
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (!hasDndPermission()) requestDndPermission() else {
                                dndEnabled = !dndEnabled
                                setDnd(dndEnabled)
                            }
                        }) {
                            Icon(imageVector = Icons.Default.DoNotDisturbOn, contentDescription = "DND", tint = if (dndEnabled) primary else onBg.copy(alpha = 0.6f))
                        }
                    }
                }

                // Middle: frosted rounded background card containing large ring and timer
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Frosted rounded card
                    Box(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(cardBg, cardBg.copy(alpha = 0.9f))
                                )
                            )
                            .padding(28.dp)
                            .then(if (!useLottie) Modifier.scale(breathScale) else Modifier)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            // Lottie breathing (optional) overlay behind ring
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                if (useLottie) {
                                    LottieBreath(modifier = Modifier.size(260.dp).align(Alignment.Center))
                                }
                                // large ring canvas (drawn in front of Lottie)
                                Canvas(modifier = Modifier.size(280.dp)) {
                                    val stroke = 20f
                                    drawArc(
                                        color = onBg.copy(alpha = 0.12f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = primary,
                                        startAngle = -90f,
                                        sweepAngle = 360f * animatedProgress,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Timer text
                            Text(text = fmt(remainingSec), color = onBg, fontSize = 36.sp, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, fontFamily = FontManager.composeFontFamily)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    !isActive -> "Ready"
                                    isRunning -> "In progress"
                                    else -> "Paused"
                                },
                                color = onBg.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Bottom row: only two action controls maximum (Start/Pause-Resume + Stop), and small toggles (BGM + Lottie)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        // Start / Pause / Resume (primary)
                        Button(
                            onClick = {
                                if (!isActive) vm.start(25 * 60, type = "preset")
                                else if (isRunning) vm.pause()
                                else vm.resume()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        ) {
                            Icon(imageVector = if (!isActive || !isRunning) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (!isActive) "Start" else if (isRunning) "Pause" else "Resume")
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Stop (secondary)
                        OutlinedButton(
                            onClick = { vm.stop() },
                            modifier = Modifier.heightIn(min = 56.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // tiny lower row for auxiliaries: BGM toggle + Lottie toggle (kept small)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { bgmEnabled = !bgmEnabled }) {
                                Icon(imageVector = Icons.Default.Headphones, contentDescription = "BGM", tint = if (bgmEnabled) primary else onBg.copy(alpha = 0.6f))
                            }
                            Text("BGM", color = onBg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Lottie toggle
                            Text("Breath animation", color = onBg.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = useLottie, onCheckedChange = { useLottie = it }, colors = SwitchDefaults.colors(checkedTrackColor = primary))
                        }
                    }
                }
            }
        }
    }
}
