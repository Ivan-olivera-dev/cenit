// Brand templates for Cenit.
// Every color token comes from design/system.md.

export const TOKENS = {
  dark: {
    bg: '#111318',
    surface: '#171A21',
    border: '#272B35',
    textPrimary: '#E9E7E2',
    textSecondary: '#A6ABB5',
    controlBorder: '#6C7382',
    sun: '#D6C07E',
    moon: '#AFBDE3',
  },
  light: {
    bg: '#FAF9F6',
    surface: '#F3F1EC',
    surfaceSubtle: '#ECE9E2',
    border: '#E6E3DC',
    controlBorder: '#857F74',
    textPrimary: '#2C2C2C',
    textSecondary: '#5C5A54',
    sun: '#7A6220',
    moon: '#3D5896',
  },
};

const FONT_SANS = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans", sans-serif';
const FONT_SERIF = '"Iowan Old Style", "Noto Serif", Georgia, serif';

/**
 * Cenit logo-mark (design/system.md):
 * semicircular arc from horizon to horizon, sun dot at the zenith apex,
 * horizon line and three decreasing reflection lines.
 */
export function markSvgInner({ stroke, sun, reflection, scale = 1 }) {
  const transform =
    scale === 1 ? '' : ` transform="translate(512 512) scale(${scale}) translate(-512 -512)"`;
  return `<g${transform} fill="none" stroke-linecap="round">
    <path d="M 272 520 A 240 240 0 0 1 414.4 300.8" stroke="${stroke}" stroke-width="34"/>
    <path d="M 609.6 300.8 A 240 240 0 0 1 752 520" stroke="${stroke}" stroke-width="34"/>
    <circle cx="512" cy="280" r="54" fill="${sun}"/>
    <line x1="156" y1="520" x2="868" y2="520" stroke="${stroke}" stroke-width="34"/>
    <line x1="322" y1="616" x2="702" y2="616" stroke="${reflection}" stroke-width="32"/>
    <line x1="396" y1="700" x2="628" y2="700" stroke="${reflection}" stroke-width="32"/>
    <line x1="460" y1="784" x2="564" y2="784" stroke="${reflection}" stroke-width="32"/>
  </g>`;
}

export function iconSvg(size = 1024) {
  const t = TOKENS.dark;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <rect x="0" y="0" width="1024" height="1024" rx="228" ry="228" fill="${t.bg}"/>
  ${markSvgInner({ stroke: t.textPrimary, sun: t.sun, reflection: t.moon, scale: 1 })}
</svg>`;
}

export function markOnlySvg({ onDark, size = 1024 }) {
  const t = onDark ? TOKENS.dark : TOKENS.light;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  ${markSvgInner({ stroke: t.textPrimary, sun: t.sun, reflection: t.moon, scale: 1.16 })}
</svg>`;
}

export function svgPageHtml(svg, width, height, background = 'transparent') {
  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    width: ${width}px;
    height: ${height}px;
    overflow: hidden;
    background: ${background};
  }
  svg { display: block; width: ${width}px; height: ${height}px; }
</style>
</head>
<body>${svg}</body>
</html>`;
}

export function bannerHtml({ line1, line2, meta }) {
  const t = TOKENS.dark;
  const tileMark = `<svg xmlns="http://www.w3.org/2000/svg" width="228" height="228" viewBox="0 0 1024 1024">
    ${markSvgInner({ stroke: t.textPrimary, sun: t.sun, reflection: t.moon, scale: 1 })}
  </svg>`;

  return `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    width: 1600px;
    height: 640px;
    overflow: hidden;
    background: ${t.bg};
    color: ${t.textPrimary};
    font-family: ${FONT_SANS};
    -webkit-font-smoothing: antialiased;
    text-rendering: geometricPrecision;
  }
  .canvas {
    width: 1600px;
    height: 640px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 96px;
  }
  .lockup {
    display: flex;
    align-items: center;
    gap: 68px;
  }
  .icon-tile {
    width: 232px;
    height: 232px;
    flex: 0 0 232px;
    border-radius: 52px;
    background: ${t.surface};
    border: 1.5px solid ${t.border};
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .copy {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }
  .wordmark {
    font-family: ${FONT_SERIF};
    font-size: 132px;
    font-weight: 400;
    line-height: 0.96;
    letter-spacing: -0.025em;
    color: ${t.textPrimary};
    margin-bottom: 24px;
  }
  .tagline {
    font-family: ${FONT_SANS};
    font-size: 29px;
    font-weight: 400;
    line-height: 1.42;
    letter-spacing: -0.005em;
    color: ${t.textSecondary};
  }
  .meta {
    margin-top: 18px;
    font-family: ${FONT_SANS};
    font-size: 20px;
    font-weight: 400;
    line-height: 1.3;
    letter-spacing: 0.01em;
    color: #828998;
  }
</style>
</head>
<body>
  <div class="canvas">
    <div class="lockup">
      <div class="icon-tile">${tileMark}</div>
      <div class="copy">
        <div class="wordmark">cenit</div>
        <div class="tagline">${line1}<br>${line2}</div>
        <div class="meta">${meta}</div>
      </div>
    </div>
  </div>
</body>
</html>`;
}

export function showcaseSpec({ leftPath, centerPath, rightPath }) {
  const light = TOKENS.light;
  const dark = TOKENS.dark;

  // 2400x1500 canvas, 1080x2412 phone ratio (1 : 2.2333)
  // Three phones centered with 112px gaps: 264 + 536 + 112 + 586 + 112 + 536 + 254 ≈ centered
  const sideW = 536;
  const sideH = 1197;
  const centerW = 586;
  const centerH = 1308;
  const gap = 112;
  const totalW = sideW * 2 + centerW + gap * 2;
  const startX = Math.round((2400 - totalW) / 2);

  return {
    width: 2400,
    height: 1500,
    background: light.surfaceSubtle,
    phones: [
      {
        imagePath: leftPath,
        x: startX,
        y: 180,
        width: sideW,
        height: sideH,
        radius: 40,
        borderColor: dark.border,
        borderWidth: 2,
      },
      {
        imagePath: centerPath,
        x: startX + sideW + gap,
        y: 96,
        width: centerW,
        height: centerH,
        radius: 44,
        borderColor: '#CFCAC0',
        borderWidth: 2,
      },
      {
        imagePath: rightPath,
        x: startX + sideW + gap + centerW + gap,
        y: 180,
        width: sideW,
        height: sideH,
        radius: 40,
        borderColor: dark.border,
        borderWidth: 2,
      },
    ],
  };
}
