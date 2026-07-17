# Weekly Vital Age + Sleep Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anchor the displayed Vital Age to Mondays, and make sleep performance honor a 7 h need floor plus a 30 % deep+REM quality share.

**Architecture:** Pure-function changes in `scoring/SleepPerformanceCalculator`, anchor-selection logic in `VitalAgeViewModel`, small copy/caption updates in `GaugeInfoSheets` and `VitalAgeScreen`. No DB changes.

**Tech Stack:** Kotlin, JUnit4 + Robolectric, Jetpack Compose.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-17-weekly-vital-age-and-sleep-quality-design.md` (formulas authoritative).
- `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"` before each Gradle call; commits via `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit`; work on `master`.

---

### Task 1: Sleep quality + 7 h floor

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculator.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt` (call site)
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/GaugeInfoSheets.kt` (sheet copy)
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculatorTest.kt`

**Interfaces:**
- Produces: `SleepPerformanceCalculator.calculate(actualSleepMinutes: Long?, typicalSleepMinutes: Double, priorDayStrain: StrainScore?, accumulatedDebtMinutes: Double, deepRemMinutes: Long? = null): Int?`

- [ ] **Step 1: Add failing tests** (append to existing test class)

```kotlin
    @Test
    fun `need is floored at 7 hours even when the personal average is lower`() {
        // typical 390 -> need floored to 420; 390/420*100 = 92.86 -> 93
        val result = SleepPerformanceCalculator.calculate(390L, 390.0, null, 0.0)
        assertEquals(93, result)
    }

    @Test
    fun `low deep and rem share drags the score even when duration is met`() {
        // duration 480/480 = 1.0; quality (48/480)/0.40 = 0.25
        // 0.7*1.0 + 0.3*0.25 = 0.775 -> 78
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = 48L)
        assertEquals(78, result)
    }

    @Test
    fun `deep and rem share at or above 40 percent scores full quality`() {
        val atTarget = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = 192L)
        val aboveTarget = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = 300L)
        assertEquals(100, atTarget)
        assertEquals(100, aboveTarget)
    }

    @Test
    fun `missing stage data falls back to duration-only scoring`() {
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = null)
        assertEquals(100, result)
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

Run: `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17" && ./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.SleepPerformanceCalculatorTest"`
Expected: FAIL — floor test gets 100 (no floor yet); quality test doesn't compile until the parameter exists (add parameter first if compile blocks; the assertion failures are the real RED).

- [ ] **Step 3: Implement**

Replace `calculate` in `SleepPerformanceCalculator.kt`:

```kotlin
    private const val MIN_NEED_MINUTES = 420.0
    private const val DEEP_REM_TARGET_SHARE = 0.40
    private const val DURATION_WEIGHT = 0.7
    private const val QUALITY_WEIGHT = 0.3

    fun calculate(
        actualSleepMinutes: Long?,
        typicalSleepMinutes: Double,
        priorDayStrain: StrainScore?,
        accumulatedDebtMinutes: Double,
        deepRemMinutes: Long? = null
    ): Int? {
        if (actualSleepMinutes == null || typicalSleepMinutes <= 0.0) return null

        val strainAdjustment = ((priorDayStrain?.value ?: 0.0) / MAX_STRAIN) * MAX_STRAIN_ADJUSTMENT_MINUTES
        val debtRepayment = accumulatedDebtMinutes.coerceIn(0.0, MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES)
        val sleepNeedMinutes = typicalSleepMinutes.coerceAtLeast(MIN_NEED_MINUTES) + strainAdjustment + debtRepayment

        val durationScore = (actualSleepMinutes / sleepNeedMinutes).coerceIn(0.0, 1.0)
        val qualityScore = if (deepRemMinutes != null && actualSleepMinutes > 0) {
            ((deepRemMinutes.toDouble() / actualSleepMinutes) / DEEP_REM_TARGET_SHARE).coerceIn(0.0, 1.0)
        } else {
            null
        }

        val performance = if (qualityScore != null) {
            (DURATION_WEIGHT * durationScore + QUALITY_WEIGHT * qualityScore) * 100.0
        } else {
            durationScore * 100.0
        }
        return performance.coerceIn(0.0, 100.0).roundToInt()
    }
```

Call site in `HealthRepository.syncDate`:

```kotlin
        val sleepPerformance = SleepPerformanceCalculator.calculate(
            actualSleepMinutes = aggregate.sleep?.durationMinutes,
            typicalSleepMinutes = typicalSleepMinutes,
            priorDayStrain = priorDayStrain,
            accumulatedDebtMinutes = sleepDebtMinutes,
            deepRemMinutes = aggregate.sleep?.let {
                it.minutesIn(SleepStageType.DEEP) + it.minutesIn(SleepStageType.REM)
            }
        )
```

(add `import com.esusic.recoverytracker.model.SleepStageType`)

Sheet copy in `GaugeInfoSheets.kt` `SleepSheetContent`, replace the explainer text with:

```kotlin
    Text(
        "How well last night covered your sleep need. Need = your typical sleep (floored at 7h), plus extra after high-strain days and repayment of recent sleep debt. 30% of the score is quality: deep + REM share of the night vs a 40% target.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextSecondary
    )
```

- [ ] **Step 4: Run full test suite**

