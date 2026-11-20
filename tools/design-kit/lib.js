/* Cenit artboard kit: shared state, formatting and renderers (SVG + HTML strings). */
const PLACES = [
  { id: 'perello', name: 'El Perelló', region: 'Sueca, Valencia', lat: 39.2817, lon: -0.2763, tz: 1 },
  { id: 'pujol', name: 'Gola de Pujol', region: "L'Albufera, Valencia", lat: 39.3406, lon: -0.3122, tz: 1 },
  { id: 'cac', name: 'Ciudad de las Artes', region: 'Valencia', lat: 39.4545, lon: -0.3505, tz: 1 },
  { id: 'montanejos', name: 'Montanejos', region: 'Castellón', lat: 40.0667, lon: -0.5236, tz: 1 },
  { id: 'teide', name: 'Parque Nacional del Teide', region: 'Tenerife', lat: 28.2724, lon: -16.6425, tz: 0 },
];
const CITIES = [
  { id: 'c1', name: 'El Perelló', region: 'Sueca, Valencia · España', lat: 39.2817, lon: -0.2763, tz: 1 },
  { id: 'c2', name: 'El Perelló', region: 'Tarragona · España', lat: 40.874, lon: 0.713, tz: 1 },
  { id: 'c3', name: 'El Perellonet', region: 'Valencia · España', lat: 39.3083, lon: -0.3011, tz: 1 },
  { id: 'c4', name: 'Peñíscola', region: 'Castellón · España', lat: 40.3594, lon: 0.4067, tz: 1 },
  { id: 'c5', name: 'Pedraza', region: 'Segovia · España', lat: 41.1307, lon: -3.8099, tz: 1 },
  { id: 'c6', name: 'Montanejos', region: 'Castellón · España', lat: 40.0667, lon: -0.5236, tz: 1 },
];
const S = { alert: false, place: PLACES[0], y: 2026, mo: 10, d: 20, today: [2026, 10, 20], min: 16 * 60 + 20, heading: 247, query: '' };
const _days = {};
let _uid = 0;
function day() {
  const k = S.place.id + S.y + '-' + S.mo + '-' + S.d;
  if (!_days[k]) {
    const d = Astro.day(S.place, S.y, S.mo, S.d); const p = S.place; d.samples = [];
    for (let m = 0; m <= 1440; m += 5) { const t = d.start + m * 60000; d.samples.push([m, Astro.sun(t, p.lat, p.lon), Astro.moon(t, p.lat, p.lon)]); }
    _days[k] = d;
  }
  return _days[k];
}
const nowT = () => day().start + S.min * 60000;
const sunNow = () => Astro.sun(nowT(), S.place.lat, S.place.lon);
const moonNow = () => Astro.moon(nowT(), S.place.lat, S.place.lon);
const phaseNow = () => Astro.phase(nowT());
const T = (ms) => Astro.fmt(ms, S.place.tz);
const minOf = (ms) => (ms - day().start) / 60000;
const num = (x, dec = 0) => x.toFixed(dec).replace('.', ',').replace(/^-/, '−');
const deg = (x, dec = 0) => num(x, dec) + '°';
const WINDS = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSO', 'SO', 'OSO', 'O', 'ONO', 'NO', 'NNO'];
const wind = (az) => WINDS[Math.round(((az % 360) + 360) % 360 / 22.5) % 16];
const inDur = (ms) => (ms < 60000 ? 'ahora' : 'en ' + dur(ms));
function dur(ms) { const m = Math.round(Math.abs(ms) / 60000); return m < 60 ? m + ' min' : Math.floor(m / 60) + ' h ' + String(m % 60).padStart(2, '0') + ' min'; }
const coords = (p) => num(Math.abs(p.lat), 2) + '° ' + (p.lat >= 0 ? 'N' : 'S') + ' · ' + num(Math.abs(p.lon), 2) + '° ' + (p.lon >= 0 ? 'E' : 'O');
const dateLabel = (y = S.y, mo = S.mo, d = S.d, opts = { weekday: 'short', day: 'numeric', month: 'short' }) =>
  new Intl.DateTimeFormat('es-ES', Object.assign({ timeZone: 'UTC' }, opts)).format(Date.UTC(y, mo, d)).replace(',', '');
const hhmm = (m) => String(Math.floor(m / 60) % 24).padStart(2, '0') + ':' + String(Math.round(m % 60)).padStart(2, '0');
const isToday = () => S.y === S.today[0] && S.mo === S.today[1] && S.d === S.today[2];

