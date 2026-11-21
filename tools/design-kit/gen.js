/* Builds self-contained Cenit artboards: inlines base.css, astro.js and lib.js into each HTML file. */
const fs = require('fs'), path = require('path');
const OUT = process.argv[2];
const css = fs.readFileSync(path.join(__dirname, 'base.css'), 'utf8');
const arCss = fs.readFileSync(path.join(__dirname, 'ar.css'), 'utf8');
const js = fs.readFileSync(path.join(__dirname, 'astro.js'), 'utf8').replace("if (typeof module !== 'undefined') module.exports = Astro;", '') + '\n' + fs.readFileSync(path.join(__dirname, 'lib.js'), 'utf8');
const arJs = fs.readFileSync(path.join(__dirname, 'ar.js'), 'utf8');

function page({ title, extraCss = '', body, script }) {
  return `<!doctype html>
<html lang="es" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>
${css}${extraCss}
</style>
</head>
<body>
${body}
<script>
${js}
${script}
</script>
</body>
</html>
`;
}

function pageAr({ title, extraCss = '', body, script }) {
  return `<!doctype html>
<html lang="es" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>
${css}${arCss}${extraCss}
</style>
</head>
<body>
${body}
<script>
${js}
${arJs}
${script}
</script>
</body>
</html>
`;
}
const head = (title, tradeoff) => `<header class="board-head">
  <div><h1><span id="logo"></span>${title}</h1><p>${tradeoff}</p></div>
  <div class="board-tools">
    <label class="tool-range">Rumbo simulado <input type="range" min="0" max="359" step="1" value="247" data-input="heading" aria-label="Rumbo simulado de la brújula"></label>
    <button class="tool" data-act="now">Volver a ahora</button>
    <button class="tool" data-act="theme" aria-pressed="false">Tema: <span>Oscuro</span></button>
  </div></header>`;
const wrap = (cap, html) => `<div class="phone-wrap"><p class="phone-cap">${cap}</p>${html}</div>`;
const board = (title, tradeoff, phones) => `<main class="board">${head(title, tradeoff)}<div class="phones">${phones.join('')}</div></main>`;
const logoInit = `document.getElementById('logo').innerHTML = logo(30);`;

/* Shared slot snippets used by several directions */
const shared = `
function placeHead(extra = '') {
  return \`<div class="place-head"><div><h2 class="place-name">\${S.place.name}</h2><p class="place-sub">\${S.place.region} · \${coords(S.place)}</p></div>\${extra}</div>\`;
}
function dateStepper() {
  return \`<div class="stepper" role="group" aria-label="Fecha"><button class="icon-btn" data-act="shift" data-days="-1" data-key="prev" aria-label="Día anterior">\${icon('prev', 20)}</button><button class="chip" data-key="datechip" aria-label="Elegir fecha: \${dateLabel(S.y, S.mo, S.d, { weekday: 'long', day: 'numeric', month: 'long' })}">\${isToday() ? 'Hoy' : dateLabel()}</button><button class="icon-btn" data-act="shift" data-days="1" data-key="next" aria-label="Día siguiente">\${icon('next', 20)}</button></div>\`;
}
function nextBlock() {
  const e = nextEvent(), from = isToday() && S.min === 980 ? 'Próximo' : 'Próximo desde las ' + hhmm(S.min);
  if (!e) return \`<p class="eyebrow">\${from}</p><p class="focal">—</p><p class="muted small">No quedan eventos este día</p>\`;
  const range = e.end ? ' · hasta ' + T(e.end) : '', az = e.az ? ' · ' + deg(e.az) + ' ' + wind(e.az) : '';
  return \`<p class="eyebrow">\${from}</p><div class="focal-row"><p class="focal">\${T(e.t)}</p>\${alertBtn()}</div><p class="next-cap"><span class="tag \${e.kind === 'golden' ? 'golden' : e.kind === 'blue' ? 'blue' : e.kind.startsWith('moon') ? 'moon' : 'sun'}"></span>\${e.label}\${range}\${az} · <b>\${inDur(e.t - nowT())}</b></p>\`;
}
function targets() {
  const d = day(), s = sunNow(), m = moonNow();
  const list = [
    d.setAz && ['sun', 'Atardecer ' + T(d.setPm), d.setAz], s.alt > -0.833 && ['sun', 'Sol ahora', s.az],
    d.riseAz && ['sun', 'Amanecer ' + T(d.setAm), d.riseAz], d.moonriseAz && ['moon', 'Salida luna ' + T(d.moonrise), d.moonriseAz],
    m.alt > -0.833 && ['moon', 'Luna ahora', m.az],
  ].filter(Boolean).slice(0, 4);
  return \`<ul class="rows">\${list.map(([k, l, az]) => \`<li class="row trow"><span class="t">\${deg(az)}</span><span class="l"><span class="tag \${k}"></span>\${l}</span><span class="a">\${relative(az)}</span></li>\`).join('')}</ul>\`;
}
function headingBlock() {
  return \`<div class="heading"><p class="display">\${deg(S.heading)} <span class="wind">\${wind(S.heading)}</span></p><p class="muted small">Norte geográfico · declinación magnética corregida</p></div>\`;
}
`;
const sharedCss = `
.focal-row{display:flex;align-items:center;justify-content:space-between}
.next-cap{margin:4px 0 0;font-size:15px;line-height:22px}.next-cap b{font-weight:500}
.wind{font:400 20px/26px var(--font-sans);color:var(--text-secondary);letter-spacing:0}
.trow{grid-template-columns:52px 1fr auto}.trow .t{font-size:17px}.trow .l{font-size:14px}
.screen-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:var(--space-group)}
.screen-title .headline{margin:0}
`;

