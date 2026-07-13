# Recovery Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app that reads Fitbit data via Health Connect and presents a Whoop-style daily Recovery score, live Strain score, and trend views, replacing the Google Health app for daily use.

**Architecture:** MVVM with a headless, fully unit-testable core (Health Connect access → aggregation → scoring engine → Room) driving reactive Compose UI via Flow. No backend, no accounts — single-device, local-only.

**Tech Stack:** Kotlin 2.2.0, Jetpack Compose (BOM 2026.06.00), Health Connect client 1.1.0, Room 2.7.1, WorkManager (work-runtime-ktx) 2.10.1, DataStore Preferences 1.1.1, kotlinx-coroutines 1.9.0, Navigation Compose 2.8.0, AGP 8.10.0.

## Global Constraints

- Package/application ID: `com.esusic.recoverytracker`.
- `minSdk = 26` (Health Connect client library floor), `compileSdk = 36`, `targetSdk = 36`.
- Before running the first build, verify the dependency versions listed above against `https://developer.android.com/jetpack/androidx/versions` and `mvnrepository.com` — this ecosystem ships new patch versions frequently and a listed patch may have been superseded. Bump patch versions only (not majors) if a listed one 404s.
- Local-only: no network calls, no accounts, no cloud dependencies anywhere in this plan.
- Pure logic (scoring engine, mapping, aggregation) lives in classes with zero Android/Context dependencies so it runs as fast JVM unit tests. Android-dependent glue code (Health Connect client calls, Compose UI) is verified manually on-device per the spec's testing approach — do not attempt to mock the Health Connect SDK's live client.
- All money/points/percent-style scores are `Int`/`Double` — no floating currency-style rounding concerns, but always `coerceIn` scores to their documented range before returning.
- Every Kotlin file goes under `app/src/main/java/com/esusic/recoverytracker/`; every test under `app/src/test/java/com/esusic/recoverytracker/` (JVM/Robolectric tests) — this project has no `androidTest` instrumented tests; everything Android-dependent is either Robolectric-backed or manually verified.

## File Structure

```
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/com/esusic/recoverytracker/
    RecoveryTrackerApp.kt
    MainActivity.kt
    ServiceLocator.kt
    model/                      // pure domain types (Task 2)
      Samples.kt
      DailyAggregate.kt
      Baseline.kt
      RecoveryScore.kt
      StrainScore.kt
    data/
      local/                    // Task 3
        AppDatabase.kt
        DailyScoreEntity.kt
        DailyAggregateEntity.kt
        Mappers.kt               // Task 12
      prefs/                    // Task 4
        UserPreferencesRepository.kt
        DataStoreExtensions.kt
      healthconnect/
        HealthDataSource.kt      // Task 8 (interface + Fake)
        HealthConnectRecordMapper.kt // Task 9
        HealthConnectDataSource.kt   // Task 10
      AggregationService.kt      // Task 11
      HealthRepository.kt        // Task 12
    scoring/                     // Tasks 5-7
      BaselineCalculator.kt
      RecoveryScoreCalculator.kt
      StrainScoreCalculator.kt
    sync/                        // Task 13
      SyncWorker.kt
      SyncScheduler.kt
      AppWorkerFactory.kt
    ui/
      theme/                     // Task 14
        Theme.kt
      navigation/RecoveryTrackerNavHost.kt // Task 14
      onboarding/                // Task 15
        OnboardingScreen.kt
        OnboardingViewModel.kt
      today/                     // Task 16
        TodayScreen.kt
        TodayViewModel.kt
      trends/                    // Task 17
        TrendsScreen.kt
        TrendsViewModel.kt
        LineChart.kt
      sleep/                     // Task 18
        SleepScreen.kt
        SleepViewModel.kt
      detail/                    // Task 19
        MetricDetailScreen.kt
        MetricDetailViewModel.kt
      settings/                  // Task 20
        SettingsScreen.kt
  src/test/java/com/esusic/recoverytracker/
    (mirrors main package for each tested class)
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/esusic/recoverytracker/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: a buildable Android app module `app` with Compose wired up, application ID `com.esusic.recoverytracker`, that launches `MainActivity` showing placeholder text. Every later task adds files under this module.

- [ ] **Step 1: Create the Gradle version catalog**

```toml
# gradle/libs.versions.toml
[versions]
agp = "8.10.0"
kotlin = "2.2.0"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
composeBom = "2026.06.00"
navigationCompose = "2.8.0"
healthConnect = "1.1.0"
room = "2.7.1"
work = "2.10.1"
datastore = "1.1.1"
coroutines = "1.9.0"
robolectric = "4.13"
junit = "4.13.2"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { module = "androidx.activity:activity-compose", version = "1.9.3" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
health-connect-client = { module = "androidx.health.connect:connect-client", version.ref = "healthConnect" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version = "1.6.1" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Create settings and root build files**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RecoveryTracker"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.room) apply false
}
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 3: Create the app module build file**

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.room)
}

android {
    namespace = "com.esusic.recoverytracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.esusic.recoverytracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.health.connect.client)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.work.testing)
}
```

Note: `ksp(libs.room.compiler)` requires the KSP Gradle plugin. Add it to the version catalog and both plugin blocks:

```toml
# add to [versions] in libs.versions.toml
ksp = "2.2.0-2.0.2"
```
```toml
# add to [plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Then add `alias(libs.plugins.ksp) apply false` to the root `build.gradle.kts` plugins block, and `alias(libs.plugins.ksp)` to `app/build.gradle.kts`'s plugins block.

- [ ] **Step 4: Create the manifest and a placeholder MainActivity**

```xml
<!-- app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE_VARIABILITY" />
    <uses-permission android:name="android.permission.health.READ_RESTING_HEART_RATE" />
    <uses-permission android:name="android.permission.health.READ_SLEEP" />
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.READ_EXERCISE" />

    <application
        android:name=".RecoveryTrackerApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity-alias
            android:name=".HealthPermissionsRationaleAlias"
            android:exported="true"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
            </intent-filter>
        </activity-alias>
    </application>
</manifest>
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/MainActivity.kt
package com.esusic.recoverytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Recovery Tracker")
                }
            }
        }
    }
}
```

```xml
<!-- app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">Recovery Tracker</string>
</resources>
```

`RecoveryTrackerApp` (the `Application` subclass referenced above) is created in Task 14 — leave the manifest reference as-is; the build won't compile until then, which is expected and resolved by Task 14's step.

- [ ] **Step 5: Verify Gradle sync**

Run: `./gradlew :app:tasks`
Expected: Gradle configures successfully (task list prints). It's fine if `:app:compileDebugKotlin` isn't run yet since `RecoveryTrackerApp` doesn't exist — this step only checks Gradle wiring, not a full build.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/esusic/recoverytracker/MainActivity.kt app/src/main/res/values/strings.xml
git commit -m "chore: scaffold Android project"
```

---

### Task 2: Domain Models

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/model/Samples.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/model/DailyAggregate.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/model/Baseline.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/model/RecoveryScore.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/model/StrainScore.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/model/BaselineTest.kt`

**Interfaces:**
- Produces: `HeartRateSample(time: Instant, bpm: Long)`, `HrvSample(time: Instant, rmssdMillis: Double)`, `RestingHrSample(time: Instant, bpm: Long)`, `SleepStageType` enum `{AWAKE, LIGHT, DEEP, REM, UNKNOWN}`, `SleepStagePeriod(stage: SleepStageType, start: Instant, end: Instant)`, `SleepSession(start: Instant, end: Instant, stages: List<SleepStagePeriod>)` with `durationMinutes: Long` and `minutesIn(stage: SleepStageType): Long`, `ExerciseSession(start: Instant, end: Instant, exerciseType: Int)`, `DailyAggregate(date: LocalDate, avgHrvMillis: Double?, restingHeartRate: Long?, sleep: SleepSession?, steps: Int, activeCaloriesKcal: Double, heartRateSamples: List<HeartRateSample>, exerciseSessions: List<ExerciseSession>, isComplete: Boolean)`, `Baseline(mean: Double, stdDev: Double, sampleCount: Int)` with `zScore(value: Double): Double`, `RecoveryScore(date: LocalDate, value: Int, isCalibrating: Boolean, hrvSubScore: Double, rhrSubScore: Double, sleepSubScore: Double)`, `StrainScore(date: LocalDate, value: Double)`.
- Consumed by every later task.

