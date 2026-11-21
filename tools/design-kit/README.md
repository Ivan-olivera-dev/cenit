# Design kit

Generates the self-contained artboards under `design/` (gentle-design rule: one HTML file per artboard, inline CSS/JS/SVG, no external URLs).

- `base.css` — tokens of `design/system.md` as CSS custom properties (dark and light) plus component styles.
- `astro.js` — low-precision sun (NOAA) and moon ephemeris, accurate to about a minute; for mockups only. The app uses Astronomy Engine.
- `lib.js` — shared state, Spanish formatting and renderers (elevation chart, sky dome, compass rose, heading tape, day ribbon, date strip, places).
- `ar.css` — stage 2 (AR camera) layout and HUD styles using `design/system.md` tokens only.
- `ar.js` — stage 2 3D camera projection, drawn Valencia dusk/day/night placeholder scene, crosshair crossing math, edge arrows, synced flat dome and key states.
- `gen.js` — writes `design/system.html`, `design/cenit-stage-1/*` and `design/cenit-stage-2/*`.
- `contrast.py` — WCAG 2.x contrast check of the palette pairs listed in `design/system.md`.

```sh
node tools/design-kit/gen.js design
python3 -I tools/design-kit/contrast.py /tmp/rows.json
```
