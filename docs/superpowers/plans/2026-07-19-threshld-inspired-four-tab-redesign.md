# Threshld-inspired Four-Tab Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Recovery Tracker as a light, explanation-first Android app with Home, Sleep, Activity, and Account roots while preserving every existing score, metric, history, and Vital Age flow.

**Architecture:** Keep Health Connect, aggregation, Room entities, migrations, and scoring unchanged. Add pure presentation helpers, feature-focused ViewModels for Activity, Account, and Recovery detail, and replace the current dark Compose screens with a shared light component system. Wire the four roots only after each feature compiles independently.

**Tech Stack:** Kotlin 2.x, Jetpack Compose Material 3, Navigation Compose, Coroutines/Flow, Room, DataStore, Health Connect, WorkManager, JUnit 4, Robolectric, Gradle 8.11.1, Android SDK 36, minSdk 26.

## Global Constraints

- Keep the existing Health Connect ingestion, Fitbit-origin filtering, Room schema version 4, and score formula version 4 unchanged.
- Use only original Recovery Tracker branding, text, and code; do not copy Threshld assets, proprietary text, subscription flows, or application code.
- The only root tabs are `home`, `sleep`, `activity`, and `account`; onboarding stays outside the root shell.
- Vital Age remains reachable from Home and Account.
- Existing metrics, day browsing, metric history, 7/30/90-day trends, pull-to-refresh, and permission management remain reachable.
- Unknown data renders as an em dash, not a measured zero; missing permissions preserve stored history.
- Do not add a backend, login, subscription, generative coaching, new font dependency, or new score formulas.
- Every behavioral change follows red-green-refactor; Compose-only layout steps must at minimum compile before commit.

---

