# Steps in Strain, Full Sheets, Vital Age Recalibration, Backfill — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Steps count toward strain, gauge sheets open fully expanded, vital age gains an estimated-VO2max component with harder calibration, and stale scores from the last 30 days are recomputed once.

**Architecture:** Pure-function changes in `scoring/`, a versioned backfill in `HealthRepository` gated by a DataStore int, and two small Compose edits. Spec: `docs/superpowers/specs/2026-07-18-strain-steps-sheets-vital-age-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, JUnit4 + Robolectric.

## Global Constraints

- `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"` inline before every Gradle call (Git Bash).
- Commits: `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit` with Co-Authored-By trailer.
- Work directly on `master`.
- Test command: `./gradlew testDebugUnitTest --tests "<Class>"`.

---

### Task 1: Steps feed strain

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/StrainScoreCalculator.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt:61`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/StrainScoreCalculatorTest.kt`

**Interfaces:**
- Produces: `StrainScoreCalculator.calculate(date, samples, maxHr, restingHr, steps: Int = 0): StrainScore`

- [ ] Step 1: add failing tests (steps-only ≈ 8–9; steps stack on HR load)
- [ ] Step 2: run, confirm RED (compile error: no `steps` parameter)
- [ ] Step 3: implement `STEP_LOAD_PER_STEP = 0.0016`, `totalLoad = hrLoad + steps × 0.0016`, drop `< 2 samples` early return, keep zero-total → 0.0; pass `aggregate.steps` in `HealthRepository.syncDate`
- [ ] Step 4: run class GREEN (all pre-existing tests must stay green via default param)
- [ ] Step 5: commit `feat: count daily steps as light load in strain score`

### Task 2: Vital age — estimated VO2max component + harder calibration

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/VitalAgeCalculator.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/VitalAgeSeries.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/vitalage/VitalAgeScreen.kt` (BreakdownCard row)
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/VitalAgeCalculatorTest.kt`

**Interfaces:**
- Produces: `VitalAgeInputs(..., maxHrBpm: Double? = null)`, `VitalAgeResult(..., fitness: VitalAgeComponent?)`, `VitalAgeSeries.build(days, birthYear, maxHrBpm: Double? = null)`
- Constants: weights FITNESS 0.35 / HRV 0.25 / RHR 0.15 / SLEEP 0.15 / ACTIVITY 0.10; fitness = `((57.5 − 15.3×maxHr/rhr)/0.4).coerceIn(18,80) − chronoAge`, clamp ±15; HRV clamp [−4,+10]; RHR `(rhr−60)×0.5` clamp ±10

- [ ] Step 1: new fitness tests + update renormalization/HRV-clamp expectations
- [ ] Step 2: run, confirm RED
- [ ] Step 3: implement calculator changes; thread `maxHrBpm` through series + view model (`userPreferences.maxHeartRate.first()`); add "Cardio fitness" row first in BreakdownCard
- [ ] Step 4: run VitalAge* test classes GREEN
- [ ] Step 5: commit `feat: estimated-VO2max component and harder vital age calibration`

### Task 3: One-time backfill of stored scores

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt`, `app/src/test/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepositoryTest.kt`

**Interfaces:**
- Produces: `HealthRepository.SCORE_FORMULA_VERSION = 2` (public), `UserPreferencesRepository.scoreFormulaVersion: Flow<Int>` (default 1), `setScoreFormulaVersion(Int)`

- [ ] Step 1: failing tests — stale version: corrupted past score recomputed by `syncToday`, version persisted; current version: corruption untouched; prefs default/round-trip
- [ ] Step 2: run, confirm RED
- [ ] Step 3: implement `backfillIfFormulaChanged(today)` — stored aggregate dates, filter `>= today−29 && < today−1`, ascending `syncDate`, then persist version; call at top of `syncToday`
- [ ] Step 4: run both classes GREEN
- [ ] Step 5: commit `feat: recompute stored scores once when the scoring formula changes`

### Task 4: Sheets open fully expanded

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/GaugeInfoSheets.kt:38`

- [ ] Step 1: add `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` (+ import)
- [ ] Step 2: compile via full test run of touched UI module (no unit test — Compose behavior)
- [ ] Step 3: commit `fix: open gauge info sheets fully expanded`

### Task 5: Verify

- [ ] Step 1: full suite `./gradlew testDebugUnitTest` GREEN
- [ ] Step 2: `./gradlew installDebug`, launch on RFCY70GP8BH
- [ ] Step 3: uiautomator checks — strain value raised for yesterday (steps now count), sheet fully expanded on open, Age tab shows Cardio fitness row and higher vital age, Jul 15 sleep score no longer 100; logcat FATAL count 0
- [ ] Step 4: report (terse German)
