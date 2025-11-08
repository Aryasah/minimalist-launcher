package com.example.minimalistlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.minimalistlauncher.ui.AppDrawer
import com.example.minimalistlauncher.ui.SettingsScreen
import androidx.compose.material3.SmallTopAppBar as SmallTopAppBar1

data class AppInfo(val label: String, val pkg: String)

/**
 * Opt in to the experimental Material3 API so SmallTopAppBar usage doesn't show experimental warnings.
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val allApps = remember { fetchLaunchableApps() }
                val dataStoreManager = remember { DataStoreManager(context) }
                val whitelist by dataStoreManager.whitelistFlow.collectAsState(initial = emptySet())

                var showDrawer by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var focusModeEnabled by remember { mutableStateOf(false) }

                // Compute home apps depending on focus mode
                val homeApps = if (focusModeEnabled) {
                    allApps.filter { whitelist.contains(it.pkg) }
                } else allApps

                val coroutineScope = rememberCoroutineScope()

                Scaffold(
                    topBar = {
                        SmallTopAppBar1(
                            title = { Text("Minimal Launcher") },
                            actions = {
                                TextButton(onClick = { showSettings = true }) { Text("Settings") }
                            }
                        )
                    },
                    content = { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                // gesture: detect swipe-up to open drawer
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        // dragAmount negative means upward drag
                                        if (dragAmount < -50f) {
                                            showDrawer = true
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Focus Mode", fontSize = 16.sp)
                                    Switch(checked = focusModeEnabled, onCheckedChange = {
                                        focusModeEnabled = it
                                    })
                                }

                                Spacer(modifier = Modifier.height(18.dp))
                                // Small top apps list
                                LazyColumn {
                                    items(homeApps.take(8)) { app ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .clickable {
                                                    context.launchPackage(app.pkg)
                                                }
                                        ) {
                                            Text(app.label)
                                        }
                                    }
                                }
                            }

                            // Drawer overlay
                            if (showDrawer) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                                ) {
                                    AppDrawer(
                                        apps = allApps,
                                        onClose = { showDrawer = false },
                                        onLaunch = { pkg ->
                                            showDrawer = false
                                            coroutineScope.launch {
                                                // leave room for small animation if needed
                                            }
                                            context.launchPackage(pkg)
                                        }
                                    )
                                }
                            }

                            // Settings screen modal
                            if (showSettings) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
                                    tonalElevation = 8.dp
                                ) {
                                    SettingsScreen(apps = allApps, onClose = { showSettings = false })
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun fetchLaunchableApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

        // Backward-compatible query: use ResolveInfoFlags on API 33+, otherwise use legacy int flags (0)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolveInfos.map {
            AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName)
        }.sortedBy { it.label.lowercase() }
    }
}