/* ---------------- 01 Almanaque ---------------- */
const a01 = page({
  title: 'Cenit · 01 Almanaque',
  extraCss: sharedCss + `
.almanac{display:grid;grid-template-columns:1fr 1fr;gap:0 18px;margin-top:4px}
.almanac h3{margin:0 0 2px}.almanac dl{margin:0}
.al-row{display:flex;justify-content:space-between;align-items:baseline;padding:5px 0;border-bottom:var(--border-hairline)}
.al-row dt{font-size:13px;color:var(--text-secondary)}.al-row dd{margin:0;font-size:17px;letter-spacing:-.3px}.al-row dd small{font-size:12px;color:var(--text-secondary);margin-left:4px}
.photo{margin-top:8px;display:grid;grid-template-columns:auto 1fr 1fr;gap:4px 12px;font-size:14px;align-items:center}
.photo .k{color:var(--text-secondary);font-size:13px}
.phase-line{display:flex;align-items:center;gap:8px;padding:5px 0 0;margin:0;font-size:13px;color:var(--text-secondary)}
`,
  body: board('01 · Almanaque', 'Una página de almanaque: lo próximo en grande, el día en una gráfica y todas las horas a la vista sin hacer scroll. Lo más parecido a la vista de detalles de Sun Seeker y Sun Locator, y lo más denso; el «dónde» queda en la pestaña Brújula.', [
    wrap('Hoy · horas y próximo evento', `<section class="phone" aria-label="Pantalla Hoy">${'${statusBar()}'}<div class="screen">
      <div data-slot="head"></div>
      <div data-slot="next" style="margin-top:12px"></div>
      <div class="plate" style="margin-top:10px" data-slot="chart"></div>
      ${'${scrubber("t1")}'}
      <div data-slot="table"></div>
    </div>${'${tabBar("hoy")}'}</section>`),
    wrap('Cielo · gráfica y planificador', `<section class="phone" aria-label="Pantalla Cielo">${'${statusBar()}'}<div class="screen">
      <div class="screen-title"><h2 class="headline">Cielo</h2><div data-slot="stepper"></div></div>
      <div class="plate" data-slot="bigchart"></div>
      ${'${scrubber("t2")}'}<div data-slot="clock"></div>
      <div data-slot="readout" style="margin-top:10px"></div>
      <p class="fine" data-slot="solstice"></p>
    </div>${'${tabBar("cielo")}'}</section>`),
    wrap('Brújula · rumbo y direcciones', `<section class="phone" aria-label="Pantalla Brújula">${'${statusBar()}'}<div class="screen">
      <h2 class="headline">Brújula</h2>
      <div data-slot="heading"></div>
      <div data-slot="rose" style="display:flex;justify-content:center;margin:14px 0 8px"></div>
      <div data-slot="targets"></div>
    </div>${'${tabBar("brujula")}'}</section>`),
    wrap('Lugares · GPS, búsqueda y guardados', '${placesPhone()}'),
  ]),
  script: shared + `
document.querySelector('.phones').innerHTML = document.querySelector('.phones').innerHTML.replace(/\\$\\{([^}]+)\\}/g, (_, x) => eval(x));
${logoInit}
mount({
  head: () => placeHead(\`<button class="chip" aria-label="Cambiar fecha">\${icon('cal', 16)} \${isToday() ? dateLabel() : dateLabel()}</button>\`),
  next: nextBlock,
  chart: () => chart({ w: 314, h: 100 }),
  table: () => { const d = day(), m = Astro.phase(d.noon), ph = phaseNow();
    return \`<div class="almanac"><div><h3 class="eyebrow">Sol</h3><dl>
      <div class="al-row"><dt>Amanecer</dt><dd>\${T(d.setAm)}<small>\${deg(d.riseAz)}</small></dd></div>
      <div class="al-row"><dt>Mediodía</dt><dd>\${T(d.noon)}<small>\${deg(d.noonAlt)}</small></dd></div>
      <div class="al-row"><dt>Atardecer</dt><dd>\${T(d.setPm)}<small>\${deg(d.setAz)}</small></dd></div></dl>
      <p class="phase-line">\${dur(dayLength())} de luz</p></div>
      <div><h3 class="eyebrow">Luna</h3><dl>
      <div class="al-row"><dt>Salida</dt><dd>\${T(d.moonrise)}<small>\${d.moonriseAz ? deg(d.moonriseAz) : ''}</small></dd></div>
      <div class="al-row"><dt>Puesta</dt><dd>\${T(d.moonset)}<small>\${d.moonsetAz ? deg(d.moonsetAz) : ''}</small></dd></div>
      <div class="al-row"><dt>Fase</dt><dd>\${Math.round(ph.illum * 100)} %</dd></div></dl>
      <p class="phase-line">\${phaseGlyph(ph, 9)} \${ph.name}</p></div></div>
      <div class="photo" role="table" aria-label="Luz para fotografía"><span class="k" role="columnheader"></span><span class="k" role="columnheader">Mañana</span><span class="k" role="columnheader">Tarde</span>
      <span role="rowheader"><span class="tag golden"></span>Dorada</span><span>\${T(d.blueAm)}–\${T(d.goldenAm)}</span><span>\${T(d.goldenPm)}–\${T(d.bluePm)}</span>
      <span role="rowheader"><span class="tag blue"></span>Azul</span><span>\${T(d.civilAm)}–\${T(d.blueAm)}</span><span>\${T(d.bluePm)}–\${T(d.civilPm)}</span>
      <span role="rowheader" class="k">Náutico</span><span>\${T(d.nauticalAm)}</span><span>\${T(d.nauticalPm)}</span>
      <span role="rowheader" class="k">Astron.</span><span>\${T(d.astroAm)}</span><span>\${T(d.astroPm)}</span></div>\`; },
  stepper: dateStepper,
  bigchart: () => chart({ w: 314, h: 250, labels: true }),
  clock: () => { const ph = skyPhase(sunNow().alt); return \`<p class="title" style="margin:0;display:flex;justify-content:space-between"><span>\${hhmm(S.min)}</span><span class="muted small" style="align-self:center">\${ph.name}</span></p>\`; },
  readout,
  solstice: () => { const w = Astro.day(S.place, 2026, 11, 21); return \`Próximo solsticio: 21 dic · \${dur(w.setPm - w.setAm)} de luz · el sol sale a \${deg(w.riseAz)}.\`; },
  heading: headingBlock,
  rose: () => rose({ size: 280 }),
  targets,
});`,
});

