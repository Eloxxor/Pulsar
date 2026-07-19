# Dark Neon Theme, Global Day, and Vital Age Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a cohesive dark neon redesign, one selected day shared by Home/Sleep/Activity, a date-correct Sleep tab, no presented Stress or duplicate `Last night` card, and a coverage-aware Vital Age whose visible impacts reconcile exactly with its headline value.

**Architecture:** A navigation-lifetime `SelectedDayStore` becomes the sole mutable day source and is injected into the three date-based ViewModels. Vital Age separates raw signal offsets from confidence-adjusted, normalized `impactYears`. A dark token set and reusable `neonSurface` modifier drive all shared Compose surfaces so visual behavior remains consistent instead of being duplicated screen by screen.

**Tech Stack:** Kotlin 2.2, Android SDK 36/min SDK 26, Jetpack Compose Material 3, Navigation Compose, Kotlin Coroutines/StateFlow, Room, Robolectric, JUnit 4, Gradle 8 Android build.

## Global Constraints

- Do not copy Threshld code, assets, wording, branding, or proprietary scoring.
- Keep the existing Home, Sleep, Activity, and Account destination structure.
- Keep stress ingestion, scoring, database fields, and repository compatibility; remove only Stress presentation from Home and Activity.
- Map the selected date directly to `DailyAggregateEntity.date` and `DailyScoreEntity.date`; Sleep must not silently substitute a different date.
- Keep all visible Vital Age component impacts mathematically consistent with `vitalAge - chronologicalAge` within floating-point tolerance.
- Keep Vital Age constrained to a maximum delta of `[-10, +10]` years and a minimum resulting age of 18.
- Return no Vital Age result for a chronological age below 18 so the adult age floor and delta cap cannot contradict each other.
- Add no external UI or chart dependency; implement glow and motion with existing Compose APIs.
- Preserve touch targets, accessibility descriptions, empty states, and non-medical guidance.
- Do not modify or stage `.claude/` or `.claude-flow/`.

---

