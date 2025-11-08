package com.example.minimalistlauncher.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.example.minimalistlauncher.AppInfo
import com.example.minimalistlauncher.DataStoreManager
import com.example.minimalistlauncher.getAppIconDrawable
import com.example.minimalistlauncher.toImageBitmapSafe

/**
 * A simple Settings screen that lists all installed apps and lets the user toggle them for Focus Mode whitelist.
 *
 * - apps: list of AppInfo (label + pkg)
 * - onClose: callback to close settings
 */
@Composable
fun SettingsScreen(
    apps: List<AppInfo>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val whitelistState by store.whitelistFlow.collectAsState(initial = emptySet())

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("Focus Mode — whitelist apps", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Toggle apps allowed when Focus Mode is enabled.", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps) { app ->
                    val checked = whitelistState.contains(app.pkg)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon (monochrome)
                        val drawable = context.getAppIconDrawable(app.pkg)
                        if (drawable != null) {
                            val img = drawable.toImageBitmapSafe()
                            if (img != null) {
                                Image(
                                    bitmap = img,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(36.dp),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.tint(Color.Black)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(app.label, modifier = Modifier.weight(1f))
                        Switch(
                            checked = checked,
                            onCheckedChange = { nowChecked ->
                                coroutineScope.launch {
                                    if (nowChecked) store.addToWhitelist(app.pkg) else store.removeFromWhitelist(app.pkg)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
