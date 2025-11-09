package com.example.minimalistlauncher.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.example.minimalistlauncher.AppInfo

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    onClose: () -> Unit,
    onLaunch: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val filtered = apps.filter {
        it.label.contains(query.text, ignoreCase = true) || it.pkg.contains(query.text, ignoreCase = true)
    }

    // visible flags for staggered animation
    val visible = remember { mutableStateListOf<Boolean>() }
    LaunchedEffect(filtered) {
        visible.clear()
        filtered.forEachIndexed { index, _ ->
            visible.add(false)
        }
        // slight stagger
        filtered.indices.forEach { i ->
            delay(35L) // stagger gap (ms) — tweak for speed
            if (i < visible.size) visible[i] = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        CommonTopBar(
            title = "All Apps",
            onClose = onClose,
            useBackIcon = true,
            actions = {
                TextButton(onClick = { /* open search */ }) {
                    Text("Search", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps", color = Color.White.copy(alpha = 0.6f)) },
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
                        Icon(
                            imageVector = Icons.Default.Close,
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

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(filtered) { index, app ->
                val isVisible = visible.getOrElse(index) { false }
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 })
                ) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunch(app.pkg) }
                        .padding(vertical = 10.dp)
                    ) {
                        // for ultra minimal, show label only. If you want icons, re-add them here.
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
