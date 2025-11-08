package com.example.minimalistlauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.pm.ResolveInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import com.example.minimalistlauncher.ui.AppDrawer
import com.example.minimalistlauncher.ui.SettingsScreen
import com.example.minimalistlauncher.ui.HomeClock
import com.example.minimalistlauncher.ui.SelectHomeAppsScreen
import com.example.minimalistlauncher.ui.FocusModeScreen
import com.example.minimalistlauncher.ui.PlaceholderIcon
import com.example.minimalistlauncher.ui.theme.MinimalLauncherTheme
import android.widget.FrameLayout
import android.view.ViewGroup

data class AppInfo(val label: String, val pkg: String, val iconBitmap: ImageBitmap? = null)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private var currentAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var hostView: AppWidgetHostView? = null
    private val APPWIDGET_HOST_ID = 1024

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        // widget picker result
        val pickWidgetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data!!
                val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    try {
                        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                        hostView = appWidgetHost.createView(this, appWidgetId, info)
                        hostView?.setAppWidget(appWidgetId, info)
                        currentAppWidgetId = appWidgetId
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        setContent {
            MinimalLauncherTheme {
                // hide status bar always (home experience)
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                LaunchedEffect(Unit) {
                    windowInsetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }

                val context = LocalContext.current
                val dataStore = remember { DataStoreManager(context) }

                // fetch installed apps (ResolveInfo list)
                val resolveInfos = remember { fetchLaunchableResolveInfoList() }

                val allApps: List<AppInfo> = remember(resolveInfos) {
                    resolveInfos
                        .map { ri: ResolveInfo ->
                            AppInfo(ri.loadLabel(packageManager).toString(), ri.activityInfo.packageName, null)
                        }
                        .sortedBy { app: AppInfo -> app.label.lowercase() }
                }

                val whitelist by dataStore.whitelistFlow.collectAsState(initial = emptySet())
                val persistedHomePkgs by dataStore.homeAppsFlow.collectAsState(initial = emptyList())

                var showDrawer by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showFocusMode by remember { mutableStateOf(false) }      // NEW
                var showSelectHome by remember { mutableStateOf(false) }     // NEW
                var focusModeEnabled by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                BackHandler(enabled = !showDrawer && !showSettings && !showFocusMode && !showSelectHome) { /* consume */ }

                // compute home list (either persisted selection OR first 5)
                val homeAppsOrdered: List<AppInfo> = remember(allApps, persistedHomePkgs, focusModeEnabled, whitelist) {
                    if (focusModeEnabled) {
                        val set = whitelist
                        allApps.filter { set.contains(it.pkg) }
                    } else {
                        if (persistedHomePkgs.isNotEmpty()) {
                            persistedHomePkgs.mapNotNull { pkg -> allApps.find { it.pkg == pkg } }
                        } else {
                            allApps.take(5)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        HomeClock()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Home list - icons + label
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            homeAppsOrdered.forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .clickable { context.launchPackage(app.pkg) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppRowIconLabel(context = context, app = app)
                                }
                            }

                            if (homeAppsOrdered.isEmpty()) {
                                Text(
                                    "No apps to show. Edit home in Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }

                    // bottom-right controls: Focus FAB + Edit Home + Apps + Settings
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(18.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        FloatingActionButton(
                            onClick = { showFocusMode = true },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            // uses your lawn graph vector drawable (ic_focus.xml)
                            Icon(painter = painterResource(id = R.drawable.ic_focus), contentDescription = "Focus", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Edit Home opens a dedicated selector modal
                        TextButton(onClick = { showSelectHome = true }) {
                            Text(text = "Edit Home", color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(onClick = { showDrawer = true }) {
                            Text("Apps", color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(onClick = { showSettings = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }

                    // App drawer overlay
                    if (showDrawer) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        ) {
                            AppDrawer(
                                apps = allApps,
                                onClose = { showDrawer = false },
                                onLaunch = { pkg: String ->
                                    showDrawer = false
                                    coroutineScope.launch { }
                                    context.launchPackage(pkg)
                                }
                            )
                        }
                    }

                    // Settings overlay
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

                    // Focus Mode overlay (placeholder UI)
                    if (showFocusMode) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            tonalElevation = 8.dp
                        ) {
                            FocusModeScreen(onClose = { showFocusMode = false })
                        }
                    }

                    // Select Home Apps overlay (dedicated picker)
                    if (showSelectHome) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            tonalElevation = 8.dp
                        ) {
                            SelectHomeAppsScreen(
                                apps = allApps,
                                initialSelection = persistedHomePkgs,
                                onClose = { showSelectHome = false }
                            )
                        }
                    }

                    // Widget host (if a widget was chosen earlier)
                    hostView?.let { hv ->
                        AndroidView(factory = { ctx ->
                            FrameLayout(ctx).apply {
                                if (hv.parent != null) (hv.parent as? ViewGroup)?.removeView(hv)
                                addView(hv, FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                                ))
                            }
                        }, modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.TopCenter)
                            .padding(top = 120.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    // helper: fetch ResolveInfo list (explicit overload selection)
    private fun fetchLaunchableResolveInfoList(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    // Row showing monochrome icon + label (force white)
    @Composable
    private fun AppRowIconLabel(context: android.content.Context, app: AppInfo, iconSizeDp: Int = 44) {
        val drawable = remember(app.pkg) { context.getAppIconDrawableSafe(app.pkg) }
        val tint = Color.White.toArgb() // force pure white tint
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (drawable != null) {
                val bmp = remember(drawable, tint) {
                    drawable.toMonochromeImageBitmap(tintColor = tint, sizePx = 96)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(iconSizeDp.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    PlaceholderIcon(app.label)
                }
            } else {
                PlaceholderIcon(app.label)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}