- [ ] **Step 1: Write the failing test for `Baseline.zScore`**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/model/BaselineTest.kt
package com.esusic.recoverytracker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BaselineTest {
    @Test
    fun `zScore returns 0 when stdDev is zero`() {
        val baseline = Baseline(mean = 50.0, stdDev = 0.0, sampleCount = 10)
        assertEquals(0.0, baseline.zScore(80.0), 0.0001)
    }

    @Test
    fun `zScore computes standard deviations from mean`() {
        val baseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = 10)
        assertEquals(2.0, baseline.zScore(70.0), 0.0001)
        assertEquals(-1.0, baseline.zScore(40.0), 0.0001)
    }

    @Test
    fun `zScore returns 0 when sample count below minimum`() {
        val baseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = 3)
        assertEquals(0.0, baseline.zScore(70.0), 0.0001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.model.BaselineTest"`
Expected: FAIL — `Baseline` is unresolved (doesn't exist yet).

- [ ] **Step 3: Implement all domain model files**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/model/Samples.kt
package com.esusic.recoverytracker.model

import java.time.Duration
import java.time.Instant

data class HeartRateSample(val time: Instant, val bpm: Long)

data class HrvSample(val time: Instant, val rmssdMillis: Double)

data class RestingHrSample(val time: Instant, val bpm: Long)

enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

data class SleepStagePeriod(val stage: SleepStageType, val start: Instant, val end: Instant)

data class SleepSession(val start: Instant, val end: Instant, val stages: List<SleepStagePeriod>) {
    val durationMinutes: Long get() = Duration.between(start, end).toMinutes()

    fun minutesIn(stage: SleepStageType): Long =
        stages.filter { it.stage == stage }.sumOf { Duration.between(it.start, it.end).toMinutes() }
}

data class ExerciseSession(val start: Instant, val end: Instant, val exerciseType: Int)
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/model/DailyAggregate.kt
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
    val isComplete: Boolean
)
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/model/Baseline.kt
package com.esusic.recoverytracker.model

data class Baseline(val mean: Double, val stdDev: Double, val sampleCount: Int) {
    fun zScore(value: Double): Double {
        if (stdDev == 0.0 || sampleCount < MIN_SAMPLES_FOR_SCORING) return 0.0
        return (value - mean) / stdDev
    }

    companion object {
        const val MIN_SAMPLES_FOR_SCORING = 7
    }
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/model/RecoveryScore.kt
package com.esusic.recoverytracker.model

import java.time.LocalDate

data class RecoveryScore(
    val date: LocalDate,
    val value: Int,
    val isCalibrating: Boolean,
    val hrvSubScore: Double,
    val rhrSubScore: Double,
    val sleepSubScore: Double
)
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/model/StrainScore.kt
package com.esusic.recoverytracker.model

import java.time.LocalDate

data class StrainScore(val date: LocalDate, val value: Double)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.model.BaselineTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/model app/src/test/java/com/esusic/recoverytracker/model
git commit -m "feat: add domain models"
```

---

### Task 3: Room Database

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/local/DailyScoreEntity.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/data/local/DailyAggregateEntity.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/local/DailyScoreDaoTest.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/local/DailyAggregateDaoTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (entities are independent of `model/`, mapping happens in Task 12).
- Produces: `DailyScoreEntity(date: String, recoveryValue: Int, recoveryIsCalibrating: Boolean, hrvSubScore: Double, rhrSubScore: Double, sleepSubScore: Double, strainValue: Double, lastSyncedAtEpochMillis: Long, isComplete: Boolean)`, `DailyAggregateEntity(date: String, avgHrvMillis: Double?, restingHeartRate: Long?, sleepDurationMinutes: Long?, deepSleepMinutes: Long?, remSleepMinutes: Long?, lightSleepMinutes: Long?, steps: Int, activeCaloriesKcal: Double, isComplete: Boolean)`. `DailyScoreDao` methods: `suspend fun upsert(entity: DailyScoreEntity)`, `suspend fun getByDate(date: String): DailyScoreEntity?`, `fun observeRecent(limit: Int): Flow<List<DailyScoreEntity>>`. `DailyAggregateDao` methods: same shape plus `suspend fun recentBefore(date: String, limit: Int): List<DailyAggregateEntity>`. `AppDatabase` exposes `dailyScoreDao()` and `dailyAggregateDao()`.
- Test strategy: Robolectric-backed in-memory Room database (`Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()`), run as a plain JVM unit test (no emulator needed).

- [ ] **Step 1: Write the failing DAO tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/local/DailyScoreDaoTest.kt
package com.esusic.recoverytracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyScoreDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DailyScoreDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyScoreDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getByDate returns the row`() = runTest {
        val entity = DailyScoreEntity(
            date = "2026-07-13", recoveryValue = 72, recoveryIsCalibrating = false,
            hrvSubScore = 60.0, rhrSubScore = 70.0, sleepSubScore = 80.0,
            strainValue = 12.5, lastSyncedAtEpochMillis = 1000L, isComplete = true
        )
        dao.upsert(entity)
        assertEquals(entity, dao.getByDate("2026-07-13"))
    }

    @Test
    fun `getByDate returns null when missing`() = runTest {
        assertNull(dao.getByDate("2099-01-01"))
    }

    @Test
    fun `upsert overwrites existing row for same date`() = runTest {
        val original = DailyScoreEntity("2026-07-13", 50, true, 0.0, 0.0, 0.0, 0.0, 1L, false)
        val updated = original.copy(recoveryValue = 90, recoveryIsCalibrating = false)
        dao.upsert(original)
        dao.upsert(updated)
        assertEquals(90, dao.getByDate("2026-07-13")?.recoveryValue)
    }
}
```

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/local/DailyAggregateDaoTest.kt
package com.esusic.recoverytracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyAggregateDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DailyAggregateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyAggregateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(date: String, hrv: Double?) = DailyAggregateEntity(
        date = date, avgHrvMillis = hrv, restingHeartRate = 55L,
        sleepDurationMinutes = 420L, deepSleepMinutes = 80L, remSleepMinutes = 90L,
        lightSleepMinutes = 250L, steps = 8000, activeCaloriesKcal = 300.0, isComplete = true
    )

    @Test
    fun `recentBefore returns rows strictly before date, newest first, limited`() = runTest {
        dao.upsert(entity("2026-07-10", 40.0))
        dao.upsert(entity("2026-07-11", 42.0))
        dao.upsert(entity("2026-07-12", 44.0))
        dao.upsert(entity("2026-07-13", 46.0))

        val result = dao.recentBefore("2026-07-13", limit = 2)

        assertEquals(listOf("2026-07-12", "2026-07-11"), result.map { it.date })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.local.*"`
Expected: FAIL — `AppDatabase`, `DailyScoreDao`, `DailyAggregateDao`, `DailyScoreEntity`, `DailyAggregateEntity` are unresolved.

- [ ] **Step 3: Implement entities, DAOs, and the database**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/local/DailyScoreEntity.kt
package com.esusic.recoverytracker.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
    val isComplete: Boolean
)

@Dao
interface DailyScoreDao {
    @Upsert
    suspend fun upsert(entity: DailyScoreEntity)

    @Query("SELECT * FROM daily_scores WHERE date = :date")
    suspend fun getByDate(date: String): DailyScoreEntity?

    @Query("SELECT * FROM daily_scores ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyScoreEntity>>
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/local/DailyAggregateEntity.kt
package com.esusic.recoverytracker.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
    val isComplete: Boolean
)

@Dao
interface DailyAggregateDao {
    @Upsert
    suspend fun upsert(entity: DailyAggregateEntity)

    @Query("SELECT * FROM daily_aggregates WHERE date = :date")
    suspend fun getByDate(date: String): DailyAggregateEntity?

    @Query("SELECT * FROM daily_aggregates ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyAggregateEntity>>

    @Query("SELECT * FROM daily_aggregates WHERE date < :date ORDER BY date DESC LIMIT :limit")
    suspend fun recentBefore(date: String, limit: Int): List<DailyAggregateEntity>
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt
package com.esusic.recoverytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyScoreEntity::class, DailyAggregateEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyScoreDao(): DailyScoreDao
    abstract fun dailyAggregateDao(): DailyAggregateDao
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.local.*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/local app/src/test/java/com/esusic/recoverytracker/data/local
git commit -m "feat: add Room database for daily scores and aggregates"
```

---

### Task 4: User Preferences (birth year → max heart rate)

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepository.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/data/prefs/DataStoreExtensions.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepositoryTest.kt`

**Interfaces:**
- Produces: `UserPreferencesRepository(dataStore: DataStore<Preferences>)` with `val birthYear: Flow<Int?>`, `val maxHeartRate: Flow<Double>`, `suspend fun setBirthYear(year: Int)`. Also `Context.userPreferencesDataStore: DataStore<Preferences>` extension property, used by `ServiceLocator` in Task 14.
- Consumed by: Task 12 (`HealthRepository`, for `maxHeartRate`) and Task 15 (`OnboardingScreen`, to write `birthYear`).
- Rationale: Strain scoring needs an estimated max heart rate. Rather than a fixed guess, the app asks for birth year once during onboarding and derives max HR via the Tanaka formula (`208 - 0.7 * age`), which is more accurate than the classic `220 - age` rule of thumb.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepositoryTest.kt
package com.esusic.recoverytracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class UserPreferencesRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("test.preferences_pb")
        }
    }

