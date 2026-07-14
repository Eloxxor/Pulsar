# Sleep Performance Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Whoop-style Sleep Performance score (0-100) computed from sleep duration vs. a strain- and debt-adjusted personal need, and render it as a third circular gauge (Sleep — Recovery — Strain) on the Today screen.

**Architecture:** A new pure-Kotlin `SleepPerformanceCalculator` (scoring package) reuses `typicalSleepMinutes` and `priorDayStrain`, both already computed in `HealthRepository.syncDate` for the Recovery score. `DailyScoreEntity` gains one nullable `Int` column. The value flows through the same path every other score field already takes: entity → `TodayUiState` → `TodayScreen`.

**Tech Stack:** Existing project stack only — no new dependencies.

## Global Constraints

- `AppDatabase` is `version = 1` with no `Migration` and no destructive-fallback configured today. Adding a column requires bumping to `version = 2` AND adding `.fallbackToDestructiveMigration()` to the `Room.databaseBuilder` call in `ServiceLocator.kt` — see the design spec's "Database Migration" section for why a full migration is deliberately out of scope here (pre-release, single-user, negligible data at stake).
- Sleep Debt: `min(180, sum over trailing 7 days of max(0, typicalSleepMinutes - actualThatDay))`.
- Sleep Need: `typicalSleepMinutes + strainAdjustment + debtRepayment`, where `strainAdjustment = (priorDayStrain.value / 21.0) * 60.0` and `debtRepayment = min(accumulatedDebtMinutes, 60.0)`.
- Sleep Performance: `(actualSleepMinutes / sleepNeedMinutes * 100).coerceIn(0, 100)`, rounded to `Int`; `null` when there's no sleep session for the day (never defaulted to 0).
- No changes to the existing `sleepSubScore` / `RecoveryScoreCalculator` formula — Sleep Performance is additive, not a replacement.
- No new automated tests for the Compose UI change (Task 5) — presentation-only, consistent with the rest of the Today screen.
- Every Bash command that runs `./gradlew` must export `JAVA_HOME` inline in the same command (shell state does not persist between tool calls): `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"`.
- Git identity isn't configured globally on this machine — commits must use `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "..."`.

## File Structure

```
app/src/main/java/com/esusic/recoverytracker/
  scoring/
    SleepPerformanceCalculator.kt   // Create (Task 1)
  data/local/
    DailyScoreEntity.kt              // Modify (Task 2): add sleepPerformance column
    AppDatabase.kt                   // Modify (Task 2): version 1 -> 2
  ServiceLocator.kt                  // Modify (Task 2): fallbackToDestructiveMigration
  data/
    HealthRepository.kt              // Modify (Task 3): compute + persist sleepPerformance
  ui/today/
    TodayViewModel.kt                // Modify (Task 4): sleepPerformance in TodayUiState
  ui/theme/
    Theme.kt                         // Modify (Task 5): add tertiary (violet) color
  ui/today/
    TodayScreen.kt                   // Modify (Task 5): three-ring row
```

---

