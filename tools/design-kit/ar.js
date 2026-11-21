/* Cenit stage 2 (AR camera): real 3D projection, drawn Valencia dusk/day/night scene,
   path-crossing detection, edge arrows, synced flat dome, and key states.
   All hex values come strictly from design/system.md. */

Object.assign(S, {
  heading: 239,
  pitch: 5,
  scene: 'dusk', /* 'dusk' | 'bright' | 'night' */
  layers: { sun: true, moon: true, solstices: true },
});

Object.assign(ICON, {
  camara: '<rect x="3" y="6.5" width="18" height="13" rx="2.5"/><path d="M8.5 6.5L10 4h4l1.5 2.5"/><circle cx="12" cy="13" r="3.2"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  camOff: '<path d="M3 3l18 18"/><path d="M9 5h5l1.5 2H19a2 2 0 0 1 2 2v7.5"/><path d="M17.5 18.5H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.5"/><path d="M9.5 11.5a3 3 0 0 0 4 3.8"/>',
  figure8: '<path d="M7.5 15.5c-2.5 0-4-1.6-4-3.5s1.5-3.5 4-3.5c3 0 6 7 9 7 2.5 0 4-1.6 4-3.5s-1.5-3.5-4-3.5c-3 0-6 7-9 7z"/>',
  sensorOff: '<circle cx="12" cy="12" r="9"/><path d="M4.5 4.5l15 15"/><path d="M14.5 9.5l-1.5 4-3.5 1.5"/>',
  ground: '<path d="M12 19V5M7 10l5-5 5 5"/><path d="M4 20h16"/>',
});

function tabBar5(active) {
  const tabs = [
    ['hoy', 'Hoy'],
    ['cielo', 'Cielo'],
    ['brujula', 'Brújula'],
    ['camara', 'Cámara'],
    ['lugares', 'Lugares'],
  ];
  return `<nav class="tabbar tabs-5" aria-label="Secciones">${tabs.map(([id, l]) =>
    `<a href="#" class="tab${id === active ? ' on' : ''}"${id === active ? ' aria-current="page"' : ''}>${icon(id, 20)}<span>${l}</span></a>`
  ).join('')}</nav>`;
}

const camStatus = () => `<div class="cam-status" aria-hidden="true"><span>${hhmm(S.min)}</span><span class="status-icons"><i></i><i></i><b></b></span></div>`;

/* ---------- 3D spherical -> screen perspective projection ---------- */
function arProject(azDeg, altDeg, cam, w, h) {
  const R = Math.PI / 180;
  const az = azDeg * R, alt = altDeg * R;
  const az0 = cam.heading * R, alt0 = cam.pitch * R;
  const cy0 = cam.cy != null ? cam.cy : h / 2;
  const vx = Math.cos(alt) * Math.sin(az);
  const vy = Math.cos(alt) * Math.cos(az);
  const vz = Math.sin(alt);

  const fx = Math.cos(alt0) * Math.sin(az0);
  const fy = Math.cos(alt0) * Math.cos(az0);
  const fz = Math.sin(alt0);

  const rx = Math.cos(az0), ry = -Math.sin(az0), rz = 0;
  const ux = -Math.sin(alt0) * Math.sin(az0);
  const uy = -Math.sin(alt0) * Math.cos(az0);
  const uz = Math.cos(alt0);

  const cx = vx * rx + vy * ry + vz * rz;
  const cy = vx * ux + vy * uy + vz * uz;
  const cz = vx * fx + vy * fy + vz * fz;

  const f = (w / 2) / Math.tan(((cam.fovH || 64) * R) / 2);
  if (cz <= 0.14) return { vis: false, cx, cy, cz, x: w / 2 + cx * w, y: cy0 - cy * h };
  const x = w / 2 + f * (cx / cz);
  const y = cy0 - f * (cy / cz);
  const inBox = x >= -24 && x <= w + 24 && y >= -24 && y <= h + 24;
  return { vis: true, inBox, x, y, cx, cy, cz };
}

/* ---------- Crosshair crossing detection (real astro.js samples) ---------- */
function angDiff(a, b) { return ((a - b + 540) % 360) - 180; }
function angularDist(az1, alt1, az2, alt2) {
  const dAz = angDiff(az1, az2) * Math.cos(((alt1 + alt2) / 2) * Math.PI / 180);
  const dAlt = alt1 - alt2;
  return Math.hypot(dAz, dAlt);
}

function nearestOnTrack(samples, az0, alt0) {
  let best = null;
  for (let i = 0; i < samples.length; i++) {
    const [m, pos] = samples[i];
    const dist = angularDist(pos.az, pos.alt, az0, alt0);
    if (!best || dist < best.dist) best = { m, pos, dist, idx: i };
  }
  /* Refine linearly between adjacent 5-min samples for minute-accurate crossing */
  if (best && best.idx > 0 && best.idx < samples.length - 1) {
    const neighbors = [best.idx - 1, best.idx + 1];
    neighbors.forEach((j) => {
      const [m1, p1] = samples[best.idx], [m2, p2] = samples[j];
      for (let k = 1; k < 5; k++) {
        const t = k / 5;
        const az = p1.az + angDiff(p2.az, p1.az) * t;
        const alt = p1.alt + (p2.alt - p1.alt) * t;
        const dist = angularDist(az, alt, az0, alt0);
        if (dist < best.dist) best = { m: m1 + (m2 - m1) * t, pos: { az, alt }, dist, idx: best.idx };
      }
    });
  }
  return best;
}

function crosshairHit(heading, pitch, layers = S.layers) {
  if (pitch <= -28) {
    return { kind: 'ground', text: 'Apuntando al suelo · eleva hacia el horizonte', short: 'Eleva hacia el horizonte' };
  }
  const d = day();
  const sunSamples = d.samples.map((s) => [s[0], s[1]]);
  const bestSun = nearestOnTrack(sunSamples, heading, pitch);
  if (layers.sun && bestSun && bestSun.dist <= 4.0) {
    const ph = skyPhase(bestSun.pos.alt);
    const tStr = hhmm(bestSun.m);
    if (bestSun.pos.alt < -0.833) {
      return { kind: 'sun-below', time: tStr, text: `Sol bajo el horizonte aquí a las ${tStr}`, short: `Sol ${tStr} · bajo horiz.` };
    }
    return {
      kind: 'sun',
      time: tStr,
      phase: ph.name.toLowerCase(),
      text: `El sol pasa por aquí a las ${tStr} · ${ph.name.toLowerCase()}`,
      short: `Sol ${tStr} · ${ph.name.toLowerCase()}`,
    };
  }
  if (layers.moon) {
    const moonSamples = d.samples.map((s) => [s[0], s[2]]);
    const bestMoon = nearestOnTrack(moonSamples, heading, pitch);
    if (bestMoon && bestMoon.dist <= 4.0 && bestMoon.pos.alt > -0.833) {
      const tStr = hhmm(bestMoon.m);
      const ill = Math.round(phaseNow().illum * 100);
      return {
        kind: 'moon',
        time: tStr,
        text: `La luna pasa por aquí a las ${tStr} · ${ill} %`,
        short: `Luna ${tStr} · ${ill} %`,
      };
    }
  }
  if (layers.solstices) {
    const rp = refPaths();
    const checks = [
      ['Solsticio jun.', rp.summer],
      ['Equinoccio', rp.equinox],
      ['Solsticio dic.', rp.winter],
    ];
    for (const [label, pts] of checks) {
      const mapped = pts.map((q, idx) => [idx * 10, q]);
      const b = nearestOnTrack(mapped, heading, pitch);
      if (b && b.dist <= 3.5 && b.pos.alt > -0.833) {
        const tStr = hhmm(b.m);
        return { kind: 'solstice', time: tStr, text: `${label} pasa aquí a las ${tStr}`, short: `${label} · ${tStr}` };
      }
    }
  }
  if (layers.sun && bestSun && bestSun.dist <= 13 && bestSun.pos.alt > -1) {
    const dir = bestSun.pos.alt > pitch ? 'arriba' : 'abajo';
    const dAlt = Math.max(1, Math.round(Math.abs(bestSun.pos.alt - pitch)));
    return {
      kind: 'near',
      text: `Trayectoria del sol a ${dAlt}° ${dir} · ${hhmm(bestSun.m)}`,
      short: `Sol ${dAlt}° ${dir} (${hhmm(bestSun.m)})`,
    };
  }
  return { kind: 'none', text: 'El sol no cruza este punto hoy', short: 'Sin cruce hoy' };
}

