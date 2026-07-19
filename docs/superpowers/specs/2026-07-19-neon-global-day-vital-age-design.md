# Dark Neon Theme, Global Day, and Vital Age Design

**Date:** 2026-07-19

**Status:** Approved for implementation planning

## Goal

Transform Recovery Tracker into a cohesive dark, neon-accented health dashboard inspired by Threshld's visual principles while keeping the implementation, assets, copy, and identity original. At the same time, make the selected day a single global state across all date-based tabs, remove redundant sleep and stress UI, and replace the confusing Vital Age breakdown with a reliable calculation whose displayed contributions exactly reconcile with the final age.

## Scope

This change covers:

- the global Compose color system and shared card, pill, header, and bottom-navigation styling;
- the Home, Sleep, Activity, Account, detail, recovery, onboarding, and Vital Age surfaces that consume the shared theme;
- a shared selected-day state used by Home, Sleep, and Activity;
- removal of the Home `Last night` card;
- removal of Stress from Home and Activity presentation;
- a revised Vital Age calculation and breakdown;
- unit, ViewModel, build, and connected-device verification.

This change does not copy Threshld code, assets, wording, branding, or proprietary scoring. Stress ingestion and persisted stress values remain intact for backward compatibility, but the value is no longer exposed by the redesigned UI.

## Reference Findings

Threshld builds its neon character through a small set of repeated visual rules rather than applying glow everywhere:

- a nearly black canvas with a subtle blue-black depth gradient;
- raised charcoal and navy cards with low-contrast borders;
- one saturated accent per health domain;
- translucent radial color blooms inside hero cards;
- bright progress rings and thin glowing indicators;
- a floating, rounded bottom-navigation capsule whose selected destination receives a restrained glow;
- white primary text with cool gray-lavender secondary text.

Recovery Tracker will use these principles with its existing four-tab structure and its own content hierarchy.

## Visual Design System

### Palette

- Canvas: near-black blue, approximately `#05060B`.
- Primary surface: charcoal navy, approximately `#11131B`.
- Elevated surface: deep navy, approximately `#141927`.
- Border/grid: cool blue-gray with low opacity, approximately `#262B3A`.
- Primary text: soft white, approximately `#F5F7FB`.
- Secondary text: cool gray-lavender, approximately `#939BB6`.
- Muted text: dark slate, approximately `#5C647D`.
- Recovery: neon mint-green.
- Activity/effort: electric cyan for the hero state and hot orange for load intensity.
- Sleep: electric violet.
- HRV: electric blue.
- Resting and live heart rate: hot pink.
- Vital Age: warm neon amber.
- Warning and danger retain distinct amber and red colors with dark-theme contrast.

The exact values may be tuned on the connected phone for legibility, but their semantic roles must remain stable.

### Shared surfaces and glow

`SectionCard` and `MetricTile` remain the common surface primitives. They gain optional accent and glow parameters so normal information cards remain neutral while hero cards, selected controls, and important metrics can opt into a color bloom and accent border.

Glow is implemented with Compose gradients, layered translucent strokes, and supported shadow properties. It must not depend on copied bitmap assets. The design uses glow at these focal points:

- Recovery, Activity, and Sleep hero cards;
- score/progress rings;
- selected bottom-navigation destination;
- highlighted chart lines and the Vital Age hero value;
- selected status pills where the extra emphasis improves hierarchy.

Long lists and explanatory cards remain mostly neutral to preserve contrast and battery-friendly rendering.

### Motion

Scores and circular progress indicators animate briefly from their previous or initial value. Navigation selection and card accent changes use short state transitions. No element pulses indefinitely. Empty, calibrating, and permission states remain readable without animation.

### Navigation

The four existing destinations remain Home, Sleep, Activity, and Account. The bottom bar becomes a floating rounded capsule above the system navigation inset. The active icon and label receive an accent color and localized glow; inactive destinations remain muted but meet touch-target and contrast requirements.

