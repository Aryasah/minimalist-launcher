package com.example.minimalistlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.minimalistlauncher.AppInfo
import com.example.minimalistlauncher.CacheManager
import com.example.minimalistlauncher.DataStoreManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    // debounced query string used for filtering
    var debouncedQuery by remember { mutableStateOf("") }

    // debounce logic: update debouncedQuery 300ms after user stops typing
    LaunchedEffect(query.text) {
        val text = query.text
        kotlinx.coroutines.delay(300) // debounce interval
        if (text == query.text) debouncedQuery = text
    }

    val selection: SnapshotStateList<String> = remember { initialSelection.toMutableStateList() }

    val tint = MaterialTheme.colorScheme.onSurface.toArgb()
    val listState = rememberLazyListState()

    // compute filtered list based on debouncedQuery and pin selected on top
    val filtered by remember(apps, debouncedQuery, selection.toList()) {
        derivedStateOf {
            val base = if (debouncedQuery.isBlank()) apps else apps.filter {
                it.label.contains(debouncedQuery, ignoreCase = true) || it.pkg.contains(debouncedQuery, ignoreCase = true)
            }
            val (selectedItems, others) = base.partition { selection.contains(it.pkg) }
            selectedItems + others
        }
    }

    // prewarm icons for initial visible buffer when iconPack/filtered changes
    LaunchedEffect(iconPack, filtered) {
        val toPrewarm = filtered.take(24).map { it.pkg }
        if (toPrewarm.isNotEmpty()) {
            try {
                CacheManager.prewarm(context, iconPack, toPrewarm, sizePx = 96)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    // New state: disable Save while writing
    var saving by remember { mutableStateOf(false) }

    // Layout: search + results count + scrolling list (weight 1) + fixed bottom actions
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        CommonTopBar(
            title = "Select home apps (max 5)",
            onClose = onClose,
            useBackIcon = true
        )

        // Search bar + small hits count
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps or packages", color = Color.White.copy(alpha = 0.6f)) },
            leadingIcon = {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Search,
                    contentDescription = "search",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue("") }) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "clear",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.12f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.06f),
                cursorColor = Color.White
            ),
            textStyle = LocalTextStyle.current.copy(color = Color.White)
        )

        // small count + pinned hint
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} result(s)", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text("Selected pinned on top", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrolling list takes remaining space
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState
        ) {
            items(filtered, key = { it.pkg }) { app ->
                val checked = selection.contains(app.pkg)

                val sizePx = 96
                val cacheKey = remember(iconPack, app.pkg, sizePx) { "${iconPack ?: "sys"}|${app.pkg}|$sizePx" }

                val iconBitmapState by produceState<ImageBitmap?>(initialValue = null, key1 = cacheKey, key2 = iconPack) {
                    try {
                        val bmp: Bitmap? = CacheManager.getBitmap(context, iconPack, app.pkg, sizePx)
                        if (bmp != null) {
                            value = withContext(Dispatchers.Default) { bmp.asImageBitmap() }
                        } else {
                            value = null
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        value = null
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) selection.remove(app.pkg)
                            else if (selection.size < 5) selection.add(app.pkg)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconBitmapState != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(bitmap = iconBitmapState!!, contentDescription = app.label, modifier = Modifier.size(36.dp))
                        }
                    } else {
                        PlaceholderIcon(app.label)
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(app.label, color = Color.White, fontSize = 15.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = { now ->
                        if (now && selection.size < 5) selection.add(app.pkg)
                        if (!now) selection.remove(app.pkg)
                    })
                }
                Divider(color = Color.White.copy(alpha = 0.04f))
            }
        }

        // fixed bottom controls: Cancel | Save
        Column(modifier = Modifier.fillMaxWidth()) {
            Divider(color = Color.White.copy(alpha = 0.06f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (!saving) onClose() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text("Cancel", color = Color.White)
                }

                Button(
                    onClick = {
                        val toSave = selection.take(5).toList()
                        coroutineScope.launch {
                            saving = true
                            try {
                                store.setHomeApps(toSave)
                                Log.d("SelectHomeApps", "Saved home apps: ${toSave.joinToString(",")}")
                                onClose()
                            } catch (t: Throwable) {
                                t.printStackTrace()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving", color = Color.White)
                    } else {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