/* Light phases a photographer plans around (sun centre elevation). */
function skyPhase(alt) {
  if (alt > 6) return { id: 'day', name: 'Día', fill: 'var(--sky-day)' };
  if (alt >= -4) return { id: 'golden', name: 'Hora dorada', fill: 'var(--sun)' };
  if (alt >= -6) return { id: 'blue', name: 'Hora azul', fill: 'var(--blue-hour)' };
  if (alt >= -12) return { id: 'nautical', name: 'Crepúsculo náutico', fill: 'var(--sky-civil)' };
  if (alt >= -18) return { id: 'astro', name: 'Crepúsculo astronómico', fill: 'var(--sky-nautical)' };
  return { id: 'night', name: 'Noche', fill: 'var(--sky-astro)' };
}
function events() {
  const d = day();
  return [
    [d.astroAm, 'Empieza el crepúsculo astronómico', 'astro'], [d.nauticalAm, 'Empieza el crepúsculo náutico', 'nautical'],
    [d.civilAm, 'Hora azul', 'blue', d.blueAm], [d.blueAm, 'Hora dorada', 'golden', d.goldenAm],
    [d.setAm, 'Amanecer', 'sunrise', null, d.riseAz], [d.noon, 'Mediodía solar', 'noon', null, null, d.noonAlt],
    [d.goldenPm, 'Hora dorada', 'golden', d.bluePm], [d.setPm, 'Atardecer', 'sunset', null, d.setAz],
    [d.bluePm, 'Hora azul', 'blue', d.civilPm], [d.nauticalPm, 'Termina el crepúsculo náutico', 'nautical'],
    [d.astroPm, 'Empieza la noche', 'night'],
    [d.moonrise, 'Salida de la luna', 'moonrise', null, d.moonriseAz], [d.moonset, 'Puesta de la luna', 'moonset', null, d.moonsetAz],
  ].filter((e) => e[0]).map(([t, label, kind, end, az, alt]) => ({ t, label, kind, end, az, alt })).sort((a, b) => a.t - b.t);
}
function nextEvent() {
  const t = nowT(); const list = events().filter((e) => e.kind !== 'nautical' && e.kind !== 'astro');
  return list.find((e) => e.t > t) || null;
}
const dayLength = (d = day()) => d.setPm - d.setAm;