## Screen Changes

### Home

- Retain the asymmetric Recovery/Effort/Sleep readiness cluster.
- Restyle the readiness cards as the strongest neon focal area.
- Retain HRV and Resting HR as the two Core metrics.
- Remove the separate `Last night` card because Sleep already exists in both the readiness cluster and the Sleep tab.
- Retain recommendation, Vital Age, and More vitals sections.
- Remove the Stress row from More vitals.
- Keep Steps and Zone minutes in More vitals.

### Sleep

- Replace the fixed `Last night` subtitle with the globally selected date.
- Add the same previous/next day controls used by the other data tabs.
- Load summary, score, session detail, stages, heart rate, sleep need, and comparison for exactly the selected date.
- If the selected date has no sleep, clear detail from the previously viewed date and show a date-specific empty state.
- Anchor the seven-night chart and weekly insight at the selected date instead of the newest database row.
- Use violet glows for the hero score, timeline highlights, and stage visualization while keeping supporting cards neutral.

### Activity

- Observe the same global selected date.
- Remove the Stress metric tile and corresponding presentation state.
- Present Steps and Zone minutes as the primary pair and Heart rate as a full-width secondary metric.
- Anchor weekly and selected-range trends so they include data only through the selected date.
- Use cyan for the main effort hero and orange for load intensity and activity-timeline emphasis.

### Account and detail screens

Account is not date-based and therefore does not show the day selector. It receives the shared dark surfaces and colored icon treatments. Recovery detail, metric detail, Vital Age, onboarding, and information sheets inherit the same palette, surfaces, controls, and chart contrast.

## Global Selected-Day Architecture

A small `SelectedDayStore` is created once for the lifetime of `RecoveryTrackerNavHost`. It owns a `StateFlow<LocalDate>` and clamps all selections to the current date. The same instance is injected into `TodayViewModel`, `SleepViewModel`, and `ActivityViewModel`.

The store is the only mutable source of the selected day:

1. A header action calls `select`, `previous`, or `next` on the store.
2. All three data ViewModels observe the same flow.
3. Each ViewModel combines the selected date with its repository streams and publishes date-specific UI state.
4. Changing tabs does not create or reset the selected date.
5. Returning to today updates all three screens together.

Screen-specific state such as the Activity trend range remains inside its own ViewModel. Account and non-root detail routes do not mutate the global day. Opening Recovery detail from Home continues to pass the currently selected date explicitly.

### Date semantics

The selected date maps directly to the persisted `DailyAggregateEntity.date` and `DailyScoreEntity.date`. Sleep uses that same date rather than silently falling back to the latest available night. This makes the behavior deterministic: selecting July 18 on Home and opening Sleep or Activity displays July 18 data on those tabs.

For a selected date with no persisted row, the screen shows a truthful empty state. Previous navigation is enabled only when older stored data exists; next navigation is enabled whenever the selected date is before today.

## Vital Age Algorithm

### Problem in the current version

The current breakdown displays each component's raw age-equivalent offset while the final result uses the weighted offset. For example, Cardio fitness can display `+15 years`, contribute roughly five weighted years, and still produce a final result near `+5`. The arithmetic is internally valid but the UI label is misleading. In addition, a component receives its full base weight even when it has only sparse coverage within the 14-day window.

### Input window and reliability

Vital Age continues to use a rolling 14-day window and updates the displayed weekly series on Mondays. Each component must have at least three valid daily samples inside that window, except static profile inputs such as configured maximum heart rate. A component's confidence is:

`confidence = min(validSampleDays / 7, 1)`

Seven or more valid days therefore receive full confidence. Three to six days receive proportionally less influence. Components below three valid days are unavailable and excluded.

### Base weights

- Cardio fitness estimate: `0.35`
- HRV: `0.25`
- Resting heart rate: `0.10`
- Sleep duration: `0.15`
- Activity: `0.15`

