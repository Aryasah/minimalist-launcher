package com.example.minimalistlauncher.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.minimalistlauncher.AppInfo
import com.example.minimalistlauncher.DataStoreManager
import com.example.minimalistlauncher.getAppIconDrawableSafe
import com.example.minimalistlauncher.drawableToMonochromeImageBitmap
import com.example.minimalistlauncher.drawableToBitmap
import com.example.minimalistlauncher.tryLoadIconFromIconPack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.asImageBitmap
import android.util.Log
private const val SETTINGS_TAG = "SettingsScreen"

/**
 * Settings screen with whitelist, icon pack picker and preview.
 */
@OptIn(ExperimentalMaterial3Api::class) // LazyVerticalGrid uses stable API in many compose versions, but Material3 remains experimental opt-in.
@Composable
fun SettingsScreen(
    apps: List<AppInfo>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val whitelistState by store.whitelistFlow.collectAsState(initial = emptySet())
    val selectedIconPack by store.selectedIconPackFlow.collectAsState(initial = null)

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() } // package names

    // preview dialog state
    var showPreview by remember { mutableStateOf(false) }
    var showOriginalIcons by remember { mutableStateOf(false) } // correct initialization

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Focus Mode — whitelist", color = Color.White) },
            actions = {
                IconButton(onClick = {
                    selectionMode = true
                    selected.clear()
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Select apps", tint = Color.White)
                }

                if (selectionMode) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val newSet = whitelistState + selected.toSet()
                            store.setWhitelist(newSet)
                            selected.clear()
                            selectionMode = false
                        }
                    }) { Text("Add selected", color = Color.White) }

                    TextButton(onClick = {
                        coroutineScope.launch {
                            val newSet = whitelistState - selected.toSet()
                            store.setWhitelist(newSet)
                            selected.clear()
                            selectionMode = false
                        }
                    }) { Text("Remove selected", color = Color.White) }
                } else {
                    TextButton(onClick = onClose) { Text("Close", color = Color.White) }
                }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Transparent)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps", color = Color.White.copy(alpha = 0.7f)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(color = Color.White)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filtered list
        val filteredApps = remember(apps, query.text) {
            if (query.text.isBlank()) apps
            else apps.filter { it.label.contains(query.text, ignoreCase = true) || it.pkg.contains(query.text, ignoreCase = true) }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(filteredApps) { app ->
                val checked = whitelistState.contains(app.pkg)

                // load drawable + source via produceState (off main thread)
                val ctx = LocalContext.current
                val drawableWithSource by produceState<Pair<android.graphics.drawable.Drawable?, String>>(initialValue = null to "loading", key1 = app.pkg, key2 = selectedIconPack) {
                    val iconFromPack = if (!selectedIconPack.isNullOrEmpty()) {
                        try {
                            tryLoadIconFromIconPack(ctx, selectedIconPack!!, app.pkg)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            null
                        }
                    } else null

                    val chosenDrawable: android.graphics.drawable.Drawable? = iconFromPack ?: ctx.getAppIconDrawableSafe(app.pkg)
                    val source = when {
                        iconFromPack != null -> "pack"
                        chosenDrawable != null -> "system"
                        else -> "none"
                    }
                    value = chosenDrawable to source
                    Log.d(SETTINGS_TAG, "icon lookup for pkg=$app.pkg , selectedPack=$selectedIconPack -> source=$source")

                }

                val drawable = drawableWithSource.first
                val source = drawableWithSource.second

                val tint = MaterialTheme.colorScheme.onSurface.toArgb()

                // create monochrome bitmap (remember); also prepare original bitmap for preview if needed
                val monoBmp: ImageBitmap? = remember(drawable, tint) {
                    if (drawable != null) drawableToMonochromeImageBitmap(drawable, tintColor = tint, sizePx = 128) else null
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected.contains(app.pkg))
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                        .clickable {
                            if (selectionMode) {
                                if (selected.contains(app.pkg)) selected.remove(app.pkg) else selected.add(app.pkg)
                            } else {
                                coroutineScope.launch {
                                    if (checked) store.removeFromWhitelist(app.pkg) else store.addToWhitelist(app.pkg)
                                }
                            }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // icon area
                    if (showOriginalIcons && originalBmp != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(bitmap = originalBmp, contentDescription = app.label, modifier = Modifier.size(36.dp))
                        }
                    } else if (monoBmp != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(bitmap = monoBmp, contentDescription = app.label, modifier = Modifier.size(36.dp))
                        }
                    } else {
                        PlaceholderIcon(app.label)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Text(app.pkg, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (source) {
                            "pack" -> "P"
                            "system" -> "S"
                            "none" -> "?"
                            "loading" -> "…"
                            else -> "?"
                        },
                        color = when (source) {
                            "pack" -> Color.Green
                            "system" -> Color.Cyan
                            "none" -> Color.Red
                            "loading" -> Color.Yellow
                            else -> Color.White
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    if (!selectionMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = checked, onCheckedChange = { nowChecked ->
                            coroutineScope.launch {
                                if (nowChecked) store.addToWhitelist(app.pkg) else store.removeFromWhitelist(app.pkg)
                            }
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                selectionMode = true
                selected.clear()
            }) { Text("Select apps", color = Color.White) }

            Button(onClick = {
                coroutineScope.launch {
                    store.setWhitelist(emptySet())
                }
            }) { Text("Clear whitelist", color = Color.White) }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.12f))

        // Icon pack picker + preview button
        IconPackPickerRow(onPick = { /* no-op */ }, currentPack = selectedIconPack)

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showOriginalIcons, onCheckedChange = { showOriginalIcons = it })
                Spacer(modifier = Modifier.width(6.dp))
                Text("Show original icons", color = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { showPreview = true }, enabled = selectedIconPack != null) {
                Text("Preview pack", color = Color.White)
            }
        }

        if (showPreview) {
            IconPackPreviewDialog(
                iconPack = selectedIconPack,
                onClose = { showPreview = false },
                showOriginal = showOriginalIcons
            )
        }
    }
}

