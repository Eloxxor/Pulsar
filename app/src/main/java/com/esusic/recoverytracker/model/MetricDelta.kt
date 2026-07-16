package com.esusic.recoverytracker.model

import kotlin.math.abs

enum class DeltaKind { BETTER, WORSE, NEUTRAL }

data class MetricDelta(val diff: Double, val kind: DeltaKind)

fun metricDelta(
    current: Double?,
    baseline: Double?,
    higherIsBetter: Boolean?,
    neutralBandFraction: Double = 0.03
): MetricDelta? {
    if (current == null || baseline == null) return null
    val diff = current - baseline
    val kind = when {
        higherIsBetter == null -> DeltaKind.NEUTRAL
        abs(diff) <= abs(baseline) * neutralBandFraction -> DeltaKind.NEUTRAL
        (diff > 0) == higherIsBetter -> DeltaKind.BETTER
        else -> DeltaKind.WORSE
    }
    return MetricDelta(diff, kind)
}