/* ---------- icons ---------- */
const ICON = {
  hoy: '<path d="M3 17h18M6.5 17a5.5 5.5 0 0 1 11 0M12 5v2.5M4.6 9.6l1.8 1.8M19.4 9.6l-1.8 1.8"/>',
  cielo: '<path d="M2.5 18h19M4.5 18C6 9.5 18 9.5 19.5 18"/><circle cx="12" cy="11.6" r="1.8"/>',
  brujula: '<circle cx="12" cy="12" r="9"/><path d="M14.8 9.2l-1.9 4.9-3.7 1.7 1.9-4.9z"/>',
  lugares: '<path d="M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
  gps: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"/><circle cx="12" cy="12" r="7"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>', prev: '<path d="M15 5l-7 7 7 7"/>', next: '<path d="M9 5l7 7-7 7"/>',
  cal: '<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8"/>',
  moon: '<path d="M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
  bell: '<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
};
const icon = (n, s = 22) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[n]}</svg>`;
function logo(s = 28) {
  return `<svg width="${s}" height="${s}" viewBox="0 0 32 32" aria-hidden="true" fill="none" stroke-linecap="round">
  <path d="M5 22a11 11 0 0 1 22 0" style="stroke:var(--text-primary)" stroke-width="1.4"/>
  <circle cx="16" cy="11" r="2.6" style="fill:var(--sun)"/>
  <path d="M3 22h26M10 25.5h12M13 28.5h6" style="stroke:var(--text-secondary)" stroke-width="1.4"/></svg>`;
}

/* ---------- chrome ---------- */
const statusBar = () => `<div class="status" aria-hidden="true"><span>16:20</span><span class="status-icons"><i></i><i></i><b></b></span></div>`;
function tabBar(active) {
  const tabs = [['hoy', 'Hoy'], ['cielo', 'Cielo'], ['brujula', 'Brújula'], ['lugares', 'Lugares']];
  return `<nav class="tabbar" aria-label="Secciones">${tabs.map(([id, l]) => `<a href="#" class="tab${id === active ? ' on' : ''}"${id === active ? ' aria-current="page"' : ''}>${icon(id)}<span>${l}</span></a>`).join('')}</nav>`;
}
function phaseGlyph(ph, r = 11, label = true) {
  const k = ph.illum, c = r + 1, rx = Math.abs(1 - 2 * k) * r;
  const d = `M${c},${c - r} A${r},${r} 0 0 ${ph.waxing ? 1 : 0} ${c},${c + r} A${rx},${r} 0 0 ${(k > 0.5) === ph.waxing ? 1 : 0} ${c},${c - r}Z`;
  return `<svg width="${2 * c}" height="${2 * c}" viewBox="0 0 ${2 * c} ${2 * c}" role="img" aria-label="${label ? ph.name + ', ' + Math.round(k * 100) + ' % iluminada' : ''}"${label ? '' : ' aria-hidden="true"'}>
  <circle cx="${c}" cy="${c}" r="${r}" style="fill:var(--surface-subtle);stroke:var(--control-border)" stroke-width="1"/><path d="${d}" style="fill:var(--moon)"/></svg>`;
}

/* ---------- day ribbon: 24 h of light phases ---------- */
function ribbonSegments() {
  const segs = []; day().samples.forEach(([m, s]) => {
    const ph = skyPhase(s.alt); const last = segs[segs.length - 1];
    if (last && last.id === ph.id) last.to = m; else segs.push(Object.assign({ from: m, to: m }, ph));
  });
  return segs;
}
function ribbon(w = 316, h = 14, nowMark = true) {
  const segs = ribbonSegments(), X = (m) => (m / 1440) * w, id = 'rb' + (++_uid);
  return `<svg class="ribbon" width="${w}" height="${h + 4}" viewBox="0 0 ${w} ${h + 4}" role="img" aria-label="Luz del día: ${segs.filter((s) => s.id === 'golden').map((s) => 'hora dorada ' + hhmm(s.from) + '–' + hhmm(s.to)).join(', ')}">
  <defs><clipPath id="${id}"><rect x="0" y="2" width="${w}" height="${h}" rx="${h / 2}"/></clipPath></defs>
  <g clip-path="url(#${id})">${segs.map((s) => `<rect x="${X(s.from)}" y="2" width="${Math.max(1, X(s.to + 5) - X(s.from))}" height="${h}" style="fill:${s.fill}"/>`).join('')}</g>
  <rect x="0.5" y="2.5" width="${w - 1}" height="${h - 1}" rx="${(h - 1) / 2}" style="fill:none;stroke:var(--control-border)"/>
  ${nowMark ? `<rect x="${X(S.min) - 1.5}" y="0" width="3" height="${h + 4}" rx="1.5" style="fill:var(--text-primary);stroke:var(--bg-color)" stroke-width="1"/>` : ''}</svg>`;
}

/* ---------- elevation chart (sun path diagram) ---------- */
function chart({ w = 316, h = 150, labels = false, ticks = true, now = true } = {}) {
  const d = day(), padT = 8, padB = ticks ? 20 : 4, id = 'cp' + (++_uid);
  const top = Math.max(...d.samples.map((s) => Math.max(s[1].alt, s[2].alt)));
  const ymax = Math.max(30, Math.ceil((top + 6) / 10) * 10), ymin = -20;
  const X = (m) => (m / 1440) * w, Y = (a) => padT + ((ymax - a) / (ymax - ymin)) * (h - padT - padB);
  const bands = [[ymax, 0, 'var(--sky-day)', 'Día'], [0, -6, 'var(--sky-civil)', 'Civil'], [-6, -12, 'var(--sky-nautical)', 'Náutico'], [-12, -18, 'var(--sky-astro)', 'Astron.'], [-18, -20, 'var(--chart-night)', '']];
  const line = (sel, k) => d.samples.filter(sel).map((s) => X(s[0]).toFixed(1) + ',' + Y(s[k].alt).toFixed(1));
  const runs = (pred) => { const out = []; let cur = []; d.samples.forEach((s) => { if (pred(s[1].alt)) cur.push(X(s[0]).toFixed(1) + ',' + Y(s[1].alt).toFixed(1)); else if (cur.length) { out.push(cur); cur = []; } }); if (cur.length) out.push(cur); return out; };
  const s = sunNow(), mo = moonNow(), nx = X(S.min);
  const golden = runs((a) => a >= -4 && a <= 6), blue = runs((a) => a >= -6 && a < -4);
  return `<svg class="chart" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="Elevación del sol (línea continua) y de la luna (discontinua) el ${dateLabel()}. Máxima del sol ${deg(d.noonAlt)} a las ${T(d.noon)}.">
  <defs><clipPath id="${id}"><rect x="0" y="0" width="${w}" height="${h - padB}"/></clipPath></defs>
  <g clip-path="url(#${id})">${bands.map(([a, b, f]) => `<rect x="0" y="${Y(a)}" width="${w}" height="${Y(b) - Y(a)}" style="fill:${f}"/>`).join('')}<rect x="0" y="0" width="${w}" height="${padT}" style="fill:var(--sky-day)"/>
  ${golden.map((r) => `<polyline points="${r.join(' ')}" style="fill:none;stroke:var(--sun)" stroke-width="6" stroke-linecap="round"/>`).join('')}
  ${blue.map((r) => `<polyline points="${r.join(' ')}" style="fill:none;stroke:var(--blue-hour)" stroke-width="6" stroke-linecap="round"/>`).join('')}
  <line x1="0" x2="${w}" y1="${Y(0)}" y2="${Y(0)}" style="stroke:var(--control-border)"/>
  <polyline points="${line(() => true, 2).join(' ')}" style="fill:none;stroke:var(--moon)" stroke-width="2" stroke-dasharray="5 4"/>
  <polyline points="${line(() => true, 1).join(' ')}" style="fill:none;stroke:var(--sun)" stroke-width="2"/>
  ${labels ? bands.filter((b) => b[3] && Y(b[1]) - Y(b[0]) >= 13).map(([a, b, , l]) => `<text x="${w - 8}" y="${(Y(a) + Y(b)) / 2 + 4}" text-anchor="end" class="svg-label">${l}</text>`).join('') : ''}
  ${now ? `<line x1="${nx}" x2="${nx}" y1="0" y2="${h - padB}" style="stroke:var(--text-primary)" stroke-width="1"/>
  <circle cx="${nx}" cy="${Y(mo.alt)}" r="4.5" style="fill:var(--bg-color);stroke:var(--moon)" stroke-width="2"/>
  <circle cx="${nx}" cy="${Y(s.alt)}" r="5.5" style="fill:var(--sun);stroke:var(--bg-color)" stroke-width="2"/>` : ''}</g>
  ${ticks ? [0, 6, 12, 18, 24].map((hh) => `<text x="${Math.min(w - 2, Math.max(2, X(hh * 60)))}" y="${h - 4}" text-anchor="${hh === 0 ? 'start' : hh === 24 ? 'end' : 'middle'}" class="svg-label">${String(hh).padStart(2, '0')}</text>`).join('') : ''}</svg>`;
}

/* ---------- sky dome: flat compass view (Sun Seeker style) ----------
   Top-down sky: zenith at the centre, horizon circle, twilight rings outside it.
   Day path solid with hour dots, night path dotted outside the horizon,
   June/December solstice and equinox reference paths; N up unless a heading is given. */
const _ref = {};
function refPaths() {
  const p = S.place; if (_ref[p.id]) return _ref[p.id];
  const mk = (y, mo, d) => { const st = Astro.dayStart(y, mo, d, p.tz), out = []; for (let m = 0; m <= 1440; m += 10) out.push(Astro.sun(st + m * 60000, p.lat, p.lon)); return out; };
  return (_ref[p.id] = { summer: mk(2026, 5, 21), equinox: mk(2026, 2, 20), winter: mk(2026, 11, 21) });
}
function dome({ size = 300, heading = 0, labels = true, refs = true } = {}) {
  const d = day(), c = size / 2, R = (c - 6) / (102 / 90), s = sunNow(), mo = moonNow();
  const rad = (alt) => (R * (90 - Math.max(-12, alt))) / 90;
  const P = (az, alt) => { const r = rad(alt), a = ((az - heading) * Math.PI) / 180; return [c + r * Math.sin(a), c - r * Math.cos(a)]; };
  const runs = (pts, pred) => { const out = []; let cur = []; pts.forEach((q) => { if (pred(q.alt)) cur.push(P(q.az, q.alt).map((v) => v.toFixed(1)).join(',')); else if (cur.length) { out.push(cur); cur = []; } }); if (cur.length) out.push(cur); return out; };
  const line = (pts, pred, style, dash) => runs(pts, pred).map((p) => `<polyline points="${p.join(' ')}" style="fill:none;${style}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`).join('');
  const sun = d.samples.map((q) => q[1]), moon = d.samples.map((q) => q[2]);
  const at = (az, r) => { const a = ((az - heading) * Math.PI) / 180; return [c + r * Math.sin(a), c - r * Math.cos(a)]; };
  let refSvg = '';
  if (refs) {
    const rp = refPaths(), apex = (pts) => pts.reduce((b, q) => (q.alt > b.alt ? q : b));
    refSvg = line(rp.summer, (a) => a > 0, 'stroke:var(--text-secondary);stroke-width:1') + line(rp.winter, (a) => a > 0, 'stroke:var(--text-secondary);stroke-width:1') + line(rp.equinox, (a) => a > 0, 'stroke:var(--text-secondary);stroke-width:1', '2 3');
    if (labels) [[rp.summer, 'jun'], [rp.winter, 'dic']].forEach(([pts, l]) => { const q = apex(pts), [x, y] = P(q.az, q.alt); refSvg += `<text x="${x}" y="${l === 'dic' ? y + 16 : y - 5}" text-anchor="middle" class="svg-label">${l}</text>`; });
  }
  let dots = '';
  d.samples.forEach(([m, q]) => { if (m % 60 || q.alt <= 0.5) return; const [x, y] = P(q.az, q.alt), h = m / 60;
    dots += `<circle cx="${x}" cy="${y}" r="3" style="fill:var(--bg-color);stroke:var(--sun)" stroke-width="1.5"/>`;
    if (labels && h % 3 === 0) { const dx = c - x, dy = c - y, n = Math.hypot(dx, dy) || 1; dots += `<text x="${x + (dx / n) * 13}" y="${y + (dy / n) * 13 + 4}" text-anchor="middle" class="svg-label strong">${h}</text>`; } });
  const tick = (az, col, t) => { const [x1, y1] = at(az, R), [x2, y2] = at(az, R - 9), [lx, ly] = at(az, R - 24);
    return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" style="stroke:var(--${col})" stroke-width="2"/>${labels ? `<text x="${lx}" y="${ly + 4}" text-anchor="middle" class="svg-label strong">${t}</text>` : ''}`; };
  return `<svg class="dome" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" role="img" aria-label="Brújula plana: recorrido del sol con sus horas y de la luna. Amanecer a ${deg(d.riseAz)} a las ${T(d.setAm)}, atardecer a ${deg(d.setAz)} a las ${T(d.setPm)}. Líneas finas: solsticios de junio y diciembre y equinoccio.">
  <circle cx="${c}" cy="${c}" r="${rad(-12)}" style="fill:var(--sky-nautical);stroke:var(--border-color)"/>
  <circle cx="${c}" cy="${c}" r="${rad(-6)}" style="fill:var(--sky-civil)"/>
  <circle cx="${c}" cy="${c}" r="${R}" style="fill:var(--sky-day);stroke:var(--control-border)"/>
  <circle cx="${c}" cy="${c}" r="${(R * 2) / 3}" style="fill:none;stroke:var(--border-color)"/><circle cx="${c}" cy="${c}" r="${R / 3}" style="fill:none;stroke:var(--border-color)"/>
  ${labels ? [[0, 'N'], [90, 'E'], [180, 'S'], [270, 'O']].map(([az, l]) => { const [x, y] = at(az, R - 11); return `<text x="${x}" y="${y + 4}" text-anchor="middle" class="svg-label${l === 'N' ? ' strong' : ''}">${l}</text>`; }).join('') : ''}
  ${refSvg}
  ${line(sun, (a) => a <= 0, 'stroke:var(--text-secondary);stroke-width:1.5', '1 4')}
  ${line(moon, (a) => a > -0.5, 'stroke:var(--moon);stroke-width:2', '5 4')}
  ${line(sun, (a) => a > -0.5, 'stroke:var(--sun);stroke-width:2.5')}
  ${dots}
  ${d.riseAz ? tick(d.riseAz, 'sun', T(d.setAm)) : ''}${d.setAz ? tick(d.setAz, 'sun', T(d.setPm)) : ''}
  ${mo.alt > 0 ? (() => { const [x, y] = P(mo.az, mo.alt); return `<circle cx="${x}" cy="${y}" r="6" style="fill:var(--bg-color);stroke:var(--moon)" stroke-width="2"/>`; })() : ''}
  ${(() => { const [x, y] = P(s.az, s.alt); return s.alt > 0 ? `<circle cx="${x}" cy="${y}" r="7.5" style="fill:var(--sun);stroke:var(--bg-color)" stroke-width="2"/>` : `<circle cx="${x}" cy="${y}" r="6" style="fill:var(--bg-color);stroke:var(--sun)" stroke-width="2"/>`; })()}
  <circle cx="${c}" cy="${c}" r="2" style="fill:var(--text-secondary)"/></svg>`;
}
const shadowRatio = (alt) => (alt <= 0.5 ? null : 1 / Math.tan((alt * Math.PI) / 180));