/* ---------------- 02 Cúpula ---------------- */
const a02 = page({
  title: 'Cenit · 02 Cúpula',
  extraCss: sharedCss + `
.keys{display:grid;grid-template-columns:repeat(3,1fr);border-top:var(--border-hairline);margin-top:12px}
.keys div{padding:10px 0 0}.keys div+div{border-left:var(--border-hairline);padding-left:12px}
.keys dt{font-size:13px;color:var(--text-secondary)}.keys dd{margin:2px 0 0;font:400 20px/26px var(--font-sans);letter-spacing:-.4px}
.keys dl{margin:0}
.time-row{display:flex;align-items:center;gap:12px;justify-content:space-between;margin-top:4px}
.legend{display:flex;gap:14px;justify-content:center;font-size:12px;line-height:16px;color:var(--text-secondary);margin:2px 0 0}.legend i{display:inline-block;width:16px;border-top:1px solid var(--text-secondary);vertical-align:3px;margin-right:5px}.legend i.dash{border-top-style:dashed}.legend i.dot{border-top:2px dotted var(--text-secondary)}
.timeline .row{grid-template-columns:96px 1fr auto;min-height:38px;padding:7px 0}
.timeline .t{font-size:17px}.timeline .l{font-size:14px}
.now-row{border-bottom:1px solid var(--text-primary)!important}
.now-row .t,.now-row .l{font-weight:500}
`,
  body: board('02 · Cúpula (brújula plana)', 'La vista principal de Sun Seeker, en el estilo de Cenit: el cielo desde arriba con el recorrido del sol hora a hora, la noche fuera del horizonte, los solsticios y el equinoccio como referencia, y la luna. Responde «hacia dónde mirar» de un vistazo y la misma cúpula gira como brújula. La lista completa de horas queda a un toque, en Cielo.', [
    wrap('Hoy · cúpula y momento elegido', `<section class="phone" aria-label="Pantalla Hoy">${'${statusBar()}'}<div class="screen">
      <div data-slot="head"></div>
      <div data-slot="dome" style="display:flex;justify-content:center;margin:4px 0 0"></div>
      <p class="legend" aria-hidden="true"><span><i></i>solsticios jun/dic</span><span><i class="dash"></i>equinoccio</span><span><i class="dot"></i>noche</span></p>
      <div data-slot="time"></div>
      <div data-slot="ribbon" style="margin-top:6px"></div>${'${scrubber("t1")}'}
      <div data-slot="keys"></div>
    </div>${'${tabBar("hoy")}'}</section>`),
    wrap('Cielo · todas las horas del día', `<section class="phone" aria-label="Pantalla Cielo">${'${statusBar()}'}<div class="screen">
      <div class="screen-title"><h2 class="headline">Cielo</h2><div data-slot="stepper"></div></div>
      <div class="plate" data-slot="chart"></div>
      <div data-slot="timeline" style="margin-top:8px"></div>
    </div>${'${tabBar("cielo")}'}</section>`),
    wrap('Brújula · la cúpula gira con el móvil', `<section class="phone" aria-label="Pantalla Brújula">${'${statusBar()}'}<div class="screen">
      <h2 class="headline">Brújula</h2>
      <div data-slot="heading"></div>
      <div data-slot="dome2" style="display:flex;justify-content:center;margin:6px 0 4px"></div>
      <div data-slot="targets"></div>
    </div>${'${tabBar("brujula")}'}</section>`),
    wrap('Lugares · GPS, búsqueda y guardados', '${placesPhone()}'),
  ]),
  script: shared + `
document.querySelector('.phones').innerHTML = document.querySelector('.phones').innerHTML.replace(/\\$\\{([^}]+)\\}/g, (_, x) => eval(x));
${logoInit}
mount({
  head: () => placeHead(dateStepper()).replace('class="place-head"', 'class="place-head" style="align-items:center"'),
  dome: () => dome({ size: 300 }),
  time: () => { const s = sunNow(), ph = skyPhase(s.alt), sh = shadowRatio(s.alt); return \`<div class="time-row"><p class="display">\${hhmm(S.min)}</p><p class="muted small" style="margin:0;text-align:right">\${ph.name} · sol \${deg(s.az)} \${wind(s.az)}, \${s.alt < -0.833 ? 'bajo el horizonte' : deg(s.alt, 1)}<br>\${sh ? 'Sombra ' + num(sh, 1) + ' × la altura' : 'Sin sombra'}</p>\${alertBtn()}</div>\`; },
  ribbon: () => ribbon(316, 12),
  keys: () => { const d = day(); return \`<dl class="keys"><div><dt>Amanecer</dt><dd>\${T(d.setAm)}</dd></div><div><dt><span class="tag golden"></span>Dorada</dt><dd>\${T(d.goldenPm)}</dd></div><div><dt>Atardecer</dt><dd>\${T(d.setPm)}</dd></div></dl>\`; },
  stepper: dateStepper,
  chart: () => chart({ w: 314, h: 100 }),
  timeline: () => { const t = nowT(); let placed = false; const rows = [];
    const ev = events().filter((e) => e.kind !== 'nautical' && e.kind !== 'astro' && e.kind !== 'night');
    ev.forEach((e) => { if (!placed && e.t > t) { rows.push(\`<li class="row now-row"><span class="t">\${hhmm(S.min)}</span><span class="l">Momento elegido</span><span class="a"></span></li>\`); placed = true; }
      const tag = e.kind === 'golden' ? 'golden' : e.kind === 'blue' ? 'blue' : e.kind.startsWith('moon') ? 'moon' : e.kind === 'night' || e.kind === 'astro' || e.kind === 'nautical' ? 'night' : 'sun';
      rows.push(\`<li class="row\${e.t < t ? ' past' : ''}"><span class="t">\${T(e.t)}\${e.end ? '–' + T(e.end) : ''}</span><span class="l"><span class="tag \${tag}"></span>\${e.label}</span><span class="a">\${e.az ? deg(e.az) : e.alt ? deg(e.alt) : ''}</span></li>\`); });
    if (!placed) rows.push(\`<li class="row now-row"><span class="t">\${hhmm(S.min)}</span><span class="l">Momento elegido</span><span class="a"></span></li>\`);
    const d = day(); return \`<ul class="rows timeline" aria-label="Horas del \${dateLabel()}">\${rows.join('')}</ul><p class="fine" style="padding-top:8px">Crepúsculo náutico \${T(d.nauticalAm)} y \${T(d.nauticalPm)} · astronómico \${T(d.astroAm)} y \${T(d.astroPm)}</p>\`; },
  heading: headingBlock,
  dome2: () => dome({ size: 300, heading: S.heading }).replace('</svg>', \`<line x1="150" x2="150" y1="26" y2="44" style="stroke:var(--text-primary)" stroke-width="3" stroke-linecap="round"/></svg>\`),
  targets,
});`,
});