/**
 * Preview dialog: shows grid of common apps to verify icon-pack.
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
        title = { Text("Preview icon pack", color = Color.White) },
        text = {
            Column {
                Text("Green=P (pack), Cyan=S (system), ?=none", color = Color.White, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(min = 240.dp, max = 480.dp)) {
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
                            Log.d(SETTINGS_TAG, "Preview icon lookup pkg=$pkg , iconPack=$iconPack -> source=$source")
                        }

                        val drawable = drawableWithSource.first
                        val source = drawableWithSource.second
                        val tint = MaterialTheme.colorScheme.onSurface.toArgb()
                        val monoBmp: ImageBitmap? = remember(drawable, tint) {
                            if (drawable != null) drawableToMonochromeImageBitmap(drawable, tintColor = tint, sizePx = 128) else null
                        }
                        val originalBmp: ImageBitmap? = remember(drawable) {
                            if (drawable != null) {
                                try {
                                    drawableToBitmap(drawable, sizePx = 128).asImageBitmap()
                                } catch (t: Throwable) {
                                    null
                                }
                            } else null
                        }

                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (showOriginal && originalBmp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(bitmap = originalBmp, contentDescription = pkg, modifier = Modifier.size(52.dp))
                                }
                            } else if (monoBmp != null) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(bitmap = monoBmp, contentDescription = pkg, modifier = Modifier.size(52.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("?", color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pkg.substringAfterLast('.'),
                                color = when (source) {
                                    "pack" -> Color.Green
                                    "system" -> Color.Cyan
                                    "none" -> Color.Red
                                    "loading" -> Color.Yellow
                                    else -> Color.White
                                },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text("Done", color = Color.White) }
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
            fontSize = 14.sp,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Icon pack picker UI — shows a few known packs and lets user install/select them.
 */
@Composable
fun IconPackPickerRow(onPick: (String?) -> Unit, currentPack: String?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val store = remember { DataStoreManager(context) }

    // Known packs to check (adjust package ids if Play Store package id differs)
    val known = listOf(
        "app.lawnchair.lawnicons.play" to "Lawnicons",
        "com.whicons.iconpack" to "Whicons",
        "org.jraf.iconpack.simple" to "ExamplePack"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Icon pack", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // show detected packs
        known.forEach { (pkg, niceName) ->
            val installed by remember { derivedStateOf { context.isPackageInstalled(pkg) } }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(niceName, color = Color.White, modifier = Modifier.weight(1f))
                if (installed) {
                    Button(onClick = {
                        coroutineScope.launch { store.setSelectedIconPack(pkg) }
                        onPick(pkg)
                    }) {
                        Text(if (currentPack == pkg) "Selected" else "Use")
                    }
                } else {
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) {
                        Text("Install")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            coroutineScope.launch { store.setSelectedIconPack(null) }
            onPick(null)
        }) {
            Text("Use system icons")
        }
    }
}

/**
 * Helper: check whether a package is installed (used to detect known icon packs).
 */
fun Context.isPackageInstalled(pkg: String): Boolean {
    return try {
        this.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }
}
