package com.example.minimalistlauncher.ui

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.minimalistlauncher.DataStoreManager
import com.example.minimalistlauncher.FontManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val ds = remember { DataStoreManager(context) }
    val coroutine = rememberCoroutineScope()

    // Candidate bundled font keys (keep synced with res/font/)
    val bundledCandidates = listOf("inter", "inter_medium", "poppins", "roboto")
    val bundledFonts = remember {
        bundledCandidates.mapNotNull { key ->
            val id = context.resources.getIdentifier(key, "font", context.packageName)
            if (id != 0) Pair(id, key) else null
        }
    }

    // DataStore state
    val fontTypeFlow = remember { ds.launcherFontTypeFlow }
    val fontValueFlow = remember { ds.launcherFontValueFlow }
    val currentType by fontTypeFlow.collectAsState(initial = null)
    val currentValue by fontValueFlow.collectAsState(initial = null)

    // UI state
    var previewTypeface by remember { mutableStateOf<Typeface?>(null) }
    var previewChoice by remember { mutableStateOf<Pair<String, String>?>(null) } // ("res"|"uri"|"pkg", value)
    var showResetConfirm by remember { mutableStateOf(false) }

    // SAF launcher
    val pickFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            }
            val tf = FontManager.loadFromUri(context, it)
            if (tf != null) {
                previewTypeface = tf
                previewChoice = Pair("uri", it.toString())
            } else {
                Toast.makeText(context, "Failed to load font", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helpers
    fun displayNameForUri(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val last = uri.lastPathSegment ?: uriString
            last.substringAfterLast('/').substringBefore('?')
        } catch (t: Throwable) { uriString }
    }

    fun truncated(s: String, max: Int = 28): String {
        return if (s.length <= max) s else s.take(max - 1) + "…"
    }

    fun heuristicMatchesBundled(uriString: String): String? {
        val filename = displayNameForUri(uriString).lowercase()
        bundledCandidates.forEach { key -> if (key.lowercase() in filename) return key }
        val path = uriString.lowercase()
        bundledCandidates.forEach { key -> if (key.lowercase() in path) return key }
        return null
    }

    // List state for nice scroll behavior
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            CommonTopBar(title = "Fonts", onClose = onClose, useBackIcon = true)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp) // room for sticky bottom bar
            ) {
                // Bundled sticky-ish header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader("Bundled")
                }

                items(bundledFonts, key = { it.second }) { (resId, key) ->
                    val tf = remember { FontManager.loadFromRes(context, resId, key) }
                    val isSelected = (currentType == "res" && currentValue == key)
                    FontRow(
                        title = key.replaceFirstChar { it.uppercase() },
                        sampleTypeface = tf,
                        selected = isSelected,
                        onClick = {
                            previewTypeface = tf
                            previewChoice = Pair("res", key)
                        }
                    )
                }

                // Custom / installed header
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionHeader("Custom / Installed")
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Pick file row
                item {
                    ActionRow(
                        label = "Pick a font file",
                        leading = { Icon(Icons.Default.Download, contentDescription = null, tint = Color.White) },
                        onClick = { pickFontLauncher.launch(arrayOf("*/*")) }
                    )
                }

                // Persisted custom uri
                item {
                    if (currentType == "uri" && currentValue != null) {
                        val display = truncated(displayNameForUri(currentValue!!))
                        val tf = remember { FontManager.loadFromUri(context, Uri.parse(currentValue)) }
                        FontRow(
                            title = display,
                            sampleTypeface = tf,
                            selected = true,
                            onClick = {
                                previewTypeface = tf
                                previewChoice = Pair("uri", currentValue!!)
                            }
                        )
                    }
                }

                // Persisted package font
                item {
                    if (currentType == "pkg" && currentValue != null) {
                        val parts = currentValue!!.split(":", limit = 2)
                        val display = parts.getOrNull(0)?.substringAfterLast('.') ?: "Package font"
                        val tf = remember {
                            if (parts.size == 2) FontManager.loadFromPackageFont(context, parts[0], parts[1]) else null
                        }
                        FontRow(
                            title = truncated(display),
                            sampleTypeface = tf,
                            selected = true,
                            onClick = { previewTypeface = tf; previewChoice = Pair("pkg", currentValue!!) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Tap a font to preview. Use Apply to set it across the app.",
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Sticky bottom surface: preview + compact actions
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
                ) {
                    // Preview
                    val previewFamily = previewTypeface?.let { FontFamily(it) } ?: FontManager.composeFontFamily
                    Text(
                        text = "Preview — The quick brown fox jumps over the lazy dog",
                        fontFamily = previewFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    )

                    // warning if matches bundled
                    val warnMatch = remember(previewChoice) {
                        if (previewChoice?.first == "uri") heuristicMatchesBundled(previewChoice!!.second) else null
                    }
                    if (warnMatch != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: file looks like bundled \"$warnMatch\".",
                            color = Color(0xFFFFC107), // amber-like
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Apply
                        Button(
                            onClick = {
                                val sel = previewChoice
                                if (sel == null) {
                                    Toast.makeText(context, "Choose a font first", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutine.launch {
                                    val (type, value) = sel
                                    when (type) {
                                        "res" -> {
                                            val resId = context.resources.getIdentifier(value, "font", context.packageName)
                                            if (resId != 0) {
                                                val tf = FontManager.loadFromRes(context, resId, value)
                                                if (tf != null) {
                                                    FontManager.applyAndPersist(context, "res", value, tf)
                                                    ds.setLauncherFont("res", value)
                                                    Toast.makeText(context, "Applied", Toast.LENGTH_SHORT).show()
                                                } else Toast.makeText(context, "Failed to load", Toast.LENGTH_SHORT).show()
                                            } else Toast.makeText(context, "Missing resource", Toast.LENGTH_SHORT).show()
                                        }
                                        "uri" -> {
                                            val tf = previewTypeface
                                            if (tf != null) {
                                                FontManager.applyAndPersist(context, "uri", value, tf)
                                                ds.setLauncherFont("uri", value)
                                                Toast.makeText(context, "Applied custom font", Toast.LENGTH_SHORT).show()
                                            } else Toast.makeText(context, "No font loaded", Toast.LENGTH_SHORT).show()
                                        }
                                        "pkg" -> {
                                            val parts = value.split(":", limit = 2)
                                            if (parts.size == 2) {
                                                val tf = FontManager.loadFromPackageFont(context, parts[0], parts[1])
                                                if (tf != null) {
                                                    FontManager.applyAndPersist(context, "pkg", value, tf)
                                                    ds.setLauncherFont("pkg", value)
                                                    Toast.makeText(context, "Applied package font", Toast.LENGTH_SHORT).show()
                                                } else Toast.makeText(context, "Failed to load package font", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply")
                        }

                        // Clear preview (minimal)
                        OutlinedButton(
                            onClick = { previewTypeface = null; previewChoice = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            // Reset confirmation dialog
            if (showResetConfirm) {
                AlertDialog(
                    onDismissRequest = { showResetConfirm = false },
                    title = { Text("Reset font") },
                    text = { Text("Reset to app default?") },
                    confirmButton = {
                        TextButton(onClick = {
                            coroutine.launch {
                                ds.clearLauncherFont()
                                FontManager.clearSelection(context)
                                Toast.makeText(context, "Reset", Toast.LENGTH_SHORT).show()
                            }
                            showResetConfirm = false
                        }) { Text("Reset") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

/** Minimal section header */
@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(50),
            tonalElevation = 0.dp
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}


/** Compact action row (used for pick file) */
@Composable
private fun ActionRow(label: String, leading: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(12.dp)
            .animateContentSize(animationSpec = tween(120)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Compact row for font entry — animated and minimal */
@Composable
private fun FontRow(title: String, sampleTypeface: Typeface?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(12.dp)
            .animateContentSize(animationSpec = tween(120)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Aa Bb Cc — 0123",
                fontFamily = sampleTypeface?.let { FontFamily(it) } ?: FontManager.composeFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (selected) {
            Text("✓", color = Color(0xFF4CAF50), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