    @Test
    fun `birthYear defaults to null`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        assertNull(repo.birthYear.first())
    }

    @Test
    fun `setBirthYear persists and is readable`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        repo.setBirthYear(1996)
        assertEquals(1996, repo.birthYear.first())
    }

    @Test
    fun `maxHeartRate uses default when birthYear unset`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        assertEquals(UserPreferencesRepository.DEFAULT_MAX_HR, repo.maxHeartRate.first(), 0.001)
    }

    @Test
    fun `maxHeartRate derives from birthYear via Tanaka formula`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        val currentYear = LocalDate.now().year
        val age = 30
        repo.setBirthYear(currentYear - age)
        val expected = 208.0 - 0.7 * age
        assertEquals(expected, repo.maxHeartRate.first(), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.prefs.UserPreferencesRepositoryTest"`
Expected: FAIL — `UserPreferencesRepository` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepository.kt
package com.esusic.recoverytracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val birthYear: Flow<Int?> = dataStore.data.map { it[BIRTH_YEAR_KEY] }

    val maxHeartRate: Flow<Double> = birthYear.map { year ->
        if (year == null) {
            DEFAULT_MAX_HR
        } else {
            val age = LocalDate.now().year - year
            (208.0 - 0.7 * age).coerceAtLeast(MIN_MAX_HR)
        }
    }

    suspend fun setBirthYear(year: Int) {
        dataStore.edit { it[BIRTH_YEAR_KEY] = year }
    }

    companion object {
        const val DEFAULT_MAX_HR = 190.0
        const val MIN_MAX_HR = 120.0
        private val BIRTH_YEAR_KEY = intPreferencesKey("birth_year")
    }
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/prefs/DataStoreExtensions.kt
package com.esusic.recoverytracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.prefs.UserPreferencesRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/prefs app/src/test/java/com/esusic/recoverytracker/data/prefs
git commit -m "feat: add user preferences for birth year and derived max heart rate"
```

---

### Task 5: BaselineCalculator

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/scoring/BaselineCalculator.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/BaselineCalculatorTest.kt`

**Interfaces:**
- Consumes: `Baseline` (Task 2).
- Produces: `BaselineCalculator.calculate(recentValues: List<Double>): Baseline`, `BaselineCalculator.WINDOW_DAYS: Int = 30`.
- Consumed by: Task 12 (`HealthRepository`) to build HRV/RHR baselines before scoring.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/scoring/BaselineCalculatorTest.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class BaselineCalculatorTest {
    @Test
    fun `empty input yields zeroed baseline`() {
        val baseline = BaselineCalculator.calculate(emptyList())
        assertEquals(Baseline(0.0, 0.0, 0), baseline)
    }

    @Test
    fun `computes mean and population stdDev`() {
        val values = listOf(10.0, 12.0, 14.0)
        val baseline = BaselineCalculator.calculate(values)
        val expectedMean = 12.0
        val expectedStdDev = sqrt(((10.0 - 12.0).let { it * it } + 0.0 + (14.0 - 12.0).let { it * it }) / 3.0)
        assertEquals(expectedMean, baseline.mean, 0.0001)
        assertEquals(expectedStdDev, baseline.stdDev, 0.0001)
        assertEquals(3, baseline.sampleCount)
    }

    @Test
    fun `only uses the trailing WINDOW_DAYS values`() {
        val oldOutlier = listOf(1000.0)
        val recent = List(BaselineCalculator.WINDOW_DAYS) { 50.0 }
        val baseline = BaselineCalculator.calculate(oldOutlier + recent)
        assertEquals(50.0, baseline.mean, 0.0001)
        assertEquals(BaselineCalculator.WINDOW_DAYS, baseline.sampleCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.BaselineCalculatorTest"`
Expected: FAIL — `BaselineCalculator` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/scoring/BaselineCalculator.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import kotlin.math.sqrt

object BaselineCalculator {
    const val WINDOW_DAYS = 30

    fun calculate(recentValues: List<Double>): Baseline {
        val windowed = recentValues.takeLast(WINDOW_DAYS)
        if (windowed.isEmpty()) return Baseline(mean = 0.0, stdDev = 0.0, sampleCount = 0)

        val mean = windowed.average()
        val variance = windowed.sumOf { (it - mean) * (it - mean) } / windowed.size
        return Baseline(mean = mean, stdDev = sqrt(variance), sampleCount = windowed.size)
    }
}
```

Note: `takeLast` assumes `recentValues` is passed in chronological (oldest-first) order — callers (Task 12) must reverse the DAO's newest-first `recentBefore` results before calling this.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.BaselineCalculatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/scoring/BaselineCalculator.kt app/src/test/java/com/esusic/recoverytracker/scoring/BaselineCalculatorTest.kt
git commit -m "feat: add rolling baseline calculator"
```

---

### Task 6: RecoveryScoreCalculator

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/scoring/RecoveryScoreCalculator.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/RecoveryScoreCalculatorTest.kt`

**Interfaces:**
- Consumes: `DailyAggregate`, `Baseline`, `RecoveryScore`, `StrainScore`, `SleepSession`, `SleepStageType` (Task 2); `Baseline.MIN_SAMPLES_FOR_SCORING` (Task 2).
- Produces: `RecoveryScoreCalculator.calculate(date: LocalDate, today: DailyAggregate, hrvBaseline: Baseline, rhrBaseline: Baseline, typicalSleepMinutes: Double, typicalDeepRemPercent: Double, priorDayStrain: StrainScore?): RecoveryScore`.
- Consumed by: Task 12 (`HealthRepository`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/scoring/RecoveryScoreCalculatorTest.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.StrainScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RecoveryScoreCalculatorTest {
    private val date = LocalDate.of(2026, 7, 13)
    private val calibratedBaseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = Baseline.MIN_SAMPLES_FOR_SCORING)
    private val uncalibratedBaseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = 2)

    private fun sleepOf(durationMinutes: Long, deepMinutes: Long, remMinutes: Long): SleepSession {
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val deepEnd = start.plus(deepMinutes, ChronoUnit.MINUTES)
        val remEnd = deepEnd.plus(remMinutes, ChronoUnit.MINUTES)
        val end = start.plus(durationMinutes, ChronoUnit.MINUTES)
        return SleepSession(
            start = start,
            end = end,
            stages = listOf(
                SleepStagePeriod(SleepStageType.DEEP, start, deepEnd),
                SleepStagePeriod(SleepStageType.REM, deepEnd, remEnd),
                SleepStagePeriod(SleepStageType.LIGHT, remEnd, end)
            )
        )
    }

    private fun aggregate(hrv: Double?, rhr: Long?, sleep: SleepSession?) = DailyAggregate(
        date = date, avgHrvMillis = hrv, restingHeartRate = rhr, sleep = sleep,
        steps = 5000, activeCaloriesKcal = 200.0, heartRateSamples = emptyList(),
        exerciseSessions = emptyList(), isComplete = true
    )

    @Test
    fun `isCalibrating true when either baseline below minimum samples`() {
        val result = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleepOf(420, 80, 90)),
            uncalibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertTrue(result.isCalibrating)
    }

    @Test
    fun `above-baseline HRV and below-baseline RHR raise the score`() {
        val goodDay = RecoveryScoreCalculator.calculate(
            date, aggregate(hrv = 70.0, rhr = 40L, sleep = sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        val badDay = RecoveryScoreCalculator.calculate(
            date, aggregate(hrv = 30.0, rhr = 60L, sleep = sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertTrue(goodDay.value > badDay.value)
    }

    @Test
    fun `high prior-day strain lowers the score relative to no prior strain`() {
        val noStrain = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, priorDayStrain = null
        )
        val highStrain = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0,
            priorDayStrain = StrainScore(date.minusDays(1), 21.0)
        )
        assertTrue(highStrain.value < noStrain.value)
    }

    @Test
    fun `score is always clamped between 0 and 100`() {
        val result = RecoveryScoreCalculator.calculate(
            date, aggregate(hrv = 200.0, rhr = 30L, sleep = sleepOf(600, 300, 200)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertEquals(result.value, result.value.coerceIn(0, 100))
    }

    @Test
    fun `missing sleep falls back to neutral sleep sub-score`() {
        val result = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleep = null),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertEquals(50.0, result.sleepSubScore, 0.0001)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.RecoveryScoreCalculatorTest"`
Expected: FAIL — `RecoveryScoreCalculator` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/scoring/RecoveryScoreCalculator.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.RecoveryScore
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.StrainScore
import java.time.LocalDate
import kotlin.math.roundToInt

object RecoveryScoreCalculator {
    private const val HRV_WEIGHT = 0.35
    private const val RHR_WEIGHT = 0.25
    private const val SLEEP_WEIGHT = 0.30
    private const val STRAIN_WEIGHT = 0.10
    private const val NEUTRAL_SUB_SCORE = 50.0
    private const val MAX_STRAIN = 21.0

    fun calculate(
        date: LocalDate,
        today: DailyAggregate,
        hrvBaseline: Baseline,
        rhrBaseline: Baseline,
        typicalSleepMinutes: Double,
        typicalDeepRemPercent: Double,
        priorDayStrain: StrainScore?
    ): RecoveryScore {
        val isCalibrating = hrvBaseline.sampleCount < Baseline.MIN_SAMPLES_FOR_SCORING ||
            rhrBaseline.sampleCount < Baseline.MIN_SAMPLES_FOR_SCORING

        val hrvSub = today.avgHrvMillis
            ?.let { subScoreFromZ(hrvBaseline.zScore(it), invert = false) }
            ?: NEUTRAL_SUB_SCORE
        val rhrSub = today.restingHeartRate
            ?.let { subScoreFromZ(rhrBaseline.zScore(it.toDouble()), invert = true) }
            ?: NEUTRAL_SUB_SCORE
        val sleepSub = sleepSubScore(today.sleep, typicalSleepMinutes, typicalDeepRemPercent)
        val strainSub = strainSubScore(priorDayStrain)

        val composite = hrvSub * HRV_WEIGHT + rhrSub * RHR_WEIGHT +
            sleepSub * SLEEP_WEIGHT + strainSub * STRAIN_WEIGHT

        return RecoveryScore(
            date = date,
            value = composite.roundToInt().coerceIn(0, 100),
            isCalibrating = isCalibrating,
            hrvSubScore = hrvSub,
            rhrSubScore = rhrSub,
            sleepSubScore = sleepSub
        )
    }

    private fun subScoreFromZ(z: Double, invert: Boolean): Double {
        val signed = if (invert) -z else z
        val clamped = signed.coerceIn(-2.0, 2.0)
        return ((clamped + 2.0) / 4.0) * 100.0
    }

    private fun strainSubScore(priorDayStrain: StrainScore?): Double {
        val strainPenalty = priorDayStrain?.let { (it.value / MAX_STRAIN).coerceIn(0.0, 1.0) } ?: 0.0
        return (1.0 - strainPenalty) * 100.0
    }

    private fun sleepSubScore(
        sleep: SleepSession?,
        typicalMinutes: Double,
        typicalDeepRemPercent: Double
    ): Double {
        if (sleep == null || typicalMinutes <= 0.0) return NEUTRAL_SUB_SCORE

        val durationScore = (sleep.durationMinutes / typicalMinutes).coerceIn(0.0, 1.0) * 100.0

        val deepRemMinutes = sleep.minutesIn(SleepStageType.DEEP) + sleep.minutesIn(SleepStageType.REM)
        val deepRemPercent = if (sleep.durationMinutes > 0) {
            deepRemMinutes.toDouble() / sleep.durationMinutes * 100.0
        } else {
            0.0
        }
        val qualityScore = if (typicalDeepRemPercent <= 0.0) {
            100.0
        } else {
            (deepRemPercent / typicalDeepRemPercent * 100.0).coerceIn(0.0, 100.0)
        }

        return durationScore * 0.6 + qualityScore * 0.4
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.RecoveryScoreCalculatorTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/scoring/RecoveryScoreCalculator.kt app/src/test/java/com/esusic/recoverytracker/scoring/RecoveryScoreCalculatorTest.kt
git commit -m "feat: add recovery score calculator"
```

---

### Task 7: StrainScoreCalculator

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/scoring/StrainScoreCalculator.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/StrainScoreCalculatorTest.kt`

**Interfaces:**
- Consumes: `HeartRateSample`, `StrainScore` (Task 2).
- Produces: `StrainScoreCalculator.calculate(date: LocalDate, samples: List<HeartRateSample>, maxHr: Double): StrainScore`.
- Consumed by: Task 12 (`HealthRepository`).
- Design note: strain is derived purely from heart-rate-zone minutes across the day's continuous HR samples. Exercise sessions (`DailyAggregate.exerciseSessions`) are surfaced separately in the UI as a "workouts today" list (Task 16) rather than double-counted into the strain formula — elevated HR during a workout is already captured by the HR-zone samples.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/scoring/StrainScoreCalculatorTest.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StrainScoreCalculatorTest {
    private val date = LocalDate.of(2026, 7, 13)
    private val maxHr = 190.0

    private fun samplesAt(bpmSequence: List<Long>, minutesApart: Long = 1): List<HeartRateSample> {
        val start = Instant.parse("2026-07-13T08:00:00Z")
        return bpmSequence.mapIndexed { index, bpm ->
            HeartRateSample(start.plus(index * minutesApart, ChronoUnit.MINUTES), bpm)
        }
    }

    @Test
    fun `fewer than 2 samples yields zero strain`() {
        val result = StrainScoreCalculator.calculate(date, samplesAt(listOf(70)), maxHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `resting heart rate all day yields low strain`() {
        val samples = samplesAt(List(60) { 60L }) // ~32% of maxHr, zone 0
        val result = StrainScoreCalculator.calculate(date, samples, maxHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `sustained high heart rate yields higher strain than sustained moderate`() {
        val moderate = samplesAt(List(60) { 130L }) // ~68% of maxHr, zone 2
        val high = samplesAt(List(60) { 175L }) // ~92% of maxHr, zone 5
        val moderateStrain = StrainScoreCalculator.calculate(date, moderate, maxHr)
        val highStrain = StrainScoreCalculator.calculate(date, high, maxHr)
        assertTrue(highStrain.value > moderateStrain.value)
    }

    @Test
    fun `strain is clamped to a max of 21`() {
        val allOutSamples = samplesAt(List(500) { 189L })
        val result = StrainScoreCalculator.calculate(date, allOutSamples, maxHr)
        assertTrue(result.value <= 21.0)
    }

    @Test
    fun `gaps longer than 15 minutes are excluded from load accumulation`() {
        val start = Instant.parse("2026-07-13T08:00:00Z")
        val samples = listOf(
            HeartRateSample(start, 175L),
            HeartRateSample(start.plus(30, ChronoUnit.MINUTES), 175L) // 30-minute gap, likely device removed
        )
        val result = StrainScoreCalculator.calculate(date, samples, maxHr)
        assertEquals(0.0, result.value, 0.0001)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.StrainScoreCalculatorTest"`
Expected: FAIL — `StrainScoreCalculator` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/scoring/StrainScoreCalculator.kt
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.StrainScore
import java.time.Duration
import java.time.LocalDate
import kotlin.math.ln

object StrainScoreCalculator {
    private val ZONE_WEIGHTS = mapOf(1 to 1.0, 2 to 2.0, 3 to 4.0, 4 to 8.0, 5 to 16.0)
    private const val SCALE = 3.5
    private const val MAX_STRAIN = 21.0
    private const val MAX_GAP_MINUTES = 15.0

    fun calculate(date: LocalDate, samples: List<HeartRateSample>, maxHr: Double): StrainScore {
        if (samples.size < 2) return StrainScore(date, 0.0)

        val sorted = samples.sortedBy { it.time }
        var rawLoad = 0.0

        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val minutes = Duration.between(a.time, b.time).toMillis() / 60000.0
            if (minutes <= 0.0 || minutes > MAX_GAP_MINUTES) continue

            val avgBpm = (a.bpm + b.bpm) / 2.0
            val zone = zoneFor(avgBpm, maxHr)
            if (zone > 0) rawLoad += (ZONE_WEIGHTS[zone] ?: 0.0) * minutes
        }

        val strain = (ln(1.0 + rawLoad) * SCALE).coerceIn(0.0, MAX_STRAIN)
        return StrainScore(date, strain)
    }

    private fun zoneFor(bpm: Double, maxHr: Double): Int {
        val pct = bpm / maxHr * 100.0
        return when {
            pct < 50.0 -> 0
            pct < 60.0 -> 1
            pct < 70.0 -> 2
            pct < 80.0 -> 3
            pct < 90.0 -> 4
            else -> 5
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.scoring.StrainScoreCalculatorTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/scoring/StrainScoreCalculator.kt app/src/test/java/com/esusic/recoverytracker/scoring/StrainScoreCalculatorTest.kt
git commit -m "feat: add strain score calculator"
```

---

### Task 8: HealthDataSource Interface + Fake

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthDataSource.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSource.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSourceTest.kt`

**Interfaces:**
- Consumes: `HeartRateSample`, `HrvSample`, `RestingHrSample`, `SleepSession`, `ExerciseSession` (Task 2).
- Produces: interface `HealthDataSource` with `suspend fun heartRateSamples(start: Instant, end: Instant): List<HeartRateSample>`, `suspend fun hrvSamples(start: Instant, end: Instant): List<HrvSample>`, `suspend fun restingHeartRate(start: Instant, end: Instant): List<RestingHrSample>`, `suspend fun sleepSessions(start: Instant, end: Instant): List<SleepSession>`, `suspend fun totalSteps(start: Instant, end: Instant): Int`, `suspend fun totalActiveCalories(start: Instant, end: Instant): Double`, `suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSession>`, `suspend fun hasAllPermissions(): Boolean`. Also `FakeHealthDataSource`, a configurable in-memory implementation for tests.
- Consumed by: Task 10 (real implementation implements this interface), Task 11 (`AggregationService` depends on the interface, not the real client).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSourceTest.kt
package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.HeartRateSample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FakeHealthDataSourceTest {
    @Test
    fun `filters samples to the requested time range`() = runTest {
        val base = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            heartRate = listOf(
                HeartRateSample(base.minus(1, ChronoUnit.DAYS), 60L),
                HeartRateSample(base.plus(1, ChronoUnit.HOURS), 70L),
                HeartRateSample(base.plus(1, ChronoUnit.DAYS), 80L)
            )
        )
        val result = source.heartRateSamples(base, base.plus(1, ChronoUnit.DAYS))
        assertEquals(listOf(70L), result.map { it.bpm })
    }

    @Test
    fun `hasAllPermissions reflects configured value`() = runTest {
        assertEquals(false, FakeHealthDataSource(permissionsGranted = false).hasAllPermissions())
        assertEquals(true, FakeHealthDataSource(permissionsGranted = true).hasAllPermissions())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSourceTest"`
Expected: FAIL — `FakeHealthDataSource` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthDataSource.kt
package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
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
    suspend fun hasAllPermissions(): Boolean
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSource.kt
package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
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

    override suspend fun hasAllPermissions() = permissionsGranted
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSourceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthDataSource.kt app/src/main/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSource.kt app/src/test/java/com/esusic/recoverytracker/data/healthconnect/FakeHealthDataSourceTest.kt
git commit -m "feat: add HealthDataSource interface and fake test double"
```

---

### Task 9: HealthConnectRecordMapper

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapper.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapperTest.kt`

**Interfaces:**
- Consumes: `HeartRateSample`, `HrvSample`, `RestingHrSample`, `SleepSession`, `SleepStagePeriod`, `SleepStageType`, `ExerciseSession` (Task 2). Health Connect SDK record types (`androidx.health.connect.client.records.*`).
- Produces: `HealthConnectRecordMapper` object with pure mapping functions: `toHeartRateSamples(List<HeartRateRecord>): List<HeartRateSample>`, `toHrvSamples(List<HeartRateVariabilityRmssdRecord>): List<HrvSample>`, `toRestingHrSamples(List<RestingHeartRateRecord>): List<RestingHrSample>`, `toSleepSessions(List<SleepSessionRecord>): List<SleepSession>`, `toTotalSteps(List<StepsRecord>): Int`, `toTotalActiveCalories(List<ActiveCaloriesBurnedRecord>): Double`, `toExerciseSessions(List<ExerciseSessionRecord>): List<ExerciseSession>`.
- Consumed by: Task 10 (`HealthConnectDataSource`).
- Design note: Health Connect record types are plain constructible data objects — no live client or Android `Context` is needed to build them in a test, so this mapping logic is fully unit-testable even though it deals with the Health Connect SDK. Only the actual `client.readRecords(...)` call (Task 10) requires a real device/Health Connect install and is therefore verified manually instead.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapperTest.kt
package com.esusic.recoverytracker.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.units.Energy
import com.esusic.recoverytracker.model.SleepStageType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectRecordMapperTest {
    private val base = Instant.parse("2026-07-13T00:00:00Z")

    @Test
    fun `flattens heart rate record samples`() {
        val record = HeartRateRecord(
            startTime = base, startZoneOffset = null,
            endTime = base.plus(1, ChronoUnit.HOURS), endZoneOffset = null,
            samples = listOf(
                HeartRateRecord.Sample(base, 60L),
                HeartRateRecord.Sample(base.plus(30, ChronoUnit.MINUTES), 65L)
            )
        )
        val result = HealthConnectRecordMapper.toHeartRateSamples(listOf(record))
        assertEquals(listOf(60L, 65L), result.map { it.bpm })
    }

    @Test
    fun `maps HRV RMSSD records`() {
        val record = HeartRateVariabilityRmssdRecord(
            time = base, zoneOffset = null, heartRateVariabilityMillis = 45.0
        )
        val result = HealthConnectRecordMapper.toHrvSamples(listOf(record))
        assertEquals(45.0, result.single().rmssdMillis, 0.0001)
    }

    @Test
    fun `maps resting heart rate records`() {
        val record = RestingHeartRateRecord(time = base, zoneOffset = null, beatsPerMinute = 52L)
        val result = HealthConnectRecordMapper.toRestingHrSamples(listOf(record))
        assertEquals(52L, result.single().bpm)
    }

    @Test
    fun `maps sleep stages to domain stage types`() {
        val deepEnd = base.plus(2, ChronoUnit.HOURS)
        val remEnd = deepEnd.plus(1, ChronoUnit.HOURS)
        val record = SleepSessionRecord(
            startTime = base, startZoneOffset = null,
            endTime = remEnd, endZoneOffset = null,
            stages = listOf(
                SleepSessionRecord.Stage(base, deepEnd, SleepSessionRecord.STAGE_TYPE_DEEP),
                SleepSessionRecord.Stage(deepEnd, remEnd, SleepSessionRecord.STAGE_TYPE_REM)
            )
        )
        val result = HealthConnectRecordMapper.toSleepSessions(listOf(record)).single()
        assertEquals(listOf(SleepStageType.DEEP, SleepStageType.REM), result.stages.map { it.stage })
    }

    @Test
    fun `sums steps across records`() {
        val records = listOf(
            StepsRecord(base, null, base.plus(1, ChronoUnit.HOURS), null, count = 1000L),
            StepsRecord(base.plus(1, ChronoUnit.HOURS), null, base.plus(2, ChronoUnit.HOURS), null, count = 500L)
        )
        assertEquals(1500, HealthConnectRecordMapper.toTotalSteps(records))
    }

    @Test
    fun `sums active calories across records`() {
        val records = listOf(
            ActiveCaloriesBurnedRecord(base, null, base.plus(1, ChronoUnit.HOURS), null, Energy.kilocalories(120.0)),
            ActiveCaloriesBurnedRecord(base.plus(1, ChronoUnit.HOURS), null, base.plus(2, ChronoUnit.HOURS), null, Energy.kilocalories(80.0))
        )
        assertEquals(200.0, HealthConnectRecordMapper.toTotalActiveCalories(records), 0.0001)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.HealthConnectRecordMapperTest"`
Expected: FAIL — `HealthConnectRecordMapper` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapper.kt
package com.esusic.recoverytracker.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType

object HealthConnectRecordMapper {

    fun toHeartRateSamples(records: List<HeartRateRecord>): List<HeartRateSample> =
        records.flatMap { record -> record.samples.map { HeartRateSample(it.time, it.beatsPerMinute) } }

    fun toHrvSamples(records: List<HeartRateVariabilityRmssdRecord>): List<HrvSample> =
        records.map { HrvSample(it.time, it.heartRateVariabilityMillis) }

    fun toRestingHrSamples(records: List<RestingHeartRateRecord>): List<RestingHrSample> =
        records.map { RestingHrSample(it.time, it.beatsPerMinute) }

    fun toSleepSessions(records: List<SleepSessionRecord>): List<SleepSession> =
        records.map { record ->
            SleepSession(
                start = record.startTime,
                end = record.endTime,
                stages = record.stages.map { toStagePeriod(it) }
            )
        }

    private fun toStagePeriod(stage: SleepSessionRecord.Stage): SleepStagePeriod =
        SleepStagePeriod(mapStageType(stage.stage), stage.startTime, stage.endTime)

    private fun mapStageType(stageType: Int): SleepStageType = when (stageType) {
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStageType.AWAKE
        else -> SleepStageType.UNKNOWN
    }

    fun toTotalSteps(records: List<StepsRecord>): Int = records.sumOf { it.count }.toInt()

    fun toTotalActiveCalories(records: List<ActiveCaloriesBurnedRecord>): Double =
        records.sumOf { it.energy.inKilocalories }

    fun toExerciseSessions(records: List<ExerciseSessionRecord>): List<ExerciseSession> =
        records.map { ExerciseSession(it.startTime, it.endTime, it.exerciseType) }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.healthconnect.HealthConnectRecordMapperTest"`
Expected: PASS (6 tests). If a Health Connect record constructor signature in the test doesn't match the actual 1.1.0 API (parameter names/order occasionally shift between versions), check `androidx.health.connect.client.records` in the installed library sources and adjust the constructor calls accordingly — the mapping logic itself does not change.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapper.kt app/src/test/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectRecordMapperTest.kt
git commit -m "feat: add Health Connect record to domain model mapper"
```

---

### Task 10: HealthConnectDataSource (real implementation)

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectDataSource.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectAvailability.kt`

**Interfaces:**
- Consumes: `HealthDataSource` (Task 8), `HealthConnectRecordMapper` (Task 9).
- Produces: `HealthConnectDataSource(client: HealthConnectClient) : HealthDataSource`, `HealthConnectDataSource.REQUIRED_PERMISSIONS: Set<String>`, `checkHealthConnectAvailability(context: Context): HealthConnectAvailability` (sealed result: `Available`, `NotInstalled`, `UpdateRequired`).
- Consumed by: Task 14 (`ServiceLocator`), Task 15 (`OnboardingScreen`, to request `REQUIRED_PERMISSIONS` and to react to `HealthConnectAvailability`).
- Testing approach: this class is a thin wrapper whose only logic is "call `client.readRecords`, hand the result to the already-tested mapper." It requires a real Android `Context` and the Health Connect app to produce meaningful data, so per the spec's testing approach it is verified manually (Task 21's on-device checklist), not with an automated test — automated coverage lives entirely in Task 9's mapper tests, which exercise the actual transformation logic.

- [ ] **Step 1: Implement availability check**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectAvailability.kt
package com.esusic.recoverytracker.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient

sealed interface HealthConnectAvailability {
    data object Available : HealthConnectAvailability
    data object NotInstalled : HealthConnectAvailability
    data object UpdateRequired : HealthConnectAvailability
}

fun checkHealthConnectAvailability(context: Context): HealthConnectAvailability =
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UpdateRequired
        else -> HealthConnectAvailability.NotInstalled
    }
```

- [ ] **Step 2: Implement the real data source**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectDataSource.kt
package com.esusic.recoverytracker.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.Instant

class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override suspend fun heartRateSamples(start: Instant, end: Instant): List<HeartRateSample> =
        HealthConnectRecordMapper.toHeartRateSamples(readRecords<HeartRateRecord>(start, end))

    override suspend fun hrvSamples(start: Instant, end: Instant): List<HrvSample> =
        HealthConnectRecordMapper.toHrvSamples(readRecords<HeartRateVariabilityRmssdRecord>(start, end))

    override suspend fun restingHeartRate(start: Instant, end: Instant): List<RestingHrSample> =
        HealthConnectRecordMapper.toRestingHrSamples(readRecords<RestingHeartRateRecord>(start, end))

    override suspend fun sleepSessions(start: Instant, end: Instant): List<SleepSession> =
        HealthConnectRecordMapper.toSleepSessions(readRecords<SleepSessionRecord>(start, end))

    override suspend fun totalSteps(start: Instant, end: Instant): Int =
        HealthConnectRecordMapper.toTotalSteps(readRecords<StepsRecord>(start, end))

    override suspend fun totalActiveCalories(start: Instant, end: Instant): Double =
        HealthConnectRecordMapper.toTotalActiveCalories(readRecords<ActiveCaloriesBurnedRecord>(start, end))

    override suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSession> =
        HealthConnectRecordMapper.toExerciseSessions(readRecords<ExerciseSessionRecord>(start, end))

    override suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readRecords(
        start: Instant,
        end: Instant
    ): List<T> = client.readRecords(
        ReadRecordsRequest(recordType = T::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records

    companion object {
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
    }
}
```

- [ ] **Step 3: Manual verification checklist (documented here, executed in Task 21)**

1. Install Health Connect and confirm the Fitbit/Google Health app is configured to write heart rate, HRV, resting heart rate, sleep, steps, active calories, and exercise into it (Settings → Health Connect → App permissions, inside the Google Health app).
2. Launch this app after Task 15 (onboarding) is implemented, grant all requested permissions.
3. Add a temporary debug `Log.d` call (or breakpoint) in `HealthConnectDataSource.heartRateSamples` and confirm non-empty results for a day you wore the Fitbit.
4. Remove the temporary logging before committing Task 21.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectDataSource.kt app/src/main/java/com/esusic/recoverytracker/data/healthconnect/HealthConnectAvailability.kt
git commit -m "feat: add real Health Connect data source implementation"
```

---

### Task 11: AggregationService

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt`

**Interfaces:**
- Consumes: `HealthDataSource` (Task 8), `FakeHealthDataSource` (Task 8), `DailyAggregate` and sample types (Task 2).
- Produces: `AggregationService(dataSource: HealthDataSource)` with `suspend fun aggregateForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DailyAggregate`.
- Consumed by: Task 12 (`HealthRepository`).

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt
package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AggregationServiceTest {
    private val date = LocalDate.of(2026, 7, 13)
    private val zone = ZoneOffset.UTC
    private val dayStart = date.atStartOfDay(zone).toInstant()

    @Test
    fun `averages HRV samples within the day`() = runTest {
        val source = FakeHealthDataSource(
            hrv = listOf(
                HrvSample(dayStart.plusSeconds(3600), 40.0),
                HrvSample(dayStart.plusSeconds(7200), 60.0)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(50.0, result.avgHrvMillis!!, 0.0001)
    }

    @Test
    fun `avgHrvMillis is null when no HRV samples exist`() = runTest {
        val result = AggregationService(FakeHealthDataSource()).aggregateForDate(date, zone)
        assertNull(result.avgHrvMillis)
    }

    @Test
    fun `restingHeartRate takes the lowest reading of the day`() = runTest {
        val source = FakeHealthDataSource(
            restingHr = listOf(
                RestingHrSample(dayStart.plusSeconds(3600), 55L),
                RestingHrSample(dayStart.plusSeconds(7200), 48L)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(48L, result.restingHeartRate)
    }

    @Test
    fun `sleep picks the longest session overlapping the day`() = runTest {
        val short = SleepSession(dayStart.plusSeconds(1000), dayStart.plusSeconds(2000), emptyList())
        val long = SleepSession(dayStart, dayStart.plusSeconds(20000), emptyList())
        val source = FakeHealthDataSource(sleep = listOf(short, long))
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(long, result.sleep)
    }

    @Test
    fun `isComplete is true only when both heart rate samples and sleep are present`() = runTest {
        val complete = AggregationService(
            FakeHealthDataSource(
                heartRate = listOf(HeartRateSample(dayStart, 60L)),
                sleep = listOf(SleepSession(dayStart, dayStart.plusSeconds(100), emptyList()))
            )
        ).aggregateForDate(date, zone)
        val incomplete = AggregationService(FakeHealthDataSource()).aggregateForDate(date, zone)

        assertTrue(complete.isComplete)
        assertTrue(!incomplete.isComplete)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.AggregationServiceTest"`
Expected: FAIL — `AggregationService` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt
package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.model.DailyAggregate
import java.time.LocalDate
import java.time.ZoneId

class AggregationService(private val dataSource: HealthDataSource) {

    suspend fun aggregateForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DailyAggregate {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()

        val heartRateSamples = dataSource.heartRateSamples(start, end)
        val hrvSamples = dataSource.hrvSamples(start, end)
        val restingHrSamples = dataSource.restingHeartRate(start, end)
        val sleep = dataSource.sleepSessions(start, end).maxByOrNull { it.durationMinutes }
        val steps = dataSource.totalSteps(start, end)
        val activeCalories = dataSource.totalActiveCalories(start, end)
        val exerciseSessions = dataSource.exerciseSessions(start, end)

        val avgHrv = hrvSamples.takeIf { it.isNotEmpty() }?.map { it.rmssdMillis }?.average()
        val restingHeartRate = restingHrSamples.minByOrNull { it.bpm }?.bpm
        val isComplete = heartRateSamples.isNotEmpty() && sleep != null

        return DailyAggregate(
            date = date,
            avgHrvMillis = avgHrv,
            restingHeartRate = restingHeartRate,
            sleep = sleep,
            steps = steps,
            activeCaloriesKcal = activeCalories,
            heartRateSamples = heartRateSamples,
            exerciseSessions = exerciseSessions,
            isComplete = isComplete
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.AggregationServiceTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/AggregationService.kt app/src/test/java/com/esusic/recoverytracker/data/AggregationServiceTest.kt
git commit -m "feat: add daily aggregation service"
```

---

### Task 12: HealthRepository

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/data/local/Mappers.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt`

**Interfaces:**
- Consumes: `AggregationService` (Task 11), `DailyScoreDao`/`DailyAggregateDao`/`DailyScoreEntity`/`DailyAggregateEntity`/`AppDatabase` (Task 3), `UserPreferencesRepository` (Task 4), `BaselineCalculator` (Task 5), `RecoveryScoreCalculator` (Task 6), `StrainScoreCalculator` (Task 7), `HealthDataSource`/`FakeHealthDataSource` (Task 8).
- Produces: `HealthRepository(aggregationService: AggregationService, scoreDao: DailyScoreDao, aggregateDao: DailyAggregateDao, userPreferences: UserPreferencesRepository, clock: Clock = Clock.systemDefaultZone())` with `fun observeRecentScores(days: Int): Flow<List<DailyScoreEntity>>`, `fun observeRecentAggregates(days: Int): Flow<List<DailyAggregateEntity>>`, `suspend fun syncToday(): DailyScoreEntity`, `suspend fun syncDate(date: LocalDate): DailyScoreEntity`. Also extension functions `DailyAggregate.toEntity(): DailyAggregateEntity` and `DailyAggregateEntity.typicalSleepMinutesFrom(history: List<DailyAggregateEntity>): Double` style helpers live in `Mappers.kt`.
- Consumed by: Task 13 (`SyncWorker`), Task 14 (`ServiceLocator`), and all UI ViewModels (Tasks 16-19).

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt
package com.esusic.recoverytracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class HealthRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var userPreferences: UserPreferencesRepository
    private val date = LocalDate.of(2026, 7, 13)
    private val fixedClock = Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher())
        ) { tempFolder.newFile("test.preferences_pb") }
        userPreferences = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repositoryWith(source: FakeHealthDataSource) = HealthRepository(
        aggregationService = AggregationService(source),
        scoreDao = db.dailyScoreDao(),
        aggregateDao = db.dailyAggregateDao(),
        userPreferences = userPreferences,
        clock = fixedClock
    )

    @Test
    fun `syncDate persists both an aggregate row and a score row`() = runTest {
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(
            heartRate = listOf(HeartRateSample(dayStart.plusSeconds(60), 70L)),
            hrv = listOf(HrvSample(dayStart.plusSeconds(60), 45.0)),
            restingHr = listOf(RestingHrSample(dayStart.plusSeconds(60), 50L)),
            sleep = listOf(
                SleepSession(
                    dayStart, dayStart.plusSeconds(6 * 3600),
                    listOf(SleepStagePeriod(SleepStageType.DEEP, dayStart, dayStart.plusSeconds(3600)))
                )
            )
        )
        val repository = repositoryWith(source)

        val result = repository.syncDate(date)

        assertEquals(date.toString(), result.date)
        assertEquals(result, db.dailyScoreDao().getByDate(date.toString()))
        assertTrue(db.dailyAggregateDao().getByDate(date.toString()) != null)
    }

    @Test
    fun `syncDate marks the result calibrating with fewer than 7 days of aggregate history`() = runTest {
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(
            heartRate = listOf(HeartRateSample(dayStart.plusSeconds(60), 70L)),
            hrv = listOf(HrvSample(dayStart.plusSeconds(60), 45.0))
        )
        val result = repositoryWith(source).syncDate(date)
        assertTrue(result.recoveryIsCalibrating)
    }

    @Test
    fun `syncToday uses the injected clock to determine the date`() = runTest {
        val repository = repositoryWith(FakeHealthDataSource())
        val result = repository.syncToday()
        assertEquals(LocalDate.now(fixedClock).toString(), result.date)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.HealthRepositoryTest"`
Expected: FAIL — `HealthRepository` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/local/Mappers.kt
package com.esusic.recoverytracker.data.local

import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.RecoveryScore
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.StrainScore

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
    isComplete = isComplete
)

fun DailyScoreEntity.toRecoveryScore(): RecoveryScore = RecoveryScore(
    date = java.time.LocalDate.parse(date),
    value = recoveryValue,
    isCalibrating = recoveryIsCalibrating,
    hrvSubScore = hrvSubScore,
    rhrSubScore = rhrSubScore,
    sleepSubScore = sleepSubScore
)

fun DailyScoreEntity.toStrainScore(): StrainScore = StrainScore(
    date = java.time.LocalDate.parse(date),
    value = strainValue
)
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt
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

        val entity = DailyScoreEntity(
            date = date.toString(),
            recoveryValue = recovery.value,
            recoveryIsCalibrating = recovery.isCalibrating,
            hrvSubScore = recovery.hrvSubScore,
            rhrSubScore = recovery.rhrSubScore,
            sleepSubScore = recovery.sleepSubScore,
            strainValue = strain.value,
            lastSyncedAtEpochMillis = clock.millis(),
            isComplete = aggregate.isComplete
        )
        scoreDao.upsert(entity)
        return entity
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.data.HealthRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/data/local/Mappers.kt app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt
git commit -m "feat: add HealthRepository wiring aggregation, baselines, and scoring"
```

---

### Task 13: SyncWorker + SyncScheduler

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/sync/SyncWorker.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/sync/AppWorkerFactory.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/sync/SyncScheduler.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/sync/SyncWorkerTest.kt`

**Interfaces:**
- Consumes: `HealthRepository` (Task 12).
- Produces: `SyncWorker(context: Context, params: WorkerParameters, private val repository: HealthRepository) : CoroutineWorker`, `SyncWorker.UNIQUE_PERIODIC_NAME: String`, `AppWorkerFactory(repositoryProvider: () -> HealthRepository) : WorkerFactory`, `SyncScheduler.schedulePeriodic(context: Context)`, `SyncScheduler.triggerImmediateSync(context: Context)`.
- Consumed by: Task 14 (`RecoveryTrackerApp` installs `AppWorkerFactory`; `ServiceLocator`/onboarding trigger `SyncScheduler`).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/sync/SyncWorkerTest.kt
package com.esusic.recoverytracker.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    @Test
    fun `doWork returns success and persists a score for today`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            File.createTempFile("test", ".preferences_pb")
        }
        val repository = HealthRepository(
            aggregationService = AggregationService(FakeHealthDataSource()),
            scoreDao = db.dailyScoreDao(),
            aggregateDao = db.dailyAggregateDao(),
            userPreferences = UserPreferencesRepository(dataStore)
        )

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(AppWorkerFactory { repository })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.sync.SyncWorkerTest"`
Expected: FAIL — `SyncWorker` and `AppWorkerFactory` are unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/sync/SyncWorker.kt
package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.esusic.recoverytracker.data.HealthRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: HealthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.syncToday()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "recovery_sync_periodic"
    }
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/sync/AppWorkerFactory.kt
package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.esusic.recoverytracker.data.HealthRepository

class AppWorkerFactory(private val repositoryProvider: () -> HealthRepository) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, repositoryProvider())
        else -> null
    }
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/sync/SyncScheduler.kt
package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SyncWorker.UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun triggerImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.sync.SyncWorkerTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/sync app/src/test/java/com/esusic/recoverytracker/sync/SyncWorkerTest.kt
git commit -m "feat: add background sync worker and scheduler"
```

---

### Task 14: App Shell — Application, ServiceLocator, Theme, Navigation Skeleton

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/RecoveryTrackerApp.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/MainActivity.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`

**Interfaces:**
- Consumes: `AppDatabase` (Task 3), `UserPreferencesRepository`/`userPreferencesDataStore` (Task 4), `HealthDataSource`/`FakeHealthDataSource` (Task 8), `HealthConnectDataSource`/`checkHealthConnectAvailability` (Task 10), `AggregationService` (Task 11), `HealthRepository` (Task 12), `AppWorkerFactory`/`SyncScheduler` (Task 13).
- Produces: `ServiceLocator` singleton object exposing `database`, `userPreferencesRepository`, `healthDataSource`, `repository: HealthRepository`, and `fun init(context: Context)`. `RecoveryTrackerApp : Application(), Configuration.Provider`. `RecoveryTrackerNavHost` composable with routes `"onboarding"`, `"today"`, `"trends"`, `"sleep"`, `"settings"`, `"detail/{metricType}"`. Bottom nav with 3 tabs (Today, Trends, Sleep) — Settings reached from Today's app bar, not bottom nav (matches spec's screen list).
- Consumed by: every remaining UI task.
- No automated test: this task is pure wiring with no branching logic of its own (each branch it calls into is already tested). Verified by Step 4's manual launch check.

- [ ] **Step 1: Implement ServiceLocator**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt
package com.esusic.recoverytracker

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.room.Room
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.healthconnect.HealthConnectDataSource
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.healthconnect.checkHealthConnectAvailability
import com.esusic.recoverytracker.data.healthconnect.HealthConnectAvailability
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.data.prefs.userPreferencesDataStore

object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "recovery_tracker.db").build()
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext.userPreferencesDataStore)
    }

    val healthDataSource: HealthDataSource by lazy {
        if (checkHealthConnectAvailability(appContext) is HealthConnectAvailability.Available) {
            HealthConnectDataSource(HealthConnectClient.getOrCreate(appContext))
        } else {
            FakeHealthDataSource()
        }
    }

    val repository: HealthRepository by lazy {
        HealthRepository(
            aggregationService = AggregationService(healthDataSource),
            scoreDao = database.dailyScoreDao(),
            aggregateDao = database.dailyAggregateDao(),
            userPreferences = userPreferencesRepository
        )
    }
}
```

- [ ] **Step 2: Implement the Application class**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/RecoveryTrackerApp.kt
package com.esusic.recoverytracker

import android.app.Application
import androidx.work.Configuration
import com.esusic.recoverytracker.sync.AppWorkerFactory

class RecoveryTrackerApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory { ServiceLocator.repository })
            .build()
}
```

Add this to `AndroidManifest.xml`'s `<application>` element to disable WorkManager's default initializer (required whenever a custom `WorkerFactory` is supplied via `Configuration.Provider`):

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="remove" />
```

This requires the manifest's root `<manifest>` tag to declare `xmlns:tools="http://schemas.android.com/tools"`.

- [ ] **Step 3: Implement theme and navigation skeleton**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt
package com.esusic.recoverytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RecoveryGreen = Color(0xFF17C97E)
private val StrainBlue = Color(0xFF2E6BFF)
private val WarningAmber = Color(0xFFE8A33D)
private val DangerRed = Color(0xFFE8503D)

private val DarkColors = darkColorScheme(primary = RecoveryGreen, secondary = StrainBlue, error = DangerRed)
private val LightColors = lightColorScheme(primary = RecoveryGreen, secondary = StrainBlue, error = DangerRed)

val ColorScheme.warning get() = WarningAmber

@Composable
fun RecoveryTrackerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
```

Note: `val ColorScheme.warning` needs `import androidx.compose.material3.ColorScheme` — add it alongside the other imports.

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt
package com.esusic.recoverytracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab("today", "Today", Icons.Filled.Home),
    BottomTab("trends", "Trends", Icons.Filled.ShowChart),
    BottomTab("sleep", "Sleep", Icons.Filled.Bedtime)
)

@Composable
fun RecoveryTrackerNavHost(startDestination: String) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = startDestination, modifier = androidx.compose.ui.Modifier.padding(padding)) {
            composable("onboarding") { Text("Onboarding placeholder") }
            composable("today") { Text("Today placeholder") }
            composable("trends") { Text("Trends placeholder") }
            composable("sleep") { Text("Sleep placeholder") }
            composable("settings") { Text("Settings placeholder") }
            composable("detail/{metricType}") { Text("Detail placeholder") }
        }
    }
}
```

Add to `libs.versions.toml`: `material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }` under `[libraries]`, and `implementation(libs.material.icons.extended)` in `app/build.gradle.kts`.

- [ ] **Step 4: Wire MainActivity and manually verify launch**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/MainActivity.kt
package com.esusic.recoverytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.esusic.recoverytracker.ui.navigation.RecoveryTrackerNavHost
import com.esusic.recoverytracker.ui.theme.RecoveryTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecoveryTrackerTheme {
                RecoveryTrackerNavHost(startDestination = "onboarding")
            }
        }
    }
}
```

Run: `./gradlew :app:assembleDebug`, then install and launch on-device: `./gradlew :app:installDebug`. Confirm the app opens showing "Onboarding placeholder" with a bottom nav bar (Today/Trends/Sleep) visible below it, and tapping each tab switches the placeholder text.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt app/src/main/java/com/esusic/recoverytracker/RecoveryTrackerApp.kt app/src/main/java/com/esusic/recoverytracker/MainActivity.kt app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt app/src/main/AndroidManifest.xml gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: wire app shell, DI container, theme, and navigation skeleton"
```

---

### Task 15: Onboarding Screen

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthConnectDataSource.REQUIRED_PERMISSIONS` (Task 10), `checkHealthConnectAvailability`/`HealthConnectAvailability` (Task 10), `UserPreferencesRepository` (Task 4), `SyncScheduler` (Task 13), `ServiceLocator` (Task 14).
- Produces: `OnboardingUiState(hasAllPermissions: Boolean, birthYear: Int?)`, `OnboardingViewModel(userPreferences: UserPreferencesRepository, healthDataSource: HealthDataSource)` with `val uiState: StateFlow<OnboardingUiState>`, `fun onBirthYearEntered(year: Int)`, `suspend fun refreshPermissionStatus()`. `OnboardingScreen` composable that first checks `checkHealthConnectAvailability(context)` (a one-time, non-reactive check done in the composable via `LocalContext.current` rather than piped through the ViewModel, since it needs an Android `Context` and doesn't change during the screen's lifetime) — if not `Available`, shows an install prompt linking to the Play Store instead of the permission/birth-year flow. Otherwise requests `HealthConnectDataSource.REQUIRED_PERMISSIONS` via `PermissionController.createRequestPermissionResultContract()`, collects birth year, and on completion calls `SyncScheduler.schedulePeriodic` + `triggerImmediateSync` then navigates to `"today"`.
- Consumed by: Task 14's nav host `"onboarding"` route (replacing the placeholder).

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/ui/onboarding/OnboardingViewModelTest.kt
package com.esusic.recoverytracker.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newUserPreferences() = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            tempFolder.newFile("test.preferences_pb")
        }
    )

    @Test
    fun `onBirthYearEntered persists the year and updates uiState`() = runTest {
        val userPreferences = newUserPreferences()
        val viewModel = OnboardingViewModel(userPreferences, FakeHealthDataSource())

        viewModel.onBirthYearEntered(1996)

        assertEquals(1996, userPreferences.birthYear.first())
        assertEquals(1996, viewModel.uiState.value.birthYear)
    }

    @Test
    fun `refreshPermissionStatus reflects the data source's granted state`() = runTest {
        val viewModel = OnboardingViewModel(newUserPreferences(), FakeHealthDataSource(permissionsGranted = true))
        viewModel.refreshPermissionStatus()
        assertTrue(viewModel.uiState.value.hasAllPermissions)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.onboarding.OnboardingViewModelTest"`
Expected: FAIL — `OnboardingViewModel` is unresolved.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/onboarding/OnboardingViewModel.kt
package com.esusic.recoverytracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val hasAllPermissions: Boolean = false,
    val birthYear: Int? = null
)

class OnboardingViewModel(
    private val userPreferences: UserPreferencesRepository,
    private val healthDataSource: HealthDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(birthYear = userPreferences.birthYear.first())
        }
    }

    fun onBirthYearEntered(year: Int) {
        viewModelScope.launch {
            userPreferences.setBirthYear(year)
            _uiState.value = _uiState.value.copy(birthYear = year)
        }
    }

    suspend fun refreshPermissionStatus() {
        _uiState.value = _uiState.value.copy(hasAllPermissions = healthDataSource.hasAllPermissions())
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement the screen and wire it into the nav host**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/onboarding/OnboardingScreen.kt
package com.esusic.recoverytracker.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.esusic.recoverytracker.data.healthconnect.HealthConnectAvailability
import com.esusic.recoverytracker.data.healthconnect.HealthConnectDataSource
import com.esusic.recoverytracker.data.healthconnect.checkHealthConnectAvailability
import com.esusic.recoverytracker.sync.SyncScheduler
import kotlinx.coroutines.launch

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val availability = remember { checkHealthConnectAvailability(context) }

    if (availability != HealthConnectAvailability.Available) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Health Connect required", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (availability == HealthConnectAvailability.UpdateRequired) {
                    "Health Connect needs an update before this app can read your Fitbit data."
                } else {
                    "This app reads your Fitbit data through Health Connect, which isn't installed yet."
                }
            )
            Button(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                )
            }) {
                Text("Open Play Store")
            }
        }
        return
    }

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var birthYearInput by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch { viewModel.refreshPermissionStatus() }
    }

    LaunchedEffect(Unit) { viewModel.refreshPermissionStatus() }

    LaunchedEffect(uiState.hasAllPermissions, uiState.birthYear) {
        if (uiState.hasAllPermissions && uiState.birthYear != null) {
            SyncScheduler.schedulePeriodic(context)
            SyncScheduler.triggerImmediateSync(context)
            onFinished()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome to Recovery Tracker", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This app reads heart rate, HRV, resting heart rate, sleep, steps, active calories, " +
                "and exercise sessions from Health Connect to compute a daily recovery score and a " +
                "live strain score. Scores need about 1-2 weeks of data to calibrate to you."
        )

        if (!uiState.hasAllPermissions) {
            Button(onClick = { permissionLauncher.launch(HealthConnectDataSource.REQUIRED_PERMISSIONS) }) {
                Text("Grant Health Connect permissions")
            }
        }

        OutlinedTextField(
            value = birthYearInput,
            onValueChange = { birthYearInput = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("Birth year (used to estimate max heart rate)") }
        )
        Button(
            onClick = { birthYearInput.toIntOrNull()?.let { viewModel.onBirthYearEntered(it) } },
            enabled = birthYearInput.length == 4
        ) {
            Text("Continue")
        }
    }
}
```

Update `RecoveryTrackerNavHost` (Task 14) to replace the onboarding placeholder:

```kotlin
composable("onboarding") {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<OnboardingViewModel> {
        OnboardingViewModel(
            com.esusic.recoverytracker.ServiceLocator.userPreferencesRepository,
            com.esusic.recoverytracker.ServiceLocator.healthDataSource
        )
    }
    com.esusic.recoverytracker.ui.onboarding.OnboardingScreen(viewModel) {
        navController.navigate("today") { popUpTo("onboarding") { inclusive = true } }
    }
}
```

This inline `viewModel { ... }` factory syntax requires `androidx.lifecycle:lifecycle-viewmodel-compose` (already added in Task 1) and Kotlin's `androidx.lifecycle.viewmodel.compose.viewModel` creation-extras overload — add `implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")` if not already resolved via the version catalog entry from Task 1.

