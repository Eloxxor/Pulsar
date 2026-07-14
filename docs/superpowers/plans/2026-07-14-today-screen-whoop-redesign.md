# Today Screen Whoop-Style Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Today screen with a dark-first theme and Whoop-style circular gauges for Recovery and Strain, replacing the current plain-text Material3 cards.

**Architecture:** A new hand-rolled `CircularGauge` Compose Canvas component (matching the existing `LineChart` pattern — no external charting/gauge dependency) is introduced and used twice on the Today screen (large for Recovery, small for Strain). The app's theme is switched from system-following light/dark to a fixed dark `ColorScheme`. No data-layer or `TodayViewModel`/`TodayUiState` changes — this is presentation-only.

**Tech Stack:** Jetpack Compose (existing project dependencies only — no new Gradle dependencies).

## Global Constraints

- No new dependencies — `CircularGauge` uses `androidx.compose.foundation.Canvas`, the same APIs `LineChart.kt` already uses.
- No custom font family — use the existing Material3 typography scale (`MaterialTheme.typography.*`) with size/weight already available on those styles.
- `TodayViewModel`, `TodayUiState`, `HealthRepository`, and all data flow are untouched — verify no file outside `ui/theme/` and `ui/today/` changes in this plan.
- Recovery ring color bands (Whoop convention): green (`MaterialTheme.colorScheme.primary`) at score ≥ 67, amber (`MaterialTheme.colorScheme.warning`) at 34–66, red (`MaterialTheme.colorScheme.error`) below 34.
- Strain ring always uses `MaterialTheme.colorScheme.secondary` (no banding).
- No new automated tests — this is a Compose UI presentation change with no new branching logic beyond what `TodayUiState` already drives (already covered by the existing `TodayViewModelTest`). Verify by building (`assembleDebug`) and manual on-device check, matching how the original Today screen (Task 16 of the main app plan) was verified.
- Every Bash command that runs `./gradlew` must export `JAVA_HOME` inline in the same command (shell state does not persist between tool calls): `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"`.
- Git identity isn't configured globally on this machine — commits must use `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "..."`.

## File Structure

```
app/src/main/java/com/esusic/recoverytracker/
  ui/theme/
    Theme.kt                    // Modify (Task 1): force dark ColorScheme, refine background/surface
  ui/today/
    CircularGauge.kt             // Create (Task 2): reusable Canvas-drawn ring gauge
    TodayScreen.kt                // Modify (Task 3): use CircularGauge, restyle cards, fix PullRefreshIndicator position
```

---

### Task 1: Dark-First Theme

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `RecoveryTrackerTheme` now always applies a dark `ColorScheme` regardless of system theme. `ColorScheme.warning` (already existed) continues to resolve to the amber accent. `MaterialTheme.colorScheme.background`/`.surface`/`.surfaceVariant` now resolve to the new near-black/elevated-card values, used by Task 3.
- Consumed by: Task 3 (`TodayScreen.kt` reads `MaterialTheme.colorScheme.primary/secondary/error/warning/background/surface`).

- [ ] **Step 1: Replace the theme file with the dark-first version**

Read the current file first (`app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt`) to confirm it matches what's below before replacing — if it doesn't, stop and report, since later steps assume this exact starting point.

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt` with:

```kotlin
package com.esusic.recoverytracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RecoveryGreen = Color(0xFF17C97E)
private val StrainBlue = Color(0xFF2E6BFF)
private val WarningAmber = Color(0xFFE8A33D)
private val DangerRed = Color(0xFFE8503D)
private val NearBlackBackground = Color(0xFF0A0A0A)
private val CardSurface = Color(0xFF161616)
private val CardSurfaceVariant = Color(0xFF1F1F1F)

private val DarkColors = darkColorScheme(
    primary = RecoveryGreen,
    secondary = StrainBlue,
    error = DangerRed,
    background = NearBlackBackground,
    surface = CardSurface,
    surfaceVariant = CardSurfaceVariant
)

val ColorScheme.warning get() = WarningAmber

@Composable
fun RecoveryTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
```

This removes `LightColors` and the `isSystemInDarkTheme()` branch entirely — the spec calls for dark-first regardless of system theme, not a toggle.

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (The app will look identical at this point — this task only changes theme colors, nothing consumes them differently yet until Task 3.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/theme/Theme.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: force dark-first theme with near-black background"
```

---

