# Health Monitor Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Health Monitor" card to the Today screen showing RHR, HRV, respiratory rate, SpO2, skin temperature variation, and the most recent synced heart rate (with a cosmetic pulsing animation on a real, timestamped value — never a fabricated live reading).

**Architecture:** Three new Health Connect record types (`RespiratoryRateRecord`, `OxygenSaturationRecord`, `SkinTemperatureRecord`) flow through the same layered path every existing metric already takes: `HealthDataSource` interface → `HealthConnectRecordMapper` → `HealthConnectDataSource` → `AggregationService` → `DailyAggregateEntity`/DB → `TodayUiState` → `TodayScreen`. The "latest heart rate" reuses the `heartRateSamples` list `AggregationService` already fetches — no new query.

**Tech Stack:** Existing project stack only — no new dependencies. Compose's built-in `androidx.compose.animation.core` APIs (already transitively available, same artifact family as the rest of Compose) drive the pulsing icon.

## Global Constraints

- No real-time/live streaming — Health Connect cannot provide it. The heart rate value shown is always the real most-recent sample already fetched for today, with an "as of HH:mm" timestamp. The pulsing animation is cosmetic only and never alters the displayed number.
- New manifest permissions: `android.permission.health.READ_RESPIRATORY_RATE`, `android.permission.health.READ_OXYGEN_SATURATION`, `android.permission.health.READ_SKIN_TEMPERATURE`.
- `AppDatabase` is currently `version = 2` with `.fallbackToDestructiveMigration()` already configured in `ServiceLocator.kt` (added for the Sleep Performance feature) — this task bumps to `version = 3` using the same existing destructive-fallback mechanism; no new migration work needed.
- Missing data for any of the three new vitals renders `"--"`, never a misleading `0`/`0.0` — consistent with how every other metric on this screen already handles missing data.
- The `readRecords<T>` helper in `HealthConnectDataSource` already filters every record type to Fitbit's own data origin (added for the steps double-counting fix) — this applies automatically to the three new record types too, no extra work needed.
- No new automated tests for the Compose UI change (Task 6) — presentation-only, consistent with the rest of the Today screen.
- Every Bash command that runs `./gradlew` must export `JAVA_HOME` inline in the same command (shell state does not persist between tool calls): `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"`.
- Git identity isn't configured globally on this machine — commits must use `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "..."`.

## File Structure

```
app/src/main/java/com/esusic/recoverytracker/
  model/Samples.kt                                  // Modify (Task 1): 3 new sample types
  data/healthconnect/
    HealthDataSource.kt                              // Modify (Task 1): 3 new interface methods
    FakeHealthDataSource.kt                           // Modify (Task 1): 3 new fake fields/overrides
    HealthConnectRecordMapper.kt                      // Modify (Task 2): 3 new mapping functions
    HealthConnectDataSource.kt                        // Modify (Task 2): 3 new overrides + permissions
  data/
    AggregationService.kt                             // Modify (Task 3): 4 new aggregate fields
  model/DailyAggregate.kt                             // Modify (Task 3): 4 new fields
  data/local/
    DailyAggregateEntity.kt                           // Modify (Task 4): 5 new columns
    AppDatabase.kt                                    // Modify (Task 4): version 2 -> 3
    Mappers.kt                                        // Modify (Task 4): toEntity() update
  ui/today/
    TodayViewModel.kt                                 // Modify (Task 5): 5 new uiState fields
    TodayScreen.kt                                    // Modify (Task 6): Health Monitor card
app/src/main/AndroidManifest.xml                       // Modify (Task 2): 3 new permissions
```

---

### Task 1: Domain Models + HealthDataSource Interface

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/model/Samples.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthDataSource.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSource.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSourceTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `RespiratoryRateSample(time: Instant, breathsPerMinute: Double)`, `OxygenSaturationSample(time: Instant, percentage: Double)`, `SkinTemperatureDeltaSample(time: Instant, deltaCelsius: Double)`. `HealthDataSource` gains `respiratoryRateSamples`, `oxygenSaturationSamples`, `skinTemperatureDeltaSamples` (each `suspend fun(start: Instant, end: Instant): List<...>`). `FakeHealthDataSource` gains matching constructor params (`respiratoryRate`, `oxygenSaturation`, `skinTemperatureDeltas`, all `= emptyList()`) and overrides.
- Consumed by: Task 2 (`HealthConnectDataSource` implements the interface for real), Task 3 (`AggregationService` calls these methods).