- [ ] **Step 6: Manually verify on-device**

Run: `./gradlew :app:installDebug`. Launch the app, confirm the onboarding screen appears, tapping "Grant Health Connect permissions" opens the system permission dialog, granting them and entering a 4-digit birth year navigates to the Today placeholder screen.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/onboarding app/src/test/java/com/esusic/recoverytracker/ui/onboarding app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt
git commit -m "feat: add onboarding screen with Health Connect permissions and birth year capture"
```

---

### Task 16: Today Screen

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository` (Task 12), `DailyScoreEntity`/`DailyAggregateEntity` (Task 3), `toRecoveryScore`/`toStrainScore` (Task 12), `SyncScheduler` (Task 13), `HealthDataSource.hasAllPermissions` (Task 8).
- Produces: `TodayUiState(recoveryValue: Int?, isCalibrating: Boolean, strainValue: Double?, sleepDurationMinutes: Long?, restingHeartRate: Long?, avgHrvMillis: Double?, steps: Int?, lastSyncedAtEpochMillis: Long?, hasAllPermissions: Boolean, isRefreshing: Boolean)`, `TodayViewModel(repository: HealthRepository, healthDataSource: HealthDataSource)` with `val uiState: StateFlow<TodayUiState>`, `fun refresh()`.
- Consumed by: nav host `"today"` route.
- Error/edge-case handling (per spec): `hasAllPermissions = false` drives a "reconnect" banner instead of showing zeroed data; `lastSyncedAtEpochMillis` renders a "Last synced" timestamp; `refresh()` triggers pull-to-refresh; `isCalibrating` renders a "Calibrating" label instead of a bare number, satisfying the spec's baseline-bootstrapping requirement.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt
package com.esusic.recoverytracker.ui.today

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.HeartRateSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repositoryWith(source: FakeHealthDataSource): HealthRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        return HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
    }

    @Test
    fun `refresh syncs today and populates uiState from the persisted score`() = runTest {
        val source = FakeHealthDataSource(heartRate = listOf(HeartRateSample(Instant.now(), 65L)))
        val repository = repositoryWith(source)
        val viewModel = TodayViewModel(repository, source)

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.recoveryValue != null)
        assertEquals(true, viewModel.uiState.value.hasAllPermissions)
        assertEquals(false, viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `uiState reflects missing permissions without showing a zero score as real`() = runTest {
        val source = FakeHealthDataSource(permissionsGranted = false)
        val viewModel = TodayViewModel(repositoryWith(source), source)

        viewModel.refresh()

        assertEquals(false, viewModel.uiState.value.hasAllPermissions)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.today.TodayViewModelTest"`
Expected: FAIL — `TodayViewModel` is unresolved.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt
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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.today.TodayViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement the screen**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt
package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pullrefresh.PullRefreshIndicator
import androidx.compose.foundation.pullrefresh.pullRefresh
import androidx.compose.foundation.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenSettings: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(uiState.isRefreshing, { viewModel.refresh() })

    Column(
        modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState).padding(16.dp),
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Recovery", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        uiState.isCalibrating -> "Calibrating"
                        uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                        else -> "--"
                    },
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Strain", style = MaterialTheme.typography.titleMedium)
                Text(uiState.strainValue?.let { "%.1f".format(it) } ?: "--", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sleep", style = MaterialTheme.typography.titleMedium)
                Text(uiState.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No sleep data yet")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RHR: ${uiState.restingHeartRate ?: "--"}")
            Text("HRV: ${uiState.avgHrvMillis?.let { "%.0f".format(it) } ?: "--"}")
            Text("Steps: ${uiState.steps ?: "--"}")
        }

        uiState.lastSyncedAtEpochMillis?.let {
            val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(it))
            Text("Last synced $formatted", style = MaterialTheme.typography.bodySmall)
        }

        PullRefreshIndicator(uiState.isRefreshing, pullRefreshState, Modifier)
    }
}
```

Add to `libs.versions.toml`/`app/build.gradle.kts`: `androidx.compose.material:material` is required for `pullrefresh` (it's part of the Material — not Material3 — artifact as of Compose BOM 2026.06.00). Add `compose-material = { module = "androidx.compose.material:material" }` to `[libraries]` and `implementation(libs.compose.material)` to the app module.

Update `RecoveryTrackerNavHost`'s `"today"` composable:

```kotlin
composable("today") {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.today.TodayViewModel> {
        com.esusic.recoverytracker.ui.today.TodayViewModel(
            com.esusic.recoverytracker.ServiceLocator.repository,
            com.esusic.recoverytracker.ServiceLocator.healthDataSource
        )
    }
    com.esusic.recoverytracker.ui.today.TodayScreen(viewModel) { navController.navigate("settings") }
}
```

- [ ] **Step 6: Manually verify on-device**

Run: `./gradlew :app:installDebug`. Confirm the Today screen shows a Recovery card (either a number or "Calibrating" depending on data history), Strain, Sleep, and the quick-stats row; pull down to refresh and confirm the "Last synced" timestamp updates.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today app/src/test/java/com/esusic/recoverytracker/ui/today app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add Today screen with recovery/strain/sleep summary and pull-to-refresh"
```

