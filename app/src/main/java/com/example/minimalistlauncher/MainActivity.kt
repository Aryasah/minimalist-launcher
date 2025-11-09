package com.example.minimalistlauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import com.example.minimalistlauncher.ui.RadialAction
import com.example.minimalistlauncher.ui.RadialMenu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GridView
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.lifecycleScope

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

        // Attempt to reapply persisted fonts. For "res" selections we map keys -> R.font.* here.
        // mapping for bundled fonts:
        val prefs = getSharedPreferences("launcher_fonts", MODE_PRIVATE)
        val type = prefs.getString("font_type", null)
        val value = prefs.getString("font_value", null)

        if (type == "res" && value != null) {
            // map the stored key to an actual resource id
            val resId = when (value) {
                "inter" -> R.font.inter
                // add other mappings like "roboto" -> R.font.roboto_regular
                else -> null
            }
            resId?.let { FontManager.loadFromRes(this, it, value) }
        } else {
            // for uri/pkg, let FontManager try to reapply itself:
            FontManager.reapplyPersistedFont(this)
        }

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        // in MainActivity.onCreate
        val ds = DataStoreManager(this)

        lifecycleScope.launchWhenCreated {
            val ds = DataStoreManager(this@MainActivity)
            val (type, value) = ds.getLauncherFontOnce()
            when (type) {
                "res" -> {
                    // map value to resId (value is the key e.g., "inter")
                    val resId = when (value) {
                        "inter" -> R.font.inter
                        "poppins" -> R.font.poppins
                        "roboto" -> R.font.roboto
                        else -> 0
                    }
                    if (resId != 0) FontManager.loadFromRes(this@MainActivity, resId, value!!)
                }
                "uri" -> value?.let { FontManager.loadFromUri(this@MainActivity, Uri.parse(it)) }
                "pkg" -> value?.let {
                    val parts = it.split(":", limit = 2)
                    if (parts.size == 2) FontManager.loadFromPackageFont(this@MainActivity, parts[0], parts[1])
                }
            }
        }

        setContent {
            MinimalLauncherTheme {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                LaunchedEffect(Unit) { windowInsetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars()) }

                val context = LocalContext.current
                val dataStore = remember { DataStoreManager(context) }
                val iconPack by dataStore.selectedIconPackFlow.collectAsState(initial = null)

                val resolveInfos = remember { fetchLaunchableResolveInfoList() }
                val allApps: List<AppInfo> = remember(resolveInfos) {
                    resolveInfos.map { ri: ResolveInfo -> AppInfo(ri.loadLabel(packageManager).toString(), ri.activityInfo.packageName, null) }
                        .sortedBy { it.label.lowercase() }
                }

                var showDrawer by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showFocusMode by remember { mutableStateOf(false) }
                var showSelectHome by remember { mutableStateOf(false) }
                var focusModeEnabled by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()

                BackHandler(enabled = !showDrawer && !showSettings && !showFocusMode && !showSelectHome) { /* consume */ }

                // --- inside setContent { MinimalLauncherTheme { ... } } ---

                val whitelist by dataStore.whitelistFlow.collectAsState(initial = emptySet())
                val persistedHomePkgs by dataStore.homeAppsFlow.collectAsState(initial = emptyList())

                // debug: log when persistedHomePkgs changes so we can see the DataStore update
                LaunchedEffect(persistedHomePkgs) {
                    Log.d("MainActivity", "persistedHomePkgs emitted: ${persistedHomePkgs.joinToString(",")}")
                }

                // Compute the home apps list as a derived state so recomposition reacts reliably to inputs.
                // derivedStateOf produces a State whose value is recomputed when any snapshot read inside it changes.
                val homeAppsOrdered by remember(allApps, persistedHomePkgs, focusModeEnabled, whitelist) {
                    derivedStateOf {
                        if (focusModeEnabled) {
                            // only apps allowed by whitelist when focus mode enabled
                            val set = whitelist
                            allApps.filter { set.contains(it.pkg) }
                        } else {
                            if (persistedHomePkgs.isNotEmpty()) {
                                // preserve ordering from persistedHomePkgs
                                persistedHomePkgs.mapNotNull { pkg -> allApps.find { it.pkg == pkg } }
                            } else {
                                // fallback: first 5 apps alphabetically
                                allApps.take(5)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        HomeClock()
                        Spacer(modifier = Modifier.height(8.dp))

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
                                    AppRowIconLabel(context = context, app = app, iconPack = iconPack)
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

                    // --- inside Box(modifier = ...) in setContent, replace the Column(...) block ---

// create the actions you want
                    val radialActions = listOf(
                        RadialAction(
                            id = "focus",
                            icon = { Icon(painter = painterResource(id = R.drawable.ic_focus), contentDescription = null, tint = Color.White) },
                            contentDescription = "Focus",
                            onClick = { showFocusMode = true }
                        ),
                        RadialAction(
                            id = "edit_home",
                            icon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                            contentDescription = "Edit home",
                            onClick = { showSelectHome = true }
                        ),
                        RadialAction(
                            id = "apps",
                            icon = { Icon(imageVector = Icons.Default.GridView, contentDescription = null, tint = Color.White) },
                            contentDescription = "Apps",
                            onClick = { showDrawer = true }
                        ),
                        RadialAction(
                            id = "settings",
                            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.White) },
                            contentDescription = "Settings",
                            onClick = { showSettings = true }
                        )
                    )

// place radial menu at bottom-end
                    Box(modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)) {
                        RadialMenu(
                            actions = radialActions,
                            radiusDp = 100.dp,
                            startAngleDegrees = 180f, // ⬅ fan starts upper-left of the FAB
                            sweepAngleDegrees = 90f,  // covers up-left to up
                            centerButtonSize = 56.dp,
                            centerButtonColor = MaterialTheme.colorScheme.primary,
                            centerIconColor = Color.White,
                            onOpenedChanged = { open -> Log.d("RadialMenu", "open=$open") }
                        )
                    }


                    if (showDrawer) {
                        Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
                            AppDrawer(apps = allApps, onClose = { showDrawer = false }, onLaunch = { pkg: String ->
                                showDrawer = false
                                coroutineScope.launch { }
                                context.launchPackage(pkg)
                            })
                        }
                    }

                    if (showSettings) {
                        Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), tonalElevation = 8.dp) {
                            // updated call signature for SettingsScreen
                            SettingsScreen(
                                onClose = { showSettings = false },
                                onManageHomeApps = { showSelectHome = true }
                            )
                        }
                    }

                    if (showFocusMode) {
                        Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), tonalElevation = 8.dp) {
                            FocusModeScreen(onClose = { showFocusMode = false })
                        }
                    }

                    if (showSelectHome) {
                        Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), tonalElevation = 8.dp) {
                            SelectHomeAppsScreen(apps = allApps, initialSelection = persistedHomePkgs, onClose = { showSelectHome = false })
                        }
                    }

                    hostView?.let { hv ->
                        AndroidView(factory = { ctx ->
                            FrameLayout(ctx).apply {
                                if (hv.parent != null) (hv.parent as? ViewGroup)?.removeView(hv)
                                addView(hv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                            }
                        }, modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter).padding(top = 120.dp))
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

    private fun fetchLaunchableResolveInfoList(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    @Composable
    private fun AppRowIconLabel(context: android.content.Context, app: AppInfo, iconPack: String?, iconSizeDp: Int = 44) {
        val drawable = remember(app.pkg, iconPack) {
            var d: android.graphics.drawable.Drawable? = null
            if (!iconPack.isNullOrEmpty()) {
                d = tryLoadIconFromIconPack(context, iconPack, app.pkg)
            }
            if (d == null) d = context.getAppIconDrawableSafe(app.pkg)
            d
        }

        val tint = Color.White.toArgb()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (drawable != null) {
                val bmp = remember(drawable, tint) {
                    drawableToMonochromeImageBitmap(drawable, tintColor = tint, sizePx = 128)
                }
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .size(iconSizeDp.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size((iconSizeDp - 8).dp))
                    }
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
    color = Color.White,
    fontFamily = FontManager.composeFontFamily // this updates reactively
)
        }
    }
}