### Task 1: Create the shared selected-day source

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/date/SelectedDayStore.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/date/SelectedDayStoreTest.kt`

**Interfaces:**
- Consumes: `java.time.Clock`, `java.time.LocalDate`, `StateFlow<LocalDate>`.
- Produces: `SelectedDayStore.selectedDate`, `select(LocalDate)`, `previous()`, `next()`, and `today()` for all date-based ViewModels.

- [ ] **Step 1: Write the failing store tests**

```kotlin
package com.esusic.recoverytracker.ui.date

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SelectedDayStoreTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `selection is shared and future dates clamp to today`() {
        val store = SelectedDayStore(clock)

        store.select(LocalDate.of(2026, 7, 18))
        assertEquals(LocalDate.of(2026, 7, 18), store.selectedDate.value)

        store.select(LocalDate.of(2026, 7, 21))
        assertEquals(LocalDate.of(2026, 7, 19), store.selectedDate.value)
    }

    @Test
    fun `previous and next mutate the same state flow`() {
        val store = SelectedDayStore(clock)

        store.previous()
        assertEquals(LocalDate.of(2026, 7, 18), store.selectedDate.value)

        store.next()
        assertEquals(LocalDate.of(2026, 7, 19), store.selectedDate.value)
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.esusic.recoverytracker.ui.date.SelectedDayStoreTest"
```

Expected: compilation fails because `SelectedDayStore` does not exist.

- [ ] **Step 3: Implement the store**

```kotlin
package com.esusic.recoverytracker.ui.date

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

class SelectedDayStore(
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun today(): LocalDate = LocalDate.now(clock)

    fun select(date: LocalDate) {
        _selectedDate.value = date.coerceAtMost(today())
    }

    fun previous() = select(_selectedDate.value.minusDays(1))

    fun next() = select(_selectedDate.value.plusDays(1))
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run the Step 2 command. Expected: `SelectedDayStoreTest` passes.

- [ ] **Step 5: Commit the shared state**

```powershell
git add app/src/main/java/com/esusic/recoverytracker/ui/date/SelectedDayStore.kt app/src/test/java/com/esusic/recoverytracker/ui/date/SelectedDayStoreTest.kt
git commit -m "feat: add shared selected day store"
```

---

### Task 2: Make Home and Activity observe the global day

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/activity/ActivityViewModelTest.kt`

**Interfaces:**
- Consumes: `SelectedDayStore` from Task 1.
- Produces: constructors accepting a shared store; Home and Activity date actions delegate to it; Activity trend windows are anchored to the selected date.

- [ ] **Step 1: Add failing external-selection tests**

Add this test to `TodayViewModelTest`, using its existing `repositoryWith` fixture:

```kotlin
@Test
fun `external selected day change reloads Home`() = runTest {
    val yesterday = LocalDate.now().minusDays(1)
    val source = FakeHealthDataSource(
        restingHr = listOf(
            RestingHrSample(yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 50L)
        )
    )
    val repository = repositoryWith(source)
    repository.syncDate(yesterday)
    val dayStore = SelectedDayStore()
    val viewModel = TodayViewModel(
        repository,
        source,
        userPreferences,
        selectedDayStore = dayStore
    )

    dayStore.select(yesterday)

    assertEquals(yesterday, viewModel.uiState.value.selectedDate)
    assertEquals(50L, viewModel.uiState.value.restingHeartRate)
}
```

Add imports for `SelectedDayStore`. Add this test to `ActivityViewModelTest`:

```kotlin
@Test
fun `external selected day anchors Activity data and trends`() = runTest {
    val source = FakeHealthDataSource()
    val repository = newRepository(source, "activity-global-day.preferences_pb")
    val july12 = LocalDate.of(2026, 7, 12)
    val july13 = LocalDate.of(2026, 7, 13)
    repository.syncDate(july12)
    repository.syncDate(july13)
    val fixedClock = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC)
    val dayStore = SelectedDayStore(fixedClock)
    val viewModel = ActivityViewModel(repository, source, fixedClock, dayStore)

    dayStore.select(july12)
    val state = viewModel.uiState.filter { !it.isLoading && it.selectedDate == july12 }.first()

    assertEquals(july12, state.selectedDate)
    assertEquals(listOf(july12), state.recovery.points.map { it.date })
}
```

- [ ] **Step 2: Run the two ViewModel test classes and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.esusic.recoverytracker.ui.today.TodayViewModelTest" --tests "com.esusic.recoverytracker.ui.activity.ActivityViewModelTest"
```

Expected: compilation fails because neither constructor accepts `selectedDayStore` and external selection is not observed.

- [ ] **Step 3: Refactor `TodayViewModel` around the shared flow**

Keep `clock` before the new optional parameter so existing constructor calls remain source compatible:

```kotlin
class TodayViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val selectedDayStore: SelectedDayStore = SelectedDayStore(clock)
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TodayUiState(selectedDate = selectedDayStore.selectedDate.value)
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            merge(
                flowOf(true),
                selectedDayStore.selectedDate.drop(1).map { false },
                refreshRequests
            ).collectLatest { sync ->
                load(selectedDayStore.selectedDate.value, sync)
            }
        }
    }

    fun refresh() {
        refreshRequests.tryEmit(true)
    }

    fun selectDate(date: LocalDate) = selectedDayStore.select(date)
    fun goToPreviousDay() = selectedDayStore.previous()
    fun goToNextDay() = selectedDayStore.next()
}
```

Import `MutableSharedFlow`, `collectLatest`, `drop`, `flowOf`, `map`, `merge`, and `SelectedDayStore`. Delete the private mutable `selectedDate`. Change the existing load wrapper from:

```kotlin
private fun load(sync: Boolean) {
    viewModelScope.launch {
```

to:

```kotlin
private suspend fun load(selectedDate: LocalDate, sync: Boolean) {
```

Remove the closing brace that belonged to the inner `viewModelScope.launch`; keep the existing load statements inside the new suspend function. Every existing date query, comparison, and `TodayUiState.selectedDate` assignment resolves to the function parameter. This lets `collectLatest` cancel stale work when the global day changes.

- [ ] **Step 4: Refactor `ActivityViewModel` and anchor trends**

Use this constructor and state source:

```kotlin
class ActivityViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val selectedDayStore: SelectedDayStore = SelectedDayStore(clock)
) : ViewModel() {
    private val selectedRange = MutableStateFlow(ActivityRange.WEEK)
    private val _uiState = MutableStateFlow(
        ActivityUiState(selectedDate = selectedDayStore.selectedDate.value)
    )

    init {
        viewModelScope.launch {
            combine(
                selectedDayStore.selectedDate,
                selectedRange,
                repository.observeRecentScores(ActivityRange.QUARTER.days),
                repository.observeRecentAggregates(ActivityRange.QUARTER.days)
            ) { date, range, scores, aggregates ->
                ActivitySnapshot(date, range, scores, aggregates)
            }.collectLatest(::publishSnapshot)
        }
    }

    fun selectDate(date: LocalDate) = selectedDayStore.select(date)
    fun goToPreviousDay() = selectedDayStore.previous()
    fun goToNextDay() = selectedDayStore.next()
}
```

Replace trend selection in `publishSnapshot` with:

```kotlin
val rangeScores = snapshot.scores
    .filter { it.date <= dateString }
    .take(snapshot.range.days)
    .reversed()
val rangeAggregates = snapshot.aggregates
    .filter { it.date <= dateString }
    .take(snapshot.range.days)
    .reversed()
```

- [ ] **Step 5: Create one navigation-lifetime store and inject it**

At the start of `RecoveryTrackerNavHost`, add:

```kotlin
val selectedDayStore = remember { SelectedDayStore() }
```

Pass it by name when creating the ViewModels:

```kotlin
TodayViewModel(
    ServiceLocator.repository,
    ServiceLocator.healthDataSource,
    ServiceLocator.userPreferencesRepository,
    selectedDayStore = selectedDayStore
)

ActivityViewModel(
    ServiceLocator.repository,
    ServiceLocator.healthDataSource,
    selectedDayStore = selectedDayStore
)
```

Import `remember` and `SelectedDayStore`.

- [ ] **Step 6: Run focused and complete unit tests**

Run the Step 2 command, then:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: both focused classes and the complete suite pass.

- [ ] **Step 7: Commit Home/Activity global-day behavior**

```powershell
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityViewModel.kt app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt app/src/test/java/com/esusic/recoverytracker/ui/activity/ActivityViewModelTest.kt
git commit -m "feat: share selected day across data tabs"
```

---

### Task 3: Make Sleep load exactly the selected date

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/sleep/SleepViewModelTest.kt`

**Interfaces:**
- Consumes: the navigation-lifetime `SelectedDayStore` and repository aggregate/detail streams.
- Produces: `SleepUiState.selectedDate`, `canGoBack`, `canGoForward`, exact-date detail, exact-date empty state, and selected-date-anchored seven-night history.

- [ ] **Step 1: Add failing exact-date and stale-detail tests**

Extend the existing synchronous Room fixture tests with a shared fixed store. The first test must create two synced nights, select the older date, and assert its duration and session start:

```kotlin
val clock = Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC)
val dayStore = SelectedDayStore(clock)
val viewModel = SleepViewModel(repository, dayStore)

