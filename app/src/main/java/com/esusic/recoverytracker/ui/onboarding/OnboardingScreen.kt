package com.esusic.recoverytracker.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.data.healthconnect.HealthConnectAvailability
import com.esusic.recoverytracker.data.healthconnect.HealthConnectDataSource
import com.esusic.recoverytracker.data.healthconnect.checkHealthConnectAvailability
import com.esusic.recoverytracker.sync.RecoveryNotifications
import com.esusic.recoverytracker.sync.SyncScheduler
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

private const val EARLIEST_BIRTH_YEAR = 1900

private val readCategories = listOf(
    R.string.onboarding_category_heart_rate_hrv to AppColors.Hrv,
    R.string.onboarding_category_resting_heart_rate to AppColors.Rhr,
    R.string.onboarding_category_sleep to AppColors.SleepViolet,
    R.string.onboarding_category_activity to AppColors.ActivityCyan
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val availability = remember { checkHealthConnectAvailability(context) }

    if (availability != HealthConnectAvailability.Available) {
        HealthConnectMissing(
            updateRequired = availability == HealthConnectAvailability.UpdateRequired,
            onOpenStore = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                )
            }
        )
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

    val currentYear = remember { LocalDate.now().year }
    val enteredYear = birthYearInput.toIntOrNull()
    val birthYearValid = enteredYear != null && enteredYear in EARLIEST_BIRTH_YEAR..currentYear
    val birthYearError = birthYearInput.length == 4 && !birthYearValid

    OnboardingScaffold {
        Hero()

        SectionCard(
            title = stringResource(R.string.onboarding_reads_title),
            accent = AppColors.Recovery,
            glowAlpha = 0.14f
        ) {
            Text(
                stringResource(R.string.onboarding_reads_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            readCategories.forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.onboarding_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                StatusPill(
                    label = if (uiState.hasAllPermissions) {
                        stringResource(R.string.onboarding_access_granted)
                    } else {
                        stringResource(R.string.onboarding_access_not_granted)
                    },
                    color = if (uiState.hasAllPermissions) AppColors.Recovery else AppColors.Warning
                )
            }
            if (!uiState.hasAllPermissions) {
                Button(
                    onClick = {
                        permissionLauncher.launch(HealthConnectDataSource.REQUIRED_PERMISSIONS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Recovery,
                        contentColor = AppColors.Canvas
                    )
                ) {
                    Text(
                        stringResource(R.string.onboarding_grant_access),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionCard(
            title = stringResource(R.string.onboarding_about_you_title),
            accent = AppColors.VitalAge,
            glowAlpha = 0.14f
        ) {
            Text(
                stringResource(R.string.onboarding_about_you_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            OutlinedTextField(
                value = birthYearInput,
                onValueChange = { birthYearInput = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.onboarding_birth_year)) },
                singleLine = true,
                isError = birthYearError,
                supportingText = if (birthYearError) {
                    {
                        Text(
                            stringResource(
                                R.string.onboarding_birth_year_error,
                                EARLIEST_BIRTH_YEAR,
                                currentYear
                            )
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.VitalAge,
                    unfocusedBorderColor = AppColors.GridLine,
                    focusedLabelColor = AppColors.VitalAge,
                    cursorColor = AppColors.VitalAge
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { enteredYear?.let(viewModel::onBirthYearEntered) },
                enabled = birthYearValid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.VitalAge,
                    contentColor = AppColors.Canvas,
                    disabledContainerColor = AppColors.SurfaceElevated,
                    disabledContentColor = AppColors.TextMuted
                )
            ) {
                Text(stringResource(R.string.onboarding_continue), fontWeight = FontWeight.Bold)
            }
        }

        MorningSummaryOptIn()

        Text(
            stringResource(R.string.onboarding_calibration_note),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextMuted
        )
    }
}

// Notifications are opt-in from Android 13 on. Asked here rather than at first fire, so the
// prompt arrives while the user is already deciding what Pulsar may do.
@Composable
private fun MorningSummaryOptIn() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = androidx.compose.ui.platform.LocalContext.current
    var asked by remember { mutableStateOf(RecoveryNotifications.canPost(context)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { asked = true }

    SectionCard(
        title = stringResource(R.string.onboarding_morning_summary_title),
        accent = AppColors.ActivityCyan,
        glowAlpha = 0.12f
    ) {
        Text(
            stringResource(R.string.onboarding_morning_summary_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
        if (asked) {
            StatusPill(
                stringResource(R.string.onboarding_notifications_enabled),
                AppColors.ActivityCyan
            )
        } else {
            Button(
                onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.ActivityCyan,
                    contentColor = AppColors.Canvas
                )
            ) {
                Text(
                    stringResource(R.string.onboarding_allow_notifications),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Hero() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.ExtraBold,
            color = AppColors.Recovery
        )
        Text(
            stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun HealthConnectMissing(updateRequired: Boolean, onOpenStore: () -> Unit) {
    OnboardingScaffold {
        Hero()
        SectionCard(
            title = stringResource(R.string.onboarding_health_connect_required),
            accent = AppColors.Warning,
            glowAlpha = 0.16f
        ) {
            Text(
                if (updateRequired) {
                    stringResource(R.string.onboarding_health_connect_update)
                } else {
                    stringResource(R.string.onboarding_health_connect_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            Button(
                onClick = onOpenStore,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Warning,
                    contentColor = AppColors.Canvas
                )
            ) {
                Text(
                    stringResource(R.string.onboarding_open_play_store),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnboardingScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        content()
    }
}
