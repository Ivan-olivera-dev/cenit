/* Builds self-contained Cenit artboards: inlines base.css, astro.js and lib.js into each HTML file. */
const fs = require('fs'), path = require('path');
const OUT = process.argv[2];
const css = fs.readFileSync(path.join(__dirname, 'base.css'), 'utf8');
const js = fs.readFileSync(path.join(__dirname, 'astro.js'), 'utf8').replace("if (typeof module !== 'undefined') module.exports = Astro;", '') + '\n' + fs.readFileSync(path.join(__dirname, 'lib.js'), 'utf8');

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

fs.mkdirSync(path.join(OUT, 'cenit-stage-1'), { recursive: true });
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
console.log('ok');