- [ ] **Step 1: Write the failing tests**

Add these three tests to the existing `app/src/test/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSourceTest.kt` (inside the `FakeHealthDataSourceTest` class, alongside the existing two `@Test` functions — do not remove or modify those):

```kotlin
@Test
fun `filters respiratory rate samples to the requested time range`() = runTest {
    val base = Instant.parse("2026-07-13T00:00:00Z")
    val source = FakeHealthDataSource(
        respiratoryRate = listOf(
            RespiratoryRateSample(base.minus(1, ChronoUnit.DAYS), 12.0),
            RespiratoryRateSample(base.plus(1, ChronoUnit.HOURS), 14.0)
        )
    )
    val result = source.respiratoryRateSamples(base, base.plus(1, ChronoUnit.DAYS))
    assertEquals(listOf(14.0), result.map { it.breathsPerMinute })
}

@Test
fun `filters oxygen saturation samples to the requested time range`() = runTest {
    val base = Instant.parse("2026-07-13T00:00:00Z")
    val source = FakeHealthDataSource(
        oxygenSaturation = listOf(
            OxygenSaturationSample(base.minus(1, ChronoUnit.DAYS), 90.0),
            OxygenSaturationSample(base.plus(1, ChronoUnit.HOURS), 97.0)
        )
    )
    val result = source.oxygenSaturationSamples(base, base.plus(1, ChronoUnit.DAYS))
    assertEquals(listOf(97.0), result.map { it.percentage })
}

@Test
fun `filters skin temperature delta samples to the requested time range`() = runTest {
    val base = Instant.parse("2026-07-13T00:00:00Z")
    val source = FakeHealthDataSource(
        skinTemperatureDeltas = listOf(
            SkinTemperatureDeltaSample(base.minus(1, ChronoUnit.DAYS), -1.0),
            SkinTemperatureDeltaSample(base.plus(1, ChronoUnit.HOURS), 0.3)
        )
    )
    val result = source.skinTemperatureDeltaSamples(base, base.plus(1, ChronoUnit.DAYS))
    assertEquals(listOf(0.3), result.map { it.deltaCelsius })
}
```

Add the three new imports (`RespiratoryRateSample`, `OxygenSaturationSample`, `SkinTemperatureDeltaSample` from `com.esusic.recoverytracker.model`) alongside the existing `import com.esusic.recoverytracker.model.HeartRateSample` line.

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSourceTest"
```
Expected: FAIL — `RespiratoryRateSample`/`OxygenSaturationSample`/`SkinTemperatureDeltaSample` and the new `FakeHealthDataSource` constructor params/methods are unresolved.

- [ ] **Step 3: Add the domain model types**

In `app/src/main/java/com/esusic/recoverytracker/model/Samples.kt`, add these three data classes (alongside the existing `HeartRateSample`/`HrvSample`/`RestingHrSample` — same file, same style, don't remove anything existing):

```kotlin
data class RespiratoryRateSample(val time: Instant, val breathsPerMinute: Double)

data class OxygenSaturationSample(val time: Instant, val percentage: Double)

data class SkinTemperatureDeltaSample(val time: Instant, val deltaCelsius: Double)
```

- [ ] **Step 4: Extend the interface**

In `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthDataSource.kt`, add the three new imports and three new methods to the interface:

```kotlin
package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.Instant

interface HealthDataSource {
    suspend fun heartRateSamples(start: Instant, end: Instant): List<HeartRateSample>
    suspend fun hrvSamples(start: Instant, end: Instant): List<HrvSample>
    suspend fun restingHeartRate(start: Instant, end: Instant): List<RestingHrSample>
    suspend fun sleepSessions(start: Instant, end: Instant): List<SleepSession>
    suspend fun totalSteps(start: Instant, end: Instant): Int
    suspend fun totalActiveCalories(start: Instant, end: Instant): Double
    suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSession>
    suspend fun respiratoryRateSamples(start: Instant, end: Instant): List<RespiratoryRateSample>
    suspend fun oxygenSaturationSamples(start: Instant, end: Instant): List<OxygenSaturationSample>
    suspend fun skinTemperatureDeltaSamples(start: Instant, end: Instant): List<SkinTemperatureDeltaSample>
    suspend fun hasAllPermissions(): Boolean
}
```

- [ ] **Step 5: Extend the fake**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSource.kt` with:

