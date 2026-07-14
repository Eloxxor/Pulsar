package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SleepScreen(viewModel: SleepViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sleep", style = MaterialTheme.typography.headlineMedium)

        if (uiState.totalMinutes == null) {
            Text("No sleep data synced yet.")
            return@Column
        }

        Text("${uiState.totalMinutes!! / 60}h ${uiState.totalMinutes!! % 60}m total", style = MaterialTheme.typography.headlineSmall)

        uiState.typicalTotalMinutes?.let {
            val diff = uiState.totalMinutes!! - it
            val comparison = if (diff >= 0) "above" else "below"
            Text("${kotlin.math.abs(diff).toInt()} min $comparison your typical sleep duration")
        }

        listOf(
            Triple("Deep", uiState.deepPercent, uiState.deepMinutes),
            Triple("REM", uiState.remPercent, uiState.remMinutes),
            Triple("Light", uiState.lightPercent, uiState.lightMinutes)
        ).forEach { (label, percent, minutes) ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text("${minutes}m")
                }
                LinearProgressIndicator(progress = { percent }, modifier = Modifier.fillMaxWidth().height(8.dp))
            }
        }
    }
}
