# Cenit design system

> **Proposed starter — pending the user's acceptance (T3 of `odd/tasks/cenit-stage-1.md`).**
> Cenit has no design sources yet (no Compose theme, no CSS). Every value below is a proposal derived from the user's two sibling apps, which the user named as the style to follow (S2). Values copied or adapted cite their source `path`, relative to `/Users/ivanoliveragonzalez/Developer/Personal/` (read-only references): Scrolless `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` and Sereno `sereno/design/system.md` (accepted by the user on 2026-11-17). Values new to Cenit say **Cenit proposal**. Contrast ratios were computed with a WCAG 2.x relative-luminance script (see "Contrast").

Direction: the same DNA as Scrolless and Sereno — editorial minimalism, system sans with one focal serif role, 1 px hairlines, no shadows, a palette expressed as a `data class` of surface tiers with light and dark variants. Cenit sits between its siblings: Scrolless is the sun on the horizon (warm paper), Sereno the moon on the horizon (ink night); Cenit is the arc between them, so its **light theme is Scrolless paper and its dark theme is Sereno ink**, and it carries both lights as accents: **sun ochre** (Scrolless `warning` `#D6C07E`) and **moonlight** (Sereno `accent` `#AFBDE3`). Theme follows the system setting (photographers move between bright daytime scouting and night shoots).

## Palette data class (Kotlin mapping)

Each colour token maps 1:1 to a field of the proposed `CenitPalette` (sibling of Scrolless `PlayaPalette` and Sereno `SerenoPalette`). Shared field names are kept; Cenit adds `sun`, `moon`, `blueHour` and the four `sky*` tiers used by the elevation chart.

```kotlin
@Immutable
data class CenitPalette(
    val bgColor: Color,        // --bg-color
    val surfaceColor: Color,   // --surface-color
    val surfaceSubtle: Color,  // --surface-subtle
    val textPrimary: Color,    // --text-primary
    val textSecondary: Color,  // --text-secondary
    val borderColor: Color,    // --border-color (decorative hairline only)
    val controlBorder: Color,  // --control-border (≥3:1, outlines of interactive controls)
    val sun: Color,            // --sun (sun path, golden hour, selected state)
    val moon: Color,           // --moon (moon path, moon times)
    val blueHour: Color,       // --blue-hour (blue-hour ranges)
    val skyDay: Color,         // --sky-day      sun above the horizon
    val skyCivil: Color,       // --sky-civil    0° … −6°
    val skyNautical: Color,    // --sky-nautical −6° … −12°
    val skyAstro: Color,       // --sky-astro    −12° … −18° (below −18° is --bg-color)
    val success: Color,        // --success
    val warning: Color,        // --warning
    val danger: Color,         // --danger
    val focusRing: Color,      // --focus-ring
    val isDark: Boolean
)
val LightCenitPalette
val DarkCenitPalette
```

## Colors — dark

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--bg-color` | `#111318` | Screen background; sky below −18° (night) | Sereno dark `--bg-color`, `sereno/design/system.md` |
| `--surface-color` | `#171A21` | Panels, chart plate frame, sheets | Sereno dark `--surface-color`, `sereno/design/system.md` |
| `--surface-subtle` | `#1F232C` | Selected segment, pressed rows, date chip | Sereno dark `--surface-subtle`, `sereno/design/system.md` |
| `--text-primary` | `#E9E7E2` | Titles, times, values, "now" marker | Sereno dark `--text-primary`, `sereno/design/system.md` |
| `--text-secondary` | `#A6ABB5` | Labels, captions, azimuths | Sereno dark `--text-secondary`, `sereno/design/system.md` |
| `--border-color` | `#272B35` | Decorative hairline rules only | Sereno dark `--border-color`, `sereno/design/system.md` |
| `--control-border` | `#6C7382` | Outlines of buttons, fields, segmented controls | Sereno dark `--control-border`, `sereno/design/system.md` |
| `--sun` | `#D6C07E` | Sun path, sun marker, golden hour, selected state | Scrolless light `warning` `#D6C07E`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt`; Sereno dark `--warning` |
| `--moon` | `#AFBDE3` | Moon path (dashed), moon marker, moon times | Sereno dark `--accent`, `sereno/design/system.md` |
| `--blue-hour` | `#7F93D0` | Blue-hour ranges on the day ribbon and chart | Cenit proposal (lifted step of Sereno `--phase-deep` `#6A7EC2`, `sereno/design/system.md`) |
| `--sky-day` | `#272C38` | Chart band: sun above the horizon | Cenit proposal |
| `--sky-civil` | `#212532` | Chart band: civil twilight (0° … −6°) | Cenit proposal |
| `--sky-nautical` | `#1B1F29` | Chart band: nautical twilight (−6° … −12°) | Cenit proposal |
| `--sky-astro` | `#161920` | Chart band: astronomical twilight (−12° … −18°) | Cenit proposal |
| `--success` | `#8DB38F` | "Lugar guardado" confirmation | Sereno dark `--success`, `sereno/design/system.md` |
| `--warning` | `#D6C07E` | Compass calibration notice (always with a 3 px rule and text) | Sereno dark `--warning`, `sereno/design/system.md` |
| `--danger` | `#E39A93` | Delete a saved place | Sereno dark `--danger`, `sereno/design/system.md` |
| `--focus-ring` | `#AFBDE3` | `:focus-visible` outline, 2 px, offset 2 px (distinct from the ochre selection) | Sereno dark `--focus-ring`, `sereno/design/system.md` |