```kotlin
package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.Instant

class FakeHealthDataSource(
    private val heartRate: List<HeartRateSample> = emptyList(),
    private val hrv: List<HrvSample> = emptyList(),
    private val restingHr: List<RestingHrSample> = emptyList(),
    private val sleep: List<SleepSession> = emptyList(),
    private val steps: Int = 0,
    private val activeCalories: Double = 0.0,
    private val exercise: List<ExerciseSession> = emptyList(),
    private val respiratoryRate: List<RespiratoryRateSample> = emptyList(),
    private val oxygenSaturation: List<OxygenSaturationSample> = emptyList(),
    private val skinTemperatureDeltas: List<SkinTemperatureDeltaSample> = emptyList(),
    private val permissionsGranted: Boolean = true
) : HealthDataSource {
    override suspend fun heartRateSamples(start: Instant, end: Instant) =
        heartRate.filter { it.time >= start && it.time < end }

    override suspend fun hrvSamples(start: Instant, end: Instant) =
        hrv.filter { it.time >= start && it.time < end }

    override suspend fun restingHeartRate(start: Instant, end: Instant) =
        restingHr.filter { it.time >= start && it.time < end }

    override suspend fun sleepSessions(start: Instant, end: Instant) =
        sleep.filter { it.start >= start && it.start < end }

    override suspend fun totalSteps(start: Instant, end: Instant) = steps

    override suspend fun totalActiveCalories(start: Instant, end: Instant) = activeCalories

    override suspend fun exerciseSessions(start: Instant, end: Instant) =
        exercise.filter { it.start >= start && it.start < end }

    override suspend fun respiratoryRateSamples(start: Instant, end: Instant) =
        respiratoryRate.filter { it.time >= start && it.time < end }

    override suspend fun oxygenSaturationSamples(start: Instant, end: Instant) =
        oxygenSaturation.filter { it.time >= start && it.time < end }

    override suspend fun skinTemperatureDeltaSamples(start: Instant, end: Instant) =
        skinTemperatureDeltas.filter { it.time >= start && it.time < end }

    override suspend fun hasAllPermissions() = permissionsGranted
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSourceTest"
```
Expected: PASS (5 tests — the 2 pre-existing plus the 3 new).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/model/Samples.kt app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthDataSource.kt app/src/main/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSource.kt app/src/test/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSourceTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add respiratory rate, SpO2, and skin temperature to domain models and HealthDataSource"
```

---

### Task 2: HealthConnectRecordMapper + HealthConnectDataSource + Permissions

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapper.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapperTest.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectDataSource.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `RespiratoryRateSample`/`OxygenSaturationSample`/`SkinTemperatureDeltaSample` (Task 1), `HealthDataSource` (Task 1).
- Produces: `HealthConnectRecordMapper.toRespiratoryRateSamples(List<RespiratoryRateRecord>): List<RespiratoryRateSample>`, `.toOxygenSaturationSamples(List<OxygenSaturationRecord>): List<OxygenSaturationSample>`, `.toSkinTemperatureDeltaSamples(List<SkinTemperatureRecord>): List<SkinTemperatureDeltaSample>`. `HealthConnectDataSource` implements the three new `HealthDataSource` methods for real and adds the three new permissions to `REQUIRED_PERMISSIONS`.
- Consumed by: Task 3 (`AggregationService`, via the `HealthDataSource` interface — doesn't call the mapper or `HealthConnectDataSource` directly).

- [ ] **Step 1: Write the failing tests**

Add these three tests to the existing `app/src/test/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapperTest.kt` (inside the `HealthConnectRecordMapperTest` class, alongside the existing six `@Test` functions — do not remove or modify those):

```kotlin
@Test
fun `maps respiratory rate records`() {
    val record = RespiratoryRateRecord(
        time = base, zoneOffset = null, rate = 14.5,
        metadata = Metadata.manualEntry()
    )
    val result = HealthConnectRecordMapper.toRespiratoryRateSamples(listOf(record))
    assertEquals(14.5, result.single().breathsPerMinute, 0.0001)
}

@Test
fun `maps oxygen saturation records`() {
    val record = OxygenSaturationRecord(
        time = base, zoneOffset = null, percentage = Percentage(97.0),
        metadata = Metadata.manualEntry()
    )
    val result = HealthConnectRecordMapper.toOxygenSaturationSamples(listOf(record))
    assertEquals(97.0, result.single().percentage, 0.0001)
}

