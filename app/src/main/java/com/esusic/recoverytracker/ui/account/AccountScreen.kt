package com.esusic.recoverytracker.ui.account

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.LocalDate

private val trackedCategories = listOf(
    "Heart rate",
    "Heart rate variability",
    "Resting heart rate",
    "Sleep",
    "Steps",
    "Active calories",
    "Exercise",
    "Respiratory rate",
    "Blood oxygen",
    "Skin temperature"
)

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onOpenAge: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showBirthYearDialog by remember { mutableStateOf(false) }
    val healthSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RootPageHeader(title = "Account", subtitle = "Local profile")

        SectionCard(title = "Profile") {
            AccountValueRow(
                label = "Birth year",
                value = state.birthYear?.toString() ?: "Not set"
            )
            AccountValueRow(
                label = "Chronological age",
                value = state.age?.let { "$it years" } ?: "Not available"
            )
            OutlinedButton(
                onClick = { showBirthYearDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.birthYear == null) "Add birth year" else "Edit birth year")
            }
        }

        SectionCard(
            title = "Vital Age",
            modifier = Modifier
                .semantics { contentDescription = "Open Vital Age" }
                .clickable(onClick = onOpenAge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Your long-term health trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "See how recent recovery signals compare with your age.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = AppColors.VitalAge
                )
            }
        }

        SectionCard(title = "Health Connect") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (state.healthConnectConnected) Icons.Default.CheckCircle else Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = if (state.healthConnectConnected) AppColors.Recovery else AppColors.Warning
                    )
                    Text(
                        if (state.healthConnectConnected) "Connected" else "Needs attention",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill(
                    label = if (state.healthConnectConnected) "Ready" else "Review",
                    color = if (state.healthConnectConnected) AppColors.Recovery else AppColors.Warning
                )
            }
            Text(
                if (state.healthConnectConnected) {
                    "Recovery Tracker can read the selected health categories."
                } else {
                    "Review Health Connect access to keep your dashboard current."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            Button(
                onClick = {
                    healthSettingsLauncher.launch(
                        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Health Connect")
            }
        }

        SectionCard(title = "Data used") {
            Text(
                "Only categories used by your scores and trends are requested.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            trackedCategories.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { category ->
                        DataCategory(category, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        SectionCard(title = "Privacy") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AppColors.Hrv)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Processed locally",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your health data is read from Health Connect and processed on this device. No account or cloud sync is required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }

        HorizontalDivider(color = AppColors.GridLine)
        Text(
            "Recovery Tracker  ·  Version 1.0",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(4.dp))
    }

    if (showBirthYearDialog) {
        BirthYearDialog(
            currentYear = state.birthYear,
            onDismiss = { showBirthYearDialog = false },
            onSave = {
                viewModel.saveBirthYear(it)
                showBirthYearDialog = false
            }
        )
    }
}

@Composable
private fun AccountValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DataCategory(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AppColors.Hrv,
            modifier = Modifier.height(16.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun BirthYearDialog(
    currentYear: Int?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var input by remember(currentYear) { mutableStateOf(currentYear?.toString().orEmpty()) }
    val parsed = input.toIntOrNull()
    val validRange = 1900..LocalDate.now().year
    val isValid = parsed in validRange

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Birth year") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = { Text("Year") },
                supportingText = {
                    Text(
                        if (input.isNotEmpty() && !isValid) {
                            "Enter a year between 1900 and ${LocalDate.now().year}."
                        } else {
                            "Used only to calculate age-based context."
                        }
                    )
                },
                isError = input.isNotEmpty() && !isValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