### Task 1: Deterministic status and recommendation presentation

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/presentation/HealthStatusPresentation.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/presentation/HealthStatusPresentationTest.kt`

**Interfaces:**
- Consumes: existing `recoveryValue: Int?`, `strainValue: Double?`, `sleepPerformance: Int?`, and calibration flag.
- Produces: `data class StatusCopy(val label: String, val message: String)` plus `recoveryStatus`, `effortStatus`, `sleepStatus`, and `todayRecommendation`.

- [ ] **Step 1: Write the failing band-boundary tests**

```kotlin
package com.esusic.recoverytracker.ui.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusPresentationTest {
    @Test fun `recovery bands use approved labels at boundaries`() {
        assertEquals("Recover", recoveryStatus(39, false).label)
        assertEquals("Balanced", recoveryStatus(40, false).label)
        assertEquals("Ready", recoveryStatus(60, false).label)
        assertEquals("Optimal", recoveryStatus(80, false).label)
    }

    @Test fun `calibration overrides numeric recovery`() {
        assertEquals("Learning your baseline", recoveryStatus(90, true).label)
    }

    @Test fun `effort preserves the real zero to twenty-one scale`() {
        assertEquals("Resting", effortStatus(0.0).label)
        assertEquals("Light", effortStatus(5.0).label)
        assertEquals("Productive", effortStatus(10.0).label)
        assertEquals("High", effortStatus(16.0).label)
    }

    @Test fun `recommendation prioritizes recovery before current effort`() {
        assertEquals(
            "Prioritize rest and easy movement today.",
            todayRecommendation(recovery = 30, strain = 2.0, sleep = 90, calibrating = false)
        )
    }

    @Test fun `missing values have honest copy`() {
        assertEquals("No score yet", sleepStatus(null).label)
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*HealthStatusPresentationTest"`

Expected: compilation fails because the presentation functions do not exist.

- [ ] **Step 3: Implement the pure presentation helpers**

```kotlin
package com.esusic.recoverytracker.ui.presentation

data class StatusCopy(val label: String, val message: String)

fun recoveryStatus(score: Int?, calibrating: Boolean): StatusCopy = when {
    calibrating -> StatusCopy("Learning your baseline", "A few more days will improve this score.")
    score == null -> StatusCopy("No score yet", "Sync Health Connect to calculate recovery.")
    score >= 80 -> StatusCopy("Optimal", "You are well recovered.")
    score >= 60 -> StatusCopy("Ready", "Good capacity for a normal day.")
    score >= 40 -> StatusCopy("Balanced", "Keep intensity controlled.")
    else -> StatusCopy("Recover", "Prioritize rest and easy movement.")
}

fun effortStatus(strain: Double?): StatusCopy = when {
    strain == null -> StatusCopy("No load yet", "Activity will appear after the next sync.")
    strain >= 16.0 -> StatusCopy("High", "A demanding day. Leave room for recovery.")
    strain >= 10.0 -> StatusCopy("Productive", "Meaningful load without reaching the top range.")
    strain >= 5.0 -> StatusCopy("Light", "Light activity is accumulating.")
    else -> StatusCopy("Resting", "Your current load is low.")
}

fun sleepStatus(performance: Int?): StatusCopy = when {
    performance == null -> StatusCopy("No score yet", "Sleep detail will appear after a recorded night.")
    performance >= 85 -> StatusCopy("Excellent", "Strong duration and sleep quality.")
    performance >= 70 -> StatusCopy("Good", "A solid night with some room to improve.")
    performance >= 50 -> StatusCopy("Fair", "Protect recovery and aim for more sleep tonight.")
    else -> StatusCopy("Low", "A lighter day may help you recover.")
}

fun todayRecommendation(recovery: Int?, strain: Double?, sleep: Int?, calibrating: Boolean): String = when {
    calibrating -> "Keep wearing your tracker while Recovery Tracker learns your baseline."
    recovery == null -> "Sync your latest health data to get a daily recommendation."
    recovery < 40 -> "Prioritize rest and easy movement today."
    (strain ?: 0.0) >= 16.0 -> "Your load is already high. Keep the rest of today easy."
    recovery >= 80 && (sleep ?: 0) >= 70 -> "You have capacity for a challenging session today."
    recovery >= 60 -> "A normal training day fits your current recovery."
    else -> "Choose controlled intensity and reassess how you feel."
}
```

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run: `./gradlew testDebugUnitTest --tests "*HealthStatusPresentationTest"`

Expected: all five tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/presentation/HealthStatusPresentation.kt app/src/test/java/com/esusic/recoverytracker/ui/presentation/HealthStatusPresentationTest.kt
git commit -m "feat: add health status presentation"
```

---

### Task 2: Light visual system and reusable root components

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/theme/AppColors.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/common/SectionCard.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/common/RootPageHeader.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/common/StatusPill.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/common/MetricTile.kt`

**Interfaces:**
- Consumes: Material 3 theme APIs and the existing `MetricDelta`/`DeltaChip` presentation.
- Produces: light `RecoveryTrackerTheme`, shared page header, status pill, and clickable metric tile used by all four roots.

- [ ] **Step 1: Replace the color tokens and Material color scheme**

Implement these exact primary tokens in `AppColors.kt`:

```kotlin
val Canvas = Color(0xFFF5F5F2)
val Surface = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF171A1D)
val TextSecondary = Color(0xFF697077)
val TextMuted = Color(0xFF92989E)
val GridLine = Color(0xFFE3E5E2)
val NavBar = Surface
val Recovery = Color(0xFF39B66D)
val Strain = Color(0xFFE9823E)
val SleepViolet = Color(0xFF6279D9)
```

Keep named aliases `Hrv`, `Rhr`, `HeartRate`, `VitalAge`, `Warning`, `Danger`, and all sleep-stage colors, adjusted for readable use on white. In `Theme.kt`, replace `darkColorScheme` with `lightColorScheme`, map `background` to `Canvas`, `surface` to `Surface`, and set `outline` to `GridLine`.

- [ ] **Step 2: Implement shared components with stable semantics**

`RootPageHeader` signature:

```kotlin
@Composable
fun RootPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = false,
    nextEnabled: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
)
```

`StatusPill` signature:

```kotlin
@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier)
```

`MetricTile` signature:

```kotlin
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.TextPrimary,
    supportingText: String? = null,
    delta: MetricDelta? = null,
    deltaUnit: String = "",
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
)
```

Apply `Modifier.semantics { contentDescription = "$label, $value${unit?.let { " $it" } ?: ""}" }` to the tile. Update `SectionCard` to a 24dp radius, 18dp padding, white surface, and 1dp `GridLine` border.

- [ ] **Step 3: Compile the shared system**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`; existing dark screens may look different but must compile.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/theme app/src/main/java/com/esusic/recoverytracker/ui/common
git commit -m "feat: add light recovery tracker design system"
```

---

### Task 3: Home data and asymmetric readiness screen

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/home/HomeReadinessCards.kt`
- Delete after navigation switches in Task 8: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`
- Delete after navigation switches in Task 8: `app/src/main/java/com/esusic/recoverytracker/ui/today/MetricGridCards.kt`

**Interfaces:**
- Consumes: `TodayViewModel`, Task 1 presentation helpers, Task 2 common components.
- Produces: `HomeScreen(viewModel, onOpenRecovery, onOpenSleep, onOpenActivity, onOpenMetric, onOpenAge)` and added Home fields on `TodayUiState`.

- [ ] **Step 1: Add a failing Home-state test**

Append to `TodayViewModelTest.kt`:

```kotlin
@Test
fun `home state exposes chronological age and stored sleep stages`() = runTest {
    val today = LocalDate.now()
    val start = today.minusDays(1).atTime(22, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
    val sleep = com.esusic.recoverytracker.model.SleepSession(
        start = start,
        end = start.plusSeconds(8 * 3600L),
        stages = listOf(
            com.esusic.recoverytracker.model.SleepStagePeriod(
                com.esusic.recoverytracker.model.SleepStageType.DEEP,
                start,
                start.plusSeconds(90 * 60L)
            )
        )
    )
    val source = FakeHealthDataSource(sleep = listOf(sleep))
    val repository = repositoryWith(source)
    userPreferences.setBirthYear(today.year - 30)

    val viewModel = TodayViewModel(repository, source, userPreferences)
    viewModel.refresh()

    assertEquals(30, viewModel.uiState.value.chronologicalAge)
    assertEquals(90L, viewModel.uiState.value.deepSleepMinutes)
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*TodayViewModelTest.home state*"`

Expected: compilation fails because `chronologicalAge` and `deepSleepMinutes` are absent.

- [ ] **Step 3: Extend `TodayUiState` and its state mapping**

Add:

```kotlin
val deepSleepMinutes: Long? = null,
val remSleepMinutes: Long? = null,
val lightSleepMinutes: Long? = null,
val chronologicalAge: Int? = null,
```

Map the three stage fields from `DailyAggregateEntity`. Derive age with:

```kotlin
val birthYear = userPreferences.birthYear.first()
val chronologicalAge = birthYear?.let { selectedDate.year - it }
```

Preserve the existing Vital Age calculation and assign `chronologicalAge` to the new state.

- [ ] **Step 4: Run `TodayViewModelTest` and confirm GREEN**

Run: `./gradlew testDebugUnitTest --tests "*TodayViewModelTest"`

Expected: all Today tests pass.

- [ ] **Step 5: Build the Home layout**

Use this public signature:

```kotlin
@Composable
fun HomeScreen(
    viewModel: TodayViewModel,
    onOpenRecovery: (LocalDate) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
    onOpenAge: () -> Unit
)
```

`HomeReadinessCards.kt` owns the two-column layout and exports:

```kotlin
@Composable
fun ReadinessCluster(
    state: TodayUiState,
    onRecoveryClick: () -> Unit,
    onEffortClick: () -> Unit,
    onSleepClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

The Recovery card uses `Modifier.weight(1f).height(360.dp)`; the right column uses two
`Modifier.weight(1f)` cards with a 12dp gap. Render score/status/recommendation from Task 1.
Set the cards' semantics descriptions to exactly `Recovery card`, `Effort card`, and
`Sleep card` so device verification can locate them without coordinate assumptions.
Below it, render in this exact order: `Core metrics`, `Last night`, `Today's recommendation`,
`Vital Age`, `More vitals`, last-sync label. Keep pull-to-refresh and date browsing.

- [ ] **Step 6: Compile Home**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` with `HomeScreen` compiled even though navigation still points to the old Overview screen.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/home app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt
git commit -m "feat: add explanation-first home screen"
```

---

### Task 4: Pushed Recovery breakdown

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/recovery/RecoveryDetailViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/recovery/RecoveryDetailScreen.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/recovery/RecoveryDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository.observeRecentScores`, selected `LocalDate`, Task 1 presentation helpers.
- Produces: `RecoveryDetailUiState` and a full-screen breakdown with an explicit back action.

- [ ] **Step 1: Write a failing ViewModel mapping test**

```kotlin
@Test
fun `selected date maps stored score and component scores`() = runTest {
    val date = LocalDate.of(2026, 7, 13)
    val synchronousExecutor = Executor { it.run() }
    val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    )
        .setQueryExecutor(synchronousExecutor)
        .setTransactionExecutor(synchronousExecutor)
        .allowMainThreadQueries()
        .build()
    val preferences = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            tempFolder.newFile("recovery-detail.preferences_pb")
        }
    )
    val repository = HealthRepository(
        AggregationService(FakeHealthDataSource()),
        database.dailyScoreDao(),
        database.dailyAggregateDao(),
        preferences
    )
    val saved = repository.syncDate(date)

    val viewModel = RecoveryDetailViewModel(repository, date)
    viewModel.uiState.filter { !it.isLoading }.first()

    assertEquals(saved.recoveryValue, viewModel.uiState.value.recoveryValue)
    assertEquals(saved.hrvSubScore, viewModel.uiState.value.hrvSubScore, 0.001)
    assertEquals(date, viewModel.uiState.value.date)
}
```

Declare `TemporaryFolder`, set `Dispatchers.Main` to `UnconfinedTestDispatcher` in `@Before`,
and reset it in `@After`, matching the imports used by `TodayViewModelTest`.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*RecoveryDetailViewModelTest"`