---

### Task 17: Trends Screen

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/trends/LineChart.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/trends/TrendsViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository` (Task 12), `DailyScoreEntity`/`DailyAggregateEntity` (Task 3).
- Produces: `TrendRange` enum `{WEEK(7), MONTH(30), QUARTER(90)}` with a `days: Int`, `TrendsUiState(range: TrendRange, recoveryValues: List<Float>, strainValues: List<Float>, hrvValues: List<Float>, rhrValues: List<Float>)`, `TrendsViewModel(repository: HealthRepository)` with `val uiState: StateFlow<TrendsUiState>`, `fun selectRange(range: TrendRange)`. `LineChart(values: List<Float>, modifier: Modifier, lineColor: Color)` composable — a minimal Canvas-drawn sparkline (no external charting dependency).
- Consumed by: nav host `"trends"` route.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/ui/trends/TrendsViewModelTest.kt
package com.esusic.recoverytracker.ui.trends

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.local.DailyScoreEntity
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrendsViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): HealthRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        return HealthRepository(AggregationService(FakeHealthDataSource()), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
    }

    @Test
    fun `selectRange updates the range and re-derives values from persisted scores`() = runTest {
        val repository = newRepository()
        repository.syncDate(java.time.LocalDate.of(2026, 7, 13))

        val viewModel = TrendsViewModel(repository)
        viewModel.selectRange(TrendRange.WEEK)

        assertEquals(TrendRange.WEEK, viewModel.uiState.value.range)
        assertEquals(1, viewModel.uiState.value.recoveryValues.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.trends.TrendsViewModelTest"`
