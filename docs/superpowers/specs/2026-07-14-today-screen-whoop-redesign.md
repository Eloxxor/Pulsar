# Today Screen Whoop-Style Redesign — Design Spec

Date: 2026-07-14

## Summary

The Today screen currently renders as plain Material3 text cards — functionally
complete (per the original design/plan) but visually flat, and doesn't deliver
on the original goal of a Whoop-inspired look. This spec covers a visual
redesign of the Today screen only; other screens (Trends, Sleep, Detail,
Settings) keep the current styling for now and get the same treatment in a
follow-up pass once this one is validated.

## Goals

- Dark-first theme, matching Whoop's visual identity.
- Recovery score rendered as an animated circular gauge (ring), color-coded by
  band, replacing the current plain number.
- Strain score rendered the same way, smaller and secondary to Recovery.
- Existing pull-to-refresh (already implemented, Task 16) gets restyled to fit
  the dark theme and be clearly visible/discoverable.
- No functional/data changes — this is presentation-layer only. `TodayViewModel`,
  `TodayUiState`, and all data flow are untouched.

## Non-goals (this pass)

- Trends, Sleep, Detail, and Settings screens — unchanged for now.
- No new dependencies (no charting/gauge library) — hand-rolled Compose Canvas,
  consistent with the existing `LineChart` component.
- No custom font family — use the existing Material3 typography scale with
  weight/size adjustments only.
- No changes to the onboarding flow's visuals.

## Technical Approach

A new `CircularGauge` composable, drawn with `Canvas` the same way `LineChart`
already is — no external gauge/charting library. Parameterized by current
value, value range, and a color (or color-band function), so the same
component serves both Recovery (0-100, banded green/yellow/red) and Strain
(0-21, single blue accent color) without duplicating drawing logic.

## Theme

Dark-first: a dark `ColorScheme` becomes the default regardless of system
theme (near-black background, elevated card surfaces slightly lighter than
the background). The existing accent color constants in `Theme.kt`
(`RecoveryGreen`, `WarningAmber`, `DangerRed`, `StrainBlue`) are reused as-is
— they already exist but are currently unused (`warning` extension is dead
code); this redesign is what finally puts them to use.

Band thresholds for Recovery ring color (Whoop convention):
- Green: score ≥ 67
- Yellow: score 34–66
- Red: score < 34

Strain ring always uses `StrainBlue` — strain doesn't have a "good/bad" band
in the same sense Recovery does.

## Today Screen Layout (top to bottom)

1. Header row (title + Settings button) — unchanged.
2. Reconnect banner (shown when `!hasAllPermissions`) — unchanged logic,
   restyled for dark theme.
3. **Recovery ring (large)** — circular gauge fills proportionally to the
   0-100 score, colored by band per above. Score number centered inside the
   ring. When `isCalibrating` is true, the ring renders as a neutral/dashed
   outline (not a filled arc, since there's no real percentage to show yet)
   with a "Calibrating" label in place of the number.
4. **Strain ring (small)** — same `CircularGauge` component, smaller size,
   `StrainBlue`, positioned directly below the Recovery ring.
5. Sleep card — restyled as a dark elevated card, same content/data as today.
6. Quick-stats row (RHR / HRV / Steps) — same tap-to-detail behavior
   (unchanged from Task 19), restyled with icons alongside the existing text,
   dark card styling.
7. Last-synced timestamp — unchanged position/logic, restyled text color for
   dark background.
8. Pull-to-refresh — existing `pullRefresh`/`PullRefreshIndicator` (Task 16)
   kept functionally as-is; the indicator's colors are restyled to fit the
   dark theme so it's visible when triggered (it's easy to miss against the
   current default light-mode styling).

## CircularGauge Component

`CircularGauge(value: Float, maxValue: Float, modifier: Modifier, color: Color, isIndeterminate: Boolean = false, centerContent: @Composable () -> Unit)`

- Draws a background track arc (dim, low-opacity) and a foreground progress
  arc (the given `color`) sized by `value / maxValue`, using `Canvas` +
  `drawArc`, matching the drawing style already established by `LineChart`.
- `isIndeterminate = true` (used for the Recovery ring while calibrating)
  draws a dashed/neutral outline instead of a filled progress arc.
- `centerContent` is a composable slot so the same gauge serves both the big
  Recovery ring (large score number) and the small Strain ring (smaller score
  number) without duplicating layout code.
- Lives in `ui/today/` alongside `TodayScreen.kt`, since it's introduced for
  this screen; if Trends/Sleep end up wanting a gauge in the follow-up pass,
  it moves to a shared `ui/components/` location then — not speculatively
  now (YAGNI).

## Testing

No new automated tests — this is a visual/presentation change to a Compose
screen with no branching logic of its own beyond what `TodayUiState` already
drives (which is already covered by `TodayViewModelTest`). Verified manually
on-device, the same way the original Today screen was verified in Task 16.