/* ---------------- 03 Línea ---------------- */
const a03 = page({
  title: 'Cenit · 03 Línea del día',
  extraCss: sharedCss + `
.sel-time{display:flex;align-items:baseline;gap:12px;margin:18px 0 2px}
.week{width:100%;border-collapse:collapse;font-size:14px;margin-top:6px}
.week th{font:500 12px/16px var(--font-sans);letter-spacing:1px;text-transform:uppercase;color:var(--text-secondary);text-align:right;padding:6px 0;font-weight:500}
.week th:first-child,.week td:first-child{text-align:left}
.week td{text-align:right;padding:7px 0;border-top:var(--border-hairline)}
.week tr.on td{color:var(--text-primary);font-weight:500}.week tr td{color:var(--text-secondary)}.week tr.on td:first-child{box-shadow:inset 3px 0 var(--sun);padding-left:8px}
.heading-big{display:flex;align-items:baseline;gap:10px}
.cross{margin:18px auto 12px;width:316px}
`,
  body: board('03 · Línea del día', 'El día como una línea de luz que se arrastra: eliges fecha y hora y todo responde (dirección, elevación, lo que viene después). La mejor para planificar una sesión concreta y comparar días de la semana; muestra menos la geometría del cielo.', [
    wrap('Hoy · planificador de fecha y hora', `<section class="phone" aria-label="Pantalla Hoy">${'${statusBar()}'}<div class="screen">
      <div data-slot="head"></div>
      <div data-slot="strip" style="margin-top:14px"></div>
      <div data-slot="sel"></div>
      <div data-slot="ribbon" style="margin-top:8px"></div>${'${scrubber("t1")}'}<div class="scrub-ticks" aria-hidden="true"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></div>
      <div data-slot="readout" style="margin-top:12px"></div>
      <div data-slot="upcoming" style="margin-top:14px"></div>
    </div>${'${tabBar("hoy")}'}</section>`),
    wrap('Cielo · gráfica y semana', `<section class="phone" aria-label="Pantalla Cielo">${'${statusBar()}'}<div class="screen">
      <div class="screen-title"><h2 class="headline">Cielo</h2><div data-slot="stepper"></div></div>
      <div class="plate" data-slot="chart"></div>
      <div data-slot="week" style="margin-top:12px"></div>
      <p class="fine" data-slot="solstice"></p>
    </div>${'${tabBar("cielo")}'}</section>`),
    wrap('Brújula · cinta de rumbo', `<section class="phone" aria-label="Pantalla Brújula">${'${statusBar()}'}<div class="screen">
      <h2 class="headline">Brújula</h2>
      <div data-slot="heading"></div>
      <div data-slot="tape" class="cross"></div>
      <div data-slot="targets"></div>
      <div class="notice" style="margin-top:auto">La brújula necesita calibrarse: mueve el móvil dibujando un 8.</div>
    </div>${'${tabBar("brujula")}'}</section>`),
    wrap('Lugares · GPS, búsqueda y guardados', '${placesPhone()}'),
  ]),
  script: shared + `
document.querySelector('.phones').innerHTML = document.querySelector('.phones').innerHTML.replace(/\\$\\{([^}]+)\\}/g, (_, x) => eval(x));
${logoInit}
mount({
  head: () => placeHead(\`<button class="chip" aria-label="Elegir otra fecha">\${icon('cal', 16)} Otra fecha</button>\`),
  strip: dateStrip,
  sel: () => { const s = sunNow(), ph = skyPhase(s.alt), e = nextEvent();
    return \`<div class="sel-time"><p class="focal">\${hhmm(S.min)}</p><p class="muted" style="margin:0">\${dateLabel()}<br>\${ph.name}</p><span style="margin-left:auto;align-self:center">\${alertBtn()}</span></div>
    <p class="next-cap" style="margin:0">\${e ? \`<span class="tag \${e.kind === 'golden' ? 'golden' : e.kind === 'blue' ? 'blue' : e.kind.startsWith('moon') ? 'moon' : 'sun'}"></span>\${e.label} <b>\${inDur(e.t - nowT())}</b> · \${T(e.t)}\` : 'No quedan eventos este día'}</p>\`; },
  ribbon: () => ribbon(316, 16),
  readout,
  upcoming: () => { const t = nowT(); const list = events().filter((e) => e.t > t && e.kind !== 'nautical' && e.kind !== 'astro').slice(1, 4);
    return \`<h3 class="eyebrow">Después</h3><ul class="rows">\${list.map((e) => \`<li class="row" style="min-height:40px;padding:8px 0"><span class="t" style="font-size:17px">\${T(e.t)}</span><span class="l" style="font-size:14px">\${e.label}</span><span class="a">\${e.az ? deg(e.az) : ''}</span></li>\`).join('')}</ul>\`; },
  stepper: dateStepper,
  chart: () => chart({ w: 314, h: 210, labels: true }),
  week: () => { const base = Date.UTC(S.today[0], S.today[1], S.today[2]), dow = (new Date(base).getUTCDay() + 6) % 7; let r = '';
    for (let i = 0; i < 7; i++) { const dt = new Date(base + (i - dow) * 86400000), y = dt.getUTCFullYear(), mo = dt.getUTCMonth(), dd = dt.getUTCDate(), x = Astro.day(S.place, y, mo, dd), on = y === S.y && mo === S.mo && dd === S.d;
      r += \`<tr class="\${on ? 'on' : ''}"\${on ? ' aria-current="date"' : ''}><td>\${dateLabel(y, mo, dd, { weekday: 'short', day: 'numeric' })}</td><td>\${T(x.setAm)}</td><td>\${T(x.goldenPm)}</td><td>\${T(x.setPm)}</td><td>\${dur(x.setPm - x.setAm).replace(' min', '')}</td></tr>\`; }
    return \`<table class="week"><caption class="sr">Semana en \${S.place.name}</caption><thead><tr><th scope="col">Día</th><th scope="col">Sale</th><th scope="col">Dorada</th><th scope="col">Se pone</th><th scope="col">Luz</th></tr></thead><tbody>\${r}</tbody></table>\`; },
  solstice: () => { const w = Astro.day(S.place, 2026, 11, 21); return \`Próximo solsticio: 21 dic · \${dur(w.setPm - w.setAm)} de luz.\`; },
  heading: headingBlock,
  tape: () => tape({ w: 316, h: 96 }),
  targets,
});`,
});