Expected: FAIL — `TrendsViewModel`/`TrendRange` are unresolved.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsViewModel.kt
package com.esusic.recoverytracker.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class TrendRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

data class TrendsUiState(
    val range: TrendRange = TrendRange.WEEK,
    val recoveryValues: List<Float> = emptyList(),
    val strainValues: List<Float> = emptyList(),
    val hrvValues: List<Float> = emptyList(),
    val rhrValues: List<Float> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(private val repository: HealthRepository) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendRange.WEEK)
    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedRange.flatMapLatest { range ->
                combine(
                    repository.observeRecentScores(range.days),
                    repository.observeRecentAggregates(range.days)
                ) { scores, aggregates ->
                    TrendsUiState(
                        range = range,
                        recoveryValues = scores.reversed().map { it.recoveryValue.toFloat() },
                        strainValues = scores.reversed().map { it.strainValue.toFloat() },
                        hrvValues = aggregates.reversed().mapNotNull { it.avgHrvMillis?.toFloat() },
                        rhrValues = aggregates.reversed().mapNotNull { it.restingHeartRate?.toFloat() }
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    fun selectRange(range: TrendRange) {
        selectedRange.value = range
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.trends.TrendsViewModelTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Implement the chart component and screen**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/trends/LineChart.kt
package com.esusic.recoverytracker.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth().height(120.dp),
    lineColor: Color = Color.Unspecified
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val color = if (lineColor == Color.Unspecified) Color(0xFF17C97E) else lineColor

        val points = values.mapIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / range) * size.height
            Offset(x, y)
        }

        for (i in 0 until points.size - 1) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = 4f)
        }
    }
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/trends/TrendsScreen.kt
package com.esusic.recoverytracker.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TrendsScreen(viewModel: TrendsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Trends", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.entries.forEach { range ->
                FilterChip(
                    selected = uiState.range == range,
                    onClick = { viewModel.selectRange(range) },
                    label = { Text(range.name.lowercase().replaceFirstChar(Char::uppercase)) }
                )
            }
        }

        Text("Recovery", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.recoveryValues)

        Text("Strain", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.strainValues, lineColor = MaterialTheme.colorScheme.secondary)

        Text("HRV", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.hrvValues)

        Text("Resting Heart Rate", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.rhrValues)
    }
}
```

Update `RecoveryTrackerNavHost`'s `"trends"` composable:

```kotlin
composable("trends") {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.trends.TrendsViewModel> {
        com.esusic.recoverytracker.ui.trends.TrendsViewModel(com.esusic.recoverytracker.ServiceLocator.repository)
    }
    com.esusic.recoverytracker.ui.trends.TrendsScreen(viewModel)
}
```

- [ ] **Step 6: Manually verify on-device**

Run: `./gradlew :app:installDebug`. Open the Trends tab, confirm the range chips switch between 7/30/90 days and the four charts redraw (they'll be flat/empty until several days of synced data accumulate — that's expected this early).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/trends app/src/test/java/com/esusic/recoverytracker/ui/trends app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt
git commit -m "feat: add Trends screen with range selector and sparkline charts"
```