### Task 1: SleepPerformanceCalculator

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculator.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculatorTest.kt`

**Interfaces:**
- Consumes: `StrainScore` (existing model, `com.esusic.recoverytracker.model.StrainScore`, has `.value: Double`).
- Produces: `SleepPerformanceCalculator.calculateSleepDebtMinutes(typicalSleepMinutes: Double, recentActualSleepMinutes: List<Long>): Double`, `SleepPerformanceCalculator.calculate(actualSleepMinutes: Long?, typicalSleepMinutes: Double, priorDayStrain: StrainScore?, accumulatedDebtMinutes: Double): Int?`.
- Consumed by: Task 3 (`HealthRepository.syncDate`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculatorTest.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.StrainScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SleepPerformanceCalculatorTest {
    private val date = LocalDate.of(2026, 7, 14)

    @Test
    fun `calculateSleepDebtMinutes sums shortfall across trailing window, capped at 180`() {
        val recent = List(10) { 420L } // 60 min short every night for 10 nights
        val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(480.0, recent)
        assertEquals(180.0, debt, 0.0001)
    }

    @Test
    fun `calculateSleepDebtMinutes ignores nights that met or exceeded baseline`() {
        val recent = listOf(480L, 500L, 480L)
        val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(480.0, recent)
        assertEquals(0.0, debt, 0.0001)
    }

    @Test
    fun `calculateSleepDebtMinutes only considers the trailing 7 days`() {
        val oldShortNight = listOf(0L) // would add 480 min of shortfall if counted
        val recentFullNights = List(7) { 480L }
        val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(480.0, oldShortNight + recentFullNights)
        assertEquals(0.0, debt, 0.0001)
    }

    @Test
    fun `calculate returns null when actual sleep is null`() {
        val result = SleepPerformanceCalculator.calculate(null, 480.0, null, 0.0)
        assertNull(result)
    }

    @Test
    fun `calculate returns 100 when actual sleep exactly meets need with no adjustments`() {
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0)
        assertEquals(100, result)
    }

    @Test
    fun `high prior-day strain increases need and lowers performance for the same actual sleep`() {
        val noStrain = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0)
        val maxStrain = SleepPerformanceCalculator.calculate(480L, 480.0, StrainScore(date, 21.0), 0.0)
        assertEquals(100, noStrain)
        // need = 480 + (21/21 * 60) = 540; 480/540*100 = 88.89 -> rounds to 89
        assertEquals(89, maxStrain)
    }

    @Test
    fun `debt repayment is capped at 60 minutes regardless of larger accumulated debt`() {
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 180.0)
        // need = 480 + min(180, 60) = 540; 480/540*100 = 88.89 -> rounds to 89
        assertEquals(89, result)
    }

    @Test
    fun `performance is clamped at 100 when actual sleep exceeds need`() {
        val result = SleepPerformanceCalculator.calculate(600L, 480.0, null, 0.0)
        assertEquals(100, result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.SleepPerformanceCalculatorTest"
```
Expected: FAIL — `SleepPerformanceCalculator` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculator.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.StrainScore
import kotlin.math.roundToInt

object SleepPerformanceCalculator {
    private const val DEBT_WINDOW_DAYS = 7
    private const val MAX_ACCUMULATED_DEBT_MINUTES = 180.0
    private const val MAX_STRAIN_ADJUSTMENT_MINUTES = 60.0
    private const val MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES = 60.0
    private const val MAX_STRAIN = 21.0

    fun calculateSleepDebtMinutes(typicalSleepMinutes: Double, recentActualSleepMinutes: List<Long>): Double {
        val windowed = recentActualSleepMinutes.takeLast(DEBT_WINDOW_DAYS)
        val totalShortfall = windowed.sumOf { (typicalSleepMinutes - it).coerceAtLeast(0.0) }
        return totalShortfall.coerceAtMost(MAX_ACCUMULATED_DEBT_MINUTES)
    }

    fun calculate(
        actualSleepMinutes: Long?,
        typicalSleepMinutes: Double,
        priorDayStrain: StrainScore?,
        accumulatedDebtMinutes: Double
    ): Int? {
        if (actualSleepMinutes == null || typicalSleepMinutes <= 0.0) return null

        val strainAdjustment = ((priorDayStrain?.value ?: 0.0) / MAX_STRAIN) * MAX_STRAIN_ADJUSTMENT_MINUTES
        val debtRepayment = accumulatedDebtMinutes.coerceIn(0.0, MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES)
        val sleepNeedMinutes = typicalSleepMinutes + strainAdjustment + debtRepayment

        val performance = (actualSleepMinutes / sleepNeedMinutes) * 100.0
        return performance.coerceIn(0.0, 100.0).roundToInt()
    }
}
```

**Do NOT add a `// path/to/File.kt` comment as the literal first line of either file** — the path shown above each code block is a label, not file content. Start each file directly with `package com.esusic.recoverytracker.scoring`.

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.SleepPerformanceCalculatorTest"
```
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculator.kt app/src/test/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculatorTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add SleepPerformanceCalculator"
```

---

### Task 2: Database Schema — sleepPerformance Column

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/DailyScoreEntity.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `DailyScoreEntity` gains `val sleepPerformance: Int? = null` (defaulted, so every existing construction call site — including `HealthRepository.kt` before Task 3 updates it, and the existing `DailyScoreDaoTest.kt` — keeps compiling unchanged).
- Consumed by: Task 3 (`HealthRepository.kt`, which sets a real value), Task 4 (`TodayViewModel.kt`, which reads it).

- [ ] **Step 1: Add the column**

In `app/src/main/java/com/esusic/recoverytracker/data/local/DailyScoreEntity.kt`, add `sleepPerformance` as the last field of the `@Entity` data class:

```kotlin
@Entity(tableName = "daily_scores")
data class DailyScoreEntity(
    @PrimaryKey val date: String,
    val recoveryValue: Int,
    val recoveryIsCalibrating: Boolean,
    val hrvSubScore: Double,
    val rhrSubScore: Double,
    val sleepSubScore: Double,
    val strainValue: Double,
    val lastSyncedAtEpochMillis: Long,
    val isComplete: Boolean,
    val sleepPerformance: Int? = null
)
```

(Only the `data class` body changes — the `@Dao interface DailyScoreDao` below it in the same file is untouched.)

- [ ] **Step 2: Bump the database version**

In `app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt`, change:

```kotlin
@Database(
    entities = [DailyScoreEntity::class, DailyAggregateEntity::class],
    version = 2,
    exportSchema = true
)
```

(only the `version` value changes, from `1` to `2`).

- [ ] **Step 3: Add destructive fallback to the database builder**

In `app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt`, find the `database` lazy property and change:

```kotlin
val database: AppDatabase by lazy {
    Room.databaseBuilder(appContext, AppDatabase::class.java, "recovery_tracker.db")
        .fallbackToDestructiveMigration()
        .build()
}
```

- [ ] **Step 4: Verify existing tests still pass unchanged**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all existing tests (including `DailyScoreDaoTest`, which constructs `DailyScoreEntity` without the new field) still pass — this proves the default value keeps every existing call site compiling. Note the schema JSON file that `exportSchema = true` generates under `app/schemas/` for version 2 as a build artifact — do not hand-edit it, and do not commit it (it's gitignored via the existing `app/schemas/` entry in `.gitignore`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/local/DailyScoreEntity.kt app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add sleepPerformance column, bump DB to version 2 with destructive fallback"
```

---

### Task 3: HealthRepository Wiring

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt`

**Interfaces:**
- Consumes: `SleepPerformanceCalculator` (Task 1), `DailyScoreEntity.sleepPerformance` (Task 2).
- Produces: `HealthRepository.syncDate` now populates `sleepPerformance` on the persisted `DailyScoreEntity`.
- Consumed by: Task 4 (`TodayViewModel`, reading `score.sleepPerformance`).

- [ ] **Step 1: Write the failing test**

Add this test to the existing `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt` (inside the `HealthRepositoryTest` class, alongside the existing three `@Test` functions — do not remove or modify those):

```kotlin
@Test
fun `syncDate computes and persists sleepPerformance for a day with a sleep session`() = runTest {
    val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
    val source = FakeHealthDataSource(
        heartRate = listOf(HeartRateSample(dayStart.plusSeconds(60), 70L)),
        sleep = listOf(SleepSession(dayStart, dayStart.plusSeconds(8 * 3600), emptyList()))
    )
    val result = repositoryWith(source).syncDate(date)
    assertTrue(result.sleepPerformance != null)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.HealthRepositoryTest"
```
Expected: FAIL — `result.sleepPerformance` is `null` (the field exists from Task 2 but `syncDate` never sets it yet).

- [ ] **Step 3: Implement**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt` with:

```kotlin
package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.local.DailyAggregateDao
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.data.local.DailyScoreDao
import com.esusic.recoverytracker.data.local.DailyScoreEntity
import com.esusic.recoverytracker.data.local.toEntity
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.StrainScore
import com.esusic.recoverytracker.scoring.BaselineCalculator
import com.esusic.recoverytracker.scoring.RecoveryScoreCalculator
import com.esusic.recoverytracker.scoring.SleepPerformanceCalculator
import com.esusic.recoverytracker.scoring.StrainScoreCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

class HealthRepository(
    private val aggregationService: AggregationService,
    private val scoreDao: DailyScoreDao,
    private val aggregateDao: DailyAggregateDao,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun observeRecentScores(days: Int): Flow<List<DailyScoreEntity>> = scoreDao.observeRecent(days)

    fun observeRecentAggregates(days: Int): Flow<List<DailyAggregateEntity>> = aggregateDao.observeRecent(days)

    suspend fun syncToday(): DailyScoreEntity = syncDate(LocalDate.now(clock))

    suspend fun syncDate(date: LocalDate): DailyScoreEntity {
        val aggregate = aggregationService.aggregateForDate(date)
        aggregateDao.upsert(aggregate.toEntity())

        val history = aggregateDao.recentBefore(date.toString(), BaselineCalculator.WINDOW_DAYS).reversed()
        val hrvBaseline = BaselineCalculator.calculate(history.mapNotNull { it.avgHrvMillis })
        val rhrBaseline = BaselineCalculator.calculate(history.mapNotNull { it.restingHeartRate?.toDouble() })
        val typicalSleepMinutes = history.mapNotNull { it.sleepDurationMinutes }
            .map { it.toDouble() }
            .let { if (it.isEmpty()) 480.0 else it.average() }
        val typicalDeepRemPercent = history.mapNotNull { entity ->
            val duration = entity.sleepDurationMinutes ?: return@mapNotNull null
            if (duration <= 0) return@mapNotNull null
            val deepRem = (entity.deepSleepMinutes ?: 0L) + (entity.remSleepMinutes ?: 0L)
            deepRem.toDouble() / duration * 100.0
        }.let { if (it.isEmpty()) 0.0 else it.average() }

        val maxHr = userPreferences.maxHeartRate.first()
        val strain = StrainScoreCalculator.calculate(date, aggregate.heartRateSamples, maxHr)

        val priorDayEntity = scoreDao.getByDate(date.minusDays(1).toString())
        val priorDayStrain = priorDayEntity?.let { StrainScore(date.minusDays(1), it.strainValue) }

        val recovery = RecoveryScoreCalculator.calculate(
            date = date,
            today = aggregate,
            hrvBaseline = hrvBaseline,
            rhrBaseline = rhrBaseline,
            typicalSleepMinutes = typicalSleepMinutes,
            typicalDeepRemPercent = typicalDeepRemPercent,
            priorDayStrain = priorDayStrain
        )

        val recentActualSleepMinutes = history.mapNotNull { it.sleepDurationMinutes }
        val sleepDebtMinutes = SleepPerformanceCalculator.calculateSleepDebtMinutes(
            typicalSleepMinutes,
            recentActualSleepMinutes
        )
        val sleepPerformance = SleepPerformanceCalculator.calculate(
            actualSleepMinutes = aggregate.sleep?.durationMinutes,
            typicalSleepMinutes = typicalSleepMinutes,
            priorDayStrain = priorDayStrain,
            accumulatedDebtMinutes = sleepDebtMinutes
        )

        val entity = DailyScoreEntity(
            date = date.toString(),
            recoveryValue = recovery.value,
            recoveryIsCalibrating = recovery.isCalibrating,
            hrvSubScore = recovery.hrvSubScore,
            rhrSubScore = recovery.rhrSubScore,
            sleepSubScore = recovery.sleepSubScore,
            strainValue = strain.value,
            lastSyncedAtEpochMillis = clock.millis(),
            isComplete = aggregate.isComplete,
            sleepPerformance = sleepPerformance
        )
        scoreDao.upsert(entity)
        return entity
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.HealthRepositoryTest"
```
Expected: PASS (4 tests — the 3 pre-existing plus the new one).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: compute and persist sleepPerformance in HealthRepository"
```

---

### Task 4: TodayViewModel Wiring

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`

**Interfaces:**
- Consumes: `DailyScoreEntity.sleepPerformance` (Task 2/3).
- Produces: `TodayUiState` gains `val sleepPerformance: Int? = null`.
- Consumed by: Task 5 (`TodayScreen.kt`).

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt` with:

```kotlin
package com.esusic.recoverytracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TodayUiState(
    val recoveryValue: Int? = null,
    val isCalibrating: Boolean = false,
    val strainValue: Double? = null,
    val sleepPerformance: Int? = null,
    val sleepDurationMinutes: Long? = null,
    val restingHeartRate: Long? = null,
    val avgHrvMillis: Double? = null,
    val steps: Int? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val hasAllPermissions: Boolean = true,
    val isRefreshing: Boolean = false
)

class TodayViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            val hasPermissions = healthDataSource.hasAllPermissions()
            if (!hasPermissions) {
                _uiState.value = _uiState.value.copy(hasAllPermissions = false, isRefreshing = false)
                return@launch
            }

            val score = repository.syncToday()
            val aggregate = repository.observeRecentAggregates(1).first().firstOrNull()

            _uiState.value = TodayUiState(
                recoveryValue = score.recoveryValue,
                isCalibrating = score.recoveryIsCalibrating,
                strainValue = score.strainValue,
                sleepPerformance = score.sleepPerformance,
                sleepDurationMinutes = aggregate?.sleepDurationMinutes,
                restingHeartRate = aggregate?.restingHeartRate,
                avgHrvMillis = aggregate?.avgHrvMillis,
                steps = aggregate?.steps,
                lastSyncedAtEpochMillis = score.lastSyncedAtEpochMillis,
                hasAllPermissions = true,
                isRefreshing = false
            )
        }
    }
}
```

- [ ] **Step 2: Verify existing tests still pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.today.TodayViewModelTest"
```
Expected: PASS (2 tests, unchanged — neither existing test fixture includes a sleep session, so `sleepPerformance` is legitimately `null` in both and no existing assertion touches it).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: surface sleepPerformance in TodayUiState"
```

---

### Task 5: Today Screen Three-Ring Row

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `TodayUiState.sleepPerformance` (Task 4), `CircularGauge` (already exists from the prior Today-screen-redesign plan).
- Produces: no new public interface — this is the screen's own composable.

- [ ] **Step 1: Add the violet tertiary color**

In `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`, add a `SleepViolet` color and pass it as `tertiary` to `darkColorScheme`:

```kotlin
private val SleepViolet = Color(0xFF9B6BFF)
```

Add this `private val` declaration alongside the existing `RecoveryGreen`/`StrainBlue`/`WarningAmber`/`DangerRed` declarations, then add `tertiary = SleepViolet` as a new named argument to the existing `darkColorScheme(...)` call (alongside its existing `primary`/`secondary`/`error`/`background`/`surface`/`surfaceVariant` arguments — don't remove or reorder those).

- [ ] **Step 2: Verify Theme.kt still compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Replace the ring section of TodayScreen.kt**

In `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`, replace the two separate `Box { CircularGauge(...) }` blocks for Recovery and Strain (currently two consecutive blocks, each `Modifier.fillMaxWidth()` with a centered `CircularGauge`) with a single three-item `Row`:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
) {
    CircularGauge(
        value = (uiState.sleepPerformance ?: 0).toFloat(),
        maxValue = 100f,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(90.dp)
    ) {
        Text(
            text = uiState.sleepPerformance?.toString() ?: "--",
            style = MaterialTheme.typography.titleMedium
        )
    }

    CircularGauge(
        value = (uiState.recoveryValue ?: 0).toFloat(),
        maxValue = 100f,
        color = recoveryColor(uiState.recoveryValue),
        isIndeterminate = uiState.isCalibrating,
        modifier = Modifier.size(140.dp)
    ) {
        Text(
            text = when {
                uiState.isCalibrating -> "Calibrating"
                uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                else -> "--"
            },
            style = if (uiState.isCalibrating) MaterialTheme.typography.bodySmall else MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
    }

    CircularGauge(
        value = (uiState.strainValue ?: 0.0).toFloat(),
        maxValue = 21f,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(90.dp)
    ) {
        Text(
            text = uiState.strainValue?.let { "%.1f".format(it) } ?: "--",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
```

This single `Row` replaces both prior `Box`-wrapped gauges — the Sleep card below (showing duration text) and the RHR/HRV/Steps quick-stats card are untouched, keep them exactly as they are in the current file.

- [ ] **Step 4: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all tests pass (this task doesn't touch `TodayViewModel`, so `TodayViewModelTest` must still pass unchanged).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: render Sleep/Recovery/Strain as a three-ring row on Today"
```

- [ ] **Step 6: Manual on-device verification checklist (executed by the user)**

1. Uninstall the existing app first (schema changed, destructive migration wipes on next launch anyway, but a clean uninstall avoids any doubt): `adb uninstall com.esusic.recoverytracker`, then install the fresh APK.
2. Complete onboarding again (uninstall wipes local data, including granted permissions' local record — Health Connect permissions themselves may still be granted at the OS level, but the app's own state resets).
3. Confirm three rings render side by side: violet Sleep on the left, green/amber/red Recovery in the middle (larger), blue Strain on the right.
4. Confirm Recovery still shows "Calibrating" (dashed ring) for the first ~7 days, consistent with before — this task doesn't change that behavior.
5. Confirm the Sleep ring shows "--" on a day with no sleep session synced yet, not a misleading "0".
6. Confirm the Sleep card below the rings (duration text) and the RHR/HRV/Steps row are both still present and unchanged.
