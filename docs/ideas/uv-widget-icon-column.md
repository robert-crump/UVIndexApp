# UV Widget: Icon Column Replaces Footer

## Problem Statement
How might we fold the 4x1 UV widget's "UV-Index | City" identity into its existing dark/neon severity-color language instead of bolting on a flat white footer bar that visually contradicts it?

## Recommended Direction
Remove the footer TextView and the divider entirely. Prepend a narrow 5th column to the hourly row, to the left of the current-hour cell, containing only the `ic_sun` glyph — no text. Both the icon column's background color and the icon's tint are derived from the **current hour's** (cell 0's) UV category, the same lookup `UVWidget.kt` already does for `widget_row_bg_<category>_left/right`. The result: the identity mark inherits severity color instead of introducing a second, unrelated palette, and it never competes with the numbers for attention since it carries no text.

City name is dropped from the widget entirely (confirmed low-value: rarely checked, and only one instance runs today). Per-hour time labels are untouched — confirmed essential, so cells 1-3 keep their current structure exactly as-is.

## Key Assumptions to Validate
- [ ] A bare sun glyph (no "UV" text) still reads as "this is UV data" at a glance, with no label to lean on — validate by looking at it live on the home screen for a day.
- [ ] `ic_sun.xml` (currently solid black) tints cleanly via `RemoteViews.setInt(id, "setColorFilter", color)` against the darker category backgrounds (e.g. `#194234`) without looking muddy — validate by building and checking each of the 5 category states (error, low, moderate, high, very_high).
- [ ] The icon column doesn't get clipped or look cramped at the narrowest 4x1 grid width some OEM launchers allow — validate on-device, ideally on a couple of launchers (stock + One UI or similar).

## MVP Scope
**In:**
- Remove `widget_uv_footer` TextView, its string resources, and the 1dp divider `FrameLayout` from `uv_widget.xml` and `uv_widget_preview.xml`.
- Add a new narrow `LinearLayout` + `ImageView` column before `widget_cell_0`, holding `ic_sun`.
- Modify the 5 `widget_row_bg_<category>_left.xml` drawables to round **both** top-left and bottom-left (currently top-only) — these become the icon column's backgrounds.
- Modify the 5 `widget_row_bg_<category>_right.xml` drawables to round **both** top-right and bottom-right (currently top-only) — cell 3 keeps these, now carrying the full corner since there's no footer below it anymore.
- Cell 0 switches from the `_left` shape drawable to the flat category background color (matching how cells 1/2 already work).
- `UVWidget.kt`: apply the category's `_left` drawable + accent-color `setColorFilter` to the new icon view instead of to cell 0; apply the flat background color to cell 0.

**Out:** see Not Doing below.

## Not Doing (and Why)
- **Restoring the divider** — its only job was separating the mismatched footer from the bar; with the footer gone, there's nothing to separate.
- **Keeping the city name anywhere in the 4x1** — confirmed low-value (rarely checked, single instance today); can revisit if multi-widget use becomes real.
- **Repeating "UV" or the icon on all 4 cells** — redundant once the whole bar is legible as one UV strip; would be visual noise fighting the numbers.
- **Rotated 90° "UV" text badge** — `RemoteViews`-hosted `TextView` rotation isn't reliably honored across all launcher hosts; the icon-only approach sidesteps that risk entirely.
- **Changing the per-hour time labels** — confirmed essential; not touched by this change.

## Open Questions
- Exact width/weight of the icon column relative to the 4 equal-weight hour cells — needs a quick visual pass in Android Studio's layout preview to avoid looking cramped or oversized.
- Solid glyph vs. an outline/stroke variant of the sun icon — decide after seeing the solid version tinted against the darkest category background (`#511D12` red / very-high).