---

### Task 18: Sleep Screen

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/sleep/SleepViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository` (Task 12), `DailyAggregateEntity` (Task 3).
- Produces: `SleepUiState(totalMinutes: Long?, deepMinutes: Long?, remMinutes: Long?, lightMinutes: Long?, deepPercent: Float, remPercent: Float, lightPercent: Float, typicalTotalMinutes: Double?)`, `SleepViewModel(repository: HealthRepository)` with `val uiState: StateFlow<SleepUiState>`.
- Consumed by: nav host `"sleep"` route.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/ui/sleep/SleepViewModelTest.kt
package com.esusic.recoverytracker.ui.sleep

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class SleepViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `uiState derives stage percentages from the most recent aggregate`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val sleep = SleepSession(
            start, start.plus(400, ChronoUnit.MINUTES),
            listOf(
                SleepStagePeriod(SleepStageType.DEEP, start, start.plus(80, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.REM, start.plus(80, ChronoUnit.MINUTES), start.plus(170, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.LIGHT, start.plus(170, ChronoUnit.MINUTES), start.plus(400, ChronoUnit.MINUTES))
            )
        )
        val source = FakeHealthDataSource(sleep = listOf(sleep))
        val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository)
        viewModel.uiState.filter { it.totalMinutes != null }.first()

        assertEquals(400L, viewModel.uiState.value.totalMinutes)
        assertEquals(80L, viewModel.uiState.value.deepMinutes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.sleep.SleepViewModelTest"`
