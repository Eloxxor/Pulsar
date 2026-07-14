package com.esusic.recoverytracker.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.esusic.recoverytracker.data.healthconnect.HealthConnectAvailability
import com.esusic.recoverytracker.data.healthconnect.HealthConnectDataSource
import com.esusic.recoverytracker.data.healthconnect.checkHealthConnectAvailability
import com.esusic.recoverytracker.sync.SyncScheduler
import kotlinx.coroutines.launch

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val availability = remember { checkHealthConnectAvailability(context) }

    if (availability != HealthConnectAvailability.Available) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Health Connect required", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (availability == HealthConnectAvailability.UpdateRequired) {
                    "Health Connect needs an update before this app can read your Fitbit data."
                } else {
                    "This app reads your Fitbit data through Health Connect, which isn't installed yet."
                }
            )
            Button(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                )
            }) {
                Text("Open Play Store")
            }
        }
        return
    }

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var birthYearInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch { viewModel.refreshPermissionStatus() }
    }

    LaunchedEffect(Unit) { viewModel.refreshPermissionStatus() }

    LaunchedEffect(uiState.hasAllPermissions, uiState.birthYear) {
        if (uiState.hasAllPermissions && uiState.birthYear != null) {
            SyncScheduler.schedulePeriodic(context)
            SyncScheduler.triggerImmediateSync(context)
            onFinished()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome to Recovery Tracker", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This app reads heart rate, HRV, resting heart rate, sleep, steps, active calories, " +
                "and exercise sessions from Health Connect to compute a daily recovery score and a " +
                "live strain score. Scores need about 1-2 weeks of data to calibrate to you."
        )

        if (!uiState.hasAllPermissions) {
            Button(onClick = { permissionLauncher.launch(HealthConnectDataSource.REQUIRED_PERMISSIONS) }) {
                Text("Grant Health Connect permissions")
            }
        }

        OutlinedTextField(
            value = birthYearInput,
            onValueChange = { birthYearInput = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("Birth year (used to estimate max heart rate)") }
        )
        Button(
            onClick = { birthYearInput.toIntOrNull()?.let { viewModel.onBirthYearEntered(it) } },
            enabled = birthYearInput.length == 4
        ) {
            Text("Continue")
        }
    }
}