/* ---------------- system board ---------------- */
const tokens = {
  'Superficies y texto': ['bg-color', 'surface-color', 'surface-subtle', 'text-primary', 'text-secondary', 'border-color', 'control-border', 'focus-ring'],
  'Luz': ['sun', 'moon', 'blue-hour'],
  'Cielo (bandas de la gráfica)': ['sky-day', 'sky-civil', 'sky-nautical', 'sky-astro'],
  'Estado': ['success', 'warning', 'danger'],
};
const sys = page({
  title: 'Cenit · Sistema de diseño',
  extraCss: `
.sys{display:grid;grid-template-columns:1fr 1fr;gap:0}
.theme-col{background:var(--bg-color);color:var(--text-primary);padding:32px 30px 48px}
.theme-col h2{font:500 12px/16px var(--font-sans);letter-spacing:1px;text-transform:uppercase;color:var(--text-secondary);margin:0 0 20px}
.sw-group{margin-bottom:22px}.sw-group h3{font-size:13px;color:var(--text-secondary);font-weight:400;margin:0 0 8px}
.sws{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
.sw{border:var(--border-hairline);border-radius:12px;overflow:hidden;font-size:12px;line-height:16px}
.sw i{display:block;height:44px;border-bottom:var(--border-hairline)}.sw span{display:block;padding:6px 8px}.sw code{color:var(--text-secondary);font-size:11px}
.sec{border-top:var(--border-hairline);padding-top:20px;margin-top:8px}
.type p{margin:0 0 10px}.type small{display:block;font:400 12px var(--font-sans);color:var(--text-secondary);letter-spacing:0}
.comp{display:flex;flex-wrap:wrap;gap:16px;align-items:center;margin:12px 0 18px}
.comp-col{display:flex;flex-direction:column;gap:12px;width:316px}
.phases{display:flex;gap:10px;align-items:center}
.tabbar.demo{border:var(--border-hairline);border-radius:16px;width:316px}
.spacing{display:flex;gap:14px;align-items:flex-end;font-size:12px;color:var(--text-secondary)}
.spacing i{display:block;background:var(--sun);height:10px}
.radii{display:flex;gap:12px}.radii i{display:block;width:54px;height:54px;border:var(--border-control);background:var(--surface-color)}
.radii span{display:block;font-size:12px;color:var(--text-secondary);text-align:center;margin-top:4px}
`,
  body: `<main><header class="board-head" style="margin:0;padding:32px 40px 20px"><div><h1><span id="logo"></span>Cenit · sistema de diseño</h1><p>Propuesta pendiente de aceptar. Derivado de Scrolless (papel cálido, modo claro) y Sereno (tinta nocturna, modo oscuro), con dos luces de acento: ocre de sol y luz de luna. Valores y fuentes en <code>design/system.md</code>.</p></div></header>
  <div class="sys"><section class="theme-col" data-theme="dark" aria-label="Tema oscuro"></section><section class="theme-col" data-theme="light" aria-label="Tema claro"></section></div></main>`,
  script: `
document.getElementById('logo').innerHTML = logo(30);
const TOK = ${JSON.stringify(tokens)};
const ph = (illum, waxing, name) => ({ illum, waxing, name });
document.querySelectorAll('.theme-col').forEach((col, i) => {
  const cs = getComputedStyle(col), tid = col.dataset.theme;
  col.innerHTML = \`<h2>Tema \${tid === 'dark' ? 'oscuro' : 'claro'}</h2>
  \${Object.entries(TOK).map(([g, list]) => \`<div class="sw-group"><h3>\${g}</h3><div class="sws">\${list.map((t) => \`<div class="sw"><i style="background:var(--\${t})"></i><span>--\${t}<br><code>\${cs.getPropertyValue('--' + t).trim()}</code></span></div>\`).join('')}</div></div>\`).join('')}
  <div class="sec type"><h2>Tipografía</h2>
   <p class="focal">17:02<small>--type-focal · serif 52/56 · 300</small></p>
   <p class="display">247° <span class="wind" style="font:400 20px var(--font-sans);color:var(--text-secondary)">OSO</span><small>--type-display · serif 36/40 · 300</small></p>
   <p class="headline" style="margin:0 0 10px">Lugares<small>--type-headline · sans 26/32 · 300</small></p>
   <p class="title">Atardecer 17:43<small>--type-title · sans 20/26 · 400</small></p>
   <p style="font-size:17px;line-height:24px;letter-spacing:-.3px">El Perelló<small>--type-body-large · 17/24</small></p>
   <p>Empieza la hora dorada<small>--type-body · 15/22</small></p>
   <p class="small">Sueca, Valencia · 39,28° N · 0,28° O<small>--type-small · 13/18</small></p>
   <p class="eyebrow">Crepúsculos<small style="text-transform:none;letter-spacing:0">--type-label · 12/16 · 500 · +1 px</small></p></div>
  <div class="sec"><h2>Espaciado y radios</h2>
   <div class="spacing">\${[['tight', 6], ['inner', 12], ['group', 16], ['panel', 20], ['screen-x', 22], ['section', 28]].map(([n, v]) => \`<div><i style="width:\${v}px"></i>\${n}<br>\${v}</div>\`).join('')}</div>
   <div class="radii" style="margin-top:16px">\${[['control', 10], ['cell', 12], ['button-secondary', 14], ['button', 16], ['panel', 20], ['pill', 999]].map(([n, v]) => \`<div><i style="border-radius:\${Math.min(v, 27)}px"></i><span>\${n}<br>\${v}</span></div>\`).join('')}</div></div>
  <div class="sec"><h2>Componentes</h2>
   <div class="comp"><button class="btn-primary" style="padding:0 22px">Guardar lugar</button><button class="btn-secondary" style="padding:0 16px">Coordenadas manuales</button><button class="btn-danger">Borrar lugar</button></div>
   <div class="comp"><div class="seg" role="group" aria-label="Astro"><button aria-pressed="true">Sol</button><button aria-pressed="false">Luna</button></div><button class="chip">\${icon('cal', 16)} vie 20 nov</button><div class="phases">\${[ph(.03, true, 'Luna nueva'), ph(.25, true, 'Luna creciente'), ph(.5, true, 'Cuarto creciente'), ph(.79, true, 'Gibosa creciente'), ph(1, true, 'Luna llena'), ph(.6, false, 'Gibosa menguante'), ph(.3, false, 'Luna menguante')].map((p) => phaseGlyph(p, 11)).join('')}</div></div>
   <div class="comp"><div class="comp-col">\${dateStrip()}<div>\${ribbon(316, 14)}\${scrubber('ts' + i)}</div><div class="plate">\${chart({ w: 314, h: 150, labels: true })}</div>
     <ul class="rows"><li class="row"><span class="t">17:02</span><span class="l"><span class="tag golden"></span>Hora dorada</span><span class="a">hasta 18:00</span></li><li class="row"><span class="t">17:43</span><span class="l"><span class="tag sun"></span>Atardecer</span><span class="a">245°</span></li><li class="row"><span class="t">14:59</span><span class="l"><span class="tag moon"></span>Salida de la luna</span><span class="a">84°</span></li></ul></div>
     <div class="comp-col">\${dome({ size: 240 })}\${tape({ w: 316, h: 92 })}<label class="search">\${icon('search', 20)}<span class="sr">Buscar ciudad</span><input type="search" placeholder="Buscar ciudad"></label>
     <button class="place" aria-current="true"><span class="pl-text"><span class="pl-name">El Perelló</span><span class="pl-sub">Sueca, Valencia · 39,28° N · 0,28° O</span></span><span class="pl-check">\${icon('check', 20)}</span></button>
     <div class="notice">La brújula necesita calibrarse: mueve el móvil dibujando un 8.</div>\${readout()}</div></div>
   <div class="comp">\${tabBar('hoy').replace('class="tabbar"', 'class="tabbar demo"')}\${rose({ size: 200 })}</div></div>\`;
});`,
});

/* ---------------- Stage 2 (AR camera): 3 directions ---------------- */
const s2Frame = (cap, slotOrHtml) => `<div class="frame-wrap"><p class="phone-cap">${cap}</p>${slotOrHtml}</div>`;
const s2StatesHtml = (permHint, retMode) => `<div class="col-states">
  ${s2Frame('Permiso de cámara denegado', `<div data-slot="stPerm"></div>`)}
  ${s2Frame('Calibración necesaria', `<div data-slot="stCal"></div>`)}
  ${s2Frame('Sol fuera de vista / bajo el horiz.', `<div data-slot="stOut"></div>`)}
  ${s2Frame('Apuntando al suelo', `<div data-slot="stGround"></div>`)}
</div>`;
const s2ExtraHtml = (landCap) => `<div class="col-extra">
  ${s2Frame(landCap, `<div data-slot="land"></div>`)}
  ${s2Frame('Legibilidad en cielo brillante (contraluz)', `<div data-slot="bright"></div>`)}
  ${s2Frame('Sin sensor de orientación', `<div data-slot="noSensor"></div>`)}
</div>`;

