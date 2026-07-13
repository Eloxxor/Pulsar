package com.esusic.recoverytracker.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

sealed interface HealthConnectAvailability {
    data object Available : HealthConnectAvailability
    data object NotInstalled : HealthConnectAvailability
    data object UpdateRequired : HealthConnectAvailability
}

fun checkHealthConnectAvailability(context: Context): HealthConnectAvailability =
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UpdateRequired
        else -> HealthConnectAvailability.NotInstalled
    }
