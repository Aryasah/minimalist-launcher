package com.example.minimalistlauncher.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.exoplayer.offline.Download

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

    // compact paddings and smaller text sizes
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
            navigationIcon = {
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Close", color = Color.White, fontSize = 13.sp)
                }
            },
            actions = {
                // small placeholder space for future actions
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.height(48.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Appearance", color = Color.White, style = MaterialTheme.typography.titleSmall, fontSize = 13.sp)
            }

            // Icon pack picker (compact)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Icon pack", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show original icons", color = Color.White, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showPreview = true },
                        enabled = selectedIconPack != null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Preview", fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            // Home management (compact)
            item {
                Text("Home", color = Color.White, style = MaterialTheme.typography.titleSmall, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onManageHomeApps,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("Manage Home Apps", fontSize = 13.sp, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch { store.setHomeApps(emptyList()) }
                        },
                        modifier = Modifier.widthIn(min = 120.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Clear", fontSize = 13.sp)
                    }
                }
            }

            // About / footer (minimal)
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("About", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Minimalist Launcher — simple, minimal home screen", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
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
        title = { Text("Preview icon pack", color = Color.White, fontSize = 14.sp) },
        text = {
            Column {
                Text("Green=P, Cyan=S, ?=none", color = Color.White, fontSize = 11.sp)
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
                                    Text("?", color = Color.White, fontSize = 12.sp)
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
                                    else -> Color.White
                                },
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, contentPadding = PaddingValues(8.dp)) { Text("Done", color = Color.White, fontSize = 13.sp) }
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
        "com.whicons.iconpack" to "Whicons",
        "org.jraf.iconpack.simple" to "ExamplePack"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        known.forEach { (pkg, niceName) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(niceName, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                val installed by remember { derivedStateOf { context.isPackageInstalled(pkg) } }

                if (installed) {
                    // compact toggle button
                    Button(
                        onClick = {
                            coroutineScope.launch { store.setSelectedIconPack(pkg) }
                            onPick(pkg)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentPack == pkg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    ) {
                        Text(if (currentPack == pkg) "Selected" else "Use", fontSize = 12.sp)
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