/* ---------- compass rose (rotates with the heading; index fixed at the top) ---------- */
function rose({ size = 300 } = {}) {
  const d = day(), c = size / 2, R = c - 8, hd = S.heading, s = sunNow(), mo = moonNow();
  const A = (az) => ((az - hd) * Math.PI) / 180, pt = (az, r) => [c + r * Math.sin(A(az)), c - r * Math.cos(A(az))];
  let ticks = ''; for (let a = 0; a < 360; a += 5) { const [x1, y1] = pt(a, R), [x2, y2] = pt(a, R - (a % 30 === 0 ? 12 : 6)); ticks += `<line x1="${x1.toFixed(1)}" y1="${y1.toFixed(1)}" x2="${x2.toFixed(1)}" y2="${y2.toFixed(1)}" style="stroke:var(--${a % 90 === 0 ? 'text-primary' : 'control-border'})" stroke-width="${a % 30 === 0 ? 1.5 : 1}"/>`; }
  const ray = (az, col, dash, label) => { const [x, y] = pt(az, R - 58), [lx, ly] = pt(az, R - 44); return `<line x1="${c}" y1="${c}" x2="${x}" y2="${y}" style="stroke:var(--${col})" stroke-width="2"${dash ? ' stroke-dasharray="5 4"' : ''}/><text x="${lx}" y="${ly + 4}" text-anchor="middle" class="svg-label strong">${label}</text>`; };
  const card = [[0, 'N'], [90, 'E'], [180, 'S'], [270, 'O']].map(([a, l]) => { const [x, y] = pt(a, R - 26); return `<text x="${x}" y="${y + 5}" text-anchor="middle" class="svg-card${l === 'N' ? ' n' : ''}">${l}</text>`; }).join('');
  return `<svg class="rose" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" role="img" aria-label="Brújula: rumbo ${deg(hd)} ${wind(hd)}. Atardecer a ${deg(d.setAz)}, salida de la luna a ${deg(d.moonriseAz || 0)}.">
  <circle cx="${c}" cy="${c}" r="${R}" style="fill:var(--surface-color);stroke:var(--control-border)"/>${ticks}${card}
  ${d.riseAz ? ray(d.riseAz, 'sun', false, T(d.setAm)) : ''}${d.setAz ? ray(d.setAz, 'sun', false, T(d.setPm)) : ''}
  ${d.moonriseAz ? ray(d.moonriseAz, 'moon', true, T(d.moonrise)) : ''}${d.moonsetAz ? ray(d.moonsetAz, 'moon', true, T(d.moonset)) : ''}
  ${s.alt > -1 ? (() => { const [x, y] = pt(s.az, R - 18); return `<circle cx="${x}" cy="${y}" r="7.5" style="fill:var(--sun);stroke:var(--bg-color)" stroke-width="2"/>`; })() : ''}
  ${mo.alt > -1 ? (() => { const [x, y] = pt(mo.az, R - 18); return `<circle cx="${x}" cy="${y}" r="6" style="fill:var(--bg-color);stroke:var(--moon)" stroke-width="2"/>`; })() : ''}
  <circle cx="${c}" cy="${c}" r="3" style="fill:var(--text-primary)"/>
  <path d="M${c - 8},4 L${c + 8},4 L${c},18 Z" style="fill:var(--text-primary)"/></svg>`;
}