dayStore.select(LocalDate.of(2026, 7, 12))
val state = viewModel.uiState.filter {
    it.selectedDate == LocalDate.of(2026, 7, 12) && it.sessionStart != null
}.first()

assertEquals(LocalDate.of(2026, 7, 12), state.selectedDate)
assertEquals(night1Start, state.sessionStart)
assertEquals(400L, state.totalMinutes)
```

The second test must load a recorded night and then select a date without sleep:

```kotlin
dayStore.select(LocalDate.of(2026, 7, 13))
viewModel.uiState.filter { it.sessionStart != null }.first()

dayStore.select(LocalDate.of(2026, 7, 14))
val empty = viewModel.uiState.filter {
    it.selectedDate == LocalDate.of(2026, 7, 14) && it.totalMinutes == null
}.first()

assertNull(empty.sessionStart)
assertTrue(empty.heartRateBuckets.isEmpty())
assertTrue(empty.stagePeriods.isEmpty())
assertNull(empty.sleepPerformance)
```

Add a third assertion to the two-night fixture that `weeklyNights.last().date <= state.selectedDate`.

- [ ] **Step 2: Run `SleepViewModelTest` and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.esusic.recoverytracker.ui.sleep.SleepViewModelTest"
```

Expected: compilation fails because Sleep has no selected-day fields or store constructor.

- [ ] **Step 3: Add selected-day fields and date actions**

Add to `SleepUiState`:

```kotlin
val selectedDate: LocalDate = LocalDate.now(),
val canGoBack: Boolean = false,
val canGoForward: Boolean = false,
```

Use this constructor and collector:

```kotlin
class SleepViewModel(
    private val repository: HealthRepository,
    private val selectedDayStore: SelectedDayStore = SelectedDayStore()
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SleepUiState(selectedDate = selectedDayStore.selectedDate.value)
    )
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedDayStore.selectedDate,
                repository.observeRecentAggregates(30)
            ) { date, aggregates -> date to aggregates }
                .collectLatest { (date, aggregates) -> publishDate(date, aggregates) }
        }
    }

    fun goToPreviousDay() = selectedDayStore.previous()
    fun goToNextDay() = selectedDayStore.next()
}
```

- [ ] **Step 4: Implement exact-date publication and state clearing**

Inside `publishDate`, derive collections with these exact filters:

```kotlin
val dateString = date.toString()
val selected = aggregates.find { it.date == dateString }
val priorNights = aggregates.filter { it.date < dateString }
val weeklyNights = aggregates
    .filter { it.date <= dateString }
    .mapNotNull { aggregate ->
        aggregate.sleepDurationMinutes?.let { duration ->
            SleepNightPoint(LocalDate.parse(aggregate.date), duration)
        }
    }
    .take(7)
    .reversed()
val typical = priorNights.mapNotNull { it.sleepDurationMinutes }
    .takeIf { it.isNotEmpty() }
    ?.map { it.toDouble() }
    ?.average()
```

Before loading detail, publish a complete base state rather than copying the previous date:

```kotlin
val base = SleepUiState(
    selectedDate = date,
    canGoBack = aggregates.any { it.date < dateString },
    canGoForward = date < selectedDayStore.today(),
    typicalTotalMinutes = typical,
    weeklyNights = weeklyNights,
    weeklyAverageMinutes = weeklyNights.takeIf { it.isNotEmpty() }
        ?.map { it.durationMinutes.toDouble() }
        ?.average(),
    weeklyInsight = weeklyInsight(weeklyNights)
)
```

