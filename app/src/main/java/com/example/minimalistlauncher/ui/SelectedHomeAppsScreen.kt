package com.example.minimalistlauncher.ui

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
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toArgb
import com.example.minimalistlauncher.getAppIconDrawableSafe
import com.example.minimalistlauncher.toMonochromeImageBitmap

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

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val selection = remember { initialSelection.toMutableStateList() } // package names

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        TopAppBar(
            title = { Text("Select home apps (max 5)") },
            actions = {
                TextButton(onClick = onClose) { Text("Close") }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val filtered = apps.filter {
                it.label.contains(query.text, ignoreCase = true) || it.pkg.contains(query.text, ignoreCase = true)
            }
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
                    // icon + label (reuse your AppRowIconLabel or simplified)
                    val drawable = context.getAppIconDrawableSafe(app.pkg)
                    val tint = MaterialTheme.colorScheme.onSurface.toArgb()
                    if (drawable != null) {
                        val bmp = remember(drawable, tint) {
                            drawable.toMonochromeImageBitmap(tintColor = tint, sizePx = 72)
                        }
                        if (bmp != null) Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size(44.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(app.label, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = {
                        if (it && selection.size < 5) selection.add(app.pkg)
                        if (!it) selection.remove(app.pkg)
                    })
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = onClose) { Text("Cancel") }
                    Button(onClick = {
                        // persist ordered selection
                        coroutineScope.launch {
                            store.setHomeApps(selection.take(5).toList())
                        }
                        onClose()
                    }) { Text("Save") }
                }
            }
        }
    }
}