/* ---------- heading tape (horizontal compass, ±60° around the heading) ---------- */
function tape({ w = 316, h = 92 } = {}) {
  const d = day(), hd = S.heading, span = 120, X = (az) => { let dd = ((az - hd + 540) % 360) - 180; return w / 2 + (dd / span) * w; }, s = sunNow(), mo = moonNow();
  let t = ''; for (let a = Math.floor((hd - 70) / 5) * 5; a <= hd + 70; a += 5) { const az = ((a % 360) + 360) % 360, x = X(az); if (x < 0 || x > w) continue;
    const major = az % 15 === 0; t += `<line x1="${x}" x2="${x}" y1="${h - 30}" y2="${h - (major ? 16 : 22)}" style="stroke:var(--${major ? 'text-secondary' : 'control-border'})"/>`;
    if (major) t += `<text x="${x}" y="${h - 2}" text-anchor="middle" class="svg-label${az % 90 === 0 ? ' strong' : ''}">${az % 45 === 0 ? WINDS[az / 22.5] : az}</text>`; }
  const mark = (az, kind, label) => { const x = X(az); if (x < 6 || x > w - 6) return ''; return `<g>${kind === 'sun' ? `<circle cx="${x}" cy="22" r="7.5" style="fill:var(--sun);stroke:var(--bg-color)" stroke-width="2"/>` : kind === 'moon' ? `<circle cx="${x}" cy="22" r="6.5" style="fill:var(--bg-color);stroke:var(--moon)" stroke-width="2"/>` : `<line x1="${x}" x2="${x}" y1="20" y2="${h - 30}" style="stroke:var(--${kind.replace('-line', '')})" stroke-width="2"${kind === 'moon-line' ? ' stroke-dasharray="4 3"' : ''}/>`}${label ? `<text x="${Math.min(w - 40, Math.max(40, x))}" y="${kind === 'sun' || kind === 'moon' ? 45 : 15}" text-anchor="middle" class="svg-label strong">${label}</text>` : ''}</g>`; };
  return `<svg class="tape" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="Rumbo ${deg(hd)}. Atardecer a ${deg(d.setAz)}.">
  <rect x="0.5" y="0.5" width="${w - 1}" height="${h - 1}" rx="14" style="fill:var(--surface-color);stroke:var(--border-color)"/>${t}
  ${d.setAz ? mark(d.setAz, 'sun-line', 'Atardecer ' + T(d.setPm)) : ''}
  ${d.riseAz ? mark(d.riseAz, 'sun-line', 'Amanecer ' + T(d.setAm)) : ''}${d.moonriseAz ? mark(d.moonriseAz, 'moon-line', 'Luna ' + T(d.moonrise)) : ''}
  ${s.alt > -1 ? mark(s.az, 'sun', '') : ''}${mo.alt > -1 ? mark(mo.az, 'moon', '') : ''}
  <path d="M${w / 2 - 7},${h - 31} L${w / 2 + 7},${h - 31} L${w / 2},${h - 41} Z" style="fill:var(--text-primary)"/><line x1="${w / 2}" x2="${w / 2}" y1="${h - 31}" y2="${h - 14}" style="stroke:var(--text-primary)" stroke-width="2"/></svg>`;
}
/* Signed angle from the heading to a target: "3° a la izquierda". */
function relative(az) { const dd = ((az - S.heading + 540) % 360) - 180; return Math.abs(dd) < 1 ? 'de frente' : deg(Math.abs(dd)) + (dd < 0 ? ' izq.' : ' der.'); }

