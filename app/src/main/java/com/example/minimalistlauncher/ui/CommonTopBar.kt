package com.example.minimalistlauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Divider
import androidx.compose.ui.text.style.TextOverflow

/**
 * Reusable top bar used across Settings, App Drawer, Select Home, etc.
 *
 * - useBackIcon: if true shows a back arrow IconButton; otherwise shows a "Close" text button.
 * - statusBarPadding: when true applies status bar padding for edge-to-edge layouts.
 * - showDivider: when true draws a subtle divider below the bar (default: subtle 4% white).
 * - actions: optional trailing content slot (RowScope) for buttons/icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonTopBar(
    title: String,
    onClose: (() -> Unit)? = null,
    useBackIcon: Boolean = false,
    statusBarPadding: Boolean = true,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            navigationIcon = {
                if (onClose != null) {
                    if (useBackIcon) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onClose,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Close",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            title = {
                // 👇 We wrap title in a Box to vertically center align
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = { actions?.invoke(this) },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White
            ),
            modifier = Modifier
                .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .height(52.dp)
        )

        if (showDivider) {
            Divider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)
        }
    }
}
