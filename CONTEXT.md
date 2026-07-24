# UV Index App — Domain Glossary

The vocabulary the codebase should use for UV forecasts and notifications. Update this file when a concept changes meaning or a new one is named.

## Forecast Domain

- **UV Forecast** — Hourly UV index values for today (and ahead), produced by Open-Meteo. **The forecast can change during the day** as the upstream model updates. Code that decides "is the user informed?" must treat the forecast as mutable, not a one-shot morning truth. UV index, wind speed, air quality, and clear-sky values are rounded to whole numbers once, at parse time in `WeatherRepository.parseWeatherResponse` — all downstream classification (`classifyUvRisk`), notification logic, and display code operates on already-rounded values, not raw API floats.
- **High UV** — UV index ≥ 6. The threshold at which sunscreen is recommended. The single source of this threshold is `classifyUvRisk` in `com.uvindex.app.uv.UvRisk`.
- **Very High UV** — UV index ≥ 8. Used as emphasis inside notification bodies; not its own channel. Threshold owned by `classifyUvRisk` in `com.uvindex.app.uv.UvRisk`.
- **High UV Hour** — A forecast hour whose UV value is High UV.
- **High UV Window** — A contiguous run of High UV Hours within a single day. A day may contain more than one window if UV dips below the threshold and rises again.

## Notification Domain

Two user-facing channels.

- **Daily Forecast Notification** — Morning briefing fired once around 6:30 AM. Title carries the location name ("Tagesprognose Aachen", or plain "Tagesprognose" if the location name is unavailable). Body states today's UV maximum and its category (niedrig/mittel/hoch/sehr hoch), with category-specific advice appended: Moderate gets the shared Schutzempfehlung text (`com.uvindex.app.uv.UvProtectionRecommendations`), High-or-above gets the window to avoid direct sun (first-to-last+1 High UV Hour). Notification ID 1. Does **not** re-fire later in the day even if the forecast changes — the UV Warning channel handles updates. See #28.
- **UV Warning** — Responsive notification about today's High UV. Notification ID 2. Fires at most once per calendar day, **unless the forecast brings Worse News**, in which case it re-fires. Has two phases:
  - **Prelude phase** — Fires before the user is in High UV: the current hour is below the threshold, but a near-future hour is at or above it. Wording: "act now before it starts."
  - **In-window phase** — Fires once UV is already High. Wording: "you're in it, here's what's left of the window." Used when Prelude was missed (worker wasn't running, user just installed, etc.).

## Dedup Rules

- **Worse News** — Predicate on a forecast update that justifies re-firing the UV Warning even though one already fired today. True if **any** of:
  - Peak UV today is higher than the peak we previously warned about.
  - There is a High UV Hour after `now` that was not in what we previously warned about, **and** the total number of High UV Hours today grew versus what we previously warned about. (A same-length window that merely slides forward — an hour drops off the front as it becomes past while one appears on the back — is not new information and must not re-fire; see #27.)
  - The first High UV Hour today is earlier than the one we previously warned about.
  A pure function over `WarnedAbout? × WarnedAbout → Boolean`. The decider's most testable surface.
- **Warned About** — Snapshot of what a fired UV Warning covered. Used to compute Worse News on the next decision tick. Holds peak UV, first High UV Hour, and the set of High UV Hours.

## User Intent

- **Notification History** — Persisted state passed into `decide()`: permanent channel enable flags, today's user suppression, and the last-fired-record for each channel. Read at the start of each worker tick; written after each successful dispatch.
- **Disabled Today** — Per-day user suppression set by the "Warnungen heute deaktivieren" action on a UV Warning notification. Silences **both Prelude and In-window phases** for the rest of the calendar day (it's the whole UV Warning channel, not just one phase). Resets at midnight.