/* ---------- planner controls ---------- */
function dateStrip() {
  const base = Date.UTC(S.today[0], S.today[1], S.today[2]), sel = Date.UTC(S.y, S.mo, S.d), dow = (new Date(base).getUTCDay() + 6) % 7;
  let cells = ''; for (let i = 0; i < 7; i++) { const ms = base + (i - dow) * 86400000, dt = new Date(ms), y = dt.getUTCFullYear(), mo = dt.getUTCMonth(), d = dt.getUTCDate();
    const today = ms === base, on = ms === sel; cells += `<button class="day${today ? ' today' : ''}${on ? ' on' : ''}" data-act="date" data-ms="${ms}" data-key="day${i}" aria-pressed="${on}" aria-label="${dateLabel(y, mo, d, { weekday: 'long', day: 'numeric', month: 'long' })}${today ? ', hoy' : ''}"><span>${dateLabel(y, mo, d, { weekday: 'narrow' })}</span><b>${d}</b></button>`; }
  return `<div class="strip" role="group" aria-label="Fecha">${cells}</div>`;
}
const scrubber = (id = 'time') => `<label class="sr" for="${id}">Hora</label><input class="scrub" id="${id}" type="range" min="0" max="1435" step="5" value="${S.min}" data-input="time" aria-valuetext="${hhmm(S.min)}">`;
function readout() {
  const s = sunNow(), m = moonNow(), ph = phaseNow();
  return `<dl class="readout"><div><dt>${icon('sun', 16)} Sol</dt><dd>${deg(s.az)} <small>${wind(s.az)}</small></dd><dd class="sub">${s.alt < -0.833 ? 'bajo el horizonte' : 'elev. ' + deg(s.alt, 1)}</dd></div>
  <div><dt>${icon('moon', 16)} Luna</dt><dd>${deg(m.az)} <small>${wind(m.az)}</small></dd><dd class="sub">${m.alt < -0.833 ? 'bajo el horizonte' : 'elev. ' + deg(m.alt, 1)} · ${Math.round(ph.illum * 100)} %</dd></div></dl>`;
}

