package com.esusic.recoverytracker.scoring

import kotlin.math.ln

data class VitalAgeInputs(
    val avgHrvMillis: Double? = null,
    val avgRestingHr: Double? = null,
    val avgSleepMinutes: Double? = null,
    val avgSteps: Double? = null,
    val avgStrain: Double? = null
)

data class VitalAgeComponent(val offsetYears: Double, val weight: Double)

data class VitalAgeResult(
    val vitalAge: Double,
    val chronologicalAge: Int,
    val hrv: VitalAgeComponent?,
    val rhr: VitalAgeComponent?,
    val sleep: VitalAgeComponent?,
    val activity: VitalAgeComponent?
)

object VitalAgeCalculator {

    private enum class Component(val baseWeight: Double) {
        HRV(0.40), RHR(0.25), SLEEP(0.20), ACTIVITY(0.15)
    }

    // Log-linear fit through Umetani et al. (1998) population RMSSD-by-age norms
    // (~45 ms in the mid-20s down to ~25 ms in the mid-60s).
    private const val HRV_LN_INTERCEPT = 4.18
    private const val HRV_DECAY_PER_YEAR = 0.0148

    fun calculate(chronologicalAge: Int, inputs: VitalAgeInputs): VitalAgeResult? {
        val offsets = buildMap {
            hrvOffset(inputs.avgHrvMillis, chronologicalAge)?.let { put(Component.HRV, it) }
            rhrOffset(inputs.avgRestingHr)?.let { put(Component.RHR, it) }
            sleepOffset(inputs.avgSleepMinutes)?.let { put(Component.SLEEP, it) }
            activityOffset(inputs.avgSteps, inputs.avgStrain)?.let { put(Component.ACTIVITY, it) }
        }
        if (offsets.size < 2) return null

        val totalWeight = offsets.keys.sumOf { it.baseWeight }
        val components = offsets.mapValues { (component, offset) ->
            VitalAgeComponent(offset, component.baseWeight / totalWeight)
        }
        val vitalAge = (chronologicalAge + components.values.sumOf { it.offsetYears * it.weight })
            .coerceAtLeast(18.0)

        return VitalAgeResult(
            vitalAge = vitalAge,
            chronologicalAge = chronologicalAge,
            hrv = components[Component.HRV],
            rhr = components[Component.RHR],
            sleep = components[Component.SLEEP],
            activity = components[Component.ACTIVITY]
        )
    }

    private fun hrvOffset(rmssdMillis: Double?, chronologicalAge: Int): Double? {
        if (rmssdMillis == null || rmssdMillis <= 0.0) return null
        val hrvAge = ((HRV_LN_INTERCEPT - ln(rmssdMillis)) / HRV_DECAY_PER_YEAR).coerceIn(18.0, 80.0)
        return (hrvAge - chronologicalAge).coerceIn(-10.0, 10.0)
    }

    private fun rhrOffset(restingHr: Double?): Double? =
        restingHr?.let { ((it - 62.0) * 0.4).coerceIn(-10.0, 10.0) }

    private fun sleepOffset(minutes: Double?): Double? = minutes?.let { m ->
        when {
            m in 420.0..510.0 -> -1.0
            m < 420.0 -> (420.0 - m) / 30.0 * 1.5
            else -> (m - 510.0) / 30.0 * 0.75
        }.coerceIn(-1.0, 8.0)
    }

    private fun activityOffset(steps: Double?, strain: Double?): Double? {
        val stepsOffset = steps?.let { ((7000.0 - it) / 2000.0 * 2.0).coerceIn(-5.0, 6.0) }
        val strainOffset = strain?.let { ((8.0 - it) * 0.4).coerceIn(-3.0, 3.0) }
        return when {
            stepsOffset != null && strainOffset != null -> 0.6 * stepsOffset + 0.4 * strainOffset
            else -> stepsOffset ?: strainOffset
        }
    }
}
