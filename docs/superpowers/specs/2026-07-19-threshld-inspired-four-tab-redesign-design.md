# Threshld-inspired four-tab redesign

Date: 2026-07-19  
Status: approved in conversation

## Goal

Restructure Recovery Tracker around the information hierarchy observed in the installed
Threshld app while retaining Recovery Tracker's existing Health Connect data, scores,
history, day browsing, and Vital Age feature. The result should feel calm, explanatory,
and decision-oriented: the first screen tells the user how recovered they are, what their
current effort is, how they slept, and what that means for today.

This is an original implementation inspired by the reference's hierarchy and visual
rhythm. It must not copy Threshld branding, proprietary text, artwork, account flows,
subscription UI, or application code.

## Product decisions

- Replace the current three root tabs with four: **Home**, **Sleep**, **Activity**, and
  **Account**.
- Keep all existing score formulas and Health Connect ingestion unchanged.
- Keep Vital Age as a prominent Home card linked to its existing detail page.
- Preserve metric details, historical day browsing, 7/30/90-day trends, pull-to-refresh,
  Health Connect permission management, and the existing calibration behavior.
- Do not invent subscription, coaching-plan, social, cloud-account, or goal-management
  features that have no backing model in Recovery Tracker.
- Use original Recovery Tracker copy and status language derived from its actual scores.

## Information architecture

### Root navigation

The bottom navigation contains four equally weighted destinations:

1. `home` — daily readiness summary and recommendation.
2. `sleep` — last night's sleep and weekly sleep context.
3. `activity` — today's load plus historical trends.
4. `account` — profile, Health Connect, and application settings.

The root tabs retain their back stacks when switching. Metric details, Recovery detail,
and Vital Age are pushed destinations. The bottom navigation is hidden on pushed detail
pages so those screens have a clear back action and are not visually confused with roots.
Onboarding remains outside the root shell.

### Home

Home opens with a large `Today` title and a smaller localized date. Existing previous- and
next-day browsing remains available in the header. The selected date controls every card
on the page.

The first content block is an asymmetric readiness cluster:

```text
+-----------------------+-----------------------+
|                       | Effort                |
| Recovery              | strain / 21           |
| score + status        +-----------------------+
|                       | Sleep                 |
| recommendation        | duration + score      |
+-----------------------+-----------------------+
```

- **Recovery** is the visual anchor. It shows the 0–100 score, band name, short body-state
  explanation, and an action-oriented recommendation. During calibration it explicitly
  says that more baseline data is needed.
- **Effort** shows the existing Strain score on its real 0–21 scale and a plain-language
  state such as Resting, Light, Productive, or High.
- **Sleep** shows duration, sleep-performance percentage, and a short quality label.
- Each card is clickable. Recovery opens a full Recovery breakdown; Effort opens Activity;
  Sleep opens the Sleep root.

Below the readiness cluster:

- **Core metrics**: side-by-side HRV and resting-heart-rate cards showing the current
  value, baseline delta, and interpretation. Each opens the existing metric detail.
- **Last night**: a wide sleep card with duration, stage summary, and a link to Sleep.
- **Today's recommendation**: a wide card generated deterministically from Recovery,
  Strain, Sleep, and calibration state. It is guidance copy, not medical advice.
- **Vital Age**: a wide linked card showing the current estimate, chronological comparison,
  or calibration status.
- **More vitals**: compact rows for respiratory rate, SpO2, skin-temperature delta, steps,
  stress, and zone minutes so no current data disappears from the product.
- Last-sync time and permission warnings remain visible but visually secondary.

### Sleep

Sleep becomes a root tab rather than a drill-in page. It follows the reference's reading
order while retaining Recovery Tracker's richer existing detail:

1. Header with `Sleep` and `Last night` context.
2. Hero summary with sleep-performance score, duration, bedtime/wake time, quality label,
   and one-sentence interpretation.
3. Sleep timeline/hypnogram with Awake, REM, Light, and Deep lanes.
4. Stage totals and percentages.
5. Hours-versus-need comparison and wake events.
6. Sleeping-heart-rate chart.
7. Weekly context using stored nights: average duration, daily bars/points, and one concise
   observation when enough history exists.

Missing stage data must not be represented as zero-quality sleep. The screen should show
the duration components that are known and explain which detail is unavailable.

### Activity

Activity combines the current Strain-focused data and the existing Trends tab:

1. Header with selected date.
2. Hero Effort card showing Strain on the 0–21 scale, status, and load interpretation.
3. Training-load recommendation derived from Recovery and current Strain.
4. Daily metric cards for steps, zone minutes, stress, and latest heart rate.
5. A compact hourly activity visualization when live Health Connect intervals exist.
6. A `This week` summary.
7. Historical trend section with the existing 7/30/90-day selector and charts for
   Recovery, Strain, HRV, and resting heart rate.

Historical cards use Room data. Intraday visualizations may be absent outside Health
Connect's readable window; the persisted daily number remains visible.

### Account

Account is a real settings/profile root, without fake cloud-account or subscription UI:

- Header with `Account` and a local profile summary.
- Profile card showing saved birth year and derived age, with an edit action that updates
  `UserPreferencesRepository`.
- Vital Age entry point for discoverability outside Home.
- Health Connect connection status and a button that opens system permission settings.
- A concise list of which categories Recovery Tracker reads.
- App name/version and a short privacy statement that health data is processed locally.

