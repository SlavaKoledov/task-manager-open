package com.taskmanager.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.taskmanager.android.model.AppPreferences
import com.taskmanager.android.model.NewTaskPlacement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onBack: () -> Unit,
    onSaveBaseUrl: (String) -> Unit,
    onSetShowCompleted: (Boolean) -> Unit,
    onSetNewTaskPlacement: (NewTaskPlacement) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    var baseUrl by remember(preferences.baseUrl) { mutableStateOf(preferences.baseUrl) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API base URL", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Use http://10.0.2.2/api/ for the Android emulator against docker compose, or replace 10.0.2.2 with your machine LAN IP on a physical device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Button(onClick = { onSaveBaseUrl(baseUrl) }) {
                        Text("Save URL")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show completed", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Hide or show completed sections and done tasks throughout the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.showCompleted,
                    onCheckedChange = onSetShowCompleted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("New task placement", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.newTaskPlacement == NewTaskPlacement.START,
                        onClick = { onSetNewTaskPlacement(NewTaskPlacement.START) },
                        label = { Text("Beginning") },
                    )
                    FilterChip(
                        selected = preferences.newTaskPlacement == NewTaskPlacement.END,
                        onClick = { onSetNewTaskPlacement(NewTaskPlacement.END) },
                        label = { Text("End") },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Manage daily reminders and device notification delivery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Button(onClick = onOpenNotifications) {
                    Text("Notifications")
                }
            }
        }
    }
}