If `selected?.sleepDurationMinutes` is null or non-positive, assign `base` and return. Otherwise populate the exact selected night with this sequence:

```kotlin
val total = requireNotNull(selected.sleepDurationMinutes)
val deep = selected.deepSleepMinutes
val rem = selected.remSleepMinutes
val light = selected.lightSleepMinutes
var state = base.copy(
    totalMinutes = total,
    deepMinutes = deep,
    remMinutes = rem,
    lightMinutes = light,
    deepPercent = deep?.toFloat()?.div(total) ?: 0f,
    remPercent = rem?.toFloat()?.div(total) ?: 0f,
    lightPercent = light?.toFloat()?.div(total) ?: 0f,
    durationDelta = metricDelta(
        total.toDouble(),
        typical,
        higherIsBetter = true,
        neutralBandFraction = 0.05
    )
)
val detail = repository.sleepDetailForDate(date)
val need = repository.sleepNeedMinutesFor(date)
val performance = repository.observeRecentScores(30).first()
    .find { it.date == dateString }
    ?.sleepPerformance
if (detail != null) {
    val inBed = java.time.Duration.between(detail.session.start, detail.session.end).toMinutes()
    val hasStageDetail = detail.session.stages.isNotEmpty()
    val awake = detail.session.stages
        .filter { it.stage == SleepStageType.AWAKE }
        .sumOf { java.time.Duration.between(it.start, it.end).toMinutes() }
    state = state.copy(
        heartRateBuckets = bucketHeartRateSamples(detail.heartRateSamples, detail.session.start),
        stagePeriods = detail.session.stages,
        sessionStart = detail.session.start,
        sessionEnd = detail.session.end,
        inBedMinutes = inBed,
        deepMinutes = deep.takeIf { hasStageDetail },
        remMinutes = rem.takeIf { hasStageDetail },
        lightMinutes = light.takeIf { hasStageDetail },
        awakeMinutes = awake.takeIf { hasStageDetail },
        wakeEvents = detail.session.stages.count { it.stage == SleepStageType.AWAKE }
            .takeIf { hasStageDetail }
    )
}
_uiState.value = state.copy(
    needMinutes = need,
    sleepPerformance = performance
)
```

Delete `detailLoadedForDate` and every latest-night fallback.

- [ ] **Step 5: Wire the shared store and date header**

Create Sleep with the same `selectedDayStore` in `RecoveryTrackerNavHost`:

```kotlin
SleepViewModel(ServiceLocator.repository, selectedDayStore)
```

Replace the fixed Sleep header with:

```kotlin
RootPageHeader(
    title = "Sleep",
    subtitle = "Night of ${state.selectedDate.format(sleepDateFormatter)}",
    previousEnabled = state.canGoBack,
    nextEnabled = state.canGoForward,
    onPrevious = viewModel::goToPreviousDay,
    onNext = viewModel::goToNextDay
)
```

Define `sleepDateFormatter` as `DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)`. Make the empty-state message include `state.selectedDate.format(sleepDateFormatter)` and remove all `Last night` copy.

- [ ] **Step 6: Run focused and complete tests**

Run the Step 2 command, then `.\gradlew.bat testDebugUnitTest`. Expected: all pass.

- [ ] **Step 7: Commit date-correct Sleep**

```powershell
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt app/src/test/java/com/esusic/recoverytracker/ui/sleep/SleepViewModelTest.kt
git commit -m "fix: bind sleep to selected day"
```

---

### Task 4: Replace Vital Age with coverage-aware, reconcilable impacts

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/VitalAgeCalculator.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/VitalAgeSeries.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeScreen.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/scoring/VitalAgeCalculatorTest.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/scoring/VitalAgeSeriesTest.kt`

**Interfaces:**
- Consumes: 14-day averages plus valid-day counts from `VitalAgeSeries`.
- Produces: `VitalAgeComponent(rawOffsetYears, impactYears, weight, confidence)` and the invariant that all `impactYears` sum to the final age delta.

- [ ] **Step 1: Replace misleading assertions with failing impact invariants**

Update `VitalAgeCalculatorTest` to supply sample-day counts where sparse coverage matters and add:

```kotlin
@Test
fun `visible impacts sum exactly to final age delta`() {
    val result = VitalAgeCalculator.calculate(
        23,
        VitalAgeInputs(
            avgHrvMillis = 30.0,
            avgRestingHr = 74.0,
            avgSleepMinutes = 460.0,
            avgSteps = 5000.0,
            avgStrain = 5.0,
            maxHrBpm = 191.9
        )
    )!!

    val impacts = listOfNotNull(
        result.fitness,
        result.hrv,
        result.rhr,
        result.sleep,
        result.activity
    ).sumOf { it.impactYears }

    assertEquals(result.vitalAge - result.chronologicalAge, impacts, 1e-9)
    assertTrue(result.fitness!!.impactYears < result.fitness!!.rawOffsetYears)
}

