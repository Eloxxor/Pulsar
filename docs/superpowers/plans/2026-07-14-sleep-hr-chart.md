# Sleep Heart Rate Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Sleep screen's bare stage-percentage bars with a heart-rate-over-time line chart (interactive drag/tap tooltip) synced to a sleep-stage timeline bar below it, for the most recent sleep session.

**Architecture:** Two small additive data-layer methods (`AggregationService.heartRateSamplesForSession`, `HealthRepository.sleepDetailForDate`) fetch fresh, non-persisted heart-rate-during-sleep data on demand. `SleepViewModel` downsamples the raw samples into 2-minute buckets (a pure, independently-tested function) and exposes them alongside the real stage periods already available from `SleepSession`. Two new hand-rolled Compose Canvas components render the chart and the stage bar — no new charting dependency, consistent with `LineChart`/`CircularGauge`.

**Tech Stack:** Existing project stack only — no new dependencies. Compose's built-in gesture APIs (`detectDragGestures`, `detectTapGestures`, `pointerInput`) drive the chart's tooltip.

## Global Constraints

- No new Room columns/schema changes — heart-rate-during-sleep and real stage periods are fetched fresh from Health Connect each time, never persisted (daily aggregate tables are for summary values, not raw time series).
- No new sleep-stage detection logic — stage periods come directly from `SleepSessionRecord.stages` via the existing `SleepSession`/`SleepStagePeriod` models, already correctly mapped since the original app build.
- Downsample heart-rate samples into 2-minute buckets before charting (raw samples can number in the thousands across a full night).
- Chart/gesture components (Task 5, 6) have no automated tests — Compose Canvas + gesture presentation, consistent with how `LineChart`/`CircularGauge` are already handled. Verified manually on-device.
- Every Bash command that runs `./gradlew` must export `JAVA_HOME` inline in the same command (shell state does not persist between tool calls): `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"`.
- Git identity isn't configured globally on this machine — commits must use `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "..."`.

## File Structure

```
app/src/main/java/com/esusic/recoverytracker/
  model/SleepDetail.kt                       // Create (Task 1): non-persisted detail bundle
  data/
    AggregationService.kt                     // Modify (Task 1): heartRateSamplesForSession
    HealthRepository.kt                       // Modify (Task 2): sleepDetailForDate
  ui/sleep/
    HeartRateBucketing.kt                      // Create (Task 3): HeartRateBucket + bucketing fn
    SleepViewModel.kt                           // Modify (Task 4): wire detail fetch + bucketing
    SleepHeartRateChart.kt                      // Create (Task 5): HR line chart + tooltip
    SleepStageTimelineBar.kt                    // Create (Task 6): stage color bar
    SleepScreen.kt                               // Modify (Task 7): wire both charts in
```

---