/* ---- 01 · Visor ---- */
const s2_01 = pageAr({
  title: 'Cenit · 01 Visor',
  body: `<main class="board stage2"><div id="bhead"></div><div class="stage2-grid">
    <div class="col-main">
      <p class="phone-cap">Cámara en vivo · visor a pantalla completa</p>
      <section class="phone ar-phone" aria-label="Pantalla Cámara — 01 Visor">
        <div class="screen ar-screen">
          <div class="cam-view" style="width:100%;height:100%">
            <div data-slot="status"></div>
            <div class="hud-top" data-slot="hudTop"></div>
            <div class="hud-tape-wrap" data-slot="tape"></div>
            <div data-slot="cam" style="width:100%;height:100%"></div>
            <span class="hud-sim-tag" style="bottom:178px">Vista simulada · cámara</span>
            <div class="visor-dock">
              <div class="dock-top" data-slot="dockTop"></div>
              <div id="scrub-slot"></div>
              <div class="scrub-ticks" aria-hidden="true"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></div>
              <div class="layer-row" data-slot="layers"></div>
            </div>
          </div>
        </div>
      </section>
    </div>
    ${s2StatesHtml('Usar brújula plana', 'standard')}
    ${s2ExtraHtml('Vista apaisada · Visor')}
  </div></main>`,
  script: `
document.getElementById('bhead').innerHTML = arBoardHead('01 · Visor', 'Cámara a pantalla completa como un visor fotográfico: cinta de rumbo arriba, trayectorias limpias y barra inferior mínima con hora y capas. Máximo campo visual; las horas del día quedan fuera de la pantalla.');
document.getElementById('scrub-slot').innerHTML = scrubber('t-visor');
${logoInit}
mountAr({
  status: () => camStatus(),
  hudTop: () => {
    const ph = phaseNow();
    return \`<button class="hud-btn icon-only" type="button" aria-label="Volver a Brújula">\${icon('back', 20)}</button>
      <div class="hud-stepper" role="group" aria-label="Lugar y fecha">
        <button type="button" data-act="shift" data-days="-1" data-key="prev" aria-label="Día anterior">\${icon('prev', 16)}</button>
        <span>\${S.place.name} · \${dateLabel(S.y, S.mo, S.d, { day: 'numeric', month: 'short' })}</span>
        <button type="button" data-act="shift" data-days="1" data-key="next" aria-label="Día siguiente">\${icon('next', 16)}</button>
      </div>
      <span class="hud-pill" aria-label="Fase lunar: \${ph.name}, \${Math.round(ph.illum * 100)} %">\${phaseGlyph(ph, 7, false)}\${Math.round(ph.illum * 100)} %</span>\`;
  },
  tape: () => arHeadingTape(336, S.heading),
  cam: () => arOverlay({
    w: 360, h: 780, cy: 356, heading: S.heading, pitch: S.pitch, min: S.min,
    fovH: 64, scene: S.scene, layers: S.layers, reticle: 'standard',
    showCrossingPill: true, edgePad: { t: 126, b: 196, x: 14 },
    ariaLabel: 'Visor de cámara a pantalla completa con trayectoria solar, lunar y solsticios',
  }),
  dockTop: () => {
    const s = sunNow(), ph = skyPhase(s.alt);
    return \`<p class="dock-time">\${hhmm(S.min)}</p>
      <p class="dock-meta"><b>\${ph.name}</b><br>Sol \${deg(s.az)} \${wind(s.az)} · \${s.alt < -0.833 ? 'bajo horiz.' : deg(s.alt, 1)}</p>
      <button class="hud-btn compact" type="button" data-act="ar-now" data-key="now">Ahora</button>\`;
  },
  layers: () => \`
    <button class="layer-btn l-sun" type="button" data-act="layer" data-layer="sun" data-key="l-sun" aria-pressed="\${S.layers.sun}"><span class="hud-dot sun"></span>Sol</button>
    <button class="layer-btn l-moon" type="button" data-act="layer" data-layer="moon" data-key="l-moon" aria-pressed="\${S.layers.moon}"><span class="hud-dot moon"></span>Luna</button>
    <button class="layer-btn" type="button" data-act="layer" data-layer="solstices" data-key="l-sol" aria-pressed="\${S.layers.solstices}"><span class="hud-dot sol"></span>Solsticios</button>\`,
  stPerm: () => statePermission('Usar brújula plana'),
  stCal: () => stateCalibration('standard'),
  stOut: () => stateOutOfView(),
  stGround: () => stateGround(),
  land: () => {
    const s = sunNow(), ph = skyPhase(s.alt);
    return \`<section class="land-phone" aria-label="Vista apaisada — 01 Visor">
      <div class="cam-view" style="flex:1;height:100%">
        <div style="position:absolute;top:8px;left:10px;right:10px;z-index:4">\${arHeadingTape(322, S.heading)}</div>
        \${arOverlay({
          w: 340, h: 254, heading: S.heading, pitch: S.pitch, min: S.min,
          fovH: 78, scene: S.scene, layers: S.layers, reticle: 'compact',
          showCrossingPill: true, edgePad: { t: 52, b: 14, x: 12 },
          ariaLabel: 'Visor de cámara en formato apaisado',
        })}
      </div>
      <div class="land-visor-dock">
        <div>
          <p class="eyebrow" style="color:var(--hud-muted);font-size:10px">\${S.place.name}</p>
          <p style="font:300 26px/30px var(--font-serif);margin:2px 0 0;color:var(--hud-fg)">\${hhmm(S.min)}</p>
          <p style="font-size:11px;line-height:15px;color:var(--hud-muted);margin:2px 0 0">\${ph.name}<br>Sol \${deg(s.az)} · \${deg(s.alt, 1)}</p>
        </div>
        <div style="display:flex;flex-direction:column;gap:5px">
          <button class="layer-btn l-sun" style="min-height:32px;font-size:11px" type="button" data-act="layer" data-layer="sun" aria-pressed="\${S.layers.sun}"><span class="hud-dot sun"></span>Sol</button>
          <button class="layer-btn l-moon" style="min-height:32px;font-size:11px" type="button" data-act="layer" data-layer="moon" aria-pressed="\${S.layers.moon}"><span class="hud-dot moon"></span>Luna</button>
          <button class="layer-btn" style="min-height:32px;font-size:11px" type="button" data-act="layer" data-layer="solstices" aria-pressed="\${S.layers.solstices}"><span class="hud-dot sol"></span>Solsticios</button>
        </div>
      </div>
    </section>\`;
  },
  bright: () => brightSkyProof('standard'),
  noSensor: () => stateNoSensor(),
});`,
});