@Test
fun `components below three sample days are excluded`() {
    val result = VitalAgeCalculator.calculate(
        30,
        VitalAgeInputs(
            avgHrvMillis = 55.0,
            hrvSampleDays = 2,
            avgRestingHr = 62.0,
            restingHrSampleDays = 7,
            avgSleepMinutes = 450.0,
            sleepSampleDays = 7
        )
    )!!

    assertNull(result.hrv)
    assertEquals(1.0, result.rhr!!.confidence, 1e-9)
}

@Test
fun `bounded result rescales impacts and never exceeds ten years`() {
    val result = VitalAgeCalculator.calculate(
        30,
        VitalAgeInputs(
            avgHrvMillis = 1.0,
            avgRestingHr = 100.0,
            avgSleepMinutes = 120.0,
            avgSteps = 0.0,
            avgStrain = 0.0,
            maxHrBpm = 200.0
        )
    )!!

    assertEquals(10.0, result.vitalAge - 30.0, 1e-9)
    assertEquals(
        10.0,
        listOfNotNull(result.fitness, result.hrv, result.rhr, result.sleep, result.activity)
            .sumOf { it.impactYears },
        1e-9
    )
}

@Test
fun `calculator is unavailable below adult age`() {
    assertNull(
        VitalAgeCalculator.calculate(
            17,
            VitalAgeInputs(avgRestingHr = 62.0, avgSleepMinutes = 450.0)
        )
    )
}
```

- [ ] **Step 2: Run calculator and series tests and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.VitalAgeCalculatorTest" --tests "com.esusic.recoverytracker.scoring.VitalAgeSeriesTest"
```

Expected: compilation fails because sample counts, confidence, raw offsets, and impacts do not exist.

- [ ] **Step 3: Introduce explicit coverage and component output types**

Use these public data types in `VitalAgeCalculator.kt`:

```kotlin
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
```

Set the component weights to Fitness `0.35`, HRV `0.25`, RHR `0.10`, Sleep `0.15`, Activity `0.15`. Add `MIN_SAMPLE_DAYS = 3`, `FULL_CONFIDENCE_DAYS = 7.0`, and `MAX_TOTAL_DELTA = 10.0`.

- [ ] **Step 4: Implement confidence, normalized impacts, and proportional bounding**

Use a private candidate model:

```kotlin
private data class Candidate(
    val component: Component,
    val rawOffset: Double,
    val confidence: Double
)

private fun confidence(sampleDays: Int): Double? = sampleDays
    .takeIf { it >= MIN_SAMPLE_DAYS }
    ?.div(FULL_CONFIDENCE_DAYS)
    ?.coerceAtMost(1.0)
```

Build candidates only when their input and confidence exist. Fitness and direct RHR both use `restingHrSampleDays`. Activity confidence is `0.6 * stepsConfidence + 0.4 * strainConfidence` when both sources exist, otherwise the available source confidence.

At the first line of `calculate`, return null when `chronologicalAge < 18`.

Calculate final output with:

```kotlin
val effectiveTotal = candidates.sumOf { it.component.baseWeight * it.confidence }
val preliminary = candidates.associateWith { candidate ->
    val weight = candidate.component.baseWeight * candidate.confidence / effectiveTotal
    weight to candidate.rawOffset * weight
}
val rawDelta = preliminary.values.sumOf { it.second }
val floorDelta = 18.0 - chronologicalAge
val boundedDelta = rawDelta
    .coerceIn(-MAX_TOTAL_DELTA, MAX_TOTAL_DELTA)
    .coerceAtLeast(floorDelta)
val scale = if (kotlin.math.abs(rawDelta) < 1e-12) 1.0 else boundedDelta / rawDelta
val components = preliminary.map { (candidate, values) ->
    candidate.component to VitalAgeComponent(
        rawOffsetYears = candidate.rawOffset,
        impactYears = values.second * scale,
        weight = values.first,
        confidence = candidate.confidence
    )
}.toMap()
val vitalAge = chronologicalAge + components.values.sumOf { it.impactYears }
```

Return null when fewer than two candidates remain. Keep the existing raw domain curves and their bounds.

- [ ] **Step 5: Pass real per-signal sample counts from the 14-day series**

In `VitalAgeSeries.build`, materialize each list once and pass both average and count:

```kotlin
val hrv = window.mapNotNull { it.hrvMillis }
val rhr = window.mapNotNull { it.restingHr }
val sleep = window.mapNotNull { it.sleepMinutes }
val steps = window.mapNotNull { it.steps }
val strain = window.mapNotNull { it.strain }
val inputs = VitalAgeInputs(
    avgHrvMillis = hrv.averageOrNull(),
    hrvSampleDays = hrv.size,
    avgRestingHr = rhr.averageOrNull(),
    restingHrSampleDays = rhr.size,
    avgSleepMinutes = sleep.averageOrNull(),
    sleepSampleDays = sleep.size,
    avgSteps = steps.averageOrNull(),
    stepsSampleDays = steps.size,
    avgStrain = strain.averageOrNull(),
    strainSampleDays = strain.size,
    maxHrBpm = maxHrBpm
)
```

- [ ] **Step 6: Display only actual impacts**

In `VitalAgeScreen`, replace every `offsetYears` use with `impactYears`. The row value becomes:

```kotlin
component?.let { "%+.1f yr impact".format(it.impactYears) } ?: "not enough data"
```

The label becomes:

```kotlin
if (component != null) {
    "$label · ${(component.weight * 100).roundToInt()}% weight · ${(component.confidence * 100).roundToInt()}% confidence"
} else {
    label
}
```

Pass `impactYears` to `DivergingBar` and change the explanatory copy to `The visible impacts add up to your Vital Age difference.`

- [ ] **Step 7: Run focused and complete tests**

Run the Step 2 command, then `.\gradlew.bat testDebugUnitTest`. Expected: all pass.

- [ ] **Step 8: Commit the Vital Age correction**

```powershell
git add app/src/main/java/com/esusic/recoverytracker/scoring/VitalAgeCalculator.kt app/src/main/java/com/esusic/recoverytracker/scoring/VitalAgeSeries.kt app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeScreen.kt app/src/test/java/com/esusic/recoverytracker/scoring/VitalAgeCalculatorTest.kt app/src/test/java/com/esusic/recoverytracker/scoring/VitalAgeSeriesTest.kt
git commit -m "fix: reconcile vital age impacts"
```

---