Resting heart rate receives a lower direct weight because it is also an input to the cardio-fitness estimate. Activity receives more weight than before so consistent movement is not drowned out by the physiological signals.

### Calculation

Each available component first produces a bounded raw age-equivalent offset using its domain curve. Existing VO2max, HRV, resting-HR, sleep, and activity mappings remain the starting point, with bounds retained to prevent extreme sensor values from dominating.

For each available component:

`effectiveWeight = baseWeight * confidence`

`normalizedWeight = effectiveWeight / sum(all effectiveWeights)`

`impactYears = rawOffsetYears * normalizedWeight`

The calculator is an adult wellness estimate and returns no result when chronological age is below 18. For supported ages, the unclamped result delta is the sum of `impactYears`. The final delta is constrained to `[-10, +10]` years, and Vital Age cannot fall below 18. If either constraint changes the total delta, every component impact is scaled proportionally so the displayed impacts still sum exactly to the final delta.

The public component model exposes the final `impactYears`, normalized weight, confidence, and optionally the raw offset for diagnostics. The product UI displays `impactYears`, not the raw offset.

### Presentation invariant

For every result:

`chronologicalAge + sum(displayed component impacts) = vitalAge`

within rounding tolerance. The breakdown labels each value as its `impact on Vital Age`. This means a raw Cardio estimate capped at `+15` may correctly appear as approximately `+5.2 years` when that is its actual contribution. The final rounded line and component rows use compatible precision so the relationship remains understandable.

Vital Age remains a wellness estimate, not a diagnosis. Existing non-medical guidance remains visible.

## Error and Empty States

- Missing Health Connect permission keeps stored history visible and provides the existing reconnect guidance.
- A selected date without an aggregate shows a date-specific empty state without reusing stale state.
- Sparse Vital Age components show `not enough data` and are excluded from normalization.
- Vital Age remains in calibration until at least two eligible components are available.
- Glow never becomes the only status cue; labels, values, and icons continue to carry meaning.

## Testing and Verification

### Unit and ViewModel tests

- `SelectedDayStore` clamps future dates and keeps one value across consumers.
- Home, Sleep, and Activity all reload the exact selected date from the shared store.
- Sleep switches between two recorded dates and loads the matching session detail.
- Sleep clears all prior detail when the newly selected date has no sleep.
- Sleep weekly data and Activity trends end on the selected date.
- Stress is absent from Home and Activity UI state/presentation paths.
- Vital Age excludes under-covered components.
- Confidence modifies effective weights as specified.
- Displayed component impacts sum to the final age delta before and after bounds are applied.
- Poor fitness cannot create a final delta outside the global cap.
- Existing strong-vitals, weak-vitals, missing-data, and age-floor expectations continue to pass with updated numeric assertions.

### Build and device checks

- Run all debug unit tests.
- Build the debug APK.
- Install the APK on device `RFCY70GP8BH`.
- Verify all root and detail screens use the dark palette with readable contrast.
- Select yesterday on Home, open Sleep and Activity, and confirm both retain yesterday.
- Return to today from another data tab and confirm Home follows.
- Verify Sleep does not show stale detail for a date without sleep.
- Verify the Home `Last night` card and all presented Stress rows/tiles are gone.
- Inspect the Vital Age breakdown and confirm its visible impacts reconcile with its headline difference.

## Acceptance Criteria

The feature is complete when:

1. All app surfaces consistently render in the new dark theme.
2. Hero cards, charts, and navigation have a restrained but clearly visible neon glow hierarchy.
3. Home no longer contains `Last night` and no Home or Activity surface presents Stress.
4. Home, Sleep, and Activity share one selected date across tab changes.
5. Sleep and Activity content, weekly summaries, and trend windows are anchored to that date.
6. Vital Age uses coverage-aware weighting and every displayed component impact sums to the displayed age difference.
7. Unit tests, the debug build, and the connected-device checks pass.