/* ---------- Drawn camera placeholder scene (Valencia rooftops / sea horizon) ----------
   Strictly uses design/system.md hexes (no photos, no external URLs). */
function camBackdrop(cam, w, h, scene = S.scene, sunPos = sunNow()) {
  const uid = 'sc' + (++_uid);
  const hzP = arProject(cam.heading, 0, cam, w, h);
  const hy = hzP.vis ? hzP.y : (cam.pitch > 0 ? h + 220 : -220);
  const hyClamped = Math.max(-60, Math.min(h + 80, hy));

  /* Sky & water palettes using ONLY design/system.md hex values */
  const pal = scene === 'bright'
    ? {
        sky: [['0%', '#AFBDE3'], ['36%', '#EEEBE4'], ['76%', '#FAF9F6'], ['100%', '#F3F1EC']],
        sea: [['0%', '#DCD7CB'], ['55%', '#857F74'], ['100%', '#5C5A54']],
        hill: '#857F74', roofFar: '#5C5A54', roofNear: '#2C2C2C', glow: '#FAF9F6', cloud: '#FAF9F6',
      }
    : scene === 'night'
    ? {
        sky: [['0%', '#111318'], ['45%', '#161920'], ['80%', '#1B1F29'], ['100%', '#212532']],
        sea: [['0%', '#1B1F29'], ['55%', '#161920'], ['100%', '#111318']],
        hill: '#1F232C', roofFar: '#171A21', roofNear: '#111318', glow: '#7F93D0', cloud: '#272B35',
      }
    : { /* golden hour dusk */
        sky: [['0%', '#1B1F29'], ['26%', '#212532'], ['52%', '#3D5896'], ['76%', '#7F93D0'], ['91%', '#D6C07E'], ['100%', '#F3F1EC']],
        sea: [['0%', '#272C38'], ['48%', '#1F232C'], ['100%', '#111318']],
        hill: '#272B35', roofFar: '#1F232C', roofNear: '#171A21', glow: '#D6C07E', cloud: '#E9E7E2',
      };

  const stops = (arr) => arr.map(([o, c]) => `<stop offset="${o}" stop-color="${c}"/>`).join('');

  /* Project angular panorama features around the horizon so panning moves Valencia's skyline */
  const px = (az) => {
    const d = angDiff(az, cam.heading);
    const f = (w / 2) / Math.tan(((cam.fovH || 64) * Math.PI / 180) / 2);
    return w / 2 + f * Math.tan(d * Math.PI / 180);
  };

  /* Distant ridge curve anchored in azimuth (180..320 W/SW hills, flat sea E) */
  let ridgePts = [];
  for (let i = -2; i <= 34; i++) {
    const x = (i / 32) * w;
    const az = ((cam.heading + ((x - w / 2) / w) * (cam.fovH || 64)) % 360 + 360) % 360;
    /* Coastal hills to West/North-West, flat Mediterranean sea to East */
    const landFactor = Math.max(0, Math.sin(((az - 165) * Math.PI) / 180));
    const bump = landFactor * (10 + 7 * Math.sin(az * 0.14) + 5 * Math.cos(az * 0.29));
    ridgePts.push(`${x.toFixed(1)},${(hyClamped - bump).toFixed(1)}`);
  }
  const ridgePath = `M-10,${(hyClamped + 8).toFixed(1)} L${ridgePts.join(' L')} L${w + 10},${(hyClamped + 8).toFixed(1)} Z`;

  /* Iconic Valencia silhouettes anchored at real compass bearings */
  let skyline = '';
  const drawIfVis = (az, fn) => {
    const d = angDiff(az, cam.heading);
    if (Math.abs(d) > (cam.fovH || 64) * 0.8) return;
    skyline += fn(px(az), hyClamped);
  };
  /* Micalet bell tower + cathedral dome near 232° */
  drawIfVis(231, (x, y) => `
    <g fill="${pal.roofFar}" opacity="0.92">
      <rect x="${(x - 10).toFixed(1)}" y="${(y - 34).toFixed(1)}" width="14" height="36" rx="1"/>
      <rect x="${(x - 12).toFixed(1)}" y="${(y - 37).toFixed(1)}" width="18" height="4"/>
      <rect x="${(x - 5).toFixed(1)}" y="${(y - 44).toFixed(1)}" width="4" height="8"/>
      <path d="M${(x + 8).toFixed(1)},${y.toFixed(1)} v-15 a11,10 0 0 1 22,0 v15 z"/>
    </g>`);
  /* Mediterranean rooftops cluster around 218°..252° */
  drawIfVis(220, (x, y) => `
    <g fill="${pal.roofNear}">
      <path d="M${(x - 42).toFixed(1)},${(y + 18).toFixed(1)} v-24 l14,-7 l14,7 v24 z"/>
      <path d="M${(x - 16).toFixed(1)},${(y + 18).toFixed(1)} v-30 h24 v30 z"/>
      <path d="M${(x + 10).toFixed(1)},${(y + 18).toFixed(1)} v-20 l16,-6 l14,6 v20 z"/>
    </g>`);
  drawIfVis(254, (x, y) => `
    <g fill="${pal.roofNear}">
      <path d="M${(x - 28).toFixed(1)},${(y + 22).toFixed(1)} v-22 l18,-8 l18,8 v22 z"/>
      <rect x="${(x + 10).toFixed(1)}" y="${(y - 14).toFixed(1)}" width="34" height="36"/>
      <path d="M${(x - 42).toFixed(1)},${(y + 22).toFixed(1)} q2,-26 8,-44" fill="none" stroke="${pal.roofNear}" stroke-width="3"/>
      <path d="M${(x - 34).toFixed(1)},${(y - 22).toFixed(1)} q-12,-4 -18,4 m18,-4 q-10,-10 -16,-6 m16,6 q10,-8 18,-2 m-18,2 q12,-2 17,6" fill="none" stroke="${pal.roofNear}" stroke-width="2" stroke-linecap="round"/>
    </g>`);
  /* Albufera shoreline / jetty on east-southeast */
  drawIfVis(115, (x, y) => `
    <g fill="${pal.roofNear}" opacity="0.88">
      <rect x="${(x - 36).toFixed(1)}" y="${(y + 4).toFixed(1)}" width="72" height="5" rx="1"/>
      <rect x="${(x + 18).toFixed(1)}" y="${(y - 18).toFixed(1)}" width="6" height="23"/>
      <rect x="${(x + 16).toFixed(1)}" y="${(y - 21).toFixed(1)}" width="10" height="4"/>
    </g>`);

  /* Foreground low rooftop sill when tilting downward */
  const groundFill = `<rect x="0" y="${(hyClamped + 16).toFixed(1)}" width="${w}" height="${Math.max(0, h - hyClamped + 80).toFixed(1)}" fill="url(#${uid}-sea)"/>`;

  /* Atmospheric sun glare & water reflection when sun is above -3° */
  const sp = arProject(sunPos.az, Math.max(-2, sunPos.alt), cam, w, h);
  let sunGlow = '';
  if (sp.vis && sunPos.alt > -4 && scene !== 'night') {
    const gx = Math.max(-60, Math.min(w + 60, sp.x));
    const gy = Math.max(-40, Math.min(hyClamped + 10, sp.y));
    sunGlow = `<circle cx="${gx.toFixed(1)}" cy="${gy.toFixed(1)}" r="${Math.round(w * 0.34)}" fill="url(#${uid}-glow)" opacity="${scene === 'bright' ? '0.65' : '0.45'}"/>`;
    if (hyClamped < h - 14) {
      sunGlow += `<g opacity="0.28" stroke="${pal.glow}" stroke-linecap="round">
        <line x1="${(gx - 28).toFixed(1)}" y1="${(hyClamped + 16).toFixed(1)}" x2="${(gx + 28).toFixed(1)}" y2="${(hyClamped + 16).toFixed(1)}" stroke-width="2"/>
        <line x1="${(gx - 18).toFixed(1)}" y1="${(hyClamped + 28).toFixed(1)}" x2="${(gx + 18).toFixed(1)}" y2="${(hyClamped + 28).toFixed(1)}" stroke-width="1.5"/>
        <line x1="${(gx - 10).toFixed(1)}" y1="${(hyClamped + 42).toFixed(1)}" x2="${(gx + 10).toFixed(1)}" y2="${(hyClamped + 42).toFixed(1)}" stroke-width="1"/>
      </g>`;
    }
  }

  /* Subtle clouds */
  const cShift = (angDiff(239, cam.heading) / 64) * w * 0.35;
  const clouds = hyClamped > 40 ? `
    <g fill="${pal.cloud}" opacity="${scene === 'bright' ? '0.42' : scene === 'night' ? '0.18' : '0.14'}">
      <rect x="${(w * 0.1 + cShift).toFixed(1)}" y="${(hyClamped * 0.36).toFixed(1)}" width="${(w * 0.32).toFixed(1)}" height="8" rx="4"/>
      <rect x="${(w * 0.56 + cShift * 0.7).toFixed(1)}" y="${(hyClamped * 0.24).toFixed(1)}" width="${(w * 0.26).toFixed(1)}" height="6" rx="3"/>
      <rect x="${(w * 0.3 + cShift * 0.5).toFixed(1)}" y="${(hyClamped * 0.62).toFixed(1)}" width="${(w * 0.38).toFixed(1)}" height="5" rx="2.5"/>
    </g>` : '';

  /* Subtle viewfinder corner brackets so placeholder nature is unmistakable */
  const brk = `
    <g stroke="#E9E7E2" stroke-opacity="0.22" stroke-width="1" fill="none">
      <path d="M12,26 v-14 h14 M${w - 26},12 h14 v14 M12,${h - 26} v14 h14 M${w - 26},${h - 12} h14 v-14"/>
    </g>`;

  return `
  <defs>
    <linearGradient id="${uid}-sky" x1="0" y1="0" x2="0" y2="1">${stops(pal.sky)}</linearGradient>
    <linearGradient id="${uid}-sea" x1="0" y1="0" x2="0" y2="1">${stops(pal.sea)}</linearGradient>
    <radialGradient id="${uid}-glow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="${pal.glow}" stop-opacity="0.9"/>
      <stop offset="55%" stop-color="${pal.glow}" stop-opacity="0.25"/>
      <stop offset="100%" stop-color="${pal.glow}" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect x="0" y="0" width="${w}" height="${h}" fill="url(#${uid}-sky)"/>
  ${clouds}
  ${sunGlow}
  ${groundFill}
  <rect x="0" y="${hyClamped.toFixed(1)}" width="${w}" height="18" fill="url(#${uid}-sea)"/>
  <path d="${ridgePath}" fill="${pal.hill}" opacity="0.88"/>
  ${skyline}
  <rect x="0" y="${(hyClamped + 18).toFixed(1)}" width="${w}" height="${Math.max(0, h - hyClamped).toFixed(1)}" fill="${pal.roofNear}" opacity="${cam.pitch < -15 ? '0.68' : '0.32'}"/>
  ${brk}`;
}