If Health Connect access is revoked, Account shows a prominent reconnect action while the
other roots retain their stored historical values and show a non-blocking stale-data notice.

## Visual system

The redesign uses a light, quiet, data-first identity similar in density and rhythm to the
reference, while remaining recognizably Recovery Tracker.

### Color tokens

- `Canvas` `#F5F5F2` — warm neutral application background.
- `Surface` `#FFFFFF` — cards and navigation.
- `Ink` `#171A1D` — primary text and dark data marks.
- `MutedInk` `#697077` — secondary labels and helper copy.
- `RecoveryGreen` `#39B66D` — positive Recovery states.
- `SleepBlue` `#6279D9` — sleep-specific visualization.
- `EffortOrange` `#E9823E` — Strain and activity emphasis.
- Warning and danger colors remain semantic and are used sparingly.

Colors must meet readable contrast for text. Status is never communicated by color alone;
every state also has a label.

### Typography and layout

- Use the platform's Android sans family to avoid adding a font dependency, but define a
  deliberate scale: 32sp page titles, 28–36sp hero values, 18–20sp card titles, 14–16sp
  body copy, and 11–12sp utility labels.
- Page titles use heavy weight and tight line height. Numeric values use tabular figures
  where Compose support allows it.
- Root pages use 20dp horizontal margins, 16dp section spacing, and 12dp card gaps.
- Cards use 20–24dp corner radii, a white surface, restrained 1dp borders or soft tonal
  separation, and no heavy drop shadows.
- The asymmetric Home cluster is the signature element. Other sections remain calm and
  regular so the hierarchy stays obvious.
- Bottom navigation is a stable white surface with four labeled icons and a restrained
  selected state. Labels remain visible for all tabs.
- Motion is limited to short score/progress transitions and pressed states; reduced-motion
  preferences must not prevent understanding.

### Copy rules

All status text is generated from stable score bands, not from an AI service. Copy is short,
specific, and consistent. Examples:

- Recovery 80–100: `Optimal` / `You are well recovered.`
- Recovery 60–79: `Ready` / `Good capacity for a normal day.`
- Recovery 40–59: `Balanced` / `Keep intensity controlled.`
- Recovery 0–39: `Recover` / `Prioritize rest and easy movement.`
- Calibrating: `Learning your baseline` / `A few more days will improve this score.`

The UI avoids diagnostic or medical claims.

## Architecture and component boundaries

- `RecoveryTrackerNavHost` owns root/detail routing and bottom-bar visibility.
- Home remains backed by `TodayViewModel`; presentation-only score-band and recommendation
  helpers are pure Kotlin and unit-tested.
- Sleep continues to use `SleepViewModel`, extended only for weekly presentation data that
  can be derived from existing repository flows.
- Activity receives a focused `ActivityViewModel` that combines the current daily state
  needed for activity with the existing trend-range behavior. It must not duplicate score
  formulas.
- Account receives an `AccountViewModel` for preferences and permission state.
- Reusable card, header, status, and metric-row components live under `ui/common` or a
  narrowly scoped feature package. Feature files should be split when a screen becomes too
  large to understand as one unit.
- Score formulas, Health Connect origin filtering, aggregation, Room entities, migrations,
  and periodic synchronization remain unchanged unless a separate tested data requirement
  emerges during implementation.

## State, errors, and empty data

- Every root has explicit loading, content, partial-data, and empty-data presentation.
- Stored data remains visible when a live Health Connect read fails; the timestamp and stale
  notice communicate freshness.
- A revoked permission never crashes navigation or clears Room data.
- Unknown metrics display `—`, not `0`, unless zero is a valid measured value.
- Day navigation cannot move beyond today or earlier than stored history.
- Pull-to-refresh stays on Home and may be exposed on Activity; failure leaves existing
  content intact and surfaces a retryable message.

## Testing and verification

- Add unit tests first for score-band labels, recommendation selection, Activity state
  composition, Account preference updates, and all new ViewModel state behavior.
- Preserve and run the complete existing `testDebugUnitTest` suite.
- Add Compose semantics/content descriptions for the four tabs, cards, charts, back actions,
  and permission actions.
- Build `assembleDebug`, install through ADB, and exercise onboarding bypass for the existing
  local profile.
- Device verification covers all four roots, tab-state restoration, day browsing, pushed
  details, back navigation, empty/partial states, and Health Connect permission handling.
- Compare final device screenshots against the inspected Threshld reference for hierarchy,
  spacing, density, and interaction flow, while confirming that branding and copy remain
  original.

## Non-goals

- No Threshld account, subscription, trial banner, proprietary assets, or copied wording.
- No new backend, login, cloud synchronization, or generative coaching.
- No change to the scientific validity or formula version of existing scores.
- No attempt to decompile or reuse code from the installed reference application.

## Acceptance criteria

- The application launches into Home after completed onboarding and exposes Home, Sleep,
  Activity, and Account as the only root tabs.
- Home visually prioritizes Recovery, Effort, and Sleep in the asymmetric cluster.
- Every existing user-facing metric remains reachable.
- Vital Age remains visible from Home and reachable from Account.
- Sleep and Activity provide both daily detail and historical context.
- Detail routes have predictable back navigation and do not show the root bottom bar.
- Permission loss and missing data have clear, non-destructive states.
- All unit tests and the debug build pass, and the four roots work on the connected device.