Expected: compilation fails because the ViewModel does not exist.

- [ ] **Step 3: Implement the state loader**

```kotlin
data class RecoveryDetailUiState(
    val isLoading: Boolean = true,
    val date: LocalDate,
    val recoveryValue: Int? = null,
    val isCalibrating: Boolean = false,
    val hrvSubScore: Double? = null,
    val rhrSubScore: Double? = null,
    val sleepSubScore: Double? = null
)
```

Load `repository.observeRecentScores(60).first().find { it.date == date.toString() }` in
`viewModelScope`. Map all fields without recomputing scores.

- [ ] **Step 4: Build the full-screen breakdown**

```kotlin
@Composable
fun RecoveryDetailScreen(viewModel: RecoveryDetailViewModel, onBack: () -> Unit)
```

Render `DetailTopBar`, score/status hero, three component cards for HRV, resting HR, and
sleep, then the Task 1 recommendation. Use `—` when no stored score exists.

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew testDebugUnitTest --tests "*RecoveryDetailViewModelTest" assembleDebug`

Expected: focused tests pass and the debug build succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/recovery app/src/test/java/com/esusic/recoverytracker/ui/recovery
git commit -m "feat: add recovery breakdown screen"
```

---

### Task 5: Sleep root with weekly context

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/ui/sleep/SleepViewModelTest.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepWeeklyChart.kt`

**Interfaces:**
- Consumes: existing sleep aggregates, detail session, need, performance, and chart components.
- Produces: `weeklyNights`, `weeklyAverageMinutes`, and `weeklyInsight` on `SleepUiState`; root `SleepScreen(viewModel)` without a back button.

- [ ] **Step 1: Add a failing weekly-state test**

After syncing three nights with durations 420, 450, and 480 minutes, assert:

```kotlin
val viewModel = SleepViewModel(repository)
viewModel.uiState.filter { it.weeklyNights.size == 3 }.first()