### Task 2: CircularGauge Component

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/today/CircularGauge.kt`

**Interfaces:**
- Consumes: nothing project-specific — pure Compose Canvas.
- Produces: `CircularGauge(value: Float, maxValue: Float, color: Color, modifier: Modifier = Modifier, isIndeterminate: Boolean = false, trackColor: Color = color.copy(alpha = 0.15f), strokeWidth: Dp = 12.dp, centerContent: @Composable () -> Unit = {})`.
- Consumed by: Task 3 (`TodayScreen.kt`, twice — once for Recovery, once for Strain).

- [ ] **Step 1: Create the component**

```kotlin
// app/src/main/java/com/esusic/recoverytracker/ui/today/CircularGauge.kt
package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CircularGauge(
    value: Float,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier,
    isIndeterminate: Boolean = false,
    trackColor: Color = color.copy(alpha = 0.15f),
    strokeWidth: Dp = 12.dp,
    centerContent: @Composable () -> Unit = {}
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            if (isIndeterminate) {
                drawArc(
                    color = color.copy(alpha = 0.5f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke), 0f)
                    )
                )
            } else {
                val fraction = (value / maxValue).coerceIn(0f, 1f)
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        centerContent()
    }
}
```

**Do NOT add a `// path/to/File.kt` comment as the literal first line of the file** — the path shown above the code block is a label, not file content. Start the file directly with `package com.esusic.recoverytracker.ui.today`.

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. (Nothing calls `CircularGauge` yet, so this only proves the file itself compiles standalone.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/CircularGauge.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: add CircularGauge Canvas component"
```

---

### Task 3: Today Screen Integration

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`

**Interfaces:**
- Consumes: `CircularGauge` (Task 2), `MaterialTheme.colorScheme.warning` / `.primary` / `.secondary` / `.error` (Task 1), `TodayUiState`/`TodayViewModel` (unchanged, pre-existing), `MetricType` (unchanged, pre-existing).
- Produces: no new public interface — this is the screen's own composable, already consumed by `RecoveryTrackerNavHost`'s existing `"today"` route (unchanged, no update needed there).

This task also fixes a real pre-existing bug found while reading the current file: `PullRefreshIndicator` was appended as the last item inside the `Column`, rendering inline at the bottom of the scrolling content instead of floating at the top during a pull gesture — which is why it wasn't visibly working. The fix wraps the screen in a `Box` so the indicator can be `Alignment.TopCenter`-positioned as an overlay, the correct usage pattern for this component.

- [ ] **Step 1: Replace the full file**

Read the current file first (`app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`) to confirm its current state before replacing.

Replace the full contents of `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt` with:

```kotlin
package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
            modifier = Modifier.fillMaxSize().padding(16.dp),
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

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularGauge(
                    value = (uiState.recoveryValue ?: 0).toFloat(),
                    maxValue = 100f,
                    color = recoveryColor(uiState.recoveryValue),
                    isIndeterminate = uiState.isCalibrating,
                    modifier = Modifier.size(200.dp).padding(vertical = 16.dp)
                ) {
                    Text(
                        text = when {
                            uiState.isCalibrating -> "Calibrating"
                            uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                            else -> "--"
                        },
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularGauge(
                    value = (uiState.strainValue ?: 0.0).toFloat(),
                    maxValue = 21f,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(110.dp).padding(vertical = 8.dp)
                ) {
                    Text(
                        text = uiState.strainValue?.let { "%.1f".format(it) } ?: "--",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

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

Note: `PullRefreshIndicator` is from `androidx.compose.material` (Material2), a separate theming system from the `androidx.compose.material3.MaterialTheme` this app uses everywhere else — it does not automatically inherit the M3 dark `ColorScheme` from Task 1. Its `backgroundColor`/`contentColor` are passed explicitly above so it actually renders in the dark theme's colors instead of Material2's light-theme defaults (which would otherwise look like a stray white circle on the new dark background).

- [ ] **Step 2: Verify it compiles**

Run:
```bash
export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"
cd "C:/Users/Elvedin/Desktop/Projects/FitBitAlternative"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, all existing unit tests still pass (this task doesn't touch `TodayViewModel`, so `TodayViewModelTest` must still pass unchanged — if it fails, something in this diff leaked into a file it shouldn't have).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: redesign Today screen with Recovery/Strain gauges and dark theme"
```

- [ ] **Step 4: Manual on-device verification checklist (executed by the user, not the implementer — no device in this environment)**

1. Install the APK, open the app to Today.
2. Confirm the background is near-black, not the previous light/system-default theme.
3. Confirm Recovery renders as a filled ring (or dashed/neutral ring if still calibrating) with the score centered inside, colored green/amber/red per the actual score band.
4. Confirm Strain renders as a smaller blue ring below Recovery.
5. Pull down from the top of the screen and confirm a refresh indicator now visibly appears at the top (previously it rendered inline at the bottom and was easy to miss).
6. Confirm Sleep card, RHR/HRV/Steps row (now with icons, still tappable to their detail screens), and "Last synced" timestamp are all still present and readable against the dark background.
7. Confirm the pull-to-refresh spinner itself is dark-themed (not a stray white/light circle).
