package com.esusic.recoverytracker.ui.account

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ServiceLocator
import com.esusic.recoverytracker.export.CsvExporter
import com.esusic.recoverytracker.export.HealthDataExporter
import com.esusic.recoverytracker.sync.RecoveryNotifications
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val trackedCategories = listOf(
    R.string.account_category_heart_rate,
    R.string.account_category_hrv,
    R.string.account_category_resting_heart_rate,
    R.string.account_category_sleep,
    R.string.account_category_steps,
    R.string.account_category_active_calories,
    R.string.account_category_exercise,
    R.string.account_category_respiratory_rate,
    R.string.account_category_blood_oxygen,
    R.string.account_category_skin_temperature
)

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onOpenAge: () -> Unit,
    contentBottomPadding: Dp = 0.dp
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBirthYearDialog by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<Int?>(null) }
    var notificationsEnabled by remember { mutableStateOf(RecoveryNotifications.canPost(context)) }
    val healthSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { notificationsEnabled = RecoveryNotifications.canPost(context) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri == null) {
            exportStatus = null
            return@rememberLauncherForActivityResult
        }
        exportStatus = R.string.account_export_in_progress
        scope.launch {
            exportStatus = try {
                withContext(Dispatchers.IO) {
                    val csv = HealthDataExporter(ServiceLocator.repository).buildCsv()
                    CsvExporter.writeTo(context.contentResolver, uri, csv)
                }
                R.string.account_export_saved
            } catch (e: Exception) {
                R.string.account_export_failed
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
        notificationsEnabled = RecoveryNotifications.canPost(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 24.dp + contentBottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RootPageHeader(
            title = stringResource(R.string.account_title),
            subtitle = stringResource(R.string.account_subtitle)
        )

        SectionCard(title = stringResource(R.string.account_profile_title)) {
            AccountValueRow(
                label = stringResource(R.string.account_birth_year),
                value = state.birthYear?.toString() ?: stringResource(R.string.account_not_set)
            )
            AccountValueRow(
                label = stringResource(R.string.account_chronological_age),
                value = state.age?.let { pluralStringResource(R.plurals.account_age_years, it, it) }
                    ?: stringResource(R.string.account_not_available)
            )
            OutlinedButton(
                onClick = { showBirthYearDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.birthYear == null) {
                        stringResource(R.string.account_add_birth_year)
                    } else {
                        stringResource(R.string.account_edit_birth_year)
                    }
                )
            }
        }

        val openVitalAge = stringResource(R.string.account_open_vital_age)
        SectionCard(
            title = stringResource(R.string.account_vital_age_title),
            modifier = Modifier
                .semantics { contentDescription = openVitalAge }
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
                        stringResource(R.string.account_vital_age_headline),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.account_vital_age_body),
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

        SectionCard(title = stringResource(R.string.account_health_connect_title)) {
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
                        if (state.healthConnectConnected) {
                            stringResource(R.string.account_health_connect_connected)
                        } else {
                            stringResource(R.string.account_health_connect_needs_attention)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill(
                    label = if (state.healthConnectConnected) {
                        stringResource(R.string.account_health_connect_ready)
                    } else {
                        stringResource(R.string.account_health_connect_review)
                    },
                    color = if (state.healthConnectConnected) AppColors.Recovery else AppColors.Warning
                )
            }
            Text(
                if (state.healthConnectConnected) {
                    stringResource(R.string.account_health_connect_connected_body)
                } else {
                    stringResource(R.string.account_health_connect_review_body)
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Recovery,
                    contentColor = AppColors.Canvas
                )
            ) {
                Text(
                    stringResource(R.string.account_manage_health_connect),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SectionCard(title = stringResource(R.string.account_notifications_title)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.account_morning_summary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusPill(
                    label = if (notificationsEnabled) {
                        stringResource(R.string.account_notifications_on)
                    } else {
                        stringResource(R.string.account_notifications_off)
                    },
                    color = if (notificationsEnabled) AppColors.Recovery else AppColors.Warning
                )
            }
            Text(
                stringResource(R.string.account_morning_summary_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            if (!notificationsEnabled) {
                OutlinedButton(
                    onClick = {
                        // A runtime request is a no-op once the user has blocked the channel,
                        // so send them where the decision can actually be reversed.
                        notificationSettingsLauncher.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.account_enable_notifications))
                }
            }
        }

        SectionCard(title = stringResource(R.string.account_data_used_title)) {
            Text(
                stringResource(R.string.account_data_used_body),
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

        SectionCard(title = stringResource(R.string.account_privacy_title)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = AppColors.Hrv)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.account_privacy_headline),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.account_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.account_your_data_title)) {
            Text(
                stringResource(R.string.account_export_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            OutlinedButton(
                onClick = {
                    exportStatus = null
                    exportLauncher.launch(
                        CsvExporter.createDocumentIntent(
                            CsvExporter.defaultFileName(LocalDate.now())
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.account_export_button))
            }
            exportStatus?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it == R.string.account_export_failed) {
                        AppColors.Danger
                    } else {
                        AppColors.Recovery
                    }
                )
            }
        }

        HorizontalDivider(color = AppColors.GridLine)
        Text(
            stringResource(R.string.account_version),
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
private fun DataCategory(@StringRes label: Int, modifier: Modifier = Modifier) {
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
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )
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
        title = { Text(stringResource(R.string.account_birth_year)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.account_year_label)) },
                supportingText = {
                    Text(
                        if (input.isNotEmpty() && !isValid) {
                            stringResource(R.string.account_year_error, LocalDate.now().year)
                        } else {
                            stringResource(R.string.account_year_help)
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
                Text(stringResource(R.string.account_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_cancel)) }
        }
    )
}
