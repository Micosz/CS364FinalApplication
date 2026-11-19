---
version: alpha
colors:
  primary: "#145A43"
  canvas_light: "#F3F8F5"
  canvas_dark: "#0E1714"
  text_light: "#102A22"
  text_dark: "#ECFDF5"
  accent_light: "#145A43"
  accent_dark: "#34D399"
typography:
  sans:
    fontFamily: "Roboto, Noto Sans Thai, sans-serif"
omitted:
  - section: spacing
    reason: "Owned by the existing Android XML layout and GameScreen dp values."
  - section: rounded
    reason: "Owned by the existing native drawable implementations."
  - section: components
    reason: "Native component ownership is recorded in the Components section."
---

# Wordward design context

## Overview

Wordward is a native Android English word guessing game with English and Thai interface text. Preserve the compact Contexto-style screen from the UI owner's PR #10: one input, a pinned latest word, and ranked history. The gold winning row is the existing visual signature. Integration work must preserve this layout rather than create another visual system.

## Colors

The runtime sources of truth are app/src/main/res/values/colors.xml, values-night/colors.xml and the existing GameScreen.Palette. The frontmatter records their current canvas/text/accent roles; it does not generate resources. GameAppearance supplies the saved uiMode before inflation, Theme.Wordward styles native windows/dialogs, and GameScreen applies its existing palette. Programmatic palette values and XML resources already coexist; reconcile drift with the UI owner before changing shared colors.

## Typography

Use Android's existing sans-serif font and Thai system fallback. Keep the established sizes in activity_game.xml and GameScreen. English guesses remain English when the interface locale changes.

## Layout

activity_game.xml owns spacing and scrolling. Preserve the narrow mobile layout and usable keyboard insets. Rotation, locale and theme recreation preserve accepted guesses and same-day unsent input. A new Bangkok day clears the previous day's draft.

## Elevation & Depth

Preserve the flat screen and the UI owner's gold-row treatment. Use native AlertDialog for confirmation; no new card, shadow or overlay system is introduced.

## Shapes

Reuse the existing row and input drawables. No new radius or spacing tokens are defined by integration work.

## Components

| Capability | Canonical owner | Contract | Verification |
|---|---|---|---|
| Daily date and puzzle | DailyWordProvider / GameSession | Asia/Bangkok; recheck before accepting actions | Clock-based unit tests |
| Round state and counters | GameRound | Accepted chronological entries are the source of truth | Domain and session tests |
| Persistence | GameStorage | Save validated rounds; settings are separate keys | Storage and device tests |
| Locale/theme | GameAppearance / GameStorage | Configure before inflation; UI saves theme before callback | Appearance and device recreation tests |
| Restart confirmation | GameScreen | Cancel preserves state; confirm sends callback; stale date cannot delete a new round | Session and native dialog tests |
| Exit confirmation | ExitConfirmation | Cancel stays; confirm finishes without clearing the round | Native Back tests |
| Recovery feedback | GameSession / GameActivity / GameScreen notice | Explain fresh round after invalid save; clear after accepted action | Session and native notice tests |
| Ranked history | GameScreen | Receive canonical repeat word and actual vocabulary size | Alias and UI tests |

There is no searchable data table, table selection, web select, browser form or remote CRUD API in this single-screen app. Browser-specific contracts are not applicable.

## Do's and Don'ts

- Keep every new user-facing message in the existing English/Thai resources.
- Use the prepared native confirmation labels from PR #10.
- Apply the theme selected by GameScreen once; do not toggle it again in its callback.
- Keep cancellation side-effect free. Restart only after confirmation and a fresh day check.
- Keep device evidence distinct from JVM tests and from untested physical-device claims.