## Colors — light

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--bg-color` | `#FAF9F6` | Screen background; chart band: sun above the horizon | Scrolless light `bgColor`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--surface-color` | `#F3F1EC` | Panels, sheets | Scrolless light `surfaceColor`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--surface-subtle` | `#ECE9E2` | Selected segment, pressed rows, date chip | Scrolless light `surfaceSubtle`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--text-primary` | `#2C2C2C` | Titles, times, values, "now" marker | Scrolless light `textPrimary`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--text-secondary` | `#5C5A54` | Labels, captions, azimuths | Sereno light `--text-secondary`, `sereno/design/system.md` |
| `--border-color` | `#E6E3DC` | Decorative hairline rules only | Scrolless light `borderColor`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--control-border` | `#857F74` | Outlines of interactive controls | Sereno light `--control-border`, `sereno/design/system.md` |
| `--sun` | `#7A6220` | Sun path, sun marker, golden hour, selected state | Sereno light `--warning` (darkened Scrolless ochre), `sereno/design/system.md` |
| `--moon` | `#3D5896` | Moon path (dashed), moon marker, moon times | Sereno light `--accent`, `sereno/design/system.md` |
| `--blue-hour` | `#4F67A6` | Blue-hour ranges | Cenit proposal (between Sereno light `--phase-light` `#6F86BE` and `--accent` `#3D5896`) |
| `--sky-day` | `#FAF9F6` | Chart band: sun above the horizon | Scrolless light `bgColor`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--sky-civil` | `#EEEBE4` | Chart band: civil twilight | Cenit proposal |
| `--sky-nautical` | `#E5E1D8` | Chart band: nautical twilight | Cenit proposal |
| `--sky-astro` | `#DCD7CB` | Chart band: astronomical twilight (night below −18° uses `#DCD7CB` too, with the "Noche" label) | Cenit proposal |
| `--success` | `#4A784E` | "Lugar guardado" confirmation | Sereno light `--success`, `sereno/design/system.md` |
| `--warning` | `#7A6220` | Compass calibration notice | Sereno light `--warning`, `sereno/design/system.md` |
| `--danger` | `#A2443C` | Delete a saved place | Sereno light `--danger`, `sereno/design/system.md` |
| `--focus-ring` | `#3D5896` | `:focus-visible` outline | Sereno light `--focus-ring`, `sereno/design/system.md` |

## Sky phases (definitions shown in the app)

Bands and ranges are elevation ranges of the sun's centre, the definitions photographers use; the app always names them in text next to the colour (never colour alone).

| Token | Value | Rule | Source |
| --- | --- | --- | --- |
| `phase-golden` | `−4° … +6°` | Golden hour, morning and evening; colour `--sun` | Cenit proposal (common photography convention) |
| `phase-blue` | `−6° … −4°` | Blue hour; colour `--blue-hour` | Cenit proposal (common photography convention) |
| `phase-civil` | `0° … −6°` | Civil twilight; band `--sky-civil` | Standard definition |
| `phase-nautical` | `−6° … −12°` | Nautical twilight; band `--sky-nautical` | Standard definition |
| `phase-astro` | `−12° … −18°` | Astronomical twilight; band `--sky-astro` | Standard definition |
| `event-rise-set` | `−0.833°` | Sunrise / sunset / moonrise / moonset (refraction + semidiameter; moon topocentric) | Standard definition |

