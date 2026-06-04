package com.openptt.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // User info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            uiState.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "@${uiState.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server URL
            SettingsItem(
                icon = Icons.Default.Dns,
                title = "Server URL",
                subtitle = uiState.serverUrl.ifEmpty { "Not configured" },
                onClick = { viewModel.showUrlDialog(true) }
            )

            // Audio Quality
            SettingsItem(
                icon = Icons.Default.GraphicEq,
                title = "Audio Quality",
                subtitle = uiState.audioQuality.replaceFirstChar { it.uppercase() },
                onClick = { viewModel.showQualityDialog(true) }
            )

            // Theme
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = uiState.theme.replaceFirstChar { it.uppercase() },
                onClick = { viewModel.showThemeDialog(true) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Logout button
            OutlinedButton(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }

            // Version
            Text(
                text = "OpenPTT v0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }

    if (uiState.showUrlDialog) {
        var url by remember { mutableStateOf(uiState.serverUrl) }
        AlertDialog(
            onDismissRequest = { viewModel.showUrlDialog(false) },
            title = { Text("Server URL") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (e.g. http://192.168.1.5:8443)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.updateServerUrl(url) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showUrlDialog(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showQualityDialog) {
        val options = listOf("low", "normal", "high")
        var selected by remember { mutableStateOf(uiState.audioQuality) }
        AlertDialog(
            onDismissRequest = { viewModel.showQualityDialog(false) },
            title = { Text("Audio Quality") },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = option }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == option, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.updateAudioQuality(selected) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showQualityDialog(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showThemeDialog) {
        val options = listOf("system", "light", "dark")
        var selected by remember { mutableStateOf(uiState.theme) }
        AlertDialog(
            onDismissRequest = { viewModel.showThemeDialog(false) },
            title = { Text("Theme") },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = option }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == option, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.updateTheme(selected) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showThemeDialog(false) }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
