package com.example.minimalistlauncher.ui

import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.minimalistlauncher.DataStoreManager
import com.example.minimalistlauncher.getAppIconDrawableSafe
import com.example.minimalistlauncher.tryLoadIconFromIconPack
import com.example.minimalistlauncher.drawableToBitmap
import com.example.minimalistlauncher.drawableToMonochromeImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.example.minimalistlauncher.FontManager

private const val SETTINGS_TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onManageHomeApps: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val selectedIconPack by store.selectedIconPackFlow.collectAsState(initial = null)

    var showPreview by remember { mutableStateOf(false) }
    var showOriginalIcons by remember { mutableStateOf(false) }

    var showFontScreen by remember { mutableStateOf(false) }

    // compact paddings and smaller text sizes
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CommonTopBar(
            title = "Settings",
            onClose = onClose,
            useBackIcon = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
//            item {
//                SectionHeader("Appearance")
//            }

            item {
                FontCardRow(onOpenFontScreen = { showFontScreen = true })
            }

            item {
                SectionHeader("Font Size")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        FontSizeSliderRow(openFontScreen = { showFontScreen = true })
                    }
                }
            }

            // Icon pack picker (compact)
            item {
                SectionHeader("Icon pack")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        IconPackPickerRow(onPick = { pkg ->
                            Log.d(SETTINGS_TAG, "Icon pack chosen: $pkg")
                        }, currentPack = selectedIconPack)
                    }
                }
            }

            // show original toggle + preview (compact row)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = showOriginalIcons,
                            onCheckedChange = { showOriginalIcons = it },
                            modifier = Modifier.size(20.dp),
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show original icons", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = { showPreview = true },
                        enabled = selectedIconPack != null,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Preview", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // Home management (compact)
            item {
                SectionHeader("Home")
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onManageHomeApps,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("Manage Home Apps", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch { store.setHomeApps(emptyList()) }
                        },
                        modifier = Modifier.widthIn(min = 120.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // About / footer (minimal)
            item {
                SectionHeader("About")
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Minimalist Launcher — simple, minimal home screen", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showPreview) {
        IconPackPreviewDialog(
            iconPack = selectedIconPack,
            onClose = { showPreview = false },
            showOriginal = showOriginalIcons
        )
    }

    if (showFontScreen) {
        Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), tonalElevation = 8.dp) {
            FontScreen(onClose = { showFontScreen = false })
        }
    }
}

/**
 * Compact preview dialog: smaller icons and tighter layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackPreviewDialog(iconPack: String?, onClose: () -> Unit, showOriginal: Boolean) {
    val context = LocalContext.current
    val previewPkgs = listOf(
        "com.google.android.gm",
        "com.android.chrome",
        "com.google.android.apps.maps",
        "com.google.android.youtube",
        "com.google.android.calendar",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.android.vending",
        "com.google.android.apps.photos",
        "com.google.android.apps.docs",
        "com.google.android.apps.nbu.files"
    )

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Preview icon pack", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium) },
        text = {
            Column {
                Text("Green=P, Cyan=S, ?=none", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(min = 180.dp, max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(previewPkgs) { pkg ->
                        val drawableWithSource by produceState<Pair<android.graphics.drawable.Drawable?, String>>(initialValue = null to "loading", key1 = pkg, key2 = iconPack) {
                            val iconFromPack = if (!iconPack.isNullOrEmpty()) {
                                try {
                                    tryLoadIconFromIconPack(context, iconPack!!, pkg)
                                } catch (t: Throwable) {
                                    t.printStackTrace()
                                    null
                                }
                            } else null
                            val chosenDrawable: android.graphics.drawable.Drawable? = iconFromPack ?: context.getAppIconDrawableSafe(pkg)
                            val source = when {
                                iconFromPack != null -> "pack"
                                chosenDrawable != null -> "system"
                                else -> "none"
                            }
                            value = chosenDrawable to source
                        }

                        val drawable = drawableWithSource.first
                        val source = drawableWithSource.second
                        val tint = MaterialTheme.colorScheme.onSurface.toArgb()
                        val monoBmp: ImageBitmap? = remember(drawable, tint) {
                            if (drawable != null) drawableToMonochromeImageBitmap(drawable, tintColor = tint, sizePx = 96) else null
                        }
                        val originalBmp: ImageBitmap? = remember(drawable) {
                            if (drawable != null) {
                                try {
                                    drawableToBitmap(drawable, sizePx = 96).asImageBitmap()
                                } catch (t: Throwable) {
                                    null
                                }
                            } else null
                        }

                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (showOriginal && originalBmp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(bitmap = originalBmp, contentDescription = pkg, modifier = Modifier.size(40.dp))
                                }
                            } else if (monoBmp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(bitmap = monoBmp, contentDescription = pkg, modifier = Modifier.size(40.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("?", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pkg.substringAfterLast('.'),
                                color = when (source) {
                                    "pack" -> Color.Green
                                    "system" -> Color.Cyan
                                    "none" -> Color.Red
                                    "loading" -> Color.Yellow
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, contentPadding = PaddingValues(8.dp)) { Text("Done", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium) }
        }
    )
}

/**
 * Small reusable placeholder icon used across screens.
 */