@Test
fun `flattens skin temperature deltas across records`() {
    val record = SkinTemperatureRecord(
        startTime = base, startZoneOffset = null,
        endTime = base.plus(8, ChronoUnit.HOURS), endZoneOffset = null,
        metadata = Metadata.manualEntry(),
        deltas = listOf(
            SkinTemperatureRecord.Delta(base.plus(1, ChronoUnit.HOURS), TemperatureDelta.celsius(-0.5)),
            SkinTemperatureRecord.Delta(base.plus(2, ChronoUnit.HOURS), TemperatureDelta.celsius(0.3))
        ),
        baseline = Temperature.celsius(33.0),
        measurementLocation = SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST
    )
    val result = HealthConnectRecordMapper.toSkinTemperatureDeltaSamples(listOf(record))
    assertEquals(listOf(-0.5, 0.3), result.map { it.deltaCelsius })
}
```

Add these imports to the top of the test file, alongside the existing `androidx.health.connect.client.records.*` imports:

```kotlin
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.HealthConnectRecordMapperTest"
```
Expected: FAIL — `toRespiratoryRateSamples`/`toOxygenSaturationSamples`/`toSkinTemperatureDeltaSamples` are unresolved.

- [ ] **Step 3: Implement the mapper functions**

Add these three functions to `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapper.kt`, inside the `HealthConnectRecordMapper` object, alongside the existing functions (don't remove anything existing):

```kotlin
fun toRespiratoryRateSamples(records: List<RespiratoryRateRecord>): List<RespiratoryRateSample> =
    records.map { RespiratoryRateSample(it.time, it.rate) }

fun toOxygenSaturationSamples(records: List<OxygenSaturationRecord>): List<OxygenSaturationSample> =
    records.map { OxygenSaturationSample(it.time, it.percentage.value) }

fun toSkinTemperatureDeltaSamples(records: List<SkinTemperatureRecord>): List<SkinTemperatureDeltaSample> =
    records.flatMap { record -> record.deltas.map { SkinTemperatureDeltaSample(it.time, it.delta.celsius) } }
```

Add these imports to the top of `HealthConnectRecordMapper.kt`, alongside the existing `androidx.health.connect.client.records.*` imports:

```kotlin
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.HealthConnectRecordMapperTest"
```
Expected: PASS (9 tests — the 6 pre-existing plus the 3 new). If a constructor call doesn't compile because the real `connect-client:1.1.0` API differs from what's shown here (the `Delta`/`Percentage`/`Temperature`/`TemperatureDelta` shapes above were verified directly against the resolved AAR via `javap`, but double-check if the compiler disagrees), read the compiler error and adjust the test's construction only — the mapper's own logic doesn't change.

- [ ] **Step 5: Implement the real data source and permissions**

In `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectDataSource.kt`, add the three new overrides (alongside the existing ones, before `hasAllPermissions`):

```kotlin
override suspend fun respiratoryRateSamples(start: Instant, end: Instant): List<RespiratoryRateSample> =
    HealthConnectRecordMapper.toRespiratoryRateSamples(readRecords<RespiratoryRateRecord>(start, end))

override suspend fun oxygenSaturationSamples(start: Instant, end: Instant): List<OxygenSaturationSample> =
    HealthConnectRecordMapper.toOxygenSaturationSamples(readRecords<OxygenSaturationRecord>(start, end))

override suspend fun skinTemperatureDeltaSamples(start: Instant, end: Instant): List<SkinTemperatureDeltaSample> =
    HealthConnectRecordMapper.toSkinTemperatureDeltaSamples(readRecords<SkinTemperatureRecord>(start, end))
```

Add these imports at the top of the file, alongside the existing `androidx.health.connect.client.records.*` imports:

```kotlin
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
```

Add the three new permissions to `REQUIRED_PERMISSIONS` in the `companion object` (alongside the existing seven, don't remove any):

```kotlin
val REQUIRED_PERMISSIONS: Set<String> = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(RespiratoryRateRecord::class),
    HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    HealthPermission.getReadPermission(SkinTemperatureRecord::class)
)
```

- [ ] **Step 6: Add the manifest permissions**

In `app/src/main/AndroidManifest.xml`, add three new `<uses-permission>` lines alongside the existing seven (before the `<application>` tag):

```xml
<uses-permission android:name="android.permission.health.READ_RESPIRATORY_RATE" />
<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />
<uses-permission android:name="android.permission.health.READ_SKIN_TEMPERATURE" />
```

- [ ] **Step 7: Verify everything compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapper.kt app/src/test/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapperTest.kt app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectDataSource.kt app/src/main/AndroidManifest.xml
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: read respiratory rate, SpO2, and skin temperature from Health Connect"
```