## Typography

Families: system only. Compose `FontFamily.SansSerif` for everything, as Scrolless (`scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt`); one serif role for the focal numbers, as Sereno (`sereno/design/system.md`). Times, angles and coordinates use tabular figures (`font-variant-numeric: tabular-nums`).

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--font-sans` | `system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", sans-serif` | All UI text | Sereno `--font-sans`, `sereno/design/system.md` |
| `--font-serif` | `"Iowan Old Style", "Noto Serif", Georgia, serif` | Focal time of a screen, place name headline | Sereno `--font-serif`, `sereno/design/system.md` |
| `--type-focal` | `52px / 56px, 300, -0.03em` | The one focal time per screen (serif) | Cenit proposal (between Sereno `--type-display` 36 px and `--type-session-clock` 72 px) |
| `--type-display` | `36px / 40px, 300, -1.2px` | Compass heading, secondary focal number (serif) | Scrolless `displayLarge`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--type-headline` | `26px / 32px, 300, -1px` | Screen title | Scrolless `headlineLarge`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--type-title` | `20px / 26px, 400, -0.4px` | Event times in rows, section values | Scrolless `titleMedium`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--type-body-large` | `17px / 24px, 400, -0.3px` | Button labels, place names in rows | Scrolless `bodyLarge`, `scrolless/app/src/main/java/com/scrolless/app/ui/PlayaTheme.kt` |
| `--type-body` | `15px / 22px, 400` | Body, row labels | Sereno `--type-body`, `sereno/design/system.md` |
| `--type-small` | `13px / 18px, 400` | Captions, azimuth after a time, chart labels | Sereno `--type-small`, `sereno/design/system.md` |
| `--type-label` | `12px / 16px, 500, +1px, uppercase` | Eyebrows (SOL, LUNA, CREPÚSCULOS) | Sereno `--type-label`, `sereno/design/system.md` |

## Spacing

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--space-screen-x` | `22px` | Screen side padding | Sereno `--space-screen-x`, `sereno/design/system.md` |
| `--space-screen-top` | `44px` | Screen top padding (below the status bar) | Sereno `--space-screen-top`, `sereno/design/system.md` |
| `--space-screen-bottom` | `36px` | Screen bottom padding | Sereno `--space-screen-bottom`, `sereno/design/system.md` |
| `--space-section` | `28px` | Between editorial sections (Sereno uses 32; Cenit is denser so the main screen does not scroll) | Cenit proposal |
| `--space-group` | `16px` | Between items of one group | Sereno `--space-group`, `sereno/design/system.md` |
| `--space-inner` | `12px` | Inside a group | Sereno `--space-inner`, `sereno/design/system.md` |
| `--space-tight` | `6px` | Label to value | Sereno `--space-tight`, `sereno/design/system.md` |
| `--space-panel` | `20px` | Panel padding | Sereno `--space-panel`, `sereno/design/system.md` |
| `--max-content` | `460px` | Max content width on large phones and tablets | Sereno `--max-content`, `sereno/design/system.md` |

