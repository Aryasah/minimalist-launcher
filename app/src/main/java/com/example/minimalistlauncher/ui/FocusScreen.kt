package com.example.minimalistlauncher.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.draw.shadow
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

    var selectedMinutes by remember { mutableStateOf(25) } // default preset
    val selectedSeconds = remember(selectedMinutes) { selectedMinutes * 60 }

    // whether the preset menu is visible (unfold)
    var showPresets by remember { mutableStateOf(false) }

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
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = ""
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
                                    LottieBreath(modifier = Modifier
                                        .size(260.dp)
                                        .align(Alignment.Center))
                                }
//                                // large ring canvas (drawn in front of Lottie)
//                                Canvas(modifier = Modifier.size(280.dp)) {
//                                    val stroke = 20f
//                                    drawArc(
//                                        color = onBg.copy(alpha = 0.12f),
//                                        startAngle = -90f,
//                                        sweepAngle = 360f,
//                                        useCenter = false,
//                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
//                                    )
//                                    drawArc(
//                                        color = primary,
//                                        startAngle = -90f,
//                                        sweepAngle = 360f * animatedProgress,
//                                        useCenter = false,
//                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
//                                    )
//                                }
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

                    // animated preset panel (unfolds above controls)
                    AnimatedPresetPanel(
                        visible = showPresets,
                        selectedMinutes = selectedMinutes,
                        onSelect = { minutes -> selectedMinutes = minutes },
                        onCollapse = { showPresets = false },
                        primary = primary,
                        onBg = onBg,
                        autoCollapseAfterSelect = true
                    )


                    Spacer(modifier = Modifier.height(if (showPresets) 8.dp else 0.dp))

                    // --- Main control row: Play / Pause / Resume + Stop ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Primary control: Play / Pause / Resume
                        FilledIconButton(
                            onClick = {
                                if (!isActive) vm.start(selectedSeconds, type = "preset")
                                else if (isRunning) vm.pause()
                                else vm.resume()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(50))
                                .shadow(elevation = 8.dp, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = if (!isActive || !isRunning)
                                    Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Toggle Focus",
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Stop / Reset
                        FilledIconButton(
                            onClick = { vm.stop() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = onBg.copy(alpha = 0.08f),
                                contentColor = onBg.copy(alpha = 0.9f)
                            ),
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(50))
                                .shadow(elevation = 8.dp, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Stop Focus"
                            )
                        }
                    }


                    Spacer(modifier = Modifier.height(20.dp))

                    // tiny auxiliary row with clock that toggles the panel
                    AuxiliaryTogglesRowWithClock(
                        bgmEnabled = bgmEnabled,
                        onToggleBgm = { bgmEnabled = !bgmEnabled },
                        useLottie = useLottie,
                        onToggleLottie = { useLottie = !useLottie },
                        dndEnabled = dndEnabled,
                        onToggleDnd = { /* keep if needed */ },
                        onTogglePresets = { showPresets = !showPresets },
                        primary = primary,
                        onBg = onBg
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    presets: List<Int> = listOf(5, 10, 15, 25, 50), // minutes
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    primary: Color,
    onBg: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { minutes ->
            val selected = minutes == selectedMinutes
            // circular button with small minutes overlay
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                IconButton(
                    onClick = { onSelect(minutes) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) primary else onBg.copy(alpha = 0.18f),
                            shape = CircleShape
                        )
                        .background(if (selected) primary.copy(alpha = 0.14f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Start ${minutes} minutes",
                        tint = if (selected) primary else onBg.copy(alpha = 0.72f)
                    )
                }

                // small minutes overlay (keeps UI compact — tiny numeric hint)
                Text(
                    text = "${minutes}",
                    color = if (selected) primary else onBg.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun AuxiliaryTogglesRowWithClock(
    bgmEnabled: Boolean,
    onToggleBgm: () -> Unit,
    useLottie: Boolean,
    onToggleLottie: () -> Unit,
    dndEnabled: Boolean,            // if you still want dnd state elsewhere
    onToggleDnd: () -> Unit,        // keep for legacy/actions if needed
    onTogglePresets: () -> Unit,    // toggle preset panel
    primary: Color,
    onBg: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BGM toggle
        ToggleIconButton(
            checked = bgmEnabled,
            onClick = onToggleBgm,
            icon = { Icon(imageVector = Icons.Default.Headphones, contentDescription = "BGM") },
            modifier = Modifier.size(48.dp),
            onColor = primary,
            offColor = onBg.copy(alpha = 0.6f)
        )

        // Clock / Preset toggle (replaces DND)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            IconButton(
                onClick = onTogglePresets,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime, // or Icons.Default.Timer
                    contentDescription = "Presets",
                    tint = onBg.copy(alpha = 0.9f)
                )
            }
            // small dot if the preset panel is open (optional)
            // you can color it primary when open; parent should pass that state if needed
        }

        // Breath animation toggle (Spa)
        ToggleIconButton(
            checked = useLottie,
            onClick = onToggleLottie,
            icon = { Icon(imageVector = Icons.Default.Spa, contentDescription = "Breath animation") },
            modifier = Modifier.size(48.dp),
            onColor = primary,
            offColor = onBg.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ToggleIconButton(
    checked: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color,
    offColor: Color
) {
    // Large clickable area; visual state indicated by icon tint + small badge
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
            // keep background subtle when off, stronger when on
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = if (checked) onColor else offColor
            )
        ) {
            // icon composable provided by caller
            icon()
        }

        // small status dot top-right
        val dotColor = if (checked) onColor else offColor.copy(alpha = 0.45f)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(
                    width = 1.dp,
                    color = Color.Black.copy(alpha = 0.18f),
                    shape = CircleShape
                )
        )
    }
}


@Composable
private fun AnimatedPresetPanel(
    visible: Boolean,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onCollapse: () -> Unit,
    primary: Color,
    onBg: Color,
    modifier: Modifier = Modifier,
    autoCollapseAfterSelect: Boolean = true
) {
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(220)),
        exit = shrinkVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = onBg.copy(alpha = 0.00f))
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                PresetRow(
                    presets = listOf(5, 10, 15, 25, 50),
                    selectedMinutes = selectedMinutes,
                    onSelect = { minutes ->
                        // first inform parent of selected value
                        onSelect(minutes)

                        // then optionally collapse after a small delay so user sees the selection
                        if (autoCollapseAfterSelect) {
                            scope.launch {
                                kotlinx.coroutines.delay(140)
                                onCollapse()
                            }
                        }
                    },
                    primary = primary,
                    onBg = onBg,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}