/* ---------- Outlined path helper (guaranteed ≥ 3:1 contrast on bright & dark sky) ---------- */
function strokedPath(pts, coreColor, width, dash = '', opacity = 1) {
  if (pts.length < 2) return '';
  const d = 'M' + pts.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' L');
  const dashAttr = dash ? ` stroke-dasharray="${dash}"` : '';
  const opAttr = opacity < 1 ? ` opacity="${opacity}"` : '';
  return `<g${opAttr} fill="none" stroke-linecap="round" stroke-linejoin="round">
    <path d="${d}" stroke="#111318" stroke-width="${(width + 3).toFixed(1)}"${dashAttr}/>
    <path d="${d}" stroke="${coreColor}" stroke-width="${width.toFixed(1)}"${dashAttr}/>
  </g>`;
}

function splitProjectedRuns(samples, cam, w, h, pred = () => true) {
  const runs = [];
  let cur = [];
  for (const q of samples) {
    const p = arProject(q.az, q.alt, cam, w, h);
    if (pred(q) && p.vis && p.x > -80 && p.x < w + 80 && p.y > -80 && p.y < h + 80) {
      if (cur.length && Math.hypot(p.x - cur[cur.length - 1].x, p.y - cur[cur.length - 1].y) > w * 0.65) {
        if (cur.length > 1) runs.push(cur);
        cur = [p];
      } else cur.push(p);
    } else if (cur.length) {
      if (cur.length > 1) runs.push(cur);
      cur = [];
    }
  }
  if (cur.length > 1) runs.push(cur);
  return runs;
}

/* ---------- Bounding-box collision helpers so HUD pills & labels never collide ---------- */
function rectHits(r, list, pad = 4) {
  return list.some((b) => !(r.x + r.w + pad <= b.x || b.x + b.w + pad <= r.x || r.y + r.h + pad <= b.y || b.y + b.h + pad <= r.y));
}

/* ---------- Scrim pill helper inside SVG ---------- */
function pillWidth(text, fontSize = 11) {
  return Math.max(36, text.length * (fontSize * 0.59) + 18);
}
function svgPill(x, y, text, borderColor = '#6C7382', textColor = '#E9E7E2', align = 'middle', fontSize = 11) {
  const h = 21;
  const estW = pillWidth(text, fontSize);
  let rx = align === 'middle' ? x - estW / 2 : align === 'end' ? x - estW : x;
  const ry = y - h / 2;
  return `<g>
    <rect x="${rx.toFixed(1)}" y="${ry.toFixed(1)}" width="${estW.toFixed(1)}" height="${h}" rx="10.5" fill="#111318" fill-opacity="0.88" stroke="${borderColor}" stroke-width="1"/>
    <text x="${(rx + estW / 2).toFixed(1)}" y="${(y + 4).toFixed(1)}" text-anchor="middle" style="font:500 ${fontSize}px var(--font-sans);fill:${textColor}">${text}</text>
  </g>`;
}

/* ---------- Edge arrow when sun is outside FOV or below horizon ---------- */
function edgeArrowSvg(sunPos, cam, w, h, pad = { t: 88, b: 84, x: 14 }, blocked = []) {
  const p = arProject(sunPos.az, sunPos.alt, cam, w, h);
  const inside = p.vis && p.x >= pad.x + 12 && p.x <= w - pad.x - 12 && p.y >= pad.t + 10 && p.y <= h - pad.b - 10;
  if (inside && sunPos.alt > -0.833) return '';

  const dAz = angDiff(sunPos.az, cam.heading);
  const below = sunPos.alt <= -0.833;
  const dx = p.cx, dy = -p.cy;
  const ang = Math.atan2(dy, dx);
  const rot = (ang * 180) / Math.PI;

  const dirTxt = Math.abs(dAz) < 6 ? '' : `${Math.round(Math.abs(dAz))}° ${dAz < 0 ? 'izq.' : 'der.'}`;
  const label = below
    ? `Sol bajo horiz. · ${dirTxt || deg(sunPos.alt, 1)}`
    : `Sol · ${dirTxt || deg(sunPos.alt, 0)}`;
  const pillW = Math.min(w - 24, Math.max(118, label.length * 6.3 + 30));
  const pillH = 26;

  /* Anchor cleanly to the perimeter edge in the direction of the sun */
  const cy0 = cam.cy != null ? cam.cy : h / 2;
  let px = dx >= 0 ? w - pillW - pad.x : pad.x;
  let py = cy0 + Math.sin(ang) * ((h - pad.t - pad.b) * 0.42) - pillH / 2;
  if (below && Math.abs(Math.sin(ang)) < 0.35) py = Math.max(py, cy0 + 46);
  py = Math.max(pad.t + 4, Math.min(h - pad.b - pillH - 4, py));

  /* Avoid colliding with center reticle / crossing pill */
  const candYs = [py, cy0 + 52, cy0 - 78, h - pad.b - pillH - 6, pad.t + 6];
  for (const ty of candYs) {
    const clampedY = Math.max(pad.t + 4, Math.min(h - pad.b - pillH - 4, ty));
    if (!rectHits({ x: px, y: clampedY, w: pillW, h: pillH }, blocked, 6)) { py = clampedY; break; }
  }
  blocked.push({ x: px, y: py, w: pillW, h: pillH });

  return `<g aria-label="${label}">
    <rect x="${px.toFixed(1)}" y="${py.toFixed(1)}" width="${pillW.toFixed(1)}" height="${pillH}" rx="13" fill="#111318" fill-opacity="0.92" stroke="#D6C07E" stroke-width="1.2"/>
    <g transform="translate(${(px + 14).toFixed(1)},${(py + 13).toFixed(1)}) rotate(${rot.toFixed(1)})">
      <path d="M-5,0 H4 M1,-3.5 L5,0 L1,3.5" fill="none" stroke="#D6C07E" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </g>
    <text x="${(px + 25).toFixed(1)}" y="${(py + 17).toFixed(1)}" style="font:500 11px var(--font-sans);fill:#E9E7E2">${label}</text>
  </g>`;
}

