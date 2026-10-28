// Composes a mock in-game scene from the sprite atlases to check scale and look: node preview.mjs out.png
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const here = path.dirname(new URL(import.meta.url).pathname);
const dir = path.resolve(here, '../composeApp/src/commonMain/composeResources/drawable');
const kt = fs.readFileSync(path.resolve(here, '../composeApp/src/commonMain/kotlin/fr/kingshot/game/SpriteData.kt'), 'utf8');
const info = {};
for (const m of kt.matchAll(/val (\w+) = SpriteInfo\((\d+), (\d+), ([\d.]+)f, ([\d.]+)f, ([\d.]+)f, (\d+), (\d+)\)/g))
  info[m[1]] = { w: +m[2], h: +m[3], ax: +m[4], ay: +m[5], ppu: +m[6] };
const imgs = {};
for (const f of fs.readdirSync(dir)) imgs[f.replace('.png', '').replace(/_(\w)/g, (_, c) => c.toUpperCase())] = 'data:image/png;base64,' + fs.readFileSync(path.join(dir, f)).toString('base64');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 540, height: 1080 } });
await page.setContent('<canvas id=c width=1080 height=2160 style="width:540px;height:1080px"></canvas>');
const png = await page.evaluate(async ({ imgs, info }) => {
  const load = src => new Promise(r => { const i = new Image(); i.onload = () => r(i); i.src = src; });
  const I = {}; for (const k in imgs) I[k] = await load(imgs[k]);
  const c = document.getElementById('c'), g = c.getContext('2d');
  const S = 1080 / 720; // world -> px (portrait phone)
  const camX = 900, camY = 1560;
  const W = (x, y) => [(x - camX) * S + 540, (y - camY) * S + 1080];
  g.fillStyle = '#8DC24F'; g.fillRect(0, 0, 1080, 2160);
  // roads
  g.lineCap = g.lineJoin = 'round';
  const roads = [[1720,-80,1580,380,1270,690,1160,1120,1030,1545],[2100,1280,1720,1340,1420,1560,1095,1650],[640,2500,780,2120,960,1900,1000,1750],[-50,1980,420,1860,760,1750,900,1690],[690,1500,760,1640,900,1680]];
  for (const [col, w] of [['#CDA36C', 104], ['#DDB57F', 88]]) for (const r of roads) { g.strokeStyle = col; g.lineWidth = w * S; g.beginPath(); for (let i = 0; i < r.length; i += 2) { const [x, y] = W(r[i], r[i+1]); i ? g.lineTo(x, y) : g.moveTo(x, y); } g.stroke(); }
  const spr = (name, col, row, x, y, k) => { const f = info[name]; const s = k / f.ppu * S; const [sx, sy] = W(x, y); g.drawImage(I[name], col * f.w, row * f.h, f.w, f.h, sx - f.ax * s, sy - f.ay * s, f.w * s, f.h * s); };
  // pad
  const pad = (x, y, frac) => { const [sx, sy] = W(x - 55, y - 55); g.fillStyle = 'rgba(64,74,30,.4)'; g.fillRect(sx, sy, 110*S, 110*S); g.fillStyle = '#4CDB3A'; g.fillRect(sx, sy, 110*S*frac, 110*S); };
  pad(720, 1600, 0.4); pad(1330, 1010, 0);
  const items = [
    ['castle', 0, 0, 1000, 1620, 0.9], ['house', 0, 0, 690, 1410, 1], ['pine0', 0, 0, 420, 1320, 1.1], ['pine1', 0, 0, 1350, 1300, 1], ['roundtree', 0, 0, 560, 2050, 1.2], ['rock0', 0, 0, 1250, 2000, 1],
    ['tower', 0, 0, 1560, 1600, 1],
    ['king', 1, 2, 830, 1750, 1.3],
    ...[[-46,0],[46,0],[0,-38],[0,38],[-30,30],[30,-30]].map(([dx, dy], i) => ['archer', i % 2 ? 2 : 8, i % 3, 830 + dx, 1760 + dy * 0.8, 1.3]),
    ['knight', 1, 6, 1200, 1900, 1.3], ['knight', 3, 6, 1250, 1950, 1.3], ['knight', 7, 7, 1150, 1960, 1.82], ['boss', 1, 7, 1100, 2150, 1.3],
    ['coinSpin', 0, 0, 1000, 1880, 1], ['coinSpin', 3, 0, 1030, 1900, 1],
  ].sort((a, b) => a[4] - b[4]);
  for (const it of items) spr(...it);
  for (let i = 0; i < 20; i++) spr('coinFlat', 0, 0, 830, 1750 - 70 - i * 4.5, 1);
  for (let k = 0; k < 9; k++) spr('stake', 0, 0, 1430 + k * 6, 1530 + k * 19, 1);
  return c.toDataURL('image/png');
}, { imgs, info });
fs.writeFileSync(process.argv[2], Buffer.from(png.split(',')[1], 'base64'));
await browser.close();