/* ---------- places (shared by every direction) ---------- */
function placesSlot() {
  const q = S.query.trim().toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
  const row = (p, act) => `<button class="place" data-act="place" data-id="${p.id}" data-key="pl-${p.id}"${p.id === S.place.id || (p.lat === S.place.lat && p.lon === S.place.lon) ? ' aria-current="true"' : ''}><span class="pl-text"><span class="pl-name">${p.name}</span><span class="pl-sub">${p.region} · ${coords(p)}</span></span>${p.lat === S.place.lat && p.lon === S.place.lon ? `<span class="pl-check">${icon('check', 20)}<span class="sr">Lugar activo</span></span>` : ''}</button>`;
  if (q) {
    const hits = CITIES.filter((c) => c.name.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '').includes(q));
    return `<p class="eyebrow">${hits.length ? hits.length + (hits.length === 1 ? ' resultado' : ' resultados') : 'Sin resultados'}</p>
    ${hits.length ? `<div class="list">${hits.map((c) => row(c)).join('')}</div>` : `<p class="muted">No hay ninguna ciudad con «${S.query}». Prueba con otro nombre o introduce las coordenadas.</p>`}`;
  }
  return `<button class="place gps" data-act="gps"><span class="pl-icon">${icon('gps', 20)}</span><span class="pl-text"><span class="pl-name">Mi ubicación</span><span class="pl-sub">GPS del móvil · sin conexión</span></span></button>
  <p class="eyebrow">Guardados</p><div class="list">${PLACES.map((p) => row(p)).join('')}</div>`;
}
function placesPhone() {
  return `<section class="phone" aria-label="Pantalla Lugares">${statusBar()}<div class="screen">
  <h2 class="headline">Lugares</h2>
  <label class="search">${icon('search', 20)}<span class="sr">Buscar ciudad</span><input type="search" placeholder="Buscar ciudad" data-input="search" autocomplete="off" value="${S.query}"></label>
  <div data-slot="places"></div>
  <div class="actions"><button class="btn-secondary" data-act="manual">${icon('edit', 18)} Coordenadas manuales</button></div>
  <p class="fine">Búsqueda sin conexión. Datos de ciudades: GeoNames (CC BY 4.0).</p>
  </div>${tabBar('lugares')}</section>`;
}