### Task 1: AggregationService.heartRateSamplesForSession

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/model/SleepDetail.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt`

**Interfaces:**
- Consumes: `HealthDataSource.heartRateSamples` (existing).
- Produces: `SleepDetail(session: SleepSession, heartRateSamples: List<HeartRateSample>)`. `AggregationService.heartRateSamplesForSession(session: SleepSession): List<HeartRateSample>`.
- Consumed by: Task 2 (`HealthRepository.sleepDetailForDate`).

- [ ] **Step 1: Write the failing test**

Add this test to the existing `app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt` (inside the `AggregationServiceTest` class, alongside the existing tests — do not remove or modify those):

```kotlin
@Test
fun `heartRateSamplesForSession queries the session's exact window, not the calendar day`() = runTest {
    val sessionStart = dayStart.minusSeconds(3600) // 23:00 the previous day, outside the calendar-day window
    val sessionEnd = dayStart.plusSeconds(3600)
    val sampleInWindow = HeartRateSample(sessionStart.plusSeconds(60), 55L)
    val sampleOutsideWindow = HeartRateSample(sessionEnd.plusSeconds(3600), 90L)
    val source = FakeHealthDataSource(heartRate = listOf(sampleInWindow, sampleOutsideWindow))
    val session = SleepSession(sessionStart, sessionEnd, emptyList())

    val result = AggregationService(source).heartRateSamplesForSession(session)

    assertEquals(listOf(sampleInWindow), result)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.AggregationServiceTest"
```
Expected: FAIL — `heartRateSamplesForSession` is unresolved.

- [ ] **Step 3: Create the SleepDetail model**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/model/SleepDetail.kt
package com.esusic.recoverytracker.model

data class SleepDetail(val session: SleepSession, val heartRateSamples: List<HeartRateSample>)
```

**Do NOT add a `// path/to/File.kt` comment as the literal first line** — the path shown above is a label, not file content. Start the file directly with `package com.esusic.recoverytracker.model`.

- [ ] **Step 4: Add the method to AggregationService**

Add this function to `app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt`, inside the `AggregationService` class, alongside `aggregateForDate` (don't remove or modify that function):

```kotlin
suspend fun heartRateSamplesForSession(session: SleepSession): List<HeartRateSample> =
    dataSource.heartRateSamples(session.start, session.end)
```

Add these two imports to the top of the file, alongside the existing ones:

```kotlin
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.SleepSession
```

- [ ] **Step 5: Run test to verify it passes**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.AggregationServiceTest"
```
Expected: PASS (12 tests — the 11 pre-existing plus the 1 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/model/SleepDetail.kt app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add AggregationService.heartRateSamplesForSession and SleepDetail model"
```

---

### Task 2: HealthRepository.sleepDetailForDate

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt`

**Interfaces:**
- Consumes: `SleepDetail`, `AggregationService.heartRateSamplesForSession` (Task 1).
- Produces: `HealthRepository.sleepDetailForDate(date: LocalDate): SleepDetail?`.
- Consumed by: Task 4 (`SleepViewModel`).

- [ ] **Step 1: Write the failing tests**

Add these tests to the existing `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt` (inside the `HealthRepositoryTest` class, alongside the existing four `@Test` functions — do not remove or modify those). Also add `import org.junit.Assert.assertNull` alongside the existing `org.junit.Assert.*` imports at the top of the file.

```kotlin
@Test
fun `sleepDetailForDate returns null when there is no sleep session`() = runTest {
    val result = repositoryWith(FakeHealthDataSource()).sleepDetailForDate(date)
    assertNull(result)
}

@Test
fun `sleepDetailForDate returns the session and its heart rate samples when sleep exists`() = runTest {
    val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
    val session = SleepSession(dayStart, dayStart.plusSeconds(6 * 3600), emptyList())
    val hrSample = HeartRateSample(dayStart.plusSeconds(60), 55L)
    val source = FakeHealthDataSource(sleep = listOf(session), heartRate = listOf(hrSample))

    val result = repositoryWith(source).sleepDetailForDate(date)

    assertEquals(session, result?.session)
    assertEquals(listOf(hrSample), result?.heartRateSamples)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.HealthRepositoryTest"
```
Expected: FAIL — `sleepDetailForDate` is unresolved.

- [ ] **Step 3: Implement**

Add this function to `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt`, inside the `HealthRepository` class, alongside `syncDate`/`syncToday` (don't remove or modify those):

```kotlin
suspend fun sleepDetailForDate(date: LocalDate): SleepDetail? {
    val aggregate = aggregationService.aggregateForDate(date)
    val session = aggregate.sleep ?: return null
    val heartRateSamples = aggregationService.heartRateSamplesForSession(session)
    return SleepDetail(session, heartRateSamples)
}
```

Add this import to the top of the file, alongside the existing ones:

```kotlin
import com.esusic.recoverytracker.model.SleepDetail
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.HealthRepositoryTest"
```
Expected: PASS (6 tests — the 4 pre-existing plus the 2 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add HealthRepository.sleepDetailForDate"
```

---

### Task 3: Heart Rate Bucketing

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/HeartRateBucketing.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/sleep/HeartRateBucketingTest.kt`

**Interfaces:**
- Consumes: `HeartRateSample` (existing model).
- Produces: `HeartRateBucket(time: Instant, avgBpm: Double)`, `bucketHeartRateSamples(samples: List<HeartRateSample>, sessionStart: Instant, bucketSize: Duration = Duration.ofMinutes(2)): List<HeartRateBucket>`.
- Consumed by: Task 4 (`SleepViewModel`), Task 5 (`SleepHeartRateChart`, via the ViewModel's already-bucketed state).

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/ui/sleep/HeartRateBucketingTest.kt
package com.esusic.recoverytracker.ui.sleep

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HeartRateBucketingTest {
    private val sessionStart = Instant.parse("2026-07-13T23:00:00Z")

    @Test
    fun `buckets evenly spaced samples into 2-minute averages`() {
        val samples = listOf(
            HeartRateSample(sessionStart, 60L),
            HeartRateSample(sessionStart.plusSeconds(30), 62L),
            HeartRateSample(sessionStart.plusSeconds(150), 70L),
            HeartRateSample(sessionStart.plusSeconds(170), 74L)
        )
        val result = bucketHeartRateSamples(samples, sessionStart, Duration.ofMinutes(2))
        assertEquals(2, result.size)
        assertEquals(61.0, result[0].avgBpm, 0.0001)
        assertEquals(72.0, result[1].avgBpm, 0.0001)
    }

    @Test
    fun `skips buckets with no samples rather than inserting gaps`() {
        val samples = listOf(
            HeartRateSample(sessionStart, 60L),
            HeartRateSample(sessionStart.plusSeconds(600), 80L)
        )
        val result = bucketHeartRateSamples(samples, sessionStart, Duration.ofMinutes(2))
        assertEquals(2, result.size)
    }

    @Test
    fun `single sample produces a single bucket`() {
        val samples = listOf(HeartRateSample(sessionStart.plusSeconds(500), 65L))
        val result = bucketHeartRateSamples(samples, sessionStart, Duration.ofMinutes(2))
        assertEquals(1, result.size)
        assertEquals(65.0, result.single().avgBpm, 0.0001)
    }

    @Test
    fun `empty input produces no buckets`() {
        val result = bucketHeartRateSamples(emptyList(), sessionStart, Duration.ofMinutes(2))
        assertEquals(0, result.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.sleep.HeartRateBucketingTest"
```
Expected: FAIL — `bucketHeartRateSamples`/`HeartRateBucket` are unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/sleep/HeartRateBucketing.kt
package com.esusic.recoverytracker.ui.sleep

import com.esusic.recoverytracker.model.HeartRateSample
import java.time.Duration
import java.time.Instant

data class HeartRateBucket(val time: Instant, val avgBpm: Double)

fun bucketHeartRateSamples(
    samples: List<HeartRateSample>,
    sessionStart: Instant,
    bucketSize: Duration = Duration.ofMinutes(2)
): List<HeartRateBucket> {
    if (samples.isEmpty()) return emptyList()
    val bucketSizeMillis = bucketSize.toMillis()
    return samples
        .groupBy { Duration.between(sessionStart, it.time).toMillis() / bucketSizeMillis }
        .toSortedMap()
        .map { (bucketIndex, bucketSamples) ->
            val bucketTime = sessionStart.plusMillis(bucketIndex * bucketSizeMillis)
            HeartRateBucket(bucketTime, bucketSamples.map { it.bpm }.average())
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.sleep.HeartRateBucketingTest"
```
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep/HeartRateBucketing.kt app/src/test/java/com/esusic/recoverytracker/ui/sleep/HeartRateBucketingTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add heart rate sample bucketing for the sleep chart"
```

---

### Task 4: SleepViewModel Wiring

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt`

**Interfaces:**
- Consumes: `HealthRepository.sleepDetailForDate` (Task 2), `bucketHeartRateSamples`/`HeartRateBucket` (Task 3).
- Produces: `SleepUiState` gains `heartRateBuckets: List<HeartRateBucket> = emptyList()`, `stagePeriods: List<SleepStagePeriod> = emptyList()`, `sessionStart: Instant? = null`, `sessionEnd: Instant? = null`.
- Consumed by: Task 7 (`SleepScreen.kt`).

- [ ] **Step 1: Replace the full file**

Read the current file first (`app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt`) to confirm it matches the state established by the Sleep Performance plan before replacing.

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt` with:

```kotlin
package com.esusic.recoverytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.model.SleepStagePeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class SleepUiState(
    val totalMinutes: Long? = null,
    val deepMinutes: Long? = null,
    val remMinutes: Long? = null,
    val lightMinutes: Long? = null,
    val deepPercent: Float = 0f,
    val remPercent: Float = 0f,
    val lightPercent: Float = 0f,
    val typicalTotalMinutes: Double? = null,
    val heartRateBuckets: List<HeartRateBucket> = emptyList(),
    val stagePeriods: List<SleepStagePeriod> = emptyList(),
    val sessionStart: Instant? = null,
    val sessionEnd: Instant? = null
)

class SleepViewModel(private val repository: HealthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecentAggregates(30).collect { aggregates ->
                val latest = aggregates.firstOrNull { it.sleepDurationMinutes != null }
                val history = aggregates.mapNotNull { it.sleepDurationMinutes }
                val typical = if (history.isEmpty()) null else history.average()

                if (latest?.sleepDurationMinutes == null || latest.sleepDurationMinutes <= 0) {
                    _uiState.value = _uiState.value.copy(
                        totalMinutes = null,
                        deepMinutes = null,
                        remMinutes = null,
                        lightMinutes = null,
                        deepPercent = 0f,
                        remPercent = 0f,
                        lightPercent = 0f,
                        typicalTotalMinutes = typical
                    )
                } else {
                    val total = latest.sleepDurationMinutes
                    val deep = latest.deepSleepMinutes ?: 0L
                    val rem = latest.remSleepMinutes ?: 0L
                    val light = latest.lightSleepMinutes ?: 0L
                    _uiState.value = _uiState.value.copy(
                        totalMinutes = total,
                        deepMinutes = deep,
                        remMinutes = rem,
                        lightMinutes = light,
                        deepPercent = deep.toFloat() / total,
                        remPercent = rem.toFloat() / total,
                        lightPercent = light.toFloat() / total,
                        typicalTotalMinutes = typical
                    )
                }
            }
        }

        viewModelScope.launch {
            val detail = repository.sleepDetailForDate(LocalDate.now())
            if (detail != null) {
                _uiState.value = _uiState.value.copy(
                    heartRateBuckets = bucketHeartRateSamples(detail.heartRateSamples, detail.session.start),
                    stagePeriods = detail.session.stages,
                    sessionStart = detail.session.start,
                    sessionEnd = detail.session.end
                )
            }
        }
    }
}
```

Note: the reactive `observeRecentAggregates` block now uses `_uiState.value.copy(...)` instead of constructing a fresh `SleepUiState(...)`, so it no longer clobbers the chart fields (`heartRateBuckets`/`stagePeriods`/`sessionStart`/`sessionEnd`) set by the second `init` block — both blocks update the same state independently without racing each other's fields.

- [ ] **Step 2: Verify existing tests still pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.sleep.SleepViewModelTest"
```
Expected: PASS (1 test, unchanged).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: fetch and bucket sleep heart rate detail in SleepViewModel"
```

---

### Task 5: SleepHeartRateChart Component

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepHeartRateChart.kt`

**Interfaces:**
- Consumes: `HeartRateBucket` (Task 3).
- Produces: `SleepHeartRateChart(buckets: List<HeartRateBucket>, sessionStart: Instant, sessionEnd: Instant, modifier: Modifier = Modifier)`.
- Consumed by: Task 7 (`SleepScreen.kt`).

- [ ] **Step 1: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepHeartRateChart.kt
package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SleepHeartRateChart(
    buckets: List<HeartRateBucket>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(buckets) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(buckets) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            selectedIndex = nearestBucketIndex(buckets, change.position.x, size.width.toFloat())
                        },
                        onDragEnd = { selectedIndex = null }
                    )
                }
                .pointerInput(buckets) {
                    detectTapGestures(
                        onPress = { offset ->
                            selectedIndex = nearestBucketIndex(buckets, offset.x, size.width.toFloat())
                            tryAwaitRelease()
                            selectedIndex = null
                        }
                    )
                }
        ) {
            if (buckets.size < 2) return@Canvas

            val minBpm = buckets.minOf { it.avgBpm }
            val maxBpm = buckets.maxOf { it.avgBpm }
            val bpmRange = (maxBpm - minBpm).takeIf { it > 0.0 } ?: 1.0
            val totalDurationMillis = Duration.between(sessionStart, sessionEnd).toMillis().toFloat()
            if (totalDurationMillis <= 0f) return@Canvas

            fun xFor(time: Instant): Float =
                (Duration.between(sessionStart, time).toMillis() / totalDurationMillis) * size.width

            fun yFor(bpm: Double): Float =
                size.height - ((bpm - minBpm) / bpmRange).toFloat() * size.height

            val points = buckets.map { Offset(xFor(it.time), yFor(it.avgBpm)) }
            for (i in 0 until points.size - 1) {
                drawLine(color = Color(0xFFE8503D), start = points[i], end = points[i + 1], strokeWidth = 3f)
            }

            selectedIndex?.let { index ->
                val point = points.getOrNull(index) ?: return@let
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, size.height),
                    strokeWidth = 2f
                )
                drawCircle(color = Color(0xFFE8503D), radius = 6f, center = point)
            }
        }

        selectedIndex?.let { index ->
            val bucket = buckets.getOrNull(index)
            if (bucket != null) {
                val timeLabel = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(bucket.time)
                Text("${bucket.avgBpm.roundToInt()} bpm at $timeLabel", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun nearestBucketIndex(buckets: List<HeartRateBucket>, x: Float, width: Float): Int {
    if (buckets.isEmpty()) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    val index = (fraction * (buckets.size - 1)).roundToInt()
    return index.coerceIn(0, buckets.size - 1)
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (Nothing calls `SleepHeartRateChart` yet, so this only proves the file itself compiles standalone.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepHeartRateChart.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add SleepHeartRateChart with drag/tap tooltip"
```

---

### Task 6: SleepStageTimelineBar Component

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepStageTimelineBar.kt`

**Interfaces:**
- Consumes: `SleepStagePeriod`/`SleepStageType` (existing models).
- Produces: `SleepStageTimelineBar(stagePeriods: List<SleepStagePeriod>, sessionStart: Instant, sessionEnd: Instant, modifier: Modifier = Modifier)`.
- Consumed by: Task 7 (`SleepScreen.kt`).

- [ ] **Step 1: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepStageTimelineBar.kt
package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import java.time.Duration
import java.time.Instant

@Composable
fun SleepStageTimelineBar(
    stagePeriods: List<SleepStagePeriod>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(24.dp)) {
        val totalDurationMillis = Duration.between(sessionStart, sessionEnd).toMillis().toFloat()
        if (totalDurationMillis <= 0f) return@Canvas

        stagePeriods.forEach { period ->
            val startX = (Duration.between(sessionStart, period.start).toMillis() / totalDurationMillis) * size.width
            val endX = (Duration.between(sessionStart, period.end).toMillis() / totalDurationMillis) * size.width
            drawRect(
                color = stageColor(period.stage),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, size.height)
            )
        }
    }
}

private fun stageColor(stage: SleepStageType): Color = when (stage) {
    SleepStageType.DEEP -> Color(0xFF3B3BB0)
    SleepStageType.REM -> Color(0xFF9B6BFF)
    SleepStageType.LIGHT -> Color(0xFF6BAFFF)
    SleepStageType.AWAKE -> Color(0xFFE8A33D)
    SleepStageType.UNKNOWN -> Color(0xFF444444)
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepStageTimelineBar.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add SleepStageTimelineBar component"
```

---

### Task 7: Sleep Screen Integration

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt`

**Interfaces:**
- Consumes: `SleepUiState` new fields (Task 4), `SleepHeartRateChart` (Task 5), `SleepStageTimelineBar` (Task 6).
- Produces: no new public interface — this is the screen's own composable.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt` with:

```kotlin
package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SleepScreen(viewModel: SleepViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sleep", style = MaterialTheme.typography.headlineMedium)

        if (uiState.totalMinutes == null) {
            Text("No sleep data synced yet.")
            return@Column
        }

        val sessionStart = uiState.sessionStart
        val sessionEnd = uiState.sessionEnd
        if (sessionStart != null && sessionEnd != null && uiState.heartRateBuckets.isNotEmpty()) {
            SleepHeartRateChart(
                buckets = uiState.heartRateBuckets,
                sessionStart = sessionStart,
                sessionEnd = sessionEnd
            )
            SleepStageTimelineBar(
                stagePeriods = uiState.stagePeriods,
                sessionStart = sessionStart,
                sessionEnd = sessionEnd
            )
        }

        Text("${uiState.totalMinutes!! / 60}h ${uiState.totalMinutes!! % 60}m total", style = MaterialTheme.typography.headlineSmall)

        uiState.typicalTotalMinutes?.let {
            val diff = uiState.totalMinutes!! - it
            val comparison = if (diff >= 0) "above" else "below"
            Text("${kotlin.math.abs(diff).toInt()} min $comparison your typical sleep duration")
        }

        listOf(
            Triple("Deep", uiState.deepPercent, uiState.deepMinutes),
            Triple("REM", uiState.remPercent, uiState.remMinutes),
            Triple("Light", uiState.lightPercent, uiState.lightMinutes)
        ).forEach { (label, percent, minutes) ->
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text("${minutes}m")
                }
                LinearProgressIndicator(progress = { percent }, modifier = Modifier.fillMaxWidth().height(8.dp))
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all tests pass (this task doesn't touch `SleepViewModel`, so `SleepViewModelTest` must still pass unchanged).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: wire heart rate chart and stage timeline into Sleep screen"
```

- [ ] **Step 4: Manual on-device verification checklist (executed by the user)**

1. Install the fresh APK (no uninstall needed — no schema change this time).
2. Open the Sleep tab. If last night's sleep has synced, confirm the HR chart renders above a colored stage timeline bar, both spanning the same time range.
3. Drag a finger across the HR chart and confirm a vertical crosshair line and a "XX bpm at HH:mm" label appear and track the touch position.
4. Tap (not drag) a point on the chart and confirm the same tooltip briefly appears then clears on release.
5. Confirm the stage timeline bar's colored segments roughly line up in time with the HR chart above it (e.g. a segment of unusually low/stable HR should generally correspond to a Deep-colored segment).
6. Confirm the existing duration/baseline/stage-minutes summary below the charts still renders correctly, unchanged from before.
