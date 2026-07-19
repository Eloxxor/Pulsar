package com.esusic.recoverytracker.scoring

import kotlin.math.abs
import kotlin.math.ln

data class VitalAgeInputs(
    val avgHrvMillis: Double? = null,
    val hrvSampleDays: Int = 7,
    val avgRestingHr: Double? = null,
    val restingHrSampleDays: Int = 7,
    val avgSleepMinutes: Double? = null,
    val sleepSampleDays: Int = 7,
    val avgSteps: Double? = null,
    val stepsSampleDays: Int = 7,
    val avgStrain: Double? = null,
    val strainSampleDays: Int = 7,
    val maxHrBpm: Double? = null
)

data class VitalAgeComponent(
    val rawOffsetYears: Double,
    val impactYears: Double,
    val weight: Double,
    val confidence: Double
)

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

    private enum class Component(val baseWeight: Double) {
        FITNESS(0.35),
        HRV(0.25),
        RHR(0.10),
        SLEEP(0.15),
        ACTIVITY(0.15)
    }

    private data class Candidate(
        val component: Component,
        val rawOffset: Double,
        val confidence: Double
    )

    private data class WeightedCandidate(
        val candidate: Candidate,
        val weight: Double,
        val impact: Double
    )

    private const val HRV_LN_INTERCEPT = 4.18
    private const val HRV_DECAY_PER_YEAR = 0.0148
    private const val VO2_FACTOR = 15.3
    private const val VO2_AGE_INTERCEPT = 57.5
    private const val VO2_DECLINE_PER_YEAR = 0.4
    private const val MIN_SAMPLE_DAYS = 3
    private const val FULL_CONFIDENCE_DAYS = 7.0
    private const val MAX_TOTAL_DELTA = 10.0

    fun calculate(chronologicalAge: Int, inputs: VitalAgeInputs): VitalAgeResult? {
        if (chronologicalAge < 18) return null

        val restingConfidence = confidence(inputs.restingHrSampleDays)
        val candidates = buildList {
            if (restingConfidence != null) {
                fitnessOffset(
                    inputs.maxHrBpm,
                    inputs.avgRestingHr,
                    chronologicalAge
                )?.let { add(Candidate(Component.FITNESS, it, restingConfidence)) }
                rhrOffset(inputs.avgRestingHr)?.let {
                    add(Candidate(Component.RHR, it, restingConfidence))
                }
            }
            confidence(inputs.hrvSampleDays)?.let { signalConfidence ->
                hrvOffset(inputs.avgHrvMillis, chronologicalAge)?.let {
                    add(Candidate(Component.HRV, it, signalConfidence))
                }
            }
            confidence(inputs.sleepSampleDays)?.let { signalConfidence ->
                sleepOffset(inputs.avgSleepMinutes)?.let {
                    add(Candidate(Component.SLEEP, it, signalConfidence))
                }
            }
            activityCandidate(inputs)?.let { add(it) }
        }
        if (candidates.size < 2) return null

        val effectiveTotal = candidates.sumOf {
            it.component.baseWeight * it.confidence
        }
        val preliminary = candidates.map { candidate ->
            val weight = candidate.component.baseWeight * candidate.confidence / effectiveTotal
            WeightedCandidate(candidate, weight, candidate.rawOffset * weight)
        }
        val rawDelta = preliminary.sumOf { it.impact }
        val boundedDelta = rawDelta
            .coerceIn(-MAX_TOTAL_DELTA, MAX_TOTAL_DELTA)
            .coerceAtLeast(18.0 - chronologicalAge)
        val scale = if (abs(rawDelta) < 1e-12) 1.0 else boundedDelta / rawDelta
        val components = preliminary.associate { weighted ->
            weighted.candidate.component to VitalAgeComponent(
                rawOffsetYears = weighted.candidate.rawOffset,
                impactYears = weighted.impact * scale,
                weight = weighted.weight,
                confidence = weighted.candidate.confidence
            )
        }
        val vitalAge = chronologicalAge + components.values.sumOf { it.impactYears }

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

    private fun confidence(sampleDays: Int): Double? = sampleDays
        .takeIf { it >= MIN_SAMPLE_DAYS }
        ?.div(FULL_CONFIDENCE_DAYS)
        ?.coerceAtMost(1.0)

    private fun activityCandidate(inputs: VitalAgeInputs): Candidate? {
        val stepsConfidence = inputs.avgSteps?.let { confidence(inputs.stepsSampleDays) }
        val strainConfidence = inputs.avgStrain?.let { confidence(inputs.strainSampleDays) }
        val eligibleSteps = inputs.avgSteps.takeIf { stepsConfidence != null }
        val eligibleStrain = inputs.avgStrain.takeIf { strainConfidence != null }
        val rawOffset = activityOffset(eligibleSteps, eligibleStrain) ?: return null
        val combinedConfidence = when {
            stepsConfidence != null && strainConfidence != null ->
                0.6 * stepsConfidence + 0.4 * strainConfidence
            stepsConfidence != null -> stepsConfidence
            strainConfidence != null -> strainConfidence
            else -> return null
        }
        return Candidate(Component.ACTIVITY, rawOffset, combinedConfidence)
    }

    private fun fitnessOffset(
        maxHrBpm: Double?,
        restingHr: Double?,
        chronologicalAge: Int
    ): Double? {
        if (maxHrBpm == null || restingHr == null || restingHr <= 0.0) return null
        val vo2Max = VO2_FACTOR * maxHrBpm / restingHr
        val fitnessAge = ((VO2_AGE_INTERCEPT - vo2Max) / VO2_DECLINE_PER_YEAR)
            .coerceIn(18.0, 80.0)
        return (fitnessAge - chronologicalAge).coerceIn(-15.0, 15.0)
    }

    private fun hrvOffset(rmssdMillis: Double?, chronologicalAge: Int): Double? {
        if (rmssdMillis == null || rmssdMillis <= 0.0) return null
        val hrvAge = ((HRV_LN_INTERCEPT - ln(rmssdMillis)) / HRV_DECAY_PER_YEAR)
            .coerceIn(18.0, 80.0)
        return (hrvAge - chronologicalAge).coerceIn(-4.0, 10.0)
    }

    private fun rhrOffset(restingHr: Double?): Double? =
        restingHr?.let { ((it - 60.0) * 0.5).coerceIn(-10.0, 10.0) }

    private fun sleepOffset(minutes: Double?): Double? = minutes?.let { value ->
        when {
            value in 420.0..510.0 -> -1.0
            value < 420.0 -> (420.0 - value) / 30.0 * 1.5
            else -> (value - 510.0) / 30.0 * 0.75
        }.coerceIn(-1.0, 8.0)
    }

    private fun activityOffset(steps: Double?, strain: Double?): Double? {
        val stepsOffset = steps?.let {
            ((7000.0 - it) / 2000.0 * 2.0).coerceIn(-5.0, 6.0)
        }
        val strainOffset = strain?.let { ((8.0 - it) * 0.4).coerceIn(-3.0, 3.0) }
        return when {
            stepsOffset != null && strainOffset != null ->
                0.6 * stepsOffset + 0.4 * strainOffset
            else -> stepsOffset ?: strainOffset
        }
    }
}