assertEquals(450.0, viewModel.uiState.value.weeklyAverageMinutes!!, 0.001)
assertEquals(3, viewModel.uiState.value.weeklyNights.size)
assertTrue(viewModel.uiState.value.weeklyInsight!!.isNotBlank())
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*SleepViewModelTest*weekly*"`

Expected: compilation fails because weekly fields do not exist.

- [ ] **Step 3: Implement weekly state**

Add:

```kotlin
data class SleepNightPoint(val date: LocalDate, val durationMinutes: Long)

val weeklyNights: List<SleepNightPoint> = emptyList(),
val weeklyAverageMinutes: Double? = null,
val weeklyInsight: String? = null,
```

Build the seven newest aggregates with non-null sleep, reverse them chronologically, and
calculate their average. Set insight deterministically: fewer than two nights gives null;
latest at least 30 minutes above the earlier average gives `Your sleep duration improved this week.`;
latest at least 30 minutes below gives `Your latest night was shorter than your recent average.`;
otherwise use `Your sleep duration is holding steady.`

- [ ] **Step 4: Rebuild Sleep in the approved reading order**

Change the public signature to:

```kotlin
@Composable
fun SleepScreen(viewModel: SleepViewModel)
```

Render root header, hero summary, hypnogram, stages, hours-versus-need, wake events,
sleeping-heart-rate chart, then `SleepWeeklyChart`. Preserve partial-stage behavior.

