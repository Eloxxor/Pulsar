package com.esusic.recoverytracker.ui.vitalage

import com.esusic.recoverytracker.scoring.VitalAgeComponent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VitalAgeComponentPresentationTest {
    @Test
    fun `long cardio label keeps metadata separate from impact`() {
        val presentation = vitalAgeComponentPresentation(
            label = "Cardio fitness (est. VO2max)",
            component = VitalAgeComponent(
                rawOffsetYears = 15.0,
                impactYears = 5.3,
                weight = 0.35,
                confidence = 6.0 / 7.0
            )
        )

        assertEquals("Cardio fitness (est. VO2max)", presentation.label)
        assertEquals("35% weight · 86% confidence", presentation.metadata)
        assertEquals(String.format(Locale.getDefault(), "%+.1f yr impact", 5.3), presentation.impact)
    }
}