/* ---- 02 · Mira ---- */
const s2_02 = pageAr({
  title: 'Cenit · 02 Mira',
  body: `<main class="board stage2"><div id="bhead"></div><div class="stage2-grid">
    <div class="col-main">
      <p class="phone-cap">Cámara · mira central y hoja de horas</p>
      <section class="phone ar-phone" aria-label="Pantalla Cámara — 02 Mira">
        <div class="screen ar-screen">
          <div class="cam-view" style="width:100%;flex:1;min-height:0">
            <div data-slot="status"></div>
            <div class="hud-top" data-slot="hudTop"></div>
            <div data-slot="cam" style="width:100%;height:100%"></div>
            <span class="hud-sim-tag">Vista simulada · cámara</span>
          </div>
          <div class="mira-sheet">
            <div class="mira-head" data-slot="sheetHead"></div>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:6px" data-slot="layers"></div>
            <div id="scrub-slot"></div>
            <div data-slot="keys"></div>
            <div class="mira-moon-line" data-slot="moonLine"></div>
          </div>
        </div>
        <div id="tab-slot"></div>
      </section>
    </div>
    ${s2StatesHtml('Abrir Brújula', 'hero')}
    ${s2ExtraHtml('Vista apaisada · Mira')}
  </div></main>`,
  script: `
document.getElementById('bhead').innerHTML = arBoardHead('02 · Mira', 'Centrada en la retícula: apunta a una fachada o pico y lee al instante «El sol pasa por aquí a las 17:08 · hora dorada». Trayectorias más discretas y hoja inferior con las horas clave del día; el visor de cámara tiene menos altura.');
document.getElementById('scrub-slot').innerHTML = scrubber('t-mira');
document.getElementById('tab-slot').innerHTML = tabBar5('camara');
${logoInit}
mountAr({
  status: () => camStatus(),
  hudTop: () => \`<span class="hud-pill">\${S.place.name}</span>
    <div class="hud-stepper" role="group" aria-label="Fecha">
      <button type="button" data-act="shift" data-days="-1" data-key="prev" aria-label="Día anterior">\${icon('prev', 16)}</button>
      <span>\${dateLabel(S.y, S.mo, S.d, { day: 'numeric', month: 'short' })}</span>
      <button type="button" data-act="shift" data-days="1" data-key="next" aria-label="Día siguiente">\${icon('next', 16)}</button>
    </div>\`,
  cam: () => arOverlay({
    w: 360, h: 448, cy: 222, heading: S.heading, pitch: S.pitch, min: S.min,
    fovH: 64, scene: S.scene, layers: S.layers, reticle: 'hero', quietPaths: true,
    showCrossingPill: true, edgePad: { t: 74, b: 22, x: 14 },
    ariaLabel: 'Visor de cámara con mira de precisión y lectura contextual de cruce solar',
  }),
  sheetHead: () => {
    const s = sunNow(), ph = skyPhase(s.alt);
    return \`<div><p class="mira-time">\${hhmm(S.min)}</p><p class="mira-phase">\${ph.name} · Sol \${deg(s.az)} \${wind(s.az)}, \${s.alt < -0.833 ? 'bajo horiz.' : deg(s.alt, 1)}</p></div>
      <button class="btn-now-sm" type="button" data-act="ar-now" data-key="now">Ahora</button>\`;
  },
  layers: () => \`<div class="chip-row" role="group" aria-label="Capas de trayectoria">
      <button class="layer-chip l-sun" type="button" data-act="layer" data-layer="sun" data-key="l-sun" aria-pressed="\${S.layers.sun}"><span class="sw-dot sun"></span>Sol</button>
      <button class="layer-chip l-moon" type="button" data-act="layer" data-layer="moon" data-key="l-moon" aria-pressed="\${S.layers.moon}"><span class="sw-dot moon"></span>Luna</button>
      <button class="layer-chip" type="button" data-act="layer" data-layer="solstices" data-key="l-sol" aria-pressed="\${S.layers.solstices}"><span class="sw-dot sol"></span>Solsticios</button>
    </div>
    <span class="small muted" style="font-size:11px">Estable</span>\`,
  keys: () => {
    const d = day();
    return \`<dl class="mira-keys">
      <div><dt>Amanecer</dt><dd>\${T(d.setAm)}</dd></div>
      <div><dt><span class="tag golden"></span>Dorada</dt><dd>\${T(d.goldenPm)}</dd></div>
      <div><dt>Atardecer</dt><dd>\${T(d.setPm)}</dd></div>
    </dl>\`;
  },
  moonLine: () => {
    const d = day(), ph = phaseNow();
    return \`<span>\${phaseGlyph(ph, 7, false)}Luna \${Math.round(ph.illum * 100)} % · \${ph.name}</span><span>\${T(d.moonrise)} – \${T(d.moonset)}</span>\`;
  },
  stPerm: () => statePermission('Abrir Brújula'),
  stCal: () => stateCalibration('hero'),
  stOut: () => stateOutOfView(),
  stGround: () => stateGround(),
  land: () => {
    const d = day(), hit = crosshairHit(S.heading, S.pitch, S.layers);
    return \`<section class="land-phone" aria-label="Vista apaisada — 02 Mira">
      <div class="cam-view" style="flex:1;height:100%">
        \${arOverlay({
          w: 302, h: 254, heading: S.heading, pitch: S.pitch, min: S.min,
          fovH: 76, scene: S.scene, layers: S.layers, reticle: 'hero', quietPaths: true,
          showCrossingPill: false, edgePad: { t: 16, b: 16, x: 12 },
          ariaLabel: 'Mira central en formato apaisado',
        })}
      </div>
      <div class="land-mira-sheet">
        <div>
          <p class="eyebrow" style="font-size:10px">\${S.place.name} · \${hhmm(S.min)}</p>
          <p style="font-size:12px;line-height:16px;margin:6px 0 0;color:var(--text-primary)">\${hit.text}</p>
        </div>
        <div style="border-top:var(--border-hairline);padding-top:8px;font-size:12px;line-height:18px">
          <div style="display:flex;justify-content:space-between"><span class="muted">Amanecer</span><span>\${T(d.setAm)}</span></div>
          <div style="display:flex;justify-content:space-between"><span class="muted">Dorada</span><span>\${T(d.goldenPm)}</span></div>
          <div style="display:flex;justify-content:space-between"><span class="muted">Atardecer</span><span>\${T(d.setPm)}</span></div>
        </div>
        <div class="chip-row">
          <button class="layer-chip l-sun" style="min-height:30px;padding:0 7px;font-size:11px" type="button" data-act="layer" data-layer="sun" aria-pressed="\${S.layers.sun}">Sol</button>
          <button class="layer-chip l-moon" style="min-height:30px;padding:0 7px;font-size:11px" type="button" data-act="layer" data-layer="moon" aria-pressed="\${S.layers.moon}">Luna</button>
          <button class="layer-chip" style="min-height:30px;padding:0 7px;font-size:11px" type="button" data-act="layer" data-layer="solstices" aria-pressed="\${S.layers.solstices}">Solst.</button>
        </div>
      </div>
    </section>\`;
  },
  bright: () => brightSkyProof('hero'),
  noSensor: () => stateNoSensor(),
});`,
});