- [ ] **Step 5: Run Sleep tests and compile**

Run: `./gradlew testDebugUnitTest --tests "*SleepViewModelTest" assembleDebug`

Expected: all Sleep tests pass and the debug build succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep app/src/test/java/com/esusic/recoverytracker/ui/sleep/SleepViewModelTest.kt
git commit -m "feat: redesign sleep as a root destination"
```

---

### Task 6: Activity root combining daily load and trends

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityScreen.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityTimeline.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/activity/ActivityViewModelTest.kt`
- Delete after navigation switches in Task 8: `app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsViewModel.kt`
- Delete after navigation switches in Task 8: `app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsScreen.kt`
- Delete after navigation switches in Task 8: `app/src/test/java/com/esusic/recoverytracker/ui/trends/TrendsViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository`, `HealthDataSource`, persisted daily scores/aggregates, and live step intervals.
- Produces: one `ActivityUiState` containing selected-day load plus the existing range-based trend models.

- [ ] **Step 1: Write failing Activity tests**

Cover both daily state and trend selection:

```kotlin
@Test fun `loads selected-day activity from persisted data`() = runTest {
    val instant = Instant.parse("2026-07-13T10:00:00Z")
    val source = FakeHealthDataSource(
        heartRate = listOf(HeartRateSample(instant, 90L)),
        restingHr = listOf(RestingHrSample(instant, 60L)),
        steps = 1234
    )
    val repository = newRepository(source, "activity-daily.preferences_pb")
    val date = LocalDate.of(2026, 7, 13)
    val saved = repository.syncDate(date)
    val fixedClock = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC)
    val viewModel = ActivityViewModel(repository, source, fixedClock)
    viewModel.uiState.filter { !it.isLoading }.first()
    assertEquals(saved.strainValue, viewModel.uiState.value.strainValue!!, 0.001)
    assertEquals(1234, viewModel.uiState.value.steps)
    assertEquals(saved.zoneMinutes, viewModel.uiState.value.zoneMinutes)
}

@Test fun `month range produces chronological recovery trends`() = runTest {
    val source = FakeHealthDataSource()
    val repository = newRepository(source, "activity-trends.preferences_pb")
    repository.syncDate(LocalDate.of(2026, 7, 12))
    repository.syncDate(LocalDate.of(2026, 7, 13))
    val fixedClock = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC)
    val viewModel = ActivityViewModel(repository, source, fixedClock)
    viewModel.selectRange(ActivityRange.MONTH)
    assertEquals(ActivityRange.MONTH, viewModel.uiState.value.range)
    assertEquals(viewModel.uiState.value.recovery.points.sortedBy { it.date }, viewModel.uiState.value.recovery.points)
}

private fun newRepository(source: FakeHealthDataSource, fileName: String): HealthRepository {
    val synchronousExecutor = Executor { it.run() }
    val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    )
        .setQueryExecutor(synchronousExecutor)
        .setTransactionExecutor(synchronousExecutor)
        .allowMainThreadQueries()
        .build()
    val preferences = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            tempFolder.newFile(fileName)
        }
    )
    return HealthRepository(
        AggregationService(source),
        database.dailyScoreDao(),
        database.dailyAggregateDao(),
        preferences
    )
}
```

Declare `TemporaryFolder`, set/reset `Dispatchers.Main`, and use the imports demonstrated by
`TodayViewModelTest` plus `Clock`, `ZoneOffset`, `HeartRateSample`, and `RestingHrSample`.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*ActivityViewModelTest"`

Expected: compilation fails because the Activity feature does not exist.

- [ ] **Step 3: Implement Activity state without duplicating formulas**

Define:

```kotlin
enum class ActivityRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

data class ActivityUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val strainValue: Double? = null,
    val recoveryValue: Int? = null,
    val steps: Int? = null,
    val zoneMinutes: Int? = null,
    val stressValue: Double? = null,
    val latestHeartRateBpm: Long? = null,
    val hourlySteps: List<Int> = List(24) { 0 },
    val range: ActivityRange = ActivityRange.WEEK,
    val recovery: MetricTrend = MetricTrend(),
    val strain: MetricTrend = MetricTrend(),
    val hrv: MetricTrend = MetricTrend(),
    val rhr: MetricTrend = MetricTrend()
)
```