Run: `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17" && ./gradlew :app:testDebugUnitTest`
Expected: PASS (HealthRepositoryTest included — it asserts presence, not exact values; fix expectations only if a test hard-codes a performance number).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculator.kt app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt app/src/main/java/com/esusic/recoverytracker/ui/today/GaugeInfoSheets.kt app/src/test/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculatorTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: floor sleep need at 7h and weigh deep+REM quality into sleep score"
```

---

### Task 2: Vital Age anchored to Mondays

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeScreen.kt` (caption)
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeViewModelTest.kt`

**Interfaces:**
- Produces: `VitalAgeUiState` gains `asOfDate: LocalDate? = null`. Display point = last series point ≤ previous-or-same Monday (fallback: latest point). History = Monday points only. Weekly delta = display point vs last point ≤ displayDate−7.

- [ ] **Step 1: Rewrite the two data-driven tests and add fallback test**

Replace `computes vital age with history and weekly delta from persisted days` and `too little data reports calibrating with daysUntilReady` stays; new/updated tests:

```kotlin
    @Test
    fun `display value anchors to the previous Monday and history holds Mondays only`() = runTest {
        userPreferences.setBirthYear(1996)
        // 20 consecutive days ending Friday 2026-07-17; HRV improves daily
        (19L downTo 0L).forEach { back ->
            insertDay(today.minusDays(back), hrv = 40.0 + (19 - back), rhr = 60, sleepMin = 450, steps = 9000, strain = 9.0)
        }
        val viewModel = VitalAgeViewModel(repository, userPreferences, fixedClock)
        val state = viewModel.uiState.filter { !it.isLoading }.first()

        val monday = LocalDate.of(2026, 7, 13)
        assertEquals(monday, state.asOfDate)
        // History: Mondays inside the qualified series (2026-07-06 and 2026-07-13)
        assertEquals(listOf(LocalDate.of(2026, 7, 6), monday), state.history.map { it.date })
        assertNotNull(state.weeklyDelta)
        assertEquals(DeltaKind.BETTER, state.weeklyDelta!!.kind) // HRV rising -> younger week-over-week
        // Displayed number equals the Monday value, not today's
        assertEquals(state.history.last().value, state.result!!.vitalAge.toFloat(), 1e-4f)
    }

    @Test
    fun `before the first qualified Monday the latest point is shown as fallback`() = runTest {
        userPreferences.setBirthYear(1996)
        // Only Wed-Fri this week: no series point at or before Monday 2026-07-13
        (2L downTo 0L).forEach { back ->
            insertDay(today.minusDays(back), hrv = 45.0, rhr = 62, sleepMin = 430, steps = 7000, strain = 5.0)
        }
        val viewModel = VitalAgeViewModel(repository, userPreferences, fixedClock)
        val state = viewModel.uiState.filter { !it.isLoading }.first()
        assertNotNull(state.result)
        assertEquals(today, state.asOfDate)
        assertNull(state.weeklyDelta)
    }
```

- [ ] **Step 2: Run to verify RED** (`asOfDate` unresolved / history assertion fails)

- [ ] **Step 3: Implement in `VitalAgeViewModel`**

Add `asOfDate: LocalDate? = null` to `VitalAgeUiState`. Replace the selection block after `val series = ...`:

```kotlin
            val anchor = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            // The shown number only changes on Mondays; before the first qualified
            // Monday fall back to the newest point so calibration still shows something.
            val display = series.lastOrNull { it.first <= anchor } ?: series.lastOrNull()
            val weekBefore = display?.let { (date, _) -> series.lastOrNull { it.first <= date.minusDays(7) } }
            val weeklyDelta = if (display != null && weekBefore != null) {
                val diff = display.second.vitalAge - weekBefore.second.vitalAge
                MetricDelta(
                    diff,
                    when {
                        abs(diff) < 0.1 -> DeltaKind.NEUTRAL
                        diff < 0 -> DeltaKind.BETTER
                        else -> DeltaKind.WORSE
                    }
                )
            } else {
                null
            }

            _uiState.value = VitalAgeUiState(
                isLoading = false,
                chronologicalAge = LocalDate.now(clock).year - birthYear,
                result = display?.second,
                weeklyDelta = weeklyDelta,
                history = series.filter { it.first.dayOfWeek == DayOfWeek.MONDAY }
                    .map { (date, result) -> ChartPoint(date, result.vitalAge.toFloat()) },
                daysUntilReady = if (display == null) {
                    (VitalAgeSeries.MIN_WINDOW_DAYS - days.size).coerceAtLeast(1)
                } else {
                    null
                },
                asOfDate = display?.first
            )
```

(imports: `java.time.DayOfWeek`, `java.time.temporal.TemporalAdjusters`)

In `VitalAgeScreen` `HeroSection`, after the DeltaChip add:

```kotlin
        uiState.asOfDate?.let {
            Text(
                "As of ${it.format(DateTimeFormatter.ofPattern("d MMM"))} · updates every Monday",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted
            )
        }
```

- [ ] **Step 4: Full test suite + compile**

Run: `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17" && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeViewModel.kt app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeScreen.kt app/src/test/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeViewModelTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: anchor displayed vital age to Mondays with weekly history"
```

---

### Task 3: Install + device check

- [ ] `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17" && ./gradlew :app:installDebug` → "Installed on 1 device."
- [ ] Force-stop, relaunch, uiautomator-dump: Age tab shows "As of …" caption; Sleep gauge shows recomputed (lower) percentage after re-sync; no AndroidRuntime crash in logcat.
