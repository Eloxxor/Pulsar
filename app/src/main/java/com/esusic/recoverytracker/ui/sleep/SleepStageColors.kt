package com.esusic.recoverytracker.ui.sleep

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.ui.theme.AppColors

fun stageColor(stage: SleepStageType): Color = when (stage) {
    SleepStageType.DEEP -> AppColors.StageDeep
    SleepStageType.REM -> AppColors.StageRem
    SleepStageType.LIGHT -> AppColors.StageLight
    SleepStageType.AWAKE -> AppColors.StageAwake
    SleepStageType.UNKNOWN -> AppColors.StageUnknown
}

@StringRes
fun stageLabel(stage: SleepStageType): Int = when (stage) {
    SleepStageType.DEEP -> R.string.sleep_stage_deep
    SleepStageType.REM -> R.string.sleep_stage_rem
    SleepStageType.LIGHT -> R.string.sleep_stage_light
    SleepStageType.AWAKE -> R.string.sleep_stage_awake
    SleepStageType.UNKNOWN -> R.string.sleep_stage_unknown
}