Move `MetricTrend` into the activity package. Reuse the existing trend construction rules:
Recovery higher-is-better, Strain neutral, HRV higher-is-better, RHR lower-is-better. Read
hourly step intervals for the selected date with `runCatching`; a failure leaves 24 zero
buckets while persisted totals remain visible.

- [ ] **Step 4: Build Activity root**

```kotlin
@Composable
fun ActivityScreen(viewModel: ActivityViewModel)
```

Render header/date browsing, Effort hero, training-load recommendation, four daily tiles
(steps, zone minutes, stress, heart rate), `ActivityTimeline`, weekly summary, range chips,
then the four existing line charts.

- [ ] **Step 5: Run Activity tests and compile**

Run: `./gradlew testDebugUnitTest --tests "*ActivityViewModelTest" assembleDebug`

Expected: Activity tests pass and the debug build succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/activity app/src/test/java/com/esusic/recoverytracker/ui/activity
git commit -m "feat: combine daily load and trends in activity"
```

---

### Task 7: Account root and editable local profile

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/account/AccountViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/account/AccountScreen.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/account/AccountViewModelTest.kt`
- Delete after navigation switches in Task 8: `app/src/main/java/com/esusic/recoverytracker/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `UserPreferencesRepository`, `HealthDataSource.hasAllPermissions`, current clock/year.
- Produces: local profile state, birth-year update, permission refresh, Vital Age link, and system Health Connect settings action.

- [ ] **Step 1: Write failing Account tests**

```kotlin
private val fixedClock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC)

private fun newPreferences(fileName: String): UserPreferencesRepository =
    UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            tempFolder.newFile(fileName)
        }
    )

@Test fun `loads birth year age and permission state`() = runTest {
    val preferences = newPreferences("account-load.preferences_pb")
    preferences.setBirthYear(1996)
    val viewModel = AccountViewModel(preferences, FakeHealthDataSource(permissionsGranted = true), fixedClock)
    viewModel.uiState.filter { !it.isLoading }.first()
    assertEquals(1996, viewModel.uiState.value.birthYear)
    assertEquals(30, viewModel.uiState.value.age)
    assertEquals(true, viewModel.uiState.value.healthConnectConnected)
}

@Test fun `save birth year updates persisted profile`() = runTest {
    val preferences = newPreferences("account-save.preferences_pb")
    val viewModel = AccountViewModel(preferences, FakeHealthDataSource(), fixedClock)
    viewModel.saveBirthYear(1990)
    assertEquals(1990, preferences.birthYear.first())
}
```

Declare `TemporaryFolder`, set/reset `Dispatchers.Main`, and import `Clock`, `Instant`, and
`ZoneOffset` exactly as shown by the fixture code.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*AccountViewModelTest"`

Expected: compilation fails because Account does not exist.

- [ ] **Step 3: Implement Account state**

```kotlin
data class AccountUiState(
    val isLoading: Boolean = true,
    val birthYear: Int? = null,
    val age: Int? = null,
    val healthConnectConnected: Boolean = false
)
```

Expose `fun refreshPermissions()` and `fun saveBirthYear(year: Int)`. Accept only years
from `1900..LocalDate.now(clock).year`; invalid input leaves state and DataStore unchanged.

- [ ] **Step 4: Build Account root**

```kotlin
@Composable
fun AccountScreen(viewModel: AccountViewModel, onOpenAge: () -> Unit)
```

Render profile header, editable birth-year dialog, Vital Age row, Health Connect connection
card, `Manage Health Connect permissions` button using
`Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)`, the exact read-category list,
local-processing privacy text, and `Recovery Tracker v1.0`.

- [ ] **Step 5: Run Account tests and compile**

Run: `./gradlew testDebugUnitTest --tests "*AccountViewModelTest" assembleDebug`

