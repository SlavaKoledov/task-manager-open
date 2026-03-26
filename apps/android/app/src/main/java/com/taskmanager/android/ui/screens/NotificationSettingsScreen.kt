package com.taskmanager.android.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.taskmanager.android.data.notifications.TaskNotificationManager
import com.taskmanager.android.model.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    preferences: AppPreferences,
    notificationManager: TaskNotificationManager,
    onBack: () -> Unit,
    onSetDailyNotificationEnabled: (Boolean) -> Unit,
    onSetDailyNotificationTime: (String) -> Unit,
) {
    val context = LocalContext.current
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationsPermissionGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionMessage = null
            onSetDailyNotificationEnabled(true)
        } else {
            permissionMessage = "Notifications stay off until permission is allowed."
            onSetDailyNotificationEnabled(false)
        }
    }

    if (showTimePicker) {
        val (hour, minute) = preferences.dailyNotificationTime
            .split(":")
            .let { parts -> (parts.getOrNull(0)?.toIntOrNull() ?: 9) to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
        DisposableEffect(showTimePicker) {
            val dialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    onSetDailyNotificationTime("%02d:%02d".format(selectedHour, selectedMinute))
                    showTimePicker = false
                },
                hour,
                minute,
                true,
            )
            dialog.setOnDismissListener { showTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifications") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily notification", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Shows a summary of overdue and today tasks using the device's local time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.dailyNotificationEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            permissionMessage = null
                            onSetDailyNotificationEnabled(false)
                        } else if (notificationsPermissionGranted) {
                            permissionMessage = null
                            onSetDailyNotificationEnabled(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daily summary time", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Notifications are scheduled in the current local timezone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = preferences.dailyNotificationEnabled,
                    onClick = { showTimePicker = true },
                ) {
                    Text(preferences.dailyNotificationTime)
                }
            }

            if (Build.VERSION.SDK_INT >= 31 && !notificationManager.canScheduleExactAlarms()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Precise timing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Android can delay reminders until Alarms & reminders access is allowed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = notificationManager::openExactAlarmSettings) {
                        Text("Allow precise alarms")
                    }
                }
            }

            if (!notificationsPermissionGranted) {
                Text(
                    text = permissionMessage ?: "Allow notifications to deliver task reminders on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