---

### Task 3: AggregationService

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/model/DailyAggregate.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt`
- Modify: `app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt`

**Interfaces:**
- Consumes: `HealthDataSource` new methods (Task 1).
- Produces: `DailyAggregate` gains `avgRespiratoryRate: Double?`, `avgOxygenSaturationPercent: Double?`, `avgSkinTemperatureDeltaCelsius: Double?`, `latestHeartRateSample: HeartRateSample?`.
- Consumed by: Task 4 (`Mappers.kt`'s `DailyAggregate.toEntity()`).

- [ ] **Step 1: Write the failing tests**

Add these tests to the existing `app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt` (inside the `AggregationServiceTest` class, alongside the existing six `@Test` functions — do not remove or modify those):

```kotlin
@Test
fun `averages respiratory rate samples within the day`() = runTest {
    val source = FakeHealthDataSource(
        respiratoryRate = listOf(
            RespiratoryRateSample(dayStart.plusSeconds(3600), 12.0),
            RespiratoryRateSample(dayStart.plusSeconds(7200), 16.0)
        )
    )
    val result = AggregationService(source).aggregateForDate(date, zone)
    assertEquals(14.0, result.avgRespiratoryRate!!, 0.0001)
}

@Test
fun `avgRespiratoryRate is null when no respiratory rate samples exist`() = runTest {
    val result = AggregationService(FakeHealthDataSource()).aggregateForDate(date, zone)
    assertNull(result.avgRespiratoryRate)
}

@Test
fun `averages oxygen saturation samples within the day`() = runTest {
    val source = FakeHealthDataSource(
        oxygenSaturation = listOf(
            OxygenSaturationSample(dayStart.plusSeconds(3600), 96.0),
            OxygenSaturationSample(dayStart.plusSeconds(7200), 98.0)
        )
    )
    val result = AggregationService(source).aggregateForDate(date, zone)
    assertEquals(97.0, result.avgOxygenSaturationPercent!!, 0.0001)
}

@Test
fun `averages skin temperature delta samples within the day`() = runTest {
    val source = FakeHealthDataSource(
        skinTemperatureDeltas = listOf(
            SkinTemperatureDeltaSample(dayStart.plusSeconds(3600), -0.4),
            SkinTemperatureDeltaSample(dayStart.plusSeconds(7200), 0.2)
        )
    )
    val result = AggregationService(source).aggregateForDate(date, zone)
    assertEquals(-0.1, result.avgSkinTemperatureDeltaCelsius!!, 0.0001)
}

@Test
fun `latestHeartRateSample picks the most recent heart rate sample of the day`() = runTest {
    val earlier = HeartRateSample(dayStart.plusSeconds(3600), 65L)
    val later = HeartRateSample(dayStart.plusSeconds(7200), 72L)
    val source = FakeHealthDataSource(heartRate = listOf(earlier, later))
    val result = AggregationService(source).aggregateForDate(date, zone)
    assertEquals(later, result.latestHeartRateSample)
}
```

Add these imports to the top of the test file, alongside the existing `com.esusic.recoverytracker.model.*` imports:

```kotlin
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.AggregationServiceTest"
```
Expected: FAIL — `avgRespiratoryRate`/`avgOxygenSaturationPercent`/`avgSkinTemperatureDeltaCelsius`/`latestHeartRateSample` are unresolved on `DailyAggregate`.

- [ ] **Step 3: Extend the domain model**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/model/DailyAggregate.kt` with:

```kotlin
package com.esusic.recoverytracker.model

import java.time.LocalDate

data class DailyAggregate(
    val date: LocalDate,
    val avgHrvMillis: Double?,
    val restingHeartRate: Long?,
    val sleep: SleepSession?,
    val steps: Int,
    val activeCaloriesKcal: Double,
    val heartRateSamples: List<HeartRateSample>,
    val exerciseSessions: List<ExerciseSession>,
    val isComplete: Boolean,
    val avgRespiratoryRate: Double? = null,
    val avgOxygenSaturationPercent: Double? = null,
    val avgSkinTemperatureDeltaCelsius: Double? = null,
    val latestHeartRateSample: HeartRateSample? = null
)
```