Expected: Account tests pass and the debug build succeeds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/account app/src/test/java/com/esusic/recoverytracker/ui/account
git commit -m "feat: add local account and health connect settings"
```

---

### Task 8: Four-root navigation and detail back behavior

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RootDestination.kt`
- Create: `app/src/test/java/com/esusic/recoverytracker/ui/navigation/RootDestinationTest.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/MainActivity.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricDetailScreen.kt`
- Delete: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`
- Delete: `app/src/main/java/com/esusic/recoverytracker/ui/today/MetricGridCards.kt`
- Delete: `app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsScreen.kt`
- Delete: `app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsViewModel.kt`
- Delete: `app/src/test/java/com/esusic/recoverytracker/ui/trends/TrendsViewModelTest.kt`
- Delete: `app/src/main/java/com/esusic/recoverytracker/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Tasks 3–7 screens/ViewModels.
- Produces: exact four-root navigation, root-only bottom bar, pushed details, and `home` start destination.

- [ ] **Step 1: Write the failing root-contract test**

```kotlin
package com.esusic.recoverytracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDestinationTest {
    @Test fun `root destinations are exactly home sleep activity account`() {
        assertEquals(listOf("home", "sleep", "activity", "account"), RootDestination.entries.map { it.route })
    }

    @Test fun `bottom bar appears only on root routes`() {
        assertTrue(shouldShowBottomBar("home"))
        assertTrue(shouldShowBottomBar("sleep"))
        assertFalse(shouldShowBottomBar("age"))
        assertFalse(shouldShowBottomBar("recovery/2026-07-19"))
        assertFalse(shouldShowBottomBar("detail/HRV"))
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew testDebugUnitTest --tests "*RootDestinationTest"`

Expected: compilation fails because the root contract does not exist.

- [ ] **Step 3: Implement the root contract**

```kotlin
enum class RootDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    SLEEP("sleep", "Sleep"),
    ACTIVITY("activity", "Activity"),
    ACCOUNT("account", "Account")
}

fun shouldShowBottomBar(route: String?): Boolean =
    RootDestination.entries.any { it.route == route }
```

Keep icons in `RecoveryTrackerNavHost`: Home, Bedtime, DirectionsRun, Person.

- [ ] **Step 4: Wire all routes**

Change `MainActivity` and onboarding completion from `today` to `home`. In the NavHost,
show the bottom bar only when `shouldShowBottomBar(currentRoute)` is true. Wire:

```text
home -> HomeScreen / TodayViewModel
sleep -> SleepScreen / SleepViewModel
activity -> ActivityScreen / ActivityViewModel
account -> AccountScreen / AccountViewModel
recovery/{date} -> RecoveryDetailScreen / RecoveryDetailViewModel
age -> VitalAgeScreen
detail/{metricType} -> MetricDetailScreen
```

Root-tab clicks use `popUpTo(navController.graph.findStartDestination().id) { saveState = true }`,
`launchSingleTop = true`, and `restoreState = true`. Home callbacks navigate to roots with
the same helper and to pushed details with normal `navigate`.

- [ ] **Step 5: Add explicit Metric Detail back behavior**

Change the signature to:

```kotlin
@Composable
fun MetricDetailScreen(
    viewModel: MetricDetailViewModel,
    metricType: MetricType,
    onBack: () -> Unit
)
```

Render `DetailTopBar(title = metricType.label, onBack = onBack)` before the value and chart.

- [ ] **Step 6: Delete replaced roots and run all unit tests**

Run: `./gradlew testDebugUnitTest`

Expected: all tests pass; no source references `today`, `trends`, or `settings` as root routes.

- [ ] **Step 7: Compile the integrated app**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker app/src/test/java/com/esusic/recoverytracker
git commit -m "feat: switch to four-tab health navigation"
```

---

### Task 9: Accessibility, device verification, and visual polish

**Files:**
- Modify for verified visual corrections: `app/src/main/java/com/esusic/recoverytracker/ui/home/HomeScreen.kt`
- Modify for verified visual corrections: `app/src/main/java/com/esusic/recoverytracker/ui/home/HomeReadinessCards.kt`
- Modify for verified visual corrections: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt`
- Modify for verified visual corrections: `app/src/main/java/com/esusic/recoverytracker/ui/activity/ActivityScreen.kt`
- Modify for verified visual corrections: `app/src/main/java/com/esusic/recoverytracker/ui/account/AccountScreen.kt`
- Modify for verified visual corrections: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Update the matching ViewModel test whenever a polish correction changes observable behavior.