## Radius

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--radius-pill` | `999px` | Date chip, phase pills, now marker label | Sereno `--radius-pill`, `sereno/design/system.md` |
| `--radius-panel` | `20px` | Panels, chart plate, sheets | Sereno `--radius-panel`, `sereno/design/system.md` |
| `--radius-button` | `16px` | Primary button | Sereno `--radius-button`, `sereno/design/system.md` |
| `--radius-button-secondary` | `14px` | Secondary buttons, search field | Sereno `--radius-button-secondary`, `sereno/design/system.md` |
| `--radius-cell` | `12px` | Day cells of the date strip | Sereno `--radius-cell`, `sereno/design/system.md` |
| `--radius-control` | `10px` | Segmented control, steppers | Sereno `--radius-control`, `sereno/design/system.md` |

## Borders and elevation

| Token | Value | Usage | Source |
| --- | --- | --- | --- |
| `--border-hairline` | `1px solid var(--border-color)` | Editorial rules between sections and rows | Sereno `--border-hairline`, `sereno/design/system.md` |
| `--border-control` | `1px solid var(--control-border)` | Interactive outlines | Sereno `--border-control`, `sereno/design/system.md` |
| `--border-today` | `1.5px solid var(--sun)` | Today in the date strip | Sereno `--border-today` (accent swapped for `--sun`), `sereno/design/system.md` |
| `--scrim` | `rgba(0,0,0,.6)` | Backdrop behind sheets and confirmation dialogs (decorative) | Sereno `--scrim`, `sereno/design/system.md` |
| `--shadow-none` | `none` | No shadows; elevation is a surface tier | Sereno `--shadow-none`, `sereno/design/system.md` |

## Components

| Token | Value | Rule | Source |
| --- | --- | --- | --- |
| `button-primary` | `min-height 54px; bg --text-primary; label --bg-color; radius 16px` | At most one per screen ("Guardar lugar", "Usar esta ubicación") | Sereno `button-primary`, `sereno/design/system.md` |
| `button-secondary` | `min-height 46px; transparent; 1px --control-border; radius 14px` | Alternatives ("Coordenadas manuales") | Sereno `button-secondary`, `sereno/design/system.md` |
| `button-danger-text` | `min-height 44px; label --danger; no fill` | "Borrar lugar"; always opens a confirmation | Sereno `button-danger-text`, `sereno/design/system.md` |
| `segmented` | `44px high; radius 10px; 1px --control-border; selected segment --surface-subtle + label --text-primary + 2px --sun underline` | Sol / Luna, Hoy / Fecha | Sereno segmented control (radius 10), `sereno/design/system.md`; underline Cenit proposal |
| `date-strip` | `7 cells 44×56, radius 12px, gap 6px; weekday --type-small, day --type-title; today outlined 1.5px --sun; selected filled --surface-subtle` | Pick a date within the week; "Otra fecha" opens the platform date picker | Sereno `seven-night-strip`, `sereno/design/system.md` |
| `time-scrubber` | `44px touch row; 2px track --control-border; thumb 20px --text-primary ring 2px --bg-color; ticks every 3 h` | Drag or arrow keys (±5 min, PageUp/PageDown ±1 h); "Ahora" resets | Cenit proposal |
| `day-ribbon` | `24 h bar, 14px high, radius 999; segments by sky phase using --sky-* / --blue-hour / --sun; phase named in text below` | Overview of the day's light; never colour alone | Cenit proposal |
| `elevation-chart` | `plate radius 20px; y = elevation −20° … +max; bands --sky-*; sun solid 2px --sun; moon dashed 2px --moon; horizon 1px --control-border; now = 1px --text-primary line + label` | Sun path diagram; values on tap/scrub | Cenit proposal |
| `sky-dome` | `flat compass: zenith at the centre, elevation radial, horizon circle 1px --control-border filled --sky-day; twilight rings outside it (--sky-civil to −6°, --sky-nautical to −12°); rings at 30° / 60°; day path solid 2.5px --sun with hour dots (labels every 3 h); night path dotted 1.5px --text-secondary outside the horizon; June/December solstice paths 1px --text-secondary (labelled jun/dic) and equinox dashed; moon path dashed --moon` | Sun/moon direction over the day (Sun Seeker's flat compass view); N up, or rotates with the live heading in Brújula | Cenit proposal |
| `compass-rose` | `ticks every 5°, cardinal letters --text-primary, rise/set azimuth rays --sun / --moon with time labels` | Live heading from the magnetometer; true north (declination corrected) | Cenit proposal |
| `event-row` | `min-height 48px; time --type-title tabular; label --type-body; azimuth --type-small --text-secondary; hairline below` | Times panel rows | Cenit proposal (row rhythm of Sereno settings rows) |
| `alert-toggle` | `44×44 icon button; 1px --control-border; pressed: fill --sun, icon --bg-color` | "Avisarme 30 minutos antes" of the next event (local notification, offline) | Cenit proposal |
| `shadow-ratio` | `--type-small --text-secondary: "Sombra 4,4 × la altura" (1 / tan elevation)` | Shown with the sun position; "Sin sombra" when the sun is down | Cenit proposal |
| `phase-glyph` | `24–44px circle; lit part --moon, dark part --surface-subtle, 1px --control-border outline` | Moon phase; always with name and % illumination in text | Cenit proposal |
| `place-row` | `min-height 56px; name --type-body-large; region + coordinates --type-small; trailing check for the active place` | Saved places and search results | Cenit proposal |
| `search-field` | `48px; radius 14px; 1px --control-border; leading magnifier; works offline` | City search over the bundled GeoNames list | Cenit proposal |
| `notice` | `--surface-color; 3px left rule --warning; text --text-primary` | Compass needs calibration | Sereno `notice`, `sereno/design/system.md` |
| `tab-bar` | `64px; hairline top; 4 items, icon 22px + label --type-small; selected --text-primary + 2px --sun bar above icon; others --text-secondary` | Hoy · Cielo · Brújula · Lugares | Cenit proposal |
| `logo-mark` | `arc from horizon to horizon + sun dot at the apex (zenith) + 3 reflection lines` | Sibling of the Scrolless sun-on-horizon and Sereno moon-on-horizon marks | Cenit proposal (Scrolless `scrolless/app/src/main/java/com/scrolless/app/ui/ScrollessLogo.kt`, Sereno `logo-mark`) |
| `touch-target` | `44px` | Minimum for every control | Sereno `touch-target`, `sereno/design/system.md` |
| `focus` | `2px solid --focus-ring, offset 2px` | Visible on every control, distinct from selection | Sereno `focus`, `sereno/design/system.md` |

## Contrast

Computed with WCAG 2.x relative luminance for every text/background and boundary/graphic pair the artboards use. Normal text needs ≥ 4.5:1; control boundaries and required graphics (sun and moon paths, markers, blue-hour ranges) need ≥ 3:1. `--border-color` and the `--sky-*` band fills are decorative (every band is labelled in text), so their pairs only appear where text or a path sits on them.

| Theme | Foreground | Background | Kind (min) | Ratio | Result |
| --- | --- | --- | --- | --- | --- |
| dark | `textPrimary` `#E9E7E2` | `bgColor` `#111318` | text (4.5) | 15.04:1 | pass |
| dark | `textPrimary` `#E9E7E2` | `surfaceColor` `#171A21` | text (4.5) | 14.09:1 | pass |
| dark | `textPrimary` `#E9E7E2` | `surfaceSubtle` `#1F232C` | text (4.5) | 12.73:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `bgColor` `#111318` | text (4.5) | 8.06:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `surfaceColor` `#171A21` | text (4.5) | 7.56:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `surfaceSubtle` `#1F232C` | text (4.5) | 6.83:1 | pass |
| dark | `bgColor` `#111318` | `textPrimary` `#E9E7E2` | text (4.5) | 15.04:1 | pass |
| dark | `sun` `#D6C07E` | `bgColor` `#111318` | text (4.5) | 10.35:1 | pass |
| dark | `sun` `#D6C07E` | `surfaceColor` `#171A21` | text (4.5) | 9.70:1 | pass |
| dark | `moon` `#AFBDE3` | `bgColor` `#111318` | text (4.5) | 9.92:1 | pass |
| dark | `moon` `#AFBDE3` | `surfaceColor` `#171A21` | text (4.5) | 9.29:1 | pass |
| dark | `danger` `#E39A93` | `bgColor` `#111318` | text (4.5) | 8.24:1 | pass |
| dark | `danger` `#E39A93` | `surfaceColor` `#171A21` | text (4.5) | 7.72:1 | pass |
| dark | `success` `#8DB38F` | `bgColor` `#111318` | text (4.5) | 7.94:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `skyDay` `#272C38` | text (4.5) | 6.06:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `skyCivil` `#212532` | text (4.5) | 6.63:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `skyNautical` `#1B1F29` | text (4.5) | 7.15:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `skyAstro` `#161920` | text (4.5) | 7.63:1 | pass |
| dark | `controlBorder` `#6C7382` | `bgColor` `#111318` | boundary (3.0) | 3.90:1 | pass |
| dark | `controlBorder` `#6C7382` | `surfaceColor` `#171A21` | boundary (3.0) | 3.66:1 | pass |
| dark | `controlBorder` `#6C7382` | `surfaceSubtle` `#1F232C` | boundary (3.0) | 3.30:1 | pass |
| dark | `focusRing` `#AFBDE3` | `bgColor` `#111318` | boundary (3.0) | 9.92:1 | pass |
| dark | `focusRing` `#AFBDE3` | `surfaceColor` `#171A21` | boundary (3.0) | 9.29:1 | pass |
| dark | `sun` `#D6C07E` | `surfaceSubtle` `#1F232C` | boundary (3.0) | 8.76:1 | pass |
| dark | `sun` `#D6C07E` | `skyDay` `#272C38` | graphic (3.0) | 7.78:1 | pass |
| dark | `sun` `#D6C07E` | `skyCivil` `#212532` | graphic (3.0) | 8.51:1 | pass |
| dark | `sun` `#D6C07E` | `skyNautical` `#1B1F29` | graphic (3.0) | 9.18:1 | pass |
| dark | `sun` `#D6C07E` | `skyAstro` `#161920` | graphic (3.0) | 9.80:1 | pass |
| dark | `moon` `#AFBDE3` | `skyDay` `#272C38` | graphic (3.0) | 7.46:1 | pass |
| dark | `moon` `#AFBDE3` | `skyAstro` `#161920` | graphic (3.0) | 9.39:1 | pass |
| dark | `moon` `#AFBDE3` | `bgColor` `#111318` | graphic (3.0) | 9.92:1 | pass |
| dark | `blueHour` `#7F93D0` | `surfaceColor` `#171A21` | graphic (3.0) | 5.79:1 | pass |
| dark | `blueHour` `#7F93D0` | `bgColor` `#111318` | graphic (3.0) | 6.18:1 | pass |
| dark | `textPrimary` `#E9E7E2` | `skyDay` `#272C38` | graphic (3.0) | 11.30:1 | pass |
| dark | `textPrimary` `#E9E7E2` | `skyAstro` `#161920` | graphic (3.0) | 14.23:1 | pass |
| dark | `sun` `#D6C07E` | `bgColor` `#111318` | graphic (3.0) | 10.35:1 | pass |
| dark | `moon` `#AFBDE3` | `surfaceColor` `#171A21` | graphic (3.0) | 9.29:1 | pass |
| dark | `textPrimary` `#E9E7E2` | `skyDay` `#272C38` | text (4.5) | 11.30:1 | pass |
| dark | `textSecondary` `#A6ABB5` | `skyCivil` `#212532` | graphic (3.0) | 6.63:1 | pass |
| dark | `bgColor` `#111318` | `sun` `#D6C07E` | text (4.5) | 10.35:1 | pass |
| light | `textPrimary` `#2C2C2C` | `bgColor` `#FAF9F6` | text (4.5) | 13.26:1 | pass |
| light | `textPrimary` `#2C2C2C` | `surfaceColor` `#F3F1EC` | text (4.5) | 12.37:1 | pass |
| light | `textPrimary` `#2C2C2C` | `surfaceSubtle` `#ECE9E2` | text (4.5) | 11.52:1 | pass |
| light | `textSecondary` `#5C5A54` | `bgColor` `#FAF9F6` | text (4.5) | 6.55:1 | pass |
| light | `textSecondary` `#5C5A54` | `surfaceColor` `#F3F1EC` | text (4.5) | 6.11:1 | pass |
| light | `textSecondary` `#5C5A54` | `surfaceSubtle` `#ECE9E2` | text (4.5) | 5.69:1 | pass |
| light | `bgColor` `#FAF9F6` | `textPrimary` `#2C2C2C` | text (4.5) | 13.26:1 | pass |
| light | `sun` `#7A6220` | `bgColor` `#FAF9F6` | text (4.5) | 5.55:1 | pass |
| light | `sun` `#7A6220` | `surfaceColor` `#F3F1EC` | text (4.5) | 5.17:1 | pass |
| light | `moon` `#3D5896` | `bgColor` `#FAF9F6` | text (4.5) | 6.57:1 | pass |
| light | `moon` `#3D5896` | `surfaceColor` `#F3F1EC` | text (4.5) | 6.13:1 | pass |
| light | `danger` `#A2443C` | `bgColor` `#FAF9F6` | text (4.5) | 5.82:1 | pass |
| light | `danger` `#A2443C` | `surfaceColor` `#F3F1EC` | text (4.5) | 5.43:1 | pass |
| light | `success` `#4A784E` | `bgColor` `#FAF9F6` | text (4.5) | 4.88:1 | pass |
| light | `textSecondary` `#5C5A54` | `skyDay` `#FAF9F6` | text (4.5) | 6.55:1 | pass |
| light | `textSecondary` `#5C5A54` | `skyCivil` `#EEEBE4` | text (4.5) | 5.79:1 | pass |
| light | `textSecondary` `#5C5A54` | `skyNautical` `#E5E1D8` | text (4.5) | 5.28:1 | pass |
| light | `textSecondary` `#5C5A54` | `skyAstro` `#DCD7CB` | text (4.5) | 4.80:1 | pass |
| light | `controlBorder` `#857F74` | `bgColor` `#FAF9F6` | boundary (3.0) | 3.77:1 | pass |
| light | `controlBorder` `#857F74` | `surfaceColor` `#F3F1EC` | boundary (3.0) | 3.52:1 | pass |
| light | `controlBorder` `#857F74` | `surfaceSubtle` `#ECE9E2` | boundary (3.0) | 3.28:1 | pass |
| light | `focusRing` `#3D5896` | `bgColor` `#FAF9F6` | boundary (3.0) | 6.57:1 | pass |
| light | `focusRing` `#3D5896` | `surfaceColor` `#F3F1EC` | boundary (3.0) | 6.13:1 | pass |
| light | `sun` `#7A6220` | `surfaceSubtle` `#ECE9E2` | boundary (3.0) | 4.82:1 | pass |
| light | `sun` `#7A6220` | `skyDay` `#FAF9F6` | graphic (3.0) | 5.55:1 | pass |
| light | `sun` `#7A6220` | `skyCivil` `#EEEBE4` | graphic (3.0) | 4.91:1 | pass |
| light | `sun` `#7A6220` | `skyNautical` `#E5E1D8` | graphic (3.0) | 4.48:1 | pass |
| light | `sun` `#7A6220` | `skyAstro` `#DCD7CB` | graphic (3.0) | 4.07:1 | pass |
| light | `moon` `#3D5896` | `skyDay` `#FAF9F6` | graphic (3.0) | 6.57:1 | pass |
| light | `moon` `#3D5896` | `skyAstro` `#DCD7CB` | graphic (3.0) | 4.82:1 | pass |
| light | `moon` `#3D5896` | `bgColor` `#FAF9F6` | graphic (3.0) | 6.57:1 | pass |
| light | `blueHour` `#4F67A6` | `surfaceColor` `#F3F1EC` | graphic (3.0) | 4.87:1 | pass |
| light | `blueHour` `#4F67A6` | `bgColor` `#FAF9F6` | graphic (3.0) | 5.22:1 | pass |
| light | `textPrimary` `#2C2C2C` | `skyDay` `#FAF9F6` | graphic (3.0) | 13.26:1 | pass |
| light | `textPrimary` `#2C2C2C` | `skyAstro` `#DCD7CB` | graphic (3.0) | 9.73:1 | pass |
| light | `sun` `#7A6220` | `bgColor` `#FAF9F6` | graphic (3.0) | 5.55:1 | pass |
| light | `moon` `#3D5896` | `surfaceColor` `#F3F1EC` | graphic (3.0) | 6.13:1 | pass |
| light | `textPrimary` `#2C2C2C` | `skyDay` `#FAF9F6` | text (4.5) | 13.26:1 | pass |
| light | `textSecondary` `#5C5A54` | `skyCivil` `#EEEBE4` | graphic (3.0) | 5.79:1 | pass |
| light | `bgColor` `#FAF9F6` | `sun` `#7A6220` | text (4.5) | 5.55:1 | pass |

SUMMARY: min normal text 4.80:1 (need 4.5); min boundary/graphic 3.28:1 (need 3.0); all pass: True

## Self-check (design/cenit-stage-1, design/system.html)

Checked with a script that extracts every hex value from each generated artboard and looks it up in this file, and lists the font families and any external URL. Result: every hex value is a token above, fonts are only `--font-sans` / `--font-serif`, no external URLs. Regenerate with `node tools/design-kit/gen.js design`.