/* ---- 03 · Cámara + cúpula ---- */
const s2_03 = pageAr({
  title: 'Cenit · 03 Cámara + cúpula',
  body: `<main class="board stage2"><div id="bhead"></div><div class="stage2-grid">
    <div class="col-main">
      <p class="phone-cap">Cámara (~58 %) + cúpula sincronizada (~42 %)</p>
      <section class="phone ar-phone" aria-label="Pantalla Cámara — 03 Cámara y cúpula">
        <div class="screen ar-screen">
          <div class="cam-view" style="width:100%;flex:1;min-height:0">
            <div data-slot="status"></div>
            <div class="hud-top" data-slot="hudTop"></div>
            <div data-slot="cam" style="width:100%;height:100%"></div>
            <div data-slot="aimPill" style="position:absolute;left:10px;bottom:8px;z-index:4"></div>
            <span class="hud-sim-tag">Vista simulada · cámara</span>
          </div>
          <div class="split-dome-panel">
            <div class="split-dome-main">
              <div class="dome-tap-wrap" role="button" tabindex="0" data-act="aim-dome" aria-label="Cúpula sincronizada: toca en cualquier dirección para apuntar la cámara" data-slot="dome"></div>
              <div class="split-side" data-slot="side"></div>
            </div>
            <div id="scrub-slot"></div>
            <p class="split-hint"><span>Toca la cúpula para orientar</span><span>Cono = encuadre 64°</span></p>
          </div>
        </div>
        <div id="tab-slot"></div>
      </section>
    </div>
    ${s2StatesHtml('Solo cúpula plana', 'compact')}
    ${s2ExtraHtml('Vista apaisada · Cámara + cúpula')}
  </div></main>`,
  script: `
document.getElementById('bhead').innerHTML = arBoardHead('03 · Cámara + cúpula', 'Cámara en directo arriba (~58 %) y la brújula plana de la etapa ① abajo (~42 %) sincronizadas al mismo rumbo y hora: toca la cúpula para girar el encuadre y ver el cono de visión. La más orientativa; recorta la altura de cámara.');
document.getElementById('scrub-slot').innerHTML = scrubber('t-split');
document.getElementById('tab-slot').innerHTML = tabBar5('camara');
${logoInit}
mountAr({
  status: () => camStatus(),
  hudTop: () => \`<span class="hud-pill">\${S.place.name} · \${dateLabel(S.y, S.mo, S.d, { day: 'numeric', month: 'short' })}</span>
    <div style="display:flex;gap:4px" role="group" aria-label="Capas">
      <button class="hud-btn compact" type="button" data-act="layer" data-layer="sun" data-key="l-sun" aria-pressed="\${S.layers.sun}">Sol</button>
      <button class="hud-btn compact moon-tog" type="button" data-act="layer" data-layer="moon" data-key="l-moon" aria-pressed="\${S.layers.moon}">Luna</button>
      <button class="hud-btn compact sol-tog" type="button" data-act="layer" data-layer="solstices" data-key="l-sol" aria-pressed="\${S.layers.solstices}">Solst.</button>
    </div>\`,
  cam: () => arOverlay({
    w: 360, h: 418, cy: 204, heading: S.heading, pitch: S.pitch, min: S.min,
    fovH: 64, scene: S.scene, layers: S.layers, reticle: 'compact',
    showCrossingPill: true, edgePad: { t: 72, b: 36, x: 14 },
    ariaLabel: 'Visor superior sincronizado con la cúpula plana inferior',
  }),
  aimPill: () => \`<span class="hud-pill" style="min-height:26px;padding:0 9px;font-size:11px">\${deg(S.heading)} \${wind(S.heading)} · \${S.pitch >= 0 ? '+' : ''}\${deg(S.pitch)}</span>\`,
  dome: () => arDome({ size: 204, heading: S.heading, fovH: 64, layers: S.layers }),
  side: () => {
    const s = sunNow(), ph = skyPhase(s.alt), mp = phaseNow();
    return \`<p class="split-time">\${hhmm(S.min)}</p>
      <p class="split-meta"><b>\${ph.name}</b><br>Sol \${deg(s.az)} \${wind(s.az)}<br>\${s.alt < -0.833 ? 'Bajo horizonte' : 'Elev. ' + deg(s.alt, 1)}</p>
      <p class="split-meta" style="display:flex;align-items:center;gap:5px">\${phaseGlyph(mp, 7, false)}<span>Luna \${Math.round(mp.illum * 100)} %</span></p>
      <div style="display:flex;gap:4px;margin-top:2px">
        <button class="icon-btn" style="width:32px;height:32px;border:var(--border-control)" type="button" data-act="shift" data-days="-1" data-key="prev" aria-label="Día anterior">\${icon('prev', 14)}</button>
        <button class="btn-now-sm" style="min-height:32px;padding:0 8px;flex:1" type="button" data-act="ar-now" data-key="now">Ahora</button>
        <button class="icon-btn" style="width:32px;height:32px;border:var(--border-control)" type="button" data-act="shift" data-days="1" data-key="next" aria-label="Día siguiente">\${icon('next', 14)}</button>
      </div>\`;
  },
  stPerm: () => statePermission('Solo cúpula plana'),
  stCal: () => stateCalibration('compact'),
  stOut: () => stateOutOfView(),
  stGround: () => stateGround(),
  land: () => \`<section class="land-phone" aria-label="Vista apaisada — 03 Cámara y cúpula">
    <div class="cam-view" style="flex:1;height:100%">
      \${arOverlay({
        w: 282, h: 254, heading: S.heading, pitch: S.pitch, min: S.min,
        fovH: 76, scene: S.scene, layers: S.layers, reticle: 'compact',
        showCrossingPill: true, edgePad: { t: 16, b: 16, x: 10 },
        ariaLabel: 'Cámara en formato apaisado sincronizada con cúpula lateral',
      })}
    </div>
    <div class="land-dome-panel">
      <div style="width:100%;display:flex;justify-content:space-between;align-items:baseline">
        <span style="font:300 20px/24px var(--font-serif)">\${hhmm(S.min)}</span>
        <span class="small muted">\${deg(S.heading)} \${wind(S.heading)}</span>
      </div>
      <div class="dome-tap-wrap" role="button" tabindex="0" data-act="aim-dome" aria-label="Toca la cúpula para girar la cámara">\${arDome({ size: 176, heading: S.heading, fovH: 76, layers: S.layers })}</div>
      <span style="font-size:10.5px;color:var(--text-secondary)">Toca la cúpula para orientar</span>
    </div>
  </section>\`,
  bright: () => brightSkyProof('compact'),
  noSensor: () => stateNoSensor(),
});`,
});

fs.mkdirSync(path.join(OUT, 'cenit-stage-1'), { recursive: true });
fs.mkdirSync(path.join(OUT, 'cenit-stage-2'), { recursive: true });
fs.writeFileSync(path.join(OUT, 'system.html'), sys);
fs.writeFileSync(path.join(OUT, 'cenit-stage-1', '01-almanaque.html'), a01);
fs.writeFileSync(path.join(OUT, 'cenit-stage-1', '02-cupula.html'), a02);
fs.writeFileSync(path.join(OUT, 'cenit-stage-1', '03-linea-del-dia.html'), a03);
fs.writeFileSync(path.join(OUT, 'cenit-stage-1', 'design.json'), JSON.stringify({
  title: 'Cenit · etapa 1',
  brief: 'Pantallas de la primera etapa de Cenit (planificador de sol y luna para fotografía, Android, sin conexión): Hoy con horas y planificador de fecha/hora, Cielo con la gráfica de elevación, Brújula y Lugares. Referencia principal: Sun Seeker (y Sun Locator). Estilo Scrolless/Sereno.',
  feature: 'odd/tasks/cenit-stage-1.md',
  artboards: [
    { file: '01-almanaque.html', title: '01 · Almanaque', tradeoff: 'Todas las horas a la vista sin scroll y lo próximo en grande; familiar y denso, el «dónde» queda en Brújula', width: 1680, height: 1060 },
    { file: '02-cupula.html', title: '02 · Cúpula (brújula plana)', tradeoff: 'La más cercana a Sun Seeker (brújula plana con horas, noche, solsticios); responde «hacia dónde mirar» de un vistazo; la lista completa de horas está a un toque', width: 1680, height: 1060 },
    { file: '03-linea-del-dia.html', title: '03 · Línea del día', tradeoff: 'Planificar fecha y hora y comparar la semana es lo principal; muestra menos la geometría del cielo', width: 1680, height: 1060 },
  ],
}, null, 2) + '\n');

fs.writeFileSync(path.join(OUT, 'cenit-stage-2', '01-visor.html'), s2_01);
fs.writeFileSync(path.join(OUT, 'cenit-stage-2', '02-mira.html'), s2_02);
fs.writeFileSync(path.join(OUT, 'cenit-stage-2', '03-camara-y-cupula.html'), s2_03);
fs.writeFileSync(path.join(OUT, 'cenit-stage-2', 'design.json'), JSON.stringify({
  title: 'Cenit · etapa 2 (cámara AR)',
  brief: 'Pantalla de cámara AR para planificar la luz en directo sobre el encuadre (sol, luna, solsticios, horizonte, mira con ángulos y cruce horario, deslizador de hora y estados clave). Referencias: Sun Seeker y Sun Locator. Estilo Scrolless/Sereno (design/system.md).',
  feature: 'odd/tasks/cenit-stage-2.md',
  artboards: [
    { file: '01-visor.html', title: '01 · Visor', tradeoff: 'Máximo campo de cámara tipo visor con cinta de rumbo arriba y controles mínimos abajo; las horas del día quedan fuera de pantalla', width: 1580, height: 1020 },
    { file: '02-mira.html', title: '02 · Mira', tradeoff: 'Mira central protagonista con lectura contextual de cruce solar y hoja inferior con horas clave; el visor de cámara tiene menos altura', width: 1580, height: 1020 },
    { file: '03-camara-y-cupula.html', title: '03 · Cámara + cúpula', tradeoff: 'Une la cámara arriba con la cúpula plana de la etapa ① abajo (toca la cúpula para apuntar); recorta la altura de cámara', width: 1580, height: 1020 },
  ],
}, null, 2) + '\n');
console.log('ok');
