package com.example.minimalistlauncher.ui
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.example.minimalistlauncher.AppInfo
import com.example.minimalistlauncher.getAppIconDrawable
import com.example.minimalistlauncher.toImageBitmapSafe
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    onClose: () -> Unit,
    onLaunch: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(TextFieldValue("")) }

    // Filtered & grouped
    val filtered = apps.filter {
        it.label.contains(query.text, ignoreCase = true) || it.pkg.contains(query.text, ignoreCase = true)
    }

    // Group by first char
    val grouped = remember(filtered) {
        filtered.groupBy {
            val c = it.label.firstOrNull()?.uppercaseChar()
            if (c == null || !c.isLetter()) '#' else c
        }.toSortedMap(compareBy<Any> { if (it == '#') "{" else it.toString() }) // put # last-ish
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("All apps", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose) { Text("Close") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (section, list) ->
                item {
                    Text(
                        text = section.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = Color.Gray
                    )
                }
                itemsIndexed(list) { _, app ->
                    AppRow(context = context, app = app, onLaunch = onLaunch)
                }
            }
        }
    }
}

@Composable
private fun AppRow(context: Context, app: AppInfo, onLaunch: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunch(app.pkg) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val drawable = context.getAppIconDrawable(app.pkg)
        if (drawable != null) {
            val bmp = drawable.toImageBitmapSafe()
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    modifier = Modifier.size(36.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
            }
        } else {
            // fallback: simple text or placeholder box
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.firstOrNull()?.toString() ?: "?", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Text(app.label)
    }
}