/* ---------- Full AR overlay SVG ---------- */
function arOverlay({
  w = 360,
  h = 460,
  cy = null,
  heading = S.heading,
  pitch = S.pitch,
  min = S.min,
  fovH = 64,
  scene = S.scene,
  layers = S.layers,
  calibrated = true,
  reticle = 'standard', /* 'standard' | 'hero' | 'compact' */
  quietPaths = false,
  showCrossingPill = true,
  edgePad = { t: 88, b: 84, x: 14 },
  ariaLabel = 'Vista de cámara con trayectorias solares y lunares',
} = {}) {
  const d = day();
  const tMs = d.start + min * 60000;
  const sNow = Astro.sun(tMs, S.place.lat, S.place.lon);
  const mNow = Astro.moon(tMs, S.place.lat, S.place.lon);
  const phNow = Astro.phase(tMs);
  const cy0 = cy != null ? cy : h / 2;
  const cx0 = w / 2;
  const cam = { heading, pitch, fovH, cy: cy0 };
  const pathOpacity = calibrated ? 1 : 0.45;
  const sunW = quietPaths ? 1.8 : 2.6;
  const moonW = quietPaths ? 1.5 : 2.1;

  /* Track occupied screen boxes so pills and labels never overlap */
  const blocked = [];

  /* Reserve reticle & crossing pill rectangles first (highest visual priority) */
  const hit = crosshairHit(heading, pitch, layers);
  const hitCol = hit.kind === 'sun' ? '#D6C07E' : hit.kind === 'moon' ? '#AFBDE3' : '#E9E7E2';
  const pitchStr = (pitch >= 0 ? '+' : '') + deg(pitch);
  const aimStr = `${deg(heading)} ${wind(heading)} · ${pitchStr}`;
  let retSvg = '';

  if (reticle === 'hero') {
    const R = 33;
    let ticks = '';
    for (let a = 0; a < 360; a += 30) {
      const rad = (a * Math.PI) / 180;
      const r1 = R - (a % 90 === 0 ? 7 : 4), r2 = R + 2;
      ticks += `<line x1="${(cx0 + r1 * Math.cos(rad)).toFixed(1)}" y1="${(cy0 + r1 * Math.sin(rad)).toFixed(1)}" x2="${(cx0 + r2 * Math.cos(rad)).toFixed(1)}" y2="${(cy0 + r2 * Math.sin(rad)).toFixed(1)}"/>`;
    }
    const aimW = pillWidth(aimStr, 11);
    const aimY = cy0 - R - 19;
    blocked.push({ x: cx0 - R - 10, y: cy0 - R - 10, w: (R + 10) * 2, h: (R + 10) * 2 });
    blocked.push({ x: cx0 - aimW / 2, y: aimY - 11, w: aimW, h: 22 });
    retSvg = `
      <g fill="none" stroke-linecap="round">
        <g stroke="#111318" stroke-width="4.2">
          <circle cx="${cx0}" cy="${cy0}" r="${R}"/>
          <path d="M${cx0 - R - 9},${cy0} h13 M${cx0 + R - 4},${cy0} h13 M${cx0},${cy0 - R - 9} v13 M${cx0},${cy0 + R - 4} v13"/>
          ${ticks}
        </g>
        <g stroke="${hitCol}" stroke-width="1.6">
          <circle cx="${cx0}" cy="${cy0}" r="${R}"/>
          <path d="M${cx0 - R - 9},${cy0} h13 M${cx0 + R - 4},${cy0} h13 M${cx0},${cy0 - R - 9} v13 M${cx0},${cy0 + R - 4} v13"/>
          ${ticks}
        </g>
        <circle cx="${cx0}" cy="${cy0}" r="3" fill="${hitCol}" stroke="#111318" stroke-width="1.5"/>
      </g>
      ${svgPill(cx0, aimY, aimStr, '#6C7382', '#E9E7E2', 'middle', 11)}`;
    if (showCrossingPill) {
      const boxW = Math.min(w - 24, Math.max(196, hit.text.length * 6.3 + 22));
      const bx = cx0 - boxW / 2, by = cy0 + R + 12;
      blocked.push({ x: bx, y: by, w: boxW, h: 28 });
      retSvg += `<g>
        <rect x="${bx.toFixed(1)}" y="${by.toFixed(1)}" width="${boxW.toFixed(1)}" height="28" rx="14" fill="#111318" fill-opacity="0.90" stroke="${hitCol}" stroke-width="1.2"/>
        <text x="${cx0}" y="${(by + 18).toFixed(1)}" text-anchor="middle" style="font:500 11.5px var(--font-sans);fill:#E9E7E2">${hit.text}</text>
      </g>`;
    }
  } else if (reticle === 'compact') {
    const R = 14;
    blocked.push({ x: cx0 - 22, y: cy0 - 22, w: 44, h: 44 });
    retSvg = `
      <g fill="none" stroke-linecap="round">
        <g stroke="#111318" stroke-width="3.6">
          <circle cx="${cx0}" cy="${cy0}" r="${R}"/>
          <path d="M${cx0 - 21},${cy0} h8 M${cx0 + 13},${cy0} h8 M${cx0},${cy0 - 21} v8 M${cx0},${cy0 + 13} v8"/>
        </g>
        <g stroke="${hitCol}" stroke-width="1.4">
          <circle cx="${cx0}" cy="${cy0}" r="${R}"/>
          <path d="M${cx0 - 21},${cy0} h8 M${cx0 + 13},${cy0} h8 M${cx0},${cy0 - 21} v8 M${cx0},${cy0 + 13} v8"/>
        </g>
        <circle cx="${cx0}" cy="${cy0}" r="2" fill="${hitCol}"/>
      </g>`;
    if (showCrossingPill) {
      const cw = pillWidth(hit.short, 10.5);
      blocked.push({ x: cx0 - cw / 2, y: cy0 + 18, w: cw, h: 22 });
      retSvg += svgPill(cx0, cy0 + 29, hit.short, hitCol, '#E9E7E2', 'middle', 10.5);
    }
  } else {
    /* Standard (Direction 01 · Visor): subtle crosshair + crisp inline readout */
    const R = 18;
    const aimW = pillWidth(aimStr, 11);
    blocked.push({ x: cx0 - 26, y: cy0 - 26, w: 52, h: 52 });
    blocked.push({ x: cx0 - aimW / 2, y: cy0 - 46, w: aimW, h: 22 });
    retSvg = `
      <g fill="none" stroke-linecap="round">
        <g stroke="#111318" stroke-width="3.8">
          <circle cx="${cx0}" cy="${cy0}" r="${R}"/>
          <path d="M${cx0 - 26},${cy0} h9 M${cx0 + 17},${cy0} h9 M${cx0},${cy0 - 26} v9 M${cx0},${cy0 + 17} v9"/>
        </g>
        <g stroke="${hitCol}" stroke-width="1.5">
          <circle cx="${cx0}" cy="${cy0}" r="${R}"/>
          <path d="M${cx0 - 26},${cy0} h9 M${cx0 + 17},${cy0} h9 M${cx0},${cy0 - 26} v9 M${cx0},${cy0 + 17} v9"/>
        </g>
        <circle cx="${cx0}" cy="${cy0}" r="2.2" fill="${hitCol}"/>
      </g>
      ${svgPill(cx0, cy0 - 35, aimStr, '#6C7382', '#E9E7E2', 'middle', 11)}`;
    if (showCrossingPill) {
      const cw = pillWidth(hit.text, 11);
      blocked.push({ x: cx0 - cw / 2, y: cy0 + 26, w: cw, h: 22 });
      retSvg += svgPill(cx0, cy0 + 37, hit.text, hitCol, '#E9E7E2', 'middle', 11);
    }
  }

  /* Helper to place a pill near (tx, ty) trying clean candidate offsets until collision-free */
  function placePill(tx, ty, text, borderCol, textCol, fontSize = 10.5) {
    const pw = pillWidth(text, fontSize), ph = 21;
    const candidates = [
      [tx, ty + 20],
      [tx + pw * 0.45, ty + 22],
      [tx - pw * 0.45, ty + 22],
      [tx, ty + 44],
      [tx, ty + 62],
      [tx + pw * 0.55, ty - 18],
      [tx - pw * 0.55, ty - 18],
      [tx, ty - 22],
    ];
    for (const [cx, cy] of candidates) {
      const clampedX = Math.max(pw / 2 + 10, Math.min(w - pw / 2 - 10, cx));
      const clampedY = Math.max(edgePad.t + 10, Math.min(h - edgePad.b - 12, cy));
      const box = { x: clampedX - pw / 2, y: clampedY - ph / 2, w: pw, h: ph };
      if (!rectHits(box, blocked, 4)) {
        blocked.push(box);
        return svgPill(clampedX, clampedY, text, borderCol, textCol, 'middle', fontSize);
      }
    }
    /* Last resort: place cleanly below blocked region */
    const fx = Math.max(pw / 2 + 10, Math.min(w - pw / 2 - 10, tx));
    const fy = Math.min(h - edgePad.b - 12, ty + 64);
    blocked.push({ x: fx - pw / 2, y: fy - ph / 2, w: pw, h: ph });
    return svgPill(fx, fy, text, borderCol, textCol, 'middle', fontSize);
  }

  /* 1. Horizon line (0°) with azimuth ticks */
  const hzPts = [];
  for (let a = -55; a <= 55; a += 2.5) {
    const p = arProject(((heading + a) % 360 + 360) % 360, 0, cam, w, h);
    if (p.vis) hzPts.push(p);
  }
  let hzSvg = strokedPath(hzPts, '#A6ABB5', 1.1, '5 4', 0.9);
  for (let az = 0; az < 360; az += 15) {
    if (Math.abs(angDiff(az, heading)) > fovH * 0.65) continue;
    const p = arProject(az, 0, cam, w, h);
    if (!p.vis || p.x < 18 || p.x > w - 18 || p.y < 18 || p.y > h - 18) continue;
    const isCard = az % 45 === 0;
    hzSvg += `<line x1="${p.x.toFixed(1)}" y1="${(p.y - (isCard ? 6 : 3)).toFixed(1)}" x2="${p.x.toFixed(1)}" y2="${(p.y + (isCard ? 6 : 3)).toFixed(1)}" stroke="#111318" stroke-width="3"/>
      <line x1="${p.x.toFixed(1)}" y1="${(p.y - (isCard ? 6 : 3)).toFixed(1)}" x2="${p.x.toFixed(1)}" y2="${(p.y + (isCard ? 6 : 3)).toFixed(1)}" stroke="#E9E7E2" stroke-width="1.2"/>`;
    if (isCard) {
      const lb = { x: p.x - 14, y: p.y + 6, w: 28, h: 14 };
      if (!rectHits(lb, blocked, 2)) {
        blocked.push(lb);
        hzSvg += `<text x="${p.x.toFixed(1)}" y="${(p.y + 18).toFixed(1)}" text-anchor="middle" class="ar-txt">${WINDS[Math.round(az / 22.5) % 16]}</text>`;
      }
    }
  }

  /* 2. Current sun marker reserved before labels */
  const spNow = arProject(sNow.az, sNow.alt, cam, w, h);
  if (layers.sun && spNow.vis && spNow.x >= 12 && spNow.x <= w - 12 && spNow.y >= 16 && spNow.y <= h - 16) {
    blocked.push({ x: spNow.x - 14, y: spNow.y - 14, w: 28, h: 28 });
  }

  /* 3. Sun path (solid --sun above horizon, dotted below horizon) + hour dots + rise/set + sun now */
  let sunSvg = '';
  if (layers.sun) {
    const sPts = d.samples.map((q) => q[1]);
    splitProjectedRuns(sPts, cam, w, h, (q) => q.alt <= 0.2).forEach((r) => {
      sunSvg += strokedPath(r, '#A6ABB5', 1.4, '2 4', 0.72 * pathOpacity);
    });
    splitProjectedRuns(sPts, cam, w, h, (q) => q.alt > -0.5).forEach((r) => {
      sunSvg += strokedPath(r, '#D6C07E', sunW, '', pathOpacity);
    });

    /* Sunrise & sunset on horizon (collision-avoided) */
    [[d.riseAz, d.setAm, 'Amanecer'], [d.setAz, d.setPm, 'Atardecer']].forEach(([az, ms, lbl]) => {
      if (!az || !ms) return;
      const p = arProject(az, 0, cam, w, h);
      if (!p.vis || p.x < 24 || p.x > w - 24 || p.y < 24 || p.y > h - 24) return;
      sunSvg += `<circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="5.2" fill="#D6C07E" stroke="#111318" stroke-width="2"/>`;
      sunSvg += placePill(p.x, p.y, `${lbl} ${T(ms)}`, '#D6C07E', '#E9E7E2', 10.5);
    });

    /* Current sun marker ("now") with smart label placement (above or left) */
    if (spNow.vis && spNow.x >= 12 && spNow.x <= w - 12 && spNow.y >= 16 && spNow.y <= h - 16) {
      if (sNow.alt > -0.833) {
        const lbl = hhmm(min);
        const topBox = { x: spNow.x - 20, y: spNow.y - 28, w: 40, h: 16 };
        const leftBox = { x: spNow.x - 48, y: spNow.y - 7, w: 36, h: 14 };
        const useLeft = rectHits(topBox, blocked, 6) || (reticle === 'hero' && spNow.x < cx0);
        const lx = useLeft ? spNow.x - 14 : spNow.x;
        const ly = useLeft ? spNow.y + 4 : spNow.y - 14;
        const anchor = useLeft ? 'end' : 'middle';
        blocked.push(useLeft ? leftBox : topBox);
        sunSvg += `<circle cx="${spNow.x.toFixed(1)}" cy="${spNow.y.toFixed(1)}" r="12" fill="#D6C07E" fill-opacity="0.22" stroke="#111318" stroke-width="1"/>
          <circle cx="${spNow.x.toFixed(1)}" cy="${spNow.y.toFixed(1)}" r="7.5" fill="#D6C07E" stroke="#111318" stroke-width="2.2"/>
          <text x="${lx.toFixed(1)}" y="${ly.toFixed(1)}" text-anchor="${anchor}" class="ar-txt sun">${lbl}</text>`;
      } else {
        sunSvg += `<circle cx="${spNow.x.toFixed(1)}" cy="${spNow.y.toFixed(1)}" r="6.5" fill="#111318" stroke="#D6C07E" stroke-width="2" stroke-dasharray="3 2"/>
          <text x="${spNow.x.toFixed(1)}" y="${(spNow.y - 12).toFixed(1)}" text-anchor="middle" class="ar-txt sun">${hhmm(min)} · bajo horiz.</text>`;
      }
    }

    /* Hourly dots + labels avoiding all reserved boxes */
    d.samples.forEach(([m, q]) => {
      if (m % 60 || q.alt <= 1.2) return;
      const p = arProject(q.az, q.alt, cam, w, h);
      if (!p.vis || p.x < 16 || p.x > w - 16 || p.y < edgePad.t - 10 || p.y > h - edgePad.b + 10) return;
      const hr = m / 60;
      sunSvg += `<circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="4.2" fill="#111318" stroke="#D6C07E" stroke-width="1.8" opacity="${pathOpacity}"/>`;
      const lb = { x: p.x + 6, y: p.y - 18, w: 26, h: 14 };
      if (p.y > edgePad.t + 6 && p.y < h - edgePad.b - 4 && !rectHits(lb, blocked, 4)) {
        blocked.push(lb);
        sunSvg += `<text x="${(p.x + 8).toFixed(1)}" y="${(p.y - 8).toFixed(1)}" text-anchor="start" class="ar-txt sun">${String(hr).padStart(2, '0')}h</text>`;
      }
    });
  }

  /* 4. Moon path (dashed --moon) + moonset/rise + moon now */
  let moonSvg = '';
  if (layers.moon) {
    const mPts = d.samples.map((q) => q[2]);
    splitProjectedRuns(mPts, cam, w, h, (q) => q.alt > -1).forEach((r) => {
      moonSvg += strokedPath(r, '#AFBDE3', moonW, '6 4', pathOpacity);
    });
    d.samples.forEach(([m, , q]) => {
      if (m % 120 || q.alt <= 2) return;
      const p = arProject(q.az, q.alt, cam, w, h);
      if (!p.vis || p.x < 18 || p.x > w - 18 || p.y < edgePad.t || p.y > h - edgePad.b) return;
      moonSvg += `<circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="3.2" fill="#111318" stroke="#AFBDE3" stroke-width="1.5" opacity="${pathOpacity}"/>`;
    });
    [[d.moonriseAz, d.moonrise, 'Sale luna'], [d.moonsetAz, d.moonset, 'Puesta luna']].forEach(([az, ms, lbl]) => {
      if (!az || !ms) return;
      const p = arProject(az, 0, cam, w, h);
      if (!p.vis || p.x < 34 || p.x > w - 34 || p.y < 24 || p.y > h - 24) return;
      moonSvg += `<circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="4.5" fill="#111318" stroke="#AFBDE3" stroke-width="2"/>`;
      const lx = Math.max(46, Math.min(w - 50, p.x));
      const box = { x: lx - 44, y: p.y - 22, w: 88, h: 15 };
      if (!rectHits(box, blocked, 3)) {
        blocked.push(box);
        moonSvg += `<text x="${lx.toFixed(1)}" y="${(p.y - 10).toFixed(1)}" text-anchor="middle" class="ar-txt moon">${lbl} ${T(ms)}</text>`;
      }
    });
    const mp = arProject(mNow.az, mNow.alt, cam, w, h);
    if (mp.vis && mNow.alt > -0.833 && mp.x > 16 && mp.x < w - 16 && mp.y > 24 && mp.y < h - 24) {
      const r = 8, rx = Math.abs(1 - 2 * phNow.illum) * r;
      const md = `M${mp.x},${mp.y - r} A${r},${r} 0 0 ${phNow.waxing ? 1 : 0} ${mp.x},${mp.y + r} A${rx},${r} 0 0 ${(phNow.illum > 0.5) === phNow.waxing ? 1 : 0} ${mp.x},${mp.y - r}Z`;
      moonSvg += `<circle cx="${mp.x.toFixed(1)}" cy="${mp.y.toFixed(1)}" r="10.5" fill="#111318" stroke="#AFBDE3" stroke-width="1.5"/>
        <path d="${md}" fill="#AFBDE3"/>
        <text x="${mp.x.toFixed(1)}" y="${(mp.y + 21).toFixed(1)}" text-anchor="middle" class="ar-txt moon">Luna ${Math.round(phNow.illum * 100)} %</text>`;
    }
  }

  /* 5. Solstice & equinox reference paths (labels pick collision-free point) */
  let solSvg = '';
  if (layers.solstices) {
    const rp = refPaths();
    const specs = [
      [rp.summer, 'jun', '8 4'],
      [rp.equinox, 'eq', '2 4'],
      [rp.winter, 'dic', '8 4'],
    ];
    specs.forEach(([pts, label, dash]) => {
      const runs = splitProjectedRuns(pts, cam, w, h, (q) => q.alt > -2);
      runs.forEach((r) => { solSvg += strokedPath(r, '#A6ABB5', 1.1, dash, 0.78 * pathOpacity); });
      const cand = pts.map((q) => ({ q, p: arProject(q.az, q.alt, cam, w, h) }))
        .filter(({ q, p }) => q.alt > 4 && p.vis && p.x > 26 && p.x < w - 26 && p.y > edgePad.t + 10 && p.y < h - edgePad.b - 10);
      /* Prefer a point clear of all blocked boxes */
      const pick = cand.find(({ p }) => !rectHits({ x: p.x - 16, y: p.y - 18, w: 32, h: 16 }, blocked, 5));
      if (pick) {
        blocked.push({ x: pick.p.x - 16, y: pick.p.y - 18, w: 32, h: 16 });
        solSvg += `<text x="${pick.p.x.toFixed(1)}" y="${(pick.p.y - 7).toFixed(1)}" text-anchor="middle" class="ar-txt muted">${label}</text>`;
      }
    });
  }

  /* 6. Horizon label ("0° · Horiz.") placed on right or left depending on collision */
  const hzRight = arProject(((heading + fovH * 0.42) % 360 + 360) % 360, 0, cam, w, h);
  const hzLeft = arProject(((heading - fovH * 0.42) % 360 + 360) % 360, 0, cam, w, h);
  if (hzRight.vis && hzRight.y > edgePad.t && hzRight.y < h - edgePad.b) {
    const rBox = { x: w - 66, y: hzRight.y - 18, w: 58, h: 14 };
    const lBox = { x: 8, y: hzLeft.y - 18, w: 58, h: 14 };
    if (!rectHits(rBox, blocked, 3)) {
      blocked.push(rBox);
      hzSvg += `<text x="${(w - 10).toFixed(1)}" y="${(hzRight.y - 6).toFixed(1)}" text-anchor="end" class="ar-txt muted">0° · Horiz.</text>`;
    } else if (hzLeft.vis && !rectHits(lBox, blocked, 3)) {
      blocked.push(lBox);
      hzSvg += `<text x="10" y="${(hzLeft.y - 6).toFixed(1)}" text-anchor="start" class="ar-txt muted">0° · Horiz.</text>`;
    }
  }

  /* 7. Edge arrow if sun is out of frame or below horizon (suppressed when pointing at ground) */
  const edgeSvg = (layers.sun && pitch > -28) ? edgeArrowSvg(sNow, cam, w, h, edgePad, blocked) : '';

  /* 8. Ground upward arrow when pointing down */
  let groundSvg = '';
  if (pitch <= -28) {
    const upDeg = Math.round(Math.abs(pitch));
    groundSvg = `<g>
      <rect x="${(cx0 - 98).toFixed(1)}" y="${(edgePad.t - 6).toFixed(1)}" width="196" height="28" rx="14" fill="#111318" fill-opacity="0.90" stroke="#D6C07E" stroke-width="1.2"/>
      <text x="${cx0}" y="${(edgePad.t + 12).toFixed(1)}" text-anchor="middle" style="font:500 11px var(--font-sans);fill:#E9E7E2">↑ Horizonte ${upDeg}° arriba · eleva el móvil</text>
    </g>`;
  }

  return `<svg class="cam-svg" viewBox="0 0 ${w} ${h}" role="img" aria-label="${ariaLabel}">
    ${camBackdrop(cam, w, h, scene, sNow)}
    ${hzSvg}
    ${solSvg}
    ${moonSvg}
    ${sunSvg}
    ${retSvg}
    ${edgeSvg}
    ${groundSvg}
  </svg>`;
}

