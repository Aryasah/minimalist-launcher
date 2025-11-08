package com.example.minimalistlauncher.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.minimalistlauncher.AppInfo
import com.example.minimalistlauncher.DataStoreManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.example.minimalistlauncher.drawableToMonochromeImageBitmap
import com.example.minimalistlauncher.tryLoadIconFromIconPack
import com.example.minimalistlauncher.getAppIconDrawableSafe
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.snapshots.SnapshotStateList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectHomeAppsScreen(
    apps: List<AppInfo>,
    initialSelection: List<String>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val iconPack by store.selectedIconPackFlow.collectAsState(initial = null)

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val selection: SnapshotStateList<String> = remember { initialSelection.toMutableStateList() }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
        TopAppBar(
            title = { Text("Select home apps (max 5)", color = Color.White) },
            actions = {
                TextButton(onClick = onClose) { Text("Close", color = Color.White) }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps", color = Color.White.copy(alpha = 0.7f)) },
            textStyle = LocalTextStyle.current.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filtered list (all apps; no hard cap)
        val filtered = remember(apps, query.text) {
            if (query.text.isBlank()) apps else apps.filter {
                it.label.contains(query.text, ignoreCase = true) || it.pkg.contains(query.text, ignoreCase = true)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered) { app ->
                val checked = selection.contains(app.pkg)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) selection.remove(app.pkg)
                            else if (selection.size < 5) selection.add(app.pkg)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // load icon (icon pack preferred)
                    val drawable = remember(app.pkg, iconPack) {
                        var d = null as android.graphics.drawable.Drawable?
                        if (!iconPack.isNullOrEmpty()) {
                            d = tryLoadIconFromIconPack(context, iconPack!!, app.pkg)
                        }
                        if (d == null) {
                            d = context.getAppIconDrawableSafe(app.pkg)
                        }
                        d
                    }
                    val tint = MaterialTheme.colorScheme.onSurface.toArgb()
                    if (drawable != null) {
                        val bmp: ImageBitmap? = remember(drawable, tint) {
                            drawableToMonochromeImageBitmap(drawable, tintColor = tint, sizePx = 128)
                        }
                        if (bmp != null) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size(36.dp))
                            }
                        } else {
                            PlaceholderIcon(app.label)
                        }
                    } else {
                        PlaceholderIcon(app.label)
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(app.label, color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = { now ->
                        if (now && selection.size < 5) selection.add(app.pkg)
                        if (!now) selection.remove(app.pkg)
                    })
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text("Cancel", color = Color.White)
                    }
                    Button(onClick = {
                        val toSave = selection.take(5).toList()
                        coroutineScope.launch {
                            store.setHomeApps(toSave)
                        }
                        onClose()
                    }) {
                        Text("Save", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