/* ---------- mount: slots re-render on every change, inputs live outside slots ---------- */
const SLOTS = { places: placesSlot };
function update() {
  const a = document.activeElement, key = a && a.dataset && a.dataset.key;
  document.querySelectorAll('[data-slot]').forEach((el) => { const f = SLOTS[el.dataset.slot]; if (f) el.innerHTML = f(); });
  document.querySelectorAll('[data-input=time]').forEach((el) => { if (el !== a) el.value = S.min; el.setAttribute('aria-valuetext', hhmm(S.min)); });
  document.querySelectorAll('[data-input=heading]').forEach((el) => { if (el !== a) el.value = S.heading; });
  if (key) { const el = document.querySelector(`[data-key="${key}"]`); if (el) el.focus(); }
}
function mount(slots) {
  Object.assign(SLOTS, slots);
  document.addEventListener('input', (e) => {
    const t = e.target.dataset && e.target.dataset.input;
    if (t === 'time') S.min = +e.target.value; else if (t === 'heading') S.heading = +e.target.value; else if (t === 'search') S.query = e.target.value; else return;
    update();
  });
  document.addEventListener('click', (e) => {
    const b = e.target.closest('[data-act]'); if (!b) return; const act = b.dataset.act;
    if (b.tagName === 'A') e.preventDefault();
    if (act === 'date') { const dt = new Date(+b.dataset.ms); S.y = dt.getUTCFullYear(); S.mo = dt.getUTCMonth(); S.d = dt.getUTCDate(); }
    else if (act === 'shift') { const dt = new Date(Date.UTC(S.y, S.mo, S.d) + +b.dataset.days * 86400000); S.y = dt.getUTCFullYear(); S.mo = dt.getUTCMonth(); S.d = dt.getUTCDate(); }
    else if (act === 'now') { S.y = S.today[0]; S.mo = S.today[1]; S.d = S.today[2]; S.min = 16 * 60 + 20; }
    else if (act === 'place') { S.place = PLACES.concat(CITIES).find((p) => p.id === b.dataset.id) || S.place; }
    else if (act === 'alert') { S.alert = !S.alert; }
    else if (act === 'gps') { S.place = PLACES[0]; S.query = ''; document.querySelectorAll('[data-input=search]').forEach((i) => (i.value = '')); }
    else if (act === 'theme') { const r = document.documentElement; r.dataset.theme = r.dataset.theme === 'dark' ? 'light' : 'dark'; b.setAttribute('aria-pressed', r.dataset.theme === 'light'); b.querySelector('span').textContent = r.dataset.theme === 'dark' ? 'Oscuro' : 'Claro'; }
    else return;
    update();
  });
  document.querySelectorAll('a.tab').forEach((a) => a.addEventListener('click', (e) => e.preventDefault()));
  update();
}

/* Golden-hour / event alert toggle (local notification; works offline). */
function alertBtn() {
  const e = nextEvent(); const what = e ? e.label.toLowerCase() : 'el próximo evento';
  return `<button class="icon-btn alert" data-act="alert" data-key="alert" aria-pressed="${S.alert}" aria-label="Avisarme 30 minutos antes de ${what}">${icon('bell', 22)}</button>`;
}
