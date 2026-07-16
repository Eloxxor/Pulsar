package com.esusic.recoverytracker.ui.sleep

import androidx.compose.ui.graphics.Color
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.ui.theme.AppColors

fun stageColor(stage: SleepStageType): Color = when (stage) {
    SleepStageType.DEEP -> AppColors.StageDeep
    SleepStageType.REM -> AppColors.StageRem
    SleepStageType.LIGHT -> AppColors.StageLight
    SleepStageType.AWAKE -> AppColors.StageAwake
    SleepStageType.UNKNOWN -> AppColors.StageUnknown
}

fun stageLabel(stage: SleepStageType): String = when (stage) {
    SleepStageType.DEEP -> "Deep"
    SleepStageType.REM -> "REM"
    SleepStageType.LIGHT -> "Light"
    SleepStageType.AWAKE -> "Awake"
    SleepStageType.UNKNOWN -> "Unknown"
}
