package com.example.minimalistlauncher.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.minimalistlauncher.AppInfo
import com.example.minimalistlauncher.DataStoreManager
import com.example.minimalistlauncher.getAppIconDrawableSafe
import com.example.minimalistlauncher.toMonochromeImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb

/**
 * Settings screen that supports selection mode and DataStore-backed whitelist.
 * Mark as opt-in for Material3 experimental APIs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apps: List<AppInfo>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val whitelistState by store.whitelistFlow.collectAsState(initial = emptySet())

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() } // package names

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Focus Mode — whitelist") },
            actions = {
                // Visible select action always
                IconButton(onClick = {
                    selectionMode = true
                    selected.clear()
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Select apps")
                }

                if (selectionMode) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val newSet = whitelistState + selected.toSet()
                            store.setWhitelist(newSet)
                            selected.clear()
                            selectionMode = false
                        }
                    }) { Text("Add selected") }

                    TextButton(onClick = {
                        coroutineScope.launch {
                            val newSet = whitelistState - selected.toSet()
                            store.setWhitelist(newSet)
                            selected.clear()
                            selectionMode = false
                        }
                    }) { Text("Remove selected") }
                } else {
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        )


        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps.filter {
                it.label.contains(query.text, ignoreCase = true) || it.pkg.contains(query.text, ignoreCase = true)
            }) { app ->
                val checked = whitelistState.contains(app.pkg)
                val drawable = context.getAppIconDrawableSafe(app.pkg)
                val tint = MaterialTheme.colorScheme.onSurface.toArgb()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected.contains(app.pkg)) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
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
                    if (drawable != null) {
                        val bmp = remember(drawable, tint) { drawable.toMonochromeImageBitmap(tintColor = tint) }
                        if (bmp != null) {
                            Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size(36.dp))
                        } else {
                            PlaceholderIcon(app.label)
                        }
                    } else {
                        PlaceholderIcon(app.label)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.pkg, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                    }

                    if (!selectionMode) {
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
            }) { Text("Select apps") }

            Button(onClick = {
                coroutineScope.launch {
                    store.setWhitelist(emptySet())
                }
            }) { Text("Clear whitelist") }
        }
    }
}
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