Expected: FAIL — `SleepViewModel` is unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepViewModel.kt
package com.esusic.recoverytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SleepUiState(
    val totalMinutes: Long? = null,
    val deepMinutes: Long? = null,
    val remMinutes: Long? = null,
    val lightMinutes: Long? = null,
    val deepPercent: Float = 0f,
    val remPercent: Float = 0f,
    val lightPercent: Float = 0f,
    val typicalTotalMinutes: Double? = null
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
                    _uiState.value = SleepUiState(typicalTotalMinutes = typical)
                } else {
                    val total = latest.sleepDurationMinutes
                    val deep = latest.deepSleepMinutes ?: 0L
                    val rem = latest.remSleepMinutes ?: 0L
                    val light = latest.lightSleepMinutes ?: 0L
                    _uiState.value = SleepUiState(
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
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.sleep.SleepViewModelTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Implement the screen**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepScreen.kt
package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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

        Text("${uiState.totalMinutes!! / 60}h ${uiState.totalMinutes!! % 60}m total", style = MaterialTheme.typography.headlineSmall)

        uiState.typicalTotalMinutes?.let {
            val diff = uiState.totalMinutes!! - it
            val comparison = if (diff >= 0) "above" else "below"
            Text("${kotlin.math.abs(diff).toInt()} min $comparison your typical bedtime baseline")
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

Update `RecoveryTrackerNavHost`'s `"sleep"` composable:

```kotlin
composable("sleep") {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.sleep.SleepViewModel> {
        com.esusic.recoverytracker.ui.sleep.SleepViewModel(com.esusic.recoverytracker.ServiceLocator.repository)
    }
    com.esusic.recoverytracker.ui.sleep.SleepScreen(viewModel)
}
```

- [ ] **Step 6: Manually verify on-device**

Run: `./gradlew :app:installDebug`. Open the Sleep tab; if last night's Fitbit sleep data has synced, confirm stage bars render with plausible minute counts summing to the total.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/sleep app/src/test/java/com/esusic/recoverytracker/ui/sleep app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt
git commit -m "feat: add Sleep screen with stage breakdown and baseline comparison"
```

---

### Task 19: Metric Detail Screen (Heart Rate / Activity / HRV)

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricType.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricDetailViewModel.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricDetailScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/detail/MetricDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository` (Task 12), `DailyAggregateEntity` (Task 3), `BaselineCalculator` (Task 5), `LineChart` (Task 17).
- Produces: `MetricType` enum `{HEART_RATE, ACTIVITY, HRV}` with `label: String`, `MetricDetailUiState(todayValue: Double?, baselineMean: Double?, history: List<Float>)`, `MetricDetailViewModel(repository: HealthRepository, metricType: MetricType)` with `val uiState: StateFlow<MetricDetailUiState>`. One composable `MetricDetailScreen(viewModel, metricType)` reused for all three metrics rather than three near-duplicate screens (DRY, per the spec's detail-screens requirement).
- Consumed by: nav host `"detail/{metricType}"` route; Today screen's cards (Task 16) now navigate here on tap instead of doing nothing.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/esusic/recoverytracker/ui/detail/MetricDetailViewModelTest.kt
package com.esusic.recoverytracker.ui.detail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.RestingHrSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MetricDetailViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `HEART_RATE metric surfaces today's resting heart rate as todayValue`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        val date = LocalDate.of(2026, 7, 13)
        val dayStart = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(restingHr = listOf(RestingHrSample(dayStart.plusSeconds(60), 48L)))
        val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
        repository.syncDate(date)

        val viewModel = MetricDetailViewModel(repository, MetricType.HEART_RATE)
        viewModel.uiState.filter { it.todayValue != null }.first()

        assertEquals(48.0, viewModel.uiState.value.todayValue!!, 0.0001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.detail.MetricDetailViewModelTest"`
Expected: FAIL — `MetricDetailViewModel`/`MetricType` are unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricType.kt
package com.esusic.recoverytracker.ui.detail

enum class MetricType(val label: String) {
    HEART_RATE("Resting Heart Rate"),
    ACTIVITY("Steps"),
    HRV("HRV")
}
```

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricDetailViewModel.kt
package com.esusic.recoverytracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.scoring.BaselineCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MetricDetailUiState(
    val todayValue: Double? = null,
    val baselineMean: Double? = null,
    val history: List<Float> = emptyList()
)

class MetricDetailViewModel(
    private val repository: HealthRepository,
    private val metricType: MetricType
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetricDetailUiState())
    val uiState: StateFlow<MetricDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecentAggregates(BaselineCalculator.WINDOW_DAYS).collect { aggregates ->
                val chronological = aggregates.reversed()
                val values = chronological.mapNotNull { valueFor(it) }
                val today = chronological.lastOrNull()?.let { valueFor(it) }
                val baseline = BaselineCalculator.calculate(values.dropLast(1))

                _uiState.value = MetricDetailUiState(
                    todayValue = today,
                    baselineMean = baseline.mean.takeIf { baseline.sampleCount > 0 },
                    history = values.map { it.toFloat() }
                )
            }
        }
    }

    private fun valueFor(entity: DailyAggregateEntity): Double? = when (metricType) {
        MetricType.HEART_RATE -> entity.restingHeartRate?.toDouble()
        MetricType.ACTIVITY -> entity.steps.toDouble()
        MetricType.HRV -> entity.avgHrvMillis
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.detail.MetricDetailViewModelTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Implement the screen and wire navigation**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/detail/MetricDetailScreen.kt
package com.esusic.recoverytracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.trends.LineChart

@Composable
fun MetricDetailScreen(viewModel: MetricDetailViewModel, metricType: MetricType) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(metricType.label, style = MaterialTheme.typography.headlineMedium)
        Text(uiState.todayValue?.let { "%.0f".format(it) } ?: "--", style = MaterialTheme.typography.displayMedium)
        uiState.baselineMean?.let { Text("Your typical: %.0f".format(it)) }
        LineChart(values = uiState.history)
    }
}
```

Update `RecoveryTrackerNavHost`'s `"detail/{metricType}"` composable:

```kotlin
composable(
    "detail/{metricType}",
    arguments = listOf(androidx.navigation.navArgument("metricType") { type = androidx.navigation.NavType.StringType })
) { backStackEntry ->
    val metricType = com.esusic.recoverytracker.ui.detail.MetricType.valueOf(
        backStackEntry.arguments?.getString("metricType") ?: com.esusic.recoverytracker.ui.detail.MetricType.HEART_RATE.name
    )
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.detail.MetricDetailViewModel>(
        key = metricType.name
    ) {
        com.esusic.recoverytracker.ui.detail.MetricDetailViewModel(com.esusic.recoverytracker.ServiceLocator.repository, metricType)
    }
    com.esusic.recoverytracker.ui.detail.MetricDetailScreen(viewModel, metricType)
}
```

Update `TodayScreen` (Task 16) so the RHR/HRV/Steps row navigates to the detail screen. Add an `onMetricClick: (MetricType) -> Unit` parameter to `TodayScreen`, wrap the three `Text` composables in the quick-stats `Row` with `Modifier.clickable { onMetricClick(MetricType.HEART_RATE) }` (and `.ACTIVITY` / `.HRV` respectively), and update the nav host's `"today"` composable to pass `{ metricType -> navController.navigate("detail/${metricType.name}") }`.

- [ ] **Step 6: Manually verify on-device**

Run: `./gradlew :app:installDebug`. From Today, tap the RHR, HRV, and Steps quick stats and confirm each opens a detail screen showing today's value, baseline (once enough history exists), and a sparkline.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/detail app/src/test/java/com/esusic/recoverytracker/ui/detail app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt
git commit -m "feat: add generic metric detail screen for RHR, steps, and HRV"
```

---

### Task 20: Settings Screen

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt`

**Interfaces:**
- Consumes: `HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS` (Health Connect SDK, Task 10's dependency).
- Produces: `SettingsScreen(onBack: () -> Unit)` composable.
- Consumed by: nav host `"settings"` route (reached from Today's app bar per Task 16, not the bottom nav — matches the spec's screen list).
- No ViewModel/test: this screen has no state or branching logic of its own — it's a static list of actions (launch an `Intent`, show version text). Manually verified in Step 2.

- [ ] **Step 1: Implement**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/settings/SettingsScreen.kt
package com.esusic.recoverytracker.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }) {
            Text("Manage Health Connect permissions")
        }

        Text("Recovery Tracker v1.0", style = MaterialTheme.typography.bodySmall)

        Button(onClick = onBack) { Text("Back") }
    }
}
```

Update `RecoveryTrackerNavHost`'s `"settings"` composable:

```kotlin
composable("settings") {
    com.esusic.recoverytracker.ui.settings.SettingsScreen { navController.popBackStack() }
}
```

- [ ] **Step 2: Manually verify on-device**

Run: `./gradlew :app:installDebug`. From Today's app bar, tap Settings, confirm "Manage Health Connect permissions" opens the system Health Connect app, and Back returns to Today.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/settings app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt
git commit -m "feat: add Settings screen with Health Connect permission management"
```

---

### Task 21: Final Integration and Manual Verification

**Files:**
- No new files — this task is a verification pass across everything built in Tasks 1-20.

**Interfaces:**
- Consumes: the entire app.
- Produces: confidence that the app works end-to-end against real Fitbit data before considering v1 done.

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean :app:testDebugUnitTest :app:assembleDebug`
Expected: all unit tests from Tasks 2-19 pass, and the debug APK builds successfully.

- [ ] **Step 2: Fresh install on-device end-to-end pass**

Run: `./gradlew :app:installDebug`. Uninstall any prior debug build first if the schema changed (`adb uninstall com.esusic.recoverytracker`) to start from a clean local database. Walk through:

1. Onboarding: grant Health Connect permissions, enter birth year, land on Today.
2. Today: confirm Recovery shows "Calibrating" (expected on day one), Strain shows a number once you've worn the Fitbit for part of the day, Sleep/RHR/HRV/Steps show real values or "--" if not yet synced by Fitbit into Health Connect.
3. Pull-to-refresh on Today updates "Last synced".
4. Tap RHR/HRV/Steps — confirm each opens its detail screen without crashing, even with sparse history.
5. Trends and Sleep tabs render without crashing (charts may be empty/flat on day one — expected).
6. Settings → Manage Health Connect permissions opens the system screen; revoke one permission there, return to the app, pull-to-refresh on Today, and confirm the "reconnect" banner from Task 16 appears instead of a misleading zero score.
7. Re-grant the permission and confirm the banner disappears on next refresh.

- [ ] **Step 3: Confirm background sync is scheduled**

Run: `adb shell dumpsys jobscheduler | grep -A 5 com.esusic.recoverytracker`
Expected: a job entry exists corresponding to the periodic `recovery_sync_periodic` work request from `SyncScheduler.schedulePeriodic`.

- [ ] **Step 4: Multi-day soak (manual, over the following week)**

Use the app daily for at least 7 days (the `Baseline.MIN_SAMPLES_FOR_SCORING` threshold) so Recovery scores move out of the "Calibrating" state, and confirm the Trends charts show a real, non-flat week of data. This isn't a scripted step — it's the real-world validation that the scoring math (Tasks 5-7) produces sensible numbers against your actual physiology, which no unit test can verify.

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "chore: v1 complete — full app wired end-to-end"
```

---
