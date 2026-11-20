/* Cenit artboard ephemeris — low-precision sun (NOAA) and moon (Schlyter) for design mockups only.
   The app uses Astronomy Engine; these values are accurate to about a minute / a few tenths of a degree. */
const Astro = (() => {
  const R = Math.PI / 180, D = 180 / Math.PI;
  const sin = (x) => Math.sin(x * R), cos = (x) => Math.cos(x * R);
  const norm = (x) => ((x % 360) + 360) % 360;
  const jd = (ms) => ms / 86400000 + 2440587.5;

  function sunEcl(ms) {
    const T = (jd(ms) - 2451545) / 36525;
    const L0 = norm(280.46646 + T * (36000.76983 + T * 0.0003032));
    const M = 357.52911 + T * (35999.05029 - 0.0001537 * T);
    const C = sin(M) * (1.914602 - T * (0.004817 + 0.000014 * T)) + sin(2 * M) * (0.019993 - 0.000101 * T) + sin(3 * M) * 0.000289;
    const om = 125.04 - 1934.136 * T;
    const lam = L0 + C - 0.00569 - 0.00478 * sin(om);
    const eps = 23 + (26 + (21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))) / 60) / 60 + 0.00256 * cos(om);
    return { lam: norm(lam), eps };
  }
  function equToHor(ms, ra, dec, lat, lon) {
    const d = jd(ms) - 2451545, T = d / 36525;
    const gmst = norm(280.46061837 + 360.98564736629 * d + 0.000387933 * T * T);
    const H = norm(gmst + lon - ra);
    const alt = Math.asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(H)) * D;
    const az = norm(Math.atan2(sin(H), cos(H) * sin(lat) - Math.tan(dec * R) * cos(lat)) * D + 180);
    return { alt, az };
  }
  function eclToEqu(lam, beta, eps) {
    const ra = norm(Math.atan2(sin(lam) * cos(eps) - Math.tan(beta * R) * sin(eps), cos(lam)) * D);
    const dec = Math.asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lam)) * D;
    return { ra, dec };
  }
  function sun(ms, lat, lon) {
    const { lam, eps } = sunEcl(ms);
    const { ra, dec } = eclToEqu(lam, 0, eps);
    return equToHor(ms, ra, dec, lat, lon);
  }
  function moonEcl(ms) {
    const d = jd(ms) - 2451543.5;
    const N = 125.1228 - 0.0529538083 * d, i = 5.1454, w = 318.0634 + 0.1643573223 * d;
    const a = 60.2666, e = 0.0549, M = norm(115.3654 + 13.0649929509 * d);
    let E = M + e * D * sin(M) * (1 + e * cos(M));
    for (let k = 0; k < 5; k++) E = E - (E - e * D * sin(E) - M) / (1 - e * cos(E));
    const xv = a * (cos(E) - e), yv = a * Math.sqrt(1 - e * e) * sin(E);
    const v = Math.atan2(yv, xv) * D; let r = Math.hypot(xv, yv);
    const xh = r * (cos(N) * cos(v + w) - sin(N) * sin(v + w) * cos(i));
    const yh = r * (sin(N) * cos(v + w) + cos(N) * sin(v + w) * cos(i));
    const zh = r * sin(v + w) * sin(i);
    let lon = Math.atan2(yh, xh) * D, lat = Math.atan2(zh, Math.hypot(xh, yh)) * D;
    const Ms = norm(356.047 + 0.9856002585 * d), ws = 282.9404 + 4.70935e-5 * d;
    const Lm = N + w + M, Ls = Ms + ws, Dm = Lm - Ls, F = Lm - N;
    lon += -1.274 * sin(M - 2 * Dm) + 0.658 * sin(2 * Dm) - 0.186 * sin(Ms) - 0.059 * sin(2 * M - 2 * Dm)
      - 0.057 * sin(M - 2 * Dm + Ms) + 0.053 * sin(M + 2 * Dm) + 0.046 * sin(2 * Dm - Ms) + 0.041 * sin(M - Ms)
      - 0.035 * sin(Dm) - 0.031 * sin(M + Ms) - 0.015 * sin(2 * F - 2 * Dm) + 0.011 * sin(M - 4 * Dm);
    lat += -0.173 * sin(F - 2 * Dm) - 0.055 * sin(M - F - 2 * Dm) - 0.046 * sin(M + F - 2 * Dm) + 0.033 * sin(F + 2 * Dm) + 0.017 * sin(2 * M + F);
    r += -0.58 * cos(M - 2 * Dm) - 0.46 * cos(2 * Dm);
    return { lon: norm(lon), lat, r, eps: 23.4393 - 3.563e-7 * d };
  }
  function moon(ms, lat, lon) {
    const m = moonEcl(ms);
    const { ra, dec } = eclToEqu(m.lon, m.lat, m.eps);
    const h = equToHor(ms, ra, dec, lat, lon);
    const par = Math.asin(1 / m.r) * D;
    return { alt: h.alt - par * cos(h.alt), az: h.az };
  }
  function phase(ms) {
    const m = moonEcl(ms), s = sunEcl(ms);
    const el = norm(m.lon - s.lam);
    const cosE = cos(m.lat) * cos(el);
    const illum = (1 - cosE) / 2;
    const age = (el / 360) * 29.530589;
    const names = ['Luna nueva', 'Luna creciente', 'Cuarto creciente', 'Gibosa creciente', 'Luna llena', 'Gibosa menguante', 'Cuarto menguante', 'Luna menguante'];
    return { illum, age, waxing: el < 180, elong: el, name: names[Math.floor(norm(el + 22.5) / 45) % 8] };
  }
  /* Spain: Europe/Madrid (UTC+1) or Atlantic/Canary (UTC+0), EU summer time. */
  function lastSunday(y, m) { const d = new Date(Date.UTC(y, m + 1, 0)); return Date.UTC(y, m, d.getUTCDate() - d.getUTCDay(), 1); }
  function tzOffset(ms, base) { const y = new Date(ms).getUTCFullYear(); return base + (ms >= lastSunday(y, 2) && ms < lastSunday(y, 9) ? 1 : 0); }
  /* Local-midnight UTC ms for a civil date in a place. */
  function dayStart(y, mo, d, base) { const guess = Date.UTC(y, mo, d) - base * 3600000; return Date.UTC(y, mo, d) - tzOffset(guess, base) * 3600000; }
  function crossings(start, f, h0, step = 60000) {
    const out = []; let prev = f(start) - h0;
    for (let t = start + step; t <= start + 86400000; t += step) {
      const cur = f(t) - h0;
      if ((prev < 0) !== (cur < 0)) out.push({ t: t - step * (cur / (cur - prev)), rising: cur > 0 });
      prev = cur;
    }
    return out;
  }
  function day(place, y, mo, d) {
    const base = place.tz, start = dayStart(y, mo, d, base);
    const S = (t) => sun(t, place.lat, place.lon).alt, Mf = (t) => moon(t, place.lat, place.lon).alt;
    const up = (list, rising) => (list.find((c) => c.rising === rising) || {}).t;
    const at = (h) => crossings(start, S, h);
    const r = {};
    [['astro', -18], ['nautical', -12], ['civil', -6], ['blue', -4], ['set', -0.833], ['golden', 6]].forEach(([k, h]) => {
      const c = at(h); r[k + 'Am'] = up(c, true); r[k + 'Pm'] = up(c, false);
    });
    let best = start, bestAlt = -99;
    for (let t = start; t <= start + 86400000; t += 60000) { const a = S(t); if (a > bestAlt) { bestAlt = a; best = t; } }
    r.noon = best; r.noonAlt = bestAlt;
    const mc = crossings(start, Mf, -0.833);
    r.moonrise = up(mc, true); r.moonset = up(mc, false);
    r.riseAz = r.setAm && sun(r.setAm, place.lat, place.lon).az;
    r.setAz = r.setPm && sun(r.setPm, place.lat, place.lon).az;
    r.moonriseAz = r.moonrise && moon(r.moonrise, place.lat, place.lon).az;
    r.moonsetAz = r.moonset && moon(r.moonset, place.lat, place.lon).az;
    r.start = start; r.tz = (t) => tzOffset(t, base);
    return r;
  }
  function fmt(ms, base) {
    if (ms == null || isNaN(ms)) return '—';
    const t = new Date(ms + tzOffset(ms, base) * 3600000);
    return String(t.getUTCHours()).padStart(2, '0') + ':' + String(t.getUTCMinutes()).padStart(2, '0');
  }
  return { sun, moon, phase, day, fmt, tzOffset, dayStart };
})();
if (typeof module !== 'undefined') module.exports = Astro;