@Composable
fun PlaceholderIcon(label: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.firstOrNull()?.uppercase() ?: "?",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Compact Icon pack picker row — minimal list with small buttons.
 */
@Composable
fun IconPackPickerRow(onPick: (String?) -> Unit, currentPack: String?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val store = remember { DataStoreManager(context) }

    // Known packs to check (adjust package ids if Play Store package id differs)
    val known = listOf(
        "app.lawnchair.lawnicons.play" to "Lawnicons",
        "com.donnnno.arcticons" to "ArtIcons",
        "com.donnnno.arcticons.you.play" to "ArtIcons By You"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        known.forEach { (pkg, niceName) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(niceName, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                val installed by remember { derivedStateOf { context.isPackageInstalled(pkg) } }

                if (installed) {
                    // compact toggle button
                    Button(
                        onClick = {
                            coroutineScope.launch { store.setSelectedIconPack(pkg) }
                            onPick(pkg)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentPack == pkg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    ) {
                        Text(if (currentPack == pkg) "Selected" else "Use", style = MaterialTheme.typography.bodySmall,
                            color =  if (currentPack != pkg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "install", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install", fontSize = 12.sp)
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.06f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    coroutineScope.launch { store.setSelectedIconPack(null) }
                    onPick(null)
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Use system icons", fontSize = 12.sp)
            }
        }
    }
}


@Composable
fun FontCardRow(onOpenFontScreen: () -> Unit) {
    val context = LocalContext.current
    val ds = remember { DataStoreManager(context) }
    val currentType by ds.launcherFontTypeFlow.collectAsState(initial = null)
    val currentValue by ds.launcherFontValueFlow.collectAsState(initial = null)

    // Convert persisted value to friendly label
    fun labelFor(type: String?, value: String?): String {
        return when (type) {
            "res" -> value?.replaceFirstChar { it.uppercase() } ?: "Bundled"
            "uri" -> value?.let {
                // show filename only
                try {
                    val last = android.net.Uri.parse(it).lastPathSegment ?: it
                    last.substringAfterLast('/').substringBefore('?')
                } catch (t: Throwable) { it }
            } ?: "Custom"
            "pkg" -> value?.substringBefore(":")?.substringAfterLast('.') ?: "Package"
            else -> "System (default)"
        }
    }
    SectionHeader("Font Family")
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFontScreen() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(labelFor(currentType, currentValue), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }

            // simple affordance and small hint of selection
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "open",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun FontSizeSliderRow(openFontScreen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val coroutine = rememberCoroutineScope()

    // Stored font size (from DataStore) — the "applied" value used by the theme
    val storedFontSize by store.launcherFontSizeFlow.collectAsState(initial = 16)

    // Local preview state — user edits this with the slider.
    var tempSize by remember { mutableStateOf(storedFontSize) }
    var isEditing by remember { mutableStateOf(false) }

    // If the stored value changes externally and user is not actively editing, update temp.
    LaunchedEffect(storedFontSize) {
        if (!isEditing) tempSize = storedFontSize
    }

    // Debounce to detect "finished" slider interaction: when tempSize changes, mark editing and clear after delay
    LaunchedEffect(tempSize) {
        isEditing = true
        // wait for a pause in updates to consider it finished
        kotlinx.coroutines.delay(600)
        isEditing = false
    }

    Column(modifier = modifier.fillMaxWidth()) {


        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = tempSize.toFloat(),
                onValueChange = { newVal ->
                    // update preview value locally while dragging
                    tempSize = newVal.toInt().coerceIn(12, 28)

                },
                valueRange = 12f..28f,
                steps = 16,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // numeric readout (preview) — shows temp value
            Text("${tempSize}sp", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(min = 48.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)

        ) {
            // Preview

            Text(
                text = "The quick brown fox jumps over the lazy dog",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontFamily = FontManager.composeFontFamily,
                fontSize = tempSize.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp)
            )






        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), )  {
            OutlinedButton(
                onClick = {
                    // Reset preview to default (does not apply)
                    tempSize = 16
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset", style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    // Apply the preview -> write to DataStore which will trigger theme recomposition
                    coroutine.launch {
                        store.setLauncherFontSize(tempSize)
                        // optional small feedback
                        Toast.makeText(context, "Font size applied: ${tempSize}sp", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

    }
}

/** Chip-style section header */
@Composable
private fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Helper: check whether a package is installed (used to detect known icon packs).
 */
fun android.content.Context.isPackageInstalled(pkg: String): Boolean {
    return try {
        this.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }
}
