package com.example.minimalistlauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeScreen(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        TopAppBar(title = { Text("Focus Mode") }, actions = { TextButton(onClick = onClose) { Text("Close") } })
        Spacer(modifier = Modifier.height(12.dp))
        Text("Focus Mode settings will be here — whitelist, schedules, timers etc.", style = MaterialTheme.typography.bodyLarge)
    }
}
