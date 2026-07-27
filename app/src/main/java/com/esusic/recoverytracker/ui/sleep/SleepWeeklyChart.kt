package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.common.currentLocale
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.format.TextStyle

@Composable
fun SleepWeeklyChart(nights: List<SleepNightPoint>, modifier: Modifier = Modifier) {
    val maxMinutes = nights.maxOfOrNull { it.durationMinutes }?.coerceAtLeast(1L) ?: 1L
    val locale = currentLocale()
    Row(
        modifier = modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        nights.forEach { night ->
            val barHeight = (88f * night.durationMinutes / maxMinutes).dp
            val day = night.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
            val spokenNight = stringResource(
                R.string.sleep_week_bar_description,
                day,
                night.durationMinutes / 60,
                night.durationMinutes % 60
            )
            Column(
                modifier = Modifier.semantics { contentDescription = spokenNight },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier.width(22.dp).height(barHeight)
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .background(AppColors.SleepViolet)
                )
                Text(day, color = AppColors.TextSecondary)
            }
        }
    }
}