/* ---------- Thin translucent heading tape (Direction 01 · Visor) ---------- */
function arHeadingTape(w = 336, heading = S.heading) {
  const h = 38, span = 96;
  const d = day(), s = sunNow(), mo = moonNow();
  const X = (az) => w / 2 + (angDiff(az, heading) / span) * w;
  let ticks = '';
  for (let a = Math.floor((heading - 55) / 5) * 5; a <= heading + 55; a += 5) {
    const az = ((a % 360) + 360) % 360, x = X(az);
    if (x < 8 || x > w - 8) continue;
    const major = az % 15 === 0;
    ticks += `<line x1="${x.toFixed(1)}" x2="${x.toFixed(1)}" y1="6" y2="${major ? 14 : 10}" stroke="${major ? '#E9E7E2' : '#6C7382'}" stroke-width="1"/>`;
    if (major) {
      const lbl = az % 45 === 0 ? WINDS[az / 22.5] : String(az);
      ticks += `<text x="${x.toFixed(1)}" y="27" text-anchor="middle" style="font:${az % 90 === 0 ? '600' : '400'} 10.5px var(--font-sans);fill:${az % 90 === 0 ? '#E9E7E2' : '#A6ABB5'}">${lbl}</text>`;
    }
  }
  const dot = (az, col, hollow) => {
    const x = X(az); if (x < 10 || x > w - 10) return '';
    return `<circle cx="${x.toFixed(1)}" cy="9" r="3.2" fill="${hollow ? '#111318' : col}" stroke="${col}" stroke-width="1.5"/>`;
  };
  return `<svg width="100%" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="Cinta de rumbo: ${deg(heading)} ${wind(heading)}">
    <rect x="0.5" y="0.5" width="${w - 1}" height="${h - 1}" rx="12" fill="#111318" fill-opacity="0.84" stroke="#6C7382"/>
    ${ticks}
    ${S.layers.sun && s.alt > -1 ? dot(s.az, '#D6C07E', false) : ''}
    ${S.layers.moon && mo.alt > -1 ? dot(mo.az, '#AFBDE3', true) : ''}
    <path d="M${w / 2 - 5},37 L${w / 2 + 5},37 L${w / 2},30 Z" fill="#D6C07E"/>
    <rect x="${w / 2 - 24}" y="1" width="48" height="15" rx="7.5" fill="#111318" stroke="#D6C07E" stroke-width="1"/>
    <text x="${w / 2}" y="12" text-anchor="middle" style="font:600 10.5px var(--font-sans);fill:#E9E7E2">${deg(heading)}</text>
  </svg>`;
}

