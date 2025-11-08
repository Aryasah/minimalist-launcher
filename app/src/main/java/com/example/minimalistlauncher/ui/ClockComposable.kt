package com.example.minimalistlauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeClock() {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM dd", Locale.getDefault()) }

    var timeText by remember { mutableStateOf(timeFormatter.format(Date())) }
    var dateText by remember { mutableStateOf(dateFormatter.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            timeText = timeFormatter.format(now)
            dateText = dateFormatter.format(now)
            delay(60_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = timeText,
            fontSize = 64.sp,
            color = Color.White,
            fontWeight = FontWeight.Light
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = dateText,
            fontSize = 20.sp,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}
