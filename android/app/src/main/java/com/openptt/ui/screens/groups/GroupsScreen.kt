package com.openptt.ui.screens.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun GroupsScreen(
    groupId: String,
    onChannelClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.group?.name ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.group?.invite_code != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                val inviteText = uiState.group!!.invite_code?.let { " Invite Code: $it" } ?: ""
                                putExtra(android.content.Intent.EXTRA_TEXT, "Join me on OpenPTT!\nGroup ID: ${uiState.group!!.id}$inviteText")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Group Info"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Invite Code")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.group != null) {
                FloatingActionButton(
                    onClick = { viewModel.showCreateChannelDialog(true) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Channel")
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group info header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = uiState.group?.name ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ID: ${uiState.group?.id ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.group?.description != null) {
                                Text(
                                    text = uiState.group!!.description!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${uiState.group?.member_count ?: 0} members",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // Channels header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Channels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Channel list
                items(uiState.channels) { channel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChannelClick(channel.id) },
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
                                imageVector = if (channel.channel_type == "priority")
                                    Icons.Default.PriorityHigh else Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    channel.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                if (channel.description != null) {
                                    Text(
                                        channel.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showCreateChannelDialog) {
        var channelName by remember { mutableStateOf("") }
        var channelDescription by remember { mutableStateOf("") }
        var isPriority by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.showCreateChannelDialog(false) },
            title = { Text("Create Channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        label = { Text("Channel Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = channelDescription,
                        onValueChange = { channelDescription = it },
                        label = { Text("Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPriority = !isPriority }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isPriority, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Priority Channel (Interacts with all)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createChannel(groupId, channelName, channelDescription.ifBlank { null }, isPriority) },
                    enabled = channelName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCreateChannelDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