/* ---------- Interactive synced flat dome (Direction 03 · Cámara + cúpula) ---------- */
function arDome({ size = 212, heading = S.heading, fovH = 64, layers = S.layers } = {}) {
  const d = day(), c = size / 2, R = (c - 6) / (102 / 90), s = sunNow(), mo = moonNow();
  const rad = (alt) => (R * (90 - Math.max(-12, alt))) / 90;
  const P = (az, alt) => { const r = rad(alt), a = ((az - heading) * Math.PI) / 180; return [c + r * Math.sin(a), c - r * Math.cos(a)]; };
  const runs = (pts, pred) => { const out = []; let cur = []; pts.forEach((q) => { if (pred(q.alt)) cur.push(P(q.az, q.alt).map((v) => v.toFixed(1)).join(',')); else if (cur.length) { out.push(cur); cur = []; } }); if (cur.length) out.push(cur); return out; };
  const line = (pts, pred, style, dash) => runs(pts, pred).map((p) => `<polyline points="${p.join(' ')}" style="fill:none;${style}"${dash ? ` stroke-dasharray="${dash}"` : ''}/>`).join('');
  const sun = d.samples.map((q) => q[1]), moon = d.samples.map((q) => q[2]);
  const at = (az, r) => { const a = ((az - heading) * Math.PI) / 180; return [c + r * Math.sin(a), c - r * Math.cos(a)]; };

  let refSvg = '';
  if (layers.solstices) {
    const rp = refPaths();
    refSvg = line(rp.summer, (a) => a > 0, 'stroke:var(--text-secondary);stroke-width:1') +
      line(rp.winter, (a) => a > 0, 'stroke:var(--text-secondary);stroke-width:1') +
      line(rp.equinox, (a) => a > 0, 'stroke:var(--text-secondary);stroke-width:1', '2 3');
  }
  let dots = '';
  if (layers.sun) {
    d.samples.forEach(([m, q]) => {
      if (m % 60 || q.alt <= 0.5) return;
      const [x, y] = P(q.az, q.alt);
      dots += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="2.4" style="fill:var(--bg-color);stroke:var(--sun)" stroke-width="1.3"/>`;
    });
  }

  /* Camera FOV wedge pointing straight up (current heading) */
  const half = ((fovH / 2) * Math.PI) / 180;
  const x1 = c - R * Math.sin(half), y1 = c - R * Math.cos(half);
  const x2 = c + R * Math.sin(half), y2 = c - R * Math.cos(half);

  return `<svg class="dome" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" role="img" aria-label="Cúpula sincronizada al rumbo ${deg(heading)} ${wind(heading)}. Toca para orientar la cámara.">
    <circle cx="${c}" cy="${c}" r="${rad(-12).toFixed(1)}" style="fill:var(--sky-nautical);stroke:var(--border-color)"/>
    <circle cx="${c}" cy="${c}" r="${rad(-6).toFixed(1)}" style="fill:var(--sky-civil)"/>
    <circle cx="${c}" cy="${c}" r="${R.toFixed(1)}" style="fill:var(--sky-day);stroke:var(--control-border)"/>
    <circle cx="${c}" cy="${c}" r="${((R * 2) / 3).toFixed(1)}" style="fill:none;stroke:var(--border-color)"/>
    <circle cx="${c}" cy="${c}" r="${(R / 3).toFixed(1)}" style="fill:none;stroke:var(--border-color)"/>
    <path d="M${c},${c} L${x1.toFixed(1)},${y1.toFixed(1)} A${R.toFixed(1)},${R.toFixed(1)} 0 0 1 ${x2.toFixed(1)},${y2.toFixed(1)} Z" style="fill:var(--sun)" fill-opacity="0.16"/>
    <line x1="${c}" y1="${c}" x2="${x1.toFixed(1)}" y2="${y1.toFixed(1)}" style="stroke:var(--sun)" stroke-width="1" stroke-dasharray="3 3"/>
    <line x1="${c}" y1="${c}" x2="${x2.toFixed(1)}" y2="${y2.toFixed(1)}" style="stroke:var(--sun)" stroke-width="1" stroke-dasharray="3 3"/>
    ${[[0, 'N'], [90, 'E'], [180, 'S'], [270, 'O']].map(([az, l]) => { const [x, y] = at(az, R - 11); return `<text x="${x.toFixed(1)}" y="${(y + 4).toFixed(1)}" text-anchor="middle" class="svg-label${l === 'N' ? ' strong' : ''}" style="paint-order:stroke;stroke:var(--sky-day);stroke-width:3px">${l}</text>`; }).join('')}
    ${refSvg}
    ${layers.sun ? line(sun, (a) => a <= 0, 'stroke:var(--text-secondary);stroke-width:1.2', '1 4') : ''}
    ${layers.moon ? line(moon, (a) => a > -0.5, 'stroke:var(--moon);stroke-width:1.8', '4 3') : ''}
    ${layers.sun ? line(sun, (a) => a > -0.5, 'stroke:var(--sun);stroke-width:2.2') : ''}
    ${dots}
    ${layers.moon && mo.alt > 0 ? (() => { const [x, y] = P(mo.az, mo.alt); return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="5" style="fill:var(--bg-color);stroke:var(--moon)" stroke-width="1.8"/>`; })() : ''}
    ${layers.sun ? (() => { const [x, y] = P(s.az, s.alt); return s.alt > 0 ? `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="6.5" style="fill:var(--sun);stroke:var(--bg-color)" stroke-width="1.8"/>` : `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="5" style="fill:var(--bg-color);stroke:var(--sun)" stroke-width="1.8"/>`; })() : ''}
    <path d="M${c - 5},6 L${c + 5},6 L${c},14 Z" style="fill:var(--text-primary)"/>
    <circle cx="${c}" cy="${c}" r="2.2" style="fill:var(--text-secondary)"/>
  </svg>`;
}

/* ---------- Reusable secondary states (tailored per direction) ---------- */
function statePermission(dirHint) {
  return `<section class="mini-phone" aria-label="Estado: permiso de cámara denegado">
    <div class="mini-bar" aria-hidden="true"><span>16:20</span><span>Cámara</span></div>
    <div class="mini-state-body">
      <div class="mini-icon-ring">${icon('camOff', 22)}</div>
      <h3>Permiso de cámara denegado</h3>
      <p>Cenit usa la cámara solo en directo para dibujar las trayectorias del sol y la luna. Sin fotos ni internet.</p>
      <div style="display:flex;flex-direction:column;gap:8px;margin-top:4px">
        <button class="btn-primary" type="button">Abrir ajustes</button>
        <button class="btn-secondary" type="button">${dirHint}</button>
      </div>
    </div>
  </section>`;
}

function stateCalibration(reticleMode = 'standard') {
  return `<section class="mini-phone" aria-label="Estado: calibración de brújula necesaria">
    <div class="mini-bar on-cam" aria-hidden="true"><span>16:20</span><span>Precisión baja</span></div>
    <div class="cam-view" style="flex:1">
      ${arOverlay({
        w: 280, h: 370, cy: 204, heading: S.heading, pitch: S.pitch, min: S.min,
        fovH: 64, scene: S.scene, layers: S.layers, calibrated: false,
        reticle: 'compact', showCrossingPill: false,
        edgePad: { t: 108, b: 44, x: 10 },
        ariaLabel: 'Cámara con aviso de calibración de brújula',
      })}
      <div class="cam-notice" style="top:32px" role="status">
        <span style="color:var(--hud-sun);flex:none">${icon('figure8', 22)}</span>
        <div><b>Brújula sin calibrar</b><span>Mueve el móvil dibujando un 8 para estabilizar las líneas.</span></div>
      </div>
      <div class="hud-pill" style="position:absolute;left:10px;right:10px;bottom:10px;justify-content:center;font-size:11px">
        Líneas atenuadas · precisión baja
      </div>
    </div>
  </section>`;
}

function stateOutOfView() {
  /* Aim South (175°) right after sunset (18:05, sun at 248°, alt -4.7°) -> BOTH out of view AND below horizon */
  return `<section class="mini-phone" aria-label="Estado: sol fuera de vista y bajo el horizonte">
    <div class="mini-bar on-cam" aria-hidden="true"><span>18:05</span><span>175° S</span></div>
    <div class="cam-view" style="flex:1">
      ${arOverlay({
        w: 280, h: 370, cy: 170, heading: 175, pitch: 4, min: 18 * 60 + 5,
        fovH: 64, scene: 'night', layers: { sun: true, moon: true, solstices: false },
        reticle: 'compact', showCrossingPill: false,
        edgePad: { t: 44, b: 50, x: 10 },
        ariaLabel: 'Sol fuera del encuadre y bajo el horizonte con flecha en el borde',
      })}
      <div class="hud-pill" style="position:absolute;left:10px;right:10px;bottom:10px;justify-content:space-between;font-size:11px">
        <span>Hora azul · 18:05</span><span style="color:var(--hud-sun)">Sol −4,7°</span>
      </div>
    </div>
  </section>`;
}

function stateGround() {
  return `<section class="mini-phone" aria-label="Estado: apuntando al suelo">
    <div class="mini-bar on-cam" aria-hidden="true"><span>16:20</span><span>−42°</span></div>
    <div class="cam-view" style="flex:1">
      ${arOverlay({
        w: 280, h: 370, heading: S.heading, pitch: -42, min: S.min,
        fovH: 64, scene: S.scene, layers: S.layers,
        reticle: 'compact', showCrossingPill: false,
        edgePad: { t: 46, b: 44, x: 10 },
        ariaLabel: 'Móvil apuntando al suelo con indicador hacia el horizonte',
      })}
      <div class="hud-pill" style="position:absolute;left:10px;right:10px;bottom:10px;justify-content:center;font-size:11px">
        ${deg(S.heading)} ${wind(S.heading)} · −42° · apuntando al suelo
      </div>
    </div>
  </section>`;
}

function brightSkyProof(reticleMode = 'standard') {
  return `<section class="bright-box" aria-label="Prueba de legibilidad sobre cielo brillante">
    <div class="cam-view" style="flex:1">
      ${arOverlay({
        w: 480, h: 204, cy: 96, heading: S.heading, pitch: 8, min: S.min,
        fovH: 76, scene: 'bright', layers: S.layers,
        reticle: 'compact', showCrossingPill: true,
        edgePad: { t: 16, b: 14, x: 12 },
        ariaLabel: 'Superposición sobre cielo claro brillante a contraluz',
      })}
    </div>
    <div class="bright-foot">
      <span><b>Contorno doble</b> (#111318 + acento) ≥ 3:1 en cielo claro y oscuro</span>
      <span><b>Velo</b> ≥ 4,5:1</span>
    </div>
  </section>`;
}

function stateNoSensor() {
  return `<section class="sensor-card" aria-label="Estado: móvil sin sensor de orientación">
    <div class="sensor-top">
      <div class="mini-icon-ring" style="flex:none">${icon('sensorOff', 22)}</div>
      <div>
        <h3>Sin sensor de orientación</h3>
        <p>Este móvil no dispone de brújula ni giroscopio. Puedes mover el encuadre manualmente arrastrando la escena o consultar las horas en Hoy.</p>
      </div>
    </div>
    <div class="sensor-actions">
      <button class="btn-secondary" type="button">Modo manual</button>
      <button class="btn-secondary" type="button">Ir a Hoy</button>
    </div>
  </section>`;
}

/* ---------- Board header for stage 2 ---------- */
const arBoardHead = (title, tradeoff) => `<header class="board-head">
  <div><h1><span id="logo"></span>${title}</h1><p>${tradeoff}</p></div>
  <div class="board-tools">
    <label class="tool-range">Rumbo <input type="range" min="0" max="359" step="1" value="239" data-input="heading" aria-label="Rumbo simulado de la cámara"></label>
    <label class="tool-range">Inclinación <input type="range" min="-45" max="45" step="1" value="5" data-input="pitch" aria-label="Inclinación vertical simulada"></label>
    <button class="tool" data-act="scene">Escena: <span data-slot="sceneLabel">Atardecer</span></button>
    <button class="tool" data-act="ar-now">Volver a ahora</button>
    <button class="tool" data-act="theme" aria-pressed="false">Tema: <span>Oscuro</span></button>
  </div></header>`;

/* ---------- Mount stage-2 interactions ---------- */
function mountAr(slots) {
  SLOTS.sceneLabel = () => (S.scene === 'dusk' ? 'Atardecer' : S.scene === 'bright' ? 'Cielo claro' : 'Noche');
  mount(slots);
  document.addEventListener('input', (e) => {
    const t = e.target.dataset && e.target.dataset.input;
    if (t === 'pitch') { S.pitch = +e.target.value; update(); }
  });
  document.addEventListener('click', (e) => {
    const b = e.target.closest('[data-act]');
    if (!b) return;
    const act = b.dataset.act;
    if (act === 'layer') {
      const k = b.dataset.layer;
      S.layers[k] = !S.layers[k];
      update();
    } else if (act === 'scene') {
      S.scene = S.scene === 'dusk' ? 'bright' : S.scene === 'bright' ? 'night' : 'dusk';
      update();
    } else if (act === 'ar-now') {
      S.y = S.today[0]; S.mo = S.today[1]; S.d = S.today[2];
      S.min = 16 * 60 + 20; S.heading = 239; S.pitch = 5;
      document.querySelectorAll('[data-input=pitch]').forEach((el) => (el.value = S.pitch));
      update();
    } else if (act === 'aim-dome') {
      const rect = b.getBoundingClientRect();
      const dx = e.clientX - (rect.left + rect.width / 2);
      const dy = e.clientY - (rect.top + rect.height / 2);
      if (Math.hypot(dx, dy) > 8) {
        const rel = (Math.atan2(dx, -dy) * 180) / Math.PI;
        S.heading = Math.round(((S.heading + rel) % 360 + 360) % 360);
        update();
      }
    }
  });
}