### Task 5: Build the dark neon design foundation

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/theme/AppColors.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/common/NeonSurface.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/common/SectionCard.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/common/MetricTile.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/common/StatusPill.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/common/RootPageHeader.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/CircularGauge.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/MainActivity.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/theme/ThemeContrastTest.kt`

**Interfaces:**
- Consumes: existing Compose Material 3 APIs only.
- Produces: dark semantic tokens, `Modifier.neonSurface(shape, accent, glowAlpha)`, optional accents on shared cards/tiles, animated glow rings, dark system bars, and a floating neon bottom bar.

- [ ] **Step 1: Add failing palette contrast tests**

```kotlin
package com.esusic.recoverytracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun `canvas is dark and primary text exceeds seven to one contrast`() {
        assertTrue(AppColors.Canvas.luminance() < 0.02f)
        assertTrue(contrast(AppColors.TextPrimary, AppColors.Canvas) >= 7.0f)
    }

    @Test
    fun `secondary text exceeds four point five to one contrast`() {
        assertTrue(contrast(AppColors.TextSecondary, AppColors.Canvas) >= 4.5f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
```

- [ ] **Step 2: Run the contrast test and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.esusic.recoverytracker.ui.theme.ThemeContrastTest"
```

Expected: the existing light canvas fails the darkness assertion.

- [ ] **Step 3: Replace the light tokens and Material scheme**

Set the semantic values in `AppColors`:

```kotlin
val Canvas = Color(0xFF05060B)
val Surface = Color(0xFF11131B)
val SurfaceElevated = Color(0xFF151927)
val TextPrimary = Color(0xFFF5F7FB)
val TextSecondary = Color(0xFFA7AEC4)
val TextMuted = Color(0xFF747C96)
val GridLine = Color(0xFF282D3C)
val NavBar = Color(0xF2111420)
val Recovery = Color(0xFF00D89F)
val ActivityCyan = Color(0xFF13C8E8)
val Strain = Color(0xFFFF6B35)
val SleepViolet = Color(0xFF8A5CFF)
val Hrv = Color(0xFF3478FF)
val Rhr = Color(0xFFF13B91)
val HeartRate = Color(0xFFFF477E)
val VitalAge = Color(0xFFF7B84B)
val Warning = Color(0xFFFFB43A)
val Danger = Color(0xFFFF5A68)
```

Tune sleep-stage colors for dark-background contrast. Replace `lightColorScheme` with `darkColorScheme`, including `surfaceVariant = AppColors.SurfaceElevated`.

- [ ] **Step 4: Add the reusable neon surface modifier**

Implement `NeonSurface.kt` with this public contract:

```kotlin
fun Modifier.neonSurface(
    shape: Shape,
    accent: Color? = null,
    glowAlpha: Float = 0.18f,
    baseColor: Color = AppColors.Surface
): Modifier = clip(shape)
    .drawWithCache {
        val center = Offset(size.width * 0.82f, size.height * 0.08f)
        val radius = size.maxDimension * 0.78f
        val glow = accent?.let {
            Brush.radialGradient(
                colors = listOf(it.copy(alpha = glowAlpha), Color.Transparent),
                center = center,
                radius = radius
            )
        }
        onDrawBehind {
            drawRect(baseColor)
            glow?.let { drawRect(it) }
        }
    }
    .border(
        1.dp,
        accent?.copy(alpha = 0.30f) ?: AppColors.GridLine,
        shape
    )
```

Use it inside `SectionCard` and `MetricTile`; add optional `accent: Color? = null` and `glowAlpha: Float = 0.18f` parameters while keeping all existing callers source compatible. Increase the pill fill to a dark translucent treatment and give selected/high-emphasis pills a thin accent border.

- [ ] **Step 5: Restyle header controls, navigation, and score rings**

Wrap previous/next actions in a rounded `Surface` using `AppColors.Surface` and `AppColors.GridLine`; tint disabled icons with `TextMuted`. Replace the edge-to-edge `NavigationBar` presentation with a horizontally padded `Surface` capsule, 30.dp shape, low border, and a selected item color based on destination:

```kotlin
private fun rootAccent(destination: RootDestination) = when (destination) {
    RootDestination.HOME -> AppColors.Recovery
    RootDestination.SLEEP -> AppColors.SleepViolet
    RootDestination.ACTIVITY -> AppColors.ActivityCyan
    RootDestination.ACCOUNT -> AppColors.Hrv
}
```

Use the selected accent for icon, label, and translucent indicator. Keep the bottom bar outside content overlap and account for system navigation insets.

In `CircularGauge`, animate the fraction with `animateFloatAsState` and draw a wide, low-alpha accent arc behind the main arc before drawing the crisp stroke. The glow arc uses twice the main stroke width and alpha no higher than `0.22f`.

- [ ] **Step 6: Set dark system bars**

Call `enableEdgeToEdge` before `setContent`:

```kotlin
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
)
```

This keeps status and navigation icons legible on the near-black canvas.

- [ ] **Step 7: Run contrast test, all tests, and compile**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.esusic.recoverytracker.ui.theme.ThemeContrastTest"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: contrast tests pass, all tests pass, and the debug APK builds.

- [ ] **Step 8: Commit the design foundation**

```powershell
git add app/src/main/java/com/esusic/recoverytracker/ui/theme app/src/main/java/com/esusic/recoverytracker/ui/common app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt app/src/main/java/com/esusic/recoverytracker/ui/today/CircularGauge.kt app/src/main/java/com/esusic/recoverytracker/MainActivity.kt app/src/test/java/com/esusic/recoverytracker/ui/theme/ThemeContrastTest.kt
git commit -m "feat: add dark neon design system"
```

---

### Task 6: Apply neon hierarchy and remove redundant UI

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/home/HomeReadinessCards.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityViewModel.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/home/HomeLayoutPolicyTest.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: optional accent/glow shared surfaces and animated `CircularGauge` from Task 5.
- Produces: neon hero hierarchy, no Home `Last night`, no Stress UI state/presentation, and a three-metric Activity layout.

- [ ] **Step 1: Remove obsolete presentation expectations from tests**

Delete the `sleep stage summary omits unknown zero-only detail` test from `HomeLayoutPolicyTest`. Rename the Today metric-grid test to `uiState exposes hourly steps heart-rate spark and zone minutes` and remove only this assertion:

```kotlin
assertNotNull(viewModel.uiState.value.stressValue)
```

Do not alter `StressScoreCalculatorTest` or repository stress assertions because storage and scoring remain supported.

- [ ] **Step 2: Remove Stress from root UI state and screen composition**

Delete `stressValue` from `TodayUiState`, its assignment in `TodayViewModel`, and the complete Home Stress `VitalLine` call. Delete `stressValue` from `ActivityUiState`, its assignment in `ActivityViewModel`, the complete Stress `MetricTile`, and `stressColor` from `ActivityScreen`.

Replace the second Activity metric row with a full-width Heart rate tile:

```kotlin
MetricTile(
    label = "Heart rate",
    value = state.latestHeartRateBpm?.toString() ?: "—",
    unit = "bpm",
    accent = AppColors.HeartRate,
    glowAlpha = 0.10f,
    modifier = Modifier.fillMaxWidth()
)
```

- [ ] **Step 3: Remove duplicate Home sleep content**

Delete the complete `SectionCard(title = "Last night")` block, its semantics string, `sleepStageSummary`, `sleepStageSummaryFor`, and imports used only by those functions. Keep the Sleep card inside `ReadinessCluster` as the Home sleep summary and link to the dedicated Sleep tab.

- [ ] **Step 4: Turn readiness cards into the main neon focal cluster**

Use `Modifier.neonSurface` directly on the three readiness card containers. In `RecoveryHeroCard`, place an animated `CircularGauge` around the recovery value with `AppColors.Recovery`; use an indeterminate segmented ring while calibrating. Use `AppColors.ActivityCyan` for the Effort card accent and `AppColors.SleepViolet` for Sleep. Keep recommendation text on neutral dark areas and do not apply an outer glow to every card.

For the asymmetric phone layout, keep the existing `360.dp` cluster height and existing stacked breakpoints. The ring must fit within the current Recovery column without reducing the 48.dp minimum touch target.

- [ ] **Step 5: Add restrained hero glows to Sleep and Activity**

Give `SleepHero` a violet accent surface and a `CircularGauge` displaying `sleepPerformance / 100`, while duration, time range, and status remain readable beside or below it at 371.dp width. Give the Activity hero an `ActivityCyan` accent surface and ring for the strain fraction `strain / 21`; retain orange for training-load emphasis. Apply only low-alpha accents to timeline and weekly cards.

Use `SectionCard(accent = AppColors.SleepViolet, glowAlpha = 0.22f)` for Sleep hero and `SectionCard(accent = AppColors.ActivityCyan, glowAlpha = 0.20f)` for Activity hero. Core metric tiles use their own domain accent with `glowAlpha` between `0.08f` and `0.12f`.

- [ ] **Step 6: Run tests and compile**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected: all tests pass and UI sources compile without obsolete stress or sleep-summary references.

- [ ] **Step 7: Verify removed copy by source scan**

```powershell
rg -n 'Last night|VitalLine\("Stress"|label = "Stress"|stressColor' app/src/main/java/com/esusic/recoverytracker/ui/home app/src/main/java/com/esusic/recoverytracker/ui/sleep app/src/main/java/com/esusic/recoverytracker/ui/activity
```

Expected: no matches.

- [ ] **Step 8: Commit the root-screen redesign**

```powershell
git add app/src/main/java/com/esusic/recoverytracker/ui/home app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt app/src/main/java/com/esusic/recoverytracker/ui/activity app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt app/src/test/java/com/esusic/recoverytracker/ui/home/HomeLayoutPolicyTest.kt app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt
git commit -m "feat: apply neon root screen hierarchy"
```

---

### Task 7: Verify the complete app on the connected phone

**Files:**
- Modify only if verification exposes a defect: the smallest directly responsible source/test file from Tasks 1–6.
- Do not commit generated screenshots or UI hierarchy dumps.

**Interfaces:**
- Consumes: completed APK and device `RFCY70GP8BH`.
- Produces: test/build evidence and connected-device confirmation for theme, navigation, exact-date behavior, removed content, and Vital Age reconciliation.

- [ ] **Step 1: Run clean project verification**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git diff --check
```

Expected: all unit tests pass, APK builds, and `git diff --check` reports no whitespace errors.

- [ ] **Step 2: Install the verified APK**

```powershell
adb -s RFCY70GP8BH install -r app\build\outputs\apk\debug\app-debug.apk
adb -s RFCY70GP8BH shell monkey -p com.esusic.recoverytracker -c android.intent.category.LAUNCHER 1
```

Expected: install reports `Success` and the app opens.

- [ ] **Step 3: Verify the visual system**

Capture Home, Sleep, Activity, Account, Vital Age, and one detail screen. Confirm:

- near-black canvas and dark system bars;
- readable white/cool-gray text;
- colored but restrained hero glows;
- no clipped Recovery/Sleep/Activity rings at the connected phone width;
- floating capsule navigation remains above the system gesture area;
- all root and detail screens inherit the dark palette.

- [ ] **Step 4: Verify global day behavior through ADB interaction**

On Home, tap Previous day once and read the displayed date from `uiautomator dump`. Tap Sleep and confirm its header contains the same date and its session matches that date. Tap Activity and confirm the same date remains. Tap Next on Activity and return Home; confirm Home now shows the advanced date.

Select a date without sleep if one exists. Confirm Sleep shows its date-specific empty state and no prior session timeline, stage periods, heart-rate chart, or score remains visible.

- [ ] **Step 5: Verify removed UI and Vital Age arithmetic**

Scroll Home and Activity through their full content. Confirm there is no `Last night` card and no presented `Stress` row/tile. Open Vital Age, record each displayed signed impact, and confirm their one-decimal sum agrees with the displayed headline difference within one rounding unit. Confirm the VO2/Cardio row uses actual impact wording rather than a raw `+15 years` label.

- [ ] **Step 6: Fix only verified defects using a regression test first**

For any functional defect, add the smallest failing unit/ViewModel test to the responsible test class, run it to prove failure, apply the minimal fix, and rerun the focused plus complete test suite. For a visual-only defect, capture the before state, adjust the responsible shared token/component before introducing a screen-specific override, rebuild, reinstall, and capture the corrected state.

- [ ] **Step 7: Commit verification fixes if any and confirm status**

```powershell
git status --short
git log -8 --oneline --decorate
```

Expected: only user-owned `.claude/` and `.claude-flow/` remain untracked; no generated artifacts are staged. If verification required code changes, commit them with a focused `fix:` message after tests pass.