- [ ] **Step 4: Extend AggregationService**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt` with:

```kotlin
package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.model.DailyAggregate
import java.time.LocalDate
import java.time.ZoneId

class AggregationService(private val dataSource: HealthDataSource) {

    suspend fun aggregateForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DailyAggregate {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()

        // Sleep sessions typically start the evening before the day they belong to (the day the
        // person wakes up). Query a wider window covering the prior evening through the end of
        // the target day, then keep only sessions that woke the person up on this calendar day.
        val sleepWindowStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()

        val heartRateSamples = dataSource.heartRateSamples(start, end)
        val hrvSamples = dataSource.hrvSamples(start, end)
        val restingHrSamples = dataSource.restingHeartRate(start, end)
        val sleep = dataSource.sleepSessions(sleepWindowStart, end)
            .filter { it.end >= start && it.end < end }
            .maxByOrNull { it.durationMinutes }
        val steps = dataSource.totalSteps(start, end)
        val activeCalories = dataSource.totalActiveCalories(start, end)
        val exerciseSessions = dataSource.exerciseSessions(start, end)
        val respiratoryRateSamples = dataSource.respiratoryRateSamples(start, end)
        val oxygenSaturationSamples = dataSource.oxygenSaturationSamples(start, end)
        val skinTemperatureDeltaSamples = dataSource.skinTemperatureDeltaSamples(start, end)

        val avgHrv = hrvSamples.takeIf { it.isNotEmpty() }?.map { it.rmssdMillis }?.average()
        val restingHeartRate = restingHrSamples.minByOrNull { it.bpm }?.bpm
        val isComplete = heartRateSamples.isNotEmpty() && sleep != null
        val avgRespiratoryRate = respiratoryRateSamples.takeIf { it.isNotEmpty() }
            ?.map { it.breathsPerMinute }?.average()
        val avgOxygenSaturationPercent = oxygenSaturationSamples.takeIf { it.isNotEmpty() }
            ?.map { it.percentage }?.average()
        val avgSkinTemperatureDeltaCelsius = skinTemperatureDeltaSamples.takeIf { it.isNotEmpty() }
            ?.map { it.deltaCelsius }?.average()
        val latestHeartRateSample = heartRateSamples.maxByOrNull { it.time }

        return DailyAggregate(
            date = date,
            avgHrvMillis = avgHrv,
            restingHeartRate = restingHeartRate,
            sleep = sleep,
            steps = steps,
            activeCaloriesKcal = activeCalories,
            heartRateSamples = heartRateSamples,
            exerciseSessions = exerciseSessions,
            isComplete = isComplete,
            avgRespiratoryRate = avgRespiratoryRate,
            avgOxygenSaturationPercent = avgOxygenSaturationPercent,
            avgSkinTemperatureDeltaCelsius = avgSkinTemperatureDeltaCelsius,
            latestHeartRateSample = latestHeartRateSample
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.AggregationServiceTest"
```
Expected: PASS (11 tests — the 6 pre-existing plus the 5 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/model/DailyAggregate.kt app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: aggregate respiratory rate, SpO2, skin temperature, and latest heart rate"
```

---

### Task 4: Database Schema

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/DailyAggregateEntity.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/Mappers.kt`

**Interfaces:**
- Consumes: `DailyAggregate` new fields (Task 3).
- Produces: `DailyAggregateEntity` gains `avgRespiratoryRate: Double? = null`, `avgOxygenSaturationPercent: Double? = null`, `avgSkinTemperatureDeltaCelsius: Double? = null`, `latestHeartRateBpm: Long? = null`, `latestHeartRateEpochMillis: Long? = null`.
- Consumed by: Task 5 (`TodayViewModel`, reading these fields off the aggregate entity).

- [ ] **Step 1: Add the columns**

In `app/src/main/java/com/esusic/recoverytracker/data/local/DailyAggregateEntity.kt`, add five new fields to the `@Entity` data class (the `@Dao interface DailyAggregateDao` below it in the same file is untouched):

```kotlin
@Entity(tableName = "daily_aggregates")
data class DailyAggregateEntity(
    @PrimaryKey val date: String,
    val avgHrvMillis: Double?,
    val restingHeartRate: Long?,
    val sleepDurationMinutes: Long?,
    val deepSleepMinutes: Long?,
    val remSleepMinutes: Long?,
    val lightSleepMinutes: Long?,
    val steps: Int,
    val activeCaloriesKcal: Double,
    val isComplete: Boolean,
    val avgRespiratoryRate: Double? = null,
    val avgOxygenSaturationPercent: Double? = null,
    val avgSkinTemperatureDeltaCelsius: Double? = null,
    val latestHeartRateBpm: Long? = null,
    val latestHeartRateEpochMillis: Long? = null
)
```

- [ ] **Step 2: Bump the database version**

In `app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt`, change `version = 2` to `version = 3` (the `.fallbackToDestructiveMigration()` already configured in `ServiceLocator.kt` handles the schema change — no other change needed there this time).

- [ ] **Step 3: Update the entity mapper**

In `app/src/main/java/com/esusic/recoverytracker/data/local/Mappers.kt`, update `DailyAggregate.toEntity()` to populate the five new fields:

```kotlin
fun DailyAggregate.toEntity(): DailyAggregateEntity = DailyAggregateEntity(
    date = date.toString(),
    avgHrvMillis = avgHrvMillis,
    restingHeartRate = restingHeartRate,
    sleepDurationMinutes = sleep?.durationMinutes,
    deepSleepMinutes = sleep?.minutesIn(SleepStageType.DEEP),
    remSleepMinutes = sleep?.minutesIn(SleepStageType.REM),
    lightSleepMinutes = sleep?.minutesIn(SleepStageType.LIGHT),
    steps = steps,
    activeCaloriesKcal = activeCaloriesKcal,
    isComplete = isComplete,
    avgRespiratoryRate = avgRespiratoryRate,
    avgOxygenSaturationPercent = avgOxygenSaturationPercent,
    avgSkinTemperatureDeltaCelsius = avgSkinTemperatureDeltaCelsius,
    latestHeartRateBpm = latestHeartRateSample?.bpm,
    latestHeartRateEpochMillis = latestHeartRateSample?.time?.toEpochMilli()
)
```

(Only this one function changes — `toRecoveryScore()`/`toStrainScore()` below it in the same file are untouched.)

- [ ] **Step 4: Verify existing tests still pass**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all tests pass — this proves the defaulted new columns keep every existing `DailyAggregateEntity`/`DailyAggregateDaoTest` call site compiling, and the updated `toEntity()` still satisfies `AggregationServiceTest`/`HealthRepositoryTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/local/DailyAggregateEntity.kt app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt app/src/main/java/com/esusic/recoverytracker/data/local/Mappers.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: persist respiratory rate, SpO2, skin temperature, and latest heart rate, bump DB to version 3"
```

---

### Task 5: TodayViewModel Wiring

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`

**Interfaces:**
- Consumes: `DailyAggregateEntity` new fields (Task 4).
- Produces: `TodayUiState` gains `avgRespiratoryRate: Double? = null`, `avgOxygenSaturationPercent: Double? = null`, `avgSkinTemperatureDeltaCelsius: Double? = null`, `latestHeartRateBpm: Long? = null`, `latestHeartRateEpochMillis: Long? = null`.
- Consumed by: Task 6 (`TodayScreen.kt`).

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
    val avgRespiratoryRate: Double? = null,
    val avgOxygenSaturationPercent: Double? = null,
    val avgSkinTemperatureDeltaCelsius: Double? = null,
    val latestHeartRateBpm: Long? = null,
    val latestHeartRateEpochMillis: Long? = null,
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
                avgRespiratoryRate = aggregate?.avgRespiratoryRate,
                avgOxygenSaturationPercent = aggregate?.avgOxygenSaturationPercent,
                avgSkinTemperatureDeltaCelsius = aggregate?.avgSkinTemperatureDeltaCelsius,
                latestHeartRateBpm = aggregate?.latestHeartRateBpm,
                latestHeartRateEpochMillis = aggregate?.latestHeartRateEpochMillis,
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
Expected: PASS (2 tests, unchanged — neither existing fixture includes the new vitals, so they're legitimately `null` and no existing assertion touches them).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: surface respiratory rate, SpO2, skin temperature, and latest heart rate in TodayUiState"
```

---

### Task 6: Health Monitor Card UI

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `TodayUiState` new fields (Task 5).
- Produces: no new public interface — this is the screen's own composable.

- [ ] **Step 1: Replace the full file**

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt` with:

```kotlin
package com.esusic.recoverytracker.ui.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.detail.MetricType
import com.esusic.recoverytracker.ui.theme.warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenSettings: () -> Unit, onMetricClick: (MetricType) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(uiState.isRefreshing, { viewModel.refresh() })

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onOpenSettings) { Text("Settings") }
            }

            if (!uiState.hasAllPermissions) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Health Connect access was revoked.", color = MaterialTheme.colorScheme.error)
                        Text("Reconnect in Settings to keep scores up to date.")
                    }
                }
            }

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

            HealthMonitorCard(uiState)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sleep", style = MaterialTheme.typography.titleMedium)
                    Text(uiState.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No sleep data yet")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickStat(
                        icon = Icons.Filled.Favorite,
                        label = "RHR",
                        value = uiState.restingHeartRate?.toString() ?: "--",
                        onClick = { onMetricClick(MetricType.HEART_RATE) }
                    )
                    QuickStat(
                        icon = Icons.Filled.MonitorHeart,
                        label = "HRV",
                        value = uiState.avgHrvMillis?.let { "%.0f".format(it) } ?: "--",
                        onClick = { onMetricClick(MetricType.HRV) }
                    )
                    QuickStat(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        label = "Steps",
                        value = uiState.steps?.toString() ?: "--",
                        onClick = { onMetricClick(MetricType.ACTIVITY) }
                    )
                }
            }

            uiState.lastSyncedAtEpochMillis?.let {
                val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(it))
                Text("Last synced $formatted", style = MaterialTheme.typography.bodySmall)
            }
        }

        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HealthMonitorCard(uiState: TodayUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Health Monitor", style = MaterialTheme.typography.titleMedium)

            VitalRow("RHR", uiState.restingHeartRate?.let { "$it bpm" } ?: "--")
            VitalRow("HRV", uiState.avgHrvMillis?.let { "%.0f ms".format(it) } ?: "--")
            VitalRow(
                "Respiratory Rate",
                uiState.avgRespiratoryRate?.let { "%.1f breaths/min".format(it) } ?: "--"
            )
            VitalRow(
                "SpO2",
                uiState.avgOxygenSaturationPercent?.let { "%.0f%%".format(it) } ?: "--"
            )
            VitalRow(
                "Skin Temp Variation",
                uiState.avgSkinTemperatureDeltaCelsius?.let { "%+.1f°C".format(it) } ?: "--"
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulsingHeartIcon()
                val heartRateText = uiState.latestHeartRateBpm?.let { bpm ->
                    val timestamp = uiState.latestHeartRateEpochMillis?.let {
                        DateTimeFormatter.ofPattern("HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(it))
                    }
                    if (timestamp != null) "$bpm bpm (as of $timestamp)" else "$bpm bpm"
                } ?: "--"
                Text(heartRateText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun VitalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PulsingHeartIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeatScale"
    )
    Icon(
        Icons.Filled.Favorite,
        contentDescription = "Heart rate",
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp).scale(scale)
    )
}

@Composable
private fun QuickStat(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun recoveryColor(score: Int?): Color = when {
    score == null -> MaterialTheme.colorScheme.primary
    score >= 67 -> MaterialTheme.colorScheme.primary
    score >= 34 -> MaterialTheme.colorScheme.warning
    else -> MaterialTheme.colorScheme.error
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all tests pass (this task doesn't touch `TodayViewModel`, so `TodayViewModelTest` must still pass unchanged).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add Health Monitor card with pulsing heart rate to Today screen"
```

- [ ] **Step 4: Manual on-device verification checklist (executed by the user)**

1. Uninstall the existing app first (schema changed, `adb uninstall com.esusic.recoverytracker`), then install the fresh APK.
2. Complete onboarding again — grant all permissions, including the three new ones (respiratory rate, SpO2, skin temperature) in the Health Connect permission dialog.
3. Confirm the "Health Monitor" card appears below the three rings and above the Sleep card, showing RHR, HRV, Respiratory Rate, SpO2, Skin Temp Variation, and a heart rate row with a visibly pulsing heart icon.
4. Confirm any vital with no synced data shows "--", not a misleading "0".
5. Confirm the heart rate row shows a real bpm value with an "as of HH:mm" timestamp when data exists, and that the number does NOT change on its own between refreshes (only the pulsing icon animates) — pull-to-refresh should be the only thing that updates the number, confirming it's a real synced value and not a fabricated live one.
