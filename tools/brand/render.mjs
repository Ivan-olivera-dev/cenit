#!/usr/bin/env node
// Reproducibly renders Cenit's brand assets into docs/brand/.
// Uses macOS WebKit (for SVG/HTML icons and banners) and CoreGraphics (for
// high-resolution screenshot showcases) via tools/brand/rasterize.swift.

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  bannerHtml,
  iconSvg,
  markOnlySvg,
  showcaseSpec,
  svgPageHtml,
} from './templates.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const OUT_DIR = path.join(REPO_ROOT, 'docs/brand');
const SWIFT_SCRIPT = path.join(__dirname, 'rasterize.swift');

function readPngInfo(filePath) {
  const buf = fs.readFileSync(filePath);
  if (buf.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') {
    throw new Error(`${filePath} is not a valid PNG`);
  }
  return {
    width: buf.readUInt32BE(16),
    height: buf.readUInt32BE(20),
    colorType: buf[25],
    bytes: buf.length,
  };
}

function repoAsset(relPath) {
  const abs = path.join(REPO_ROOT, relPath);
  if (!fs.existsSync(abs)) {
    throw new Error(`Missing input asset: ${relPath}`);
  }
  return abs;
}

function main() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cenit-brand-'));

  const htmlSpecs = [
    {
      name: 'cenit-icon-1024.png',
      width: 1024,
      height: 1024,
      transparent: true,
      html: svgPageHtml(iconSvg(1024), 1024, 1024),
    },
    {
      name: 'cenit-icon-512.png',
      width: 512,
      height: 512,
      transparent: true,
      html: svgPageHtml(iconSvg(512), 512, 512),
    },
    {
      name: 'cenit-mark-on-dark.png',
      width: 1024,
      height: 1024,
      transparent: true,
      html: svgPageHtml(markOnlySvg({ onDark: true, size: 1024 }), 1024, 1024),
    },
    {
      name: 'cenit-mark-on-light.png',
      width: 1024,
      height: 1024,
      transparent: true,
      html: svgPageHtml(markOnlySvg({ onDark: false, size: 1024 }), 1024, 1024),
    },
    {
      name: 'cenit-banner.png',
      width: 1600,
      height: 640,
      transparent: false,
      html: bannerHtml({
        line1: 'Sun and moon planner for photographers.',
        line2: 'Golden hour, blue hour, twilights, eclipses. Works offline.',
        meta: 'Open source · No Internet permission · Android',
      }),
    },
    {
      name: 'cenit-banner-es.png',
      width: 1600,
      height: 640,
      transparent: false,
      html: bannerHtml({
        line1: 'Planificador de sol y luna para fotografía.',
        line2: 'Hora dorada, hora azul, crepúsculos y eclipses. Sin conexión.',
        meta: 'Código abierto · Sin permiso de internet · Android',
      }),
    },
  ];

  const showcaseSpecs = [
    {
      name: 'cenit-showcase.png',
      ...showcaseSpec({
        leftPath: repoAsset('docs/screenshots/en/today-dark.png'),
        centerPath: repoAsset('docs/screenshots/en/sky-light.png'),
        rightPath: repoAsset('docs/screenshots/en/camera-dark.png'),
      }),
    },
    {
      name: 'cenit-showcase-es.png',
      ...showcaseSpec({
        leftPath: repoAsset('docs/screenshots/es/hoy-dark.png'),
        centerPath: repoAsset('docs/screenshots/es/cielo-light.png'),
        rightPath: repoAsset('docs/screenshots/es/camara-dark.png'),
      }),
    },
  ];

  try {
    const htmlJobs = htmlSpecs.map((spec) => {
      const htmlPath = path.join(tmpDir, `${spec.name}.html`);
      fs.writeFileSync(htmlPath, spec.html, 'utf8');
      return {
        htmlPath,
        outPath: path.join(OUT_DIR, spec.name),
        width: spec.width,
        height: spec.height,
        transparent: spec.transparent,
      };
    });

    const showcaseJobs = showcaseSpecs.map(({ name, ...rest }) => ({
      outPath: path.join(OUT_DIR, name),
      ...rest,
    }));

    const manifestPath = path.join(tmpDir, 'manifest.json');
    fs.writeFileSync(
      manifestPath,
      JSON.stringify({ readAccessRoot: tmpDir, htmlJobs, showcaseJobs }, null, 2),
      'utf8',
    );

    execFileSync('swift', [SWIFT_SCRIPT, manifestPath], { stdio: 'inherit' });

    const expected = [
      ...htmlSpecs.map((s) => ({ name: s.name, width: s.width, height: s.height })),
      ...showcaseSpecs.map((s) => ({ name: s.name, width: s.width, height: s.height })),
    ];
    for (const item of expected) {
      const outPath = path.join(OUT_DIR, item.name);
      const info = readPngInfo(outPath);
      if (info.width !== item.width || info.height !== item.height) {
        throw new Error(
          `${item.name}: expected ${item.width}x${item.height}, got ${info.width}x${info.height}`,
        );
      }
      const mode = info.colorType === 6 ? 'RGBA' : info.colorType === 2 ? 'RGB' : `type=${info.colorType}`;
      console.log(
        `  ${item.name.padEnd(26)} ${info.width}x${info.height}  ${mode.padEnd(4)}  ${(info.bytes / 1024).toFixed(1)} KB`,
      );
    }
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

main();
