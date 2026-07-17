package com.esusic.recoverytracker.scoring

import kotlin.math.ln

data class VitalAgeInputs(
    val avgHrvMillis: Double? = null,
    val avgRestingHr: Double? = null,
    val avgSleepMinutes: Double? = null,
    val avgSteps: Double? = null,
    val avgStrain: Double? = null,
    val maxHrBpm: Double? = null
)

data class VitalAgeComponent(val offsetYears: Double, val weight: Double)

data class VitalAgeResult(
    val vitalAge: Double,
    val chronologicalAge: Int,
    val fitness: VitalAgeComponent?,
    val hrv: VitalAgeComponent?,
    val rhr: VitalAgeComponent?,
    val sleep: VitalAgeComponent?,
    val activity: VitalAgeComponent?
)

object VitalAgeCalculator {

    // RHR's base weight is low because resting HR also drives the VO2max estimate.
    private enum class Component(val baseWeight: Double) {
        FITNESS(0.35), HRV(0.25), RHR(0.15), SLEEP(0.15), ACTIVITY(0.10)
    }

    // Log-linear fit through Umetani et al. (1998) population RMSSD-by-age norms
    // (~45 ms in the mid-20s down to ~25 ms in the mid-60s).
    private const val HRV_LN_INTERCEPT = 4.18
    private const val HRV_DECAY_PER_YEAR = 0.0148

    // Uth et al. (2004): VO2max ≈ 15.3 × maxHR / restingHR. Fitness age from a linear
    // fit through male VO2max-by-age norms (~47.5 ml/kg/min at 25 down to ~33.5 at 60).
    private const val VO2_FACTOR = 15.3
    private const val VO2_AGE_INTERCEPT = 57.5
    private const val VO2_DECLINE_PER_YEAR = 0.4

    fun calculate(chronologicalAge: Int, inputs: VitalAgeInputs): VitalAgeResult? {
        val offsets = buildMap {
            fitnessOffset(inputs.maxHrBpm, inputs.avgRestingHr, chronologicalAge)?.let { put(Component.FITNESS, it) }
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
            fitness = components[Component.FITNESS],
            hrv = components[Component.HRV],
            rhr = components[Component.RHR],
            sleep = components[Component.SLEEP],
            activity = components[Component.ACTIVITY]
        )
    }

    private fun fitnessOffset(maxHrBpm: Double?, restingHr: Double?, chronologicalAge: Int): Double? {
        if (maxHrBpm == null || restingHr == null || restingHr <= 0.0) return null
        val vo2Max = VO2_FACTOR * maxHrBpm / restingHr
        val fitnessAge = ((VO2_AGE_INTERCEPT - vo2Max) / VO2_DECLINE_PER_YEAR).coerceIn(18.0, 80.0)
        return (fitnessAge - chronologicalAge).coerceIn(-15.0, 15.0)
    }

    private fun hrvOffset(rmssdMillis: Double?, chronologicalAge: Int): Double? {
        if (rmssdMillis == null || rmssdMillis <= 0.0) return null
        val hrvAge = ((HRV_LN_INTERCEPT - ln(rmssdMillis)) / HRV_DECAY_PER_YEAR).coerceIn(18.0, 80.0)
        // Bonus capped at -4 so an excellent HRV can't mask poor fitness or resting HR.
        return (hrvAge - chronologicalAge).coerceIn(-4.0, 10.0)
    }

    private fun rhrOffset(restingHr: Double?): Double? =
        restingHr?.let { ((it - 60.0) * 0.5).coerceIn(-10.0, 10.0) }

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
