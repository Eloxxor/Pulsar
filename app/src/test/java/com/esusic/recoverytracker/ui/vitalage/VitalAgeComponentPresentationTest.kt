package com.esusic.recoverytracker.ui.vitalage

import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.scoring.VitalAgeComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VitalAgeComponentPresentationTest {
    @Test
    fun `long cardio label keeps metadata separate from impact`() {
        val presentation = vitalAgeComponentPresentation(
            label = R.string.vital_age_component_fitness,
            component = VitalAgeComponent(
                rawOffsetYears = 15.0,
                impactYears = 5.3,
                weight = 0.35,
                confidence = 6.0 / 7.0
            )
        )

        assertEquals(R.string.vital_age_component_fitness, presentation.label)
        assertEquals(35, presentation.weightPercent)
        assertEquals(86, presentation.confidencePercent)
        assertEquals(5.3, presentation.impactYears!!, 0.0001)
    }

    @Test
    fun `missing component leaves metadata and impact unset`() {
        val presentation = vitalAgeComponentPresentation(
            label = R.string.vital_age_component_hrv,
            component = null
        )

        assertEquals(R.string.vital_age_component_hrv, presentation.label)
        assertNull(presentation.weightPercent)
        assertNull(presentation.confidencePercent)
        assertNull(presentation.impactYears)
    }
}