**Interfaces:**
- Consumes: integrated application from Task 8 and connected ADB device `RFCY70GP8BH`.
- Produces: verified four-root application with original branding and robust partial-data states.

- [ ] **Step 1: Run the complete automated verification from a clean Gradle invocation**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: both tasks finish with `BUILD SUCCESSFUL` and no failed tests.

- [ ] **Step 2: Install the debug APK without clearing app data**

Run: `adb -s RFCY70GP8BH install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`. Do not uninstall, clear data, or modify Health Connect records.

- [ ] **Step 3: Launch and inspect root semantics**

Run:

```powershell
adb -s RFCY70GP8BH shell am force-stop com.esusic.recoverytracker
adb -s RFCY70GP8BH shell monkey -p com.esusic.recoverytracker -c android.intent.category.LAUNCHER 1
adb -s RFCY70GP8BH exec-out uiautomator dump /dev/tty
```

Expected hierarchy contains `Home`, `Sleep`, `Activity`, and `Account`, with only `Home`
selected on launch.

- [ ] **Step 4: Exercise navigation and details**

Use this PowerShell helper to tap nodes by the exact Compose semantics description rather
than relying on fixed coordinates:

```powershell
function Invoke-RecoveryTrackerNode([string]$description) {
    $raw = adb -s RFCY70GP8BH exec-out uiautomator dump /dev/tty
    $match = [regex]::Match($raw, '<hierarchy[\s\S]*?</hierarchy>')
    [xml]$document = $match.Value
    $escaped = $description.Replace("'", "&apos;")
    $node = $document.SelectSingleNode("//*[@content-desc='$escaped']")
    if ($null -eq $node) { throw "UI node not found: $description" }
    $bounds = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($bounds[0] + $bounds[2]) / 2)
    $y = [int](($bounds[1] + $bounds[3]) / 2)
    adb -s RFCY70GP8BH shell input tap $x $y
    Start-Sleep -Milliseconds 500
}

Invoke-RecoveryTrackerNode 'Sleep'
Invoke-RecoveryTrackerNode 'Activity'
Invoke-RecoveryTrackerNode 'Account'
Invoke-RecoveryTrackerNode 'Home'
Invoke-RecoveryTrackerNode 'Recovery card'
```

Verify:

- all four roots open and retain their scroll/navigation state;
- Home previous/next day browsing respects stored bounds;
- Recovery, HRV/RHR, and Vital Age details hide the bottom bar and return correctly;
- Sleep shows timeline, stages, need, heart rate, and weekly context when data exists;
- Activity shows daily load plus range-switchable trends;
- Account opens Health Connect settings and returns without losing state;
- missing data is an em dash or explanatory state, never a fake zero;
- revoked permission messaging does not erase stored data.

- [ ] **Step 5: Capture and compare screenshots**

Capture one screenshot per root with `adb exec-out screencap -p`. Compare hierarchy,
spacing, card density, text wrapping, and status contrast against the inspected reference.
Adjust only visual tokens/layout or copy that violates the approved spec; do not add new
features during polish.

- [ ] **Step 6: Check runtime stability**

Run: `adb -s RFCY70GP8BH logcat -d -t 300`

Expected: no `FATAL EXCEPTION` from `com.esusic.recoverytracker` during the exercised flow.

- [ ] **Step 7: Re-run tests after polish and commit**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui app/src/test/java/com/esusic/recoverytracker/ui
git commit -m "fix: polish four-tab device experience"
```

## Plan self-review

- Spec coverage: Tasks 2–8 cover the light visual system, all four roots, Vital Age reachability,
  historical trends, day browsing, detail navigation, permission handling, and original copy.
- Data safety: no task alters Health Connect filters, Room entities, migrations, score formulas,
  or formula version. Device installation explicitly preserves app data.
- Type consistency: `StatusCopy`, `TodayUiState`, `SleepNightPoint`, `ActivityRange`,
  `ActivityUiState`, `AccountUiState`, and `RootDestination` are each defined before use.
- Scope: subscription, account backend, goals, cloud sync, and copied reference assets remain excluded.
- Verification: focused red-green tests precede production behavior; Tasks 8–9 run the full suite,
  debug compilation, ADB installation, semantics inspection, screenshots, and runtime logs.
