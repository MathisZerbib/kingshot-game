// Low-poly, cel-shaded 3D models for Kingshot, rendered offline into isometric sprite atlases.
// Units are game world units; every model stands on y = 0 and faces +Z (toward the camera).
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

// Three-band toon ramp: deep shade, mid tone, lit.
const ramp = new THREE.DataTexture(new Uint8Array([118, 118, 118, 255, 186, 186, 186, 255, 255, 255, 255, 255]), 3, 1, THREE.RGBAFormat);
ramp.minFilter = ramp.magFilter = THREE.NearestFilter;
ramp.needsUpdate = true;

const matCache = new Map();
export function mat(color, { emissive = 0x000000, shiny = false } = {}) {
  const key = `${color}|${emissive}|${shiny}`;
  if (!matCache.has(key)) {
    const m = new THREE.MeshToonMaterial({ color, gradientMap: ramp, emissive });
    if (shiny) m.emissiveIntensity = 0.6;
    matCache.set(key, m);
  }
  return matCache.get(key);
}

export const outlineMaterial = new THREE.MeshBasicMaterial({ color: 0x2a2320, side: THREE.BackSide, polygonOffset: true, polygonOffsetFactor: 1, polygonOffsetUnits: 2 });

function mesh(geo, material, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geo, material);
  m.position.set(x, y, z);
  m.castShadow = true;
  m.receiveShadow = true;
  return m;
}
const rbox = (w, h, d, r, material, x, y, z) => mesh(new RoundedBoxGeometry(w, h, d, 4, Math.min(r, w / 2, h / 2, d / 2) - 0.001), material, x, y, z);
const box = (w, h, d, material, x, y, z) => mesh(new THREE.BoxGeometry(w, h, d), material, x, y, z);
const sphere = (r, material, x, y, z, ws = 24, hs = 16) => mesh(new THREE.SphereGeometry(r, ws, hs), material, x, y, z);
const cyl = (rt, rb, h, material, x, y, z, seg = 16) => mesh(new THREE.CylinderGeometry(rt, rb, h, seg), material, x, y, z);
const cone = (r, h, material, x, y, z, seg = 10) => mesh(new THREE.ConeGeometry(r, h, seg), material, x, y, z);
/** Marks a mesh so the renderer does not draw an ink outline around it. */
const noInk = m => { m.userData.noOutline = true; return m; };

/** Adds an inverted-hull ink outline to every mesh under root (thickness in world units). */
export function addOutlines(root, thickness = 0.9) {
  const targets = [];
  root.traverse(o => { if (o.isMesh && !o.userData.noOutline && !o.userData.isOutline) targets.push(o); });
  for (const m of targets) {
    if (!m.geometry.boundingSphere) m.geometry.computeBoundingSphere();
    const r = m.geometry.boundingSphere.radius;
    if (r < 2.2) continue;
    const o = new THREE.Mesh(m.geometry, outlineMaterial);
    o.scale.setScalar(1 + thickness / r);
    o.userData.isOutline = true;
    m.add(o);
  }
}

/** A pivot group positioned at (x,y,z); children hang from it. */
function pivot(parent, x, y, z) {
  const g = new THREE.Group();
  g.position.set(x, y, z);
  parent.add(g);
  return g;
}

const C = {
  skin: 0xf6c9a4, skinDark: 0xe0a07c, hair: 0x26222a, shirt: 0xf6f0e6, shorts: 0x7a5538, boot: 0x4a3326,
  blue: 0x2f6ad8, blueDark: 0x1d47a8, bow: 0x4a90ee, wood: 0xb0733f, woodDark: 0x6e4325, plank: 0xc8894c,
  red: 0xe02a2e, redLight: 0xf04444, redDark: 0x9c1820, steel: 0x5e6370, silver: 0xdfe4ea,
  gold: 0xffc531, goldDark: 0xd9901a, black: 0x1b1b1f, white: 0xffffff,
  horse: 0xe3c79b, horseDark: 0xa07048, mane: 0x5a3a22,
  stone: 0xb2b7bf, stoneDark: 0x858b94, roof: 0x9a5f36, roofDark: 0x6b4228,
  leaf: 0x6db83c, leafDark: 0x4f9a2a, leafLight: 0x92d44e, rock: 0xa89d98, rockLight: 0xc9bfba,
  pale: 0xf2e8e2, paleDark: 0xd8c8c0,
};

// ------------------------------------------------------------------ archer

export function makeArcher() {
  const root = new THREE.Group();
  const body = pivot(root, 0, 0, 0);
  const rig = { root, body };
  rig.legL = pivot(body, -3.8, 10, 0);
  rig.legR = pivot(body, 3.8, 10, 0);
  for (const leg of [rig.legL, rig.legR]) {
    leg.add(rbox(5.6, 6, 6, 2, mat(C.shorts), 0, -2.5, 0));
    leg.add(cyl(2.2, 2.4, 5, mat(C.skin), 0, -6.5, 0, 10));
    leg.add(rbox(5.6, 3.4, 7.4, 1.5, mat(C.boot), 0, -9, 0.9));
  }
  const torso = pivot(body, 0, 10, 0);
  rig.torso = torso;
  torso.add(rbox(15, 15, 10.5, 4, mat(C.shirt), 0, 7.5, 0)); // tank top
  torso.add(rbox(15.6, 2.4, 11, 1, mat(C.woodDark), 0, 1.6, 0)); // belt
  torso.add(box(3.2, 1.8, 1, mat(C.gold, { shiny: true }), 0, 1.6, 5.6));
  const strap = rbox(4.4, 15, 1.6, 0.7, mat(C.blue), -3.6, 8, 5.2); // chest strap
  strap.rotation.z = 0.35;
  torso.add(strap);
  torso.add(rbox(15.6, 13, 2.6, 1, mat(C.blue), 0, 7.5, -5.8)); // cape
  const q = cyl(2.6, 2.3, 13, mat(C.woodDark), 4.4, 8.5, -7.2, 10);
  q.rotation.z = -0.35;
  torso.add(q);
  for (let i = 0; i < 3; i++) noInk(torso.add(box(1.2, 4, 1.2, mat(C.silver), 5.6 + i * 1.4, 16.5 - i * 0.6, -7.2 + i * 0.5)) || torso.children.at(-1));
  // head: big round head with a bowl cut
  const head = pivot(torso, 0, 15, 0);
  rig.head = head;
  head.add(sphere(11, mat(C.skin), 0, 9.5, 0));
  const hair = mesh(new THREE.SphereGeometry(11.7, 26, 18, 0, Math.PI * 2, 0, Math.PI * 0.55), mat(C.hair), 0, 10.2, -0.6);
  hair.rotation.x = -0.28;
  head.add(hair);
  head.add(rbox(16, 3, 10, 1.2, mat(C.hair), 0, 19.4, -1)); // flat top of the bowl
  const fringe = rbox(13, 3.2, 3, 1, mat(C.hair), 0, 14.6, 10.2);
  fringe.rotation.x = 0.25;
  head.add(fringe);
  // eyes and angry brows
  for (const s of [-1, 1]) {
    noInk(head.add(box(2.2, 3.2, 0.8, mat(C.black), 3.9 * s, 8.6, 10.4)) || head.children.at(-1));
    noInk(head.add(box(0.8, 1, 0.6, mat(C.white), 3.5 * s, 9.4, 10.9)) || head.children.at(-1));
    const brow = box(4.2, 1.2, 0.8, mat(C.hair), 3.9 * s, 11.4, 10.2);
    brow.rotation.z = -0.4 * s;
    noInk(brow); head.add(brow);
    noInk(head.add(sphere(1.4, mat(0xf0a888), 6.2 * s, 6.8, 9.2, 8, 6)) || head.children.at(-1)); // cheeks
  }
  noInk(head.add(box(2.2, 0.8, 0.6, mat(C.skinDark), 0, 6.4, 10.9)) || head.children.at(-1)); // mouth
  // arms (bare)
  rig.armL = pivot(torso, -8.6, 12.5, 0);
  rig.armR = pivot(torso, 8.6, 12.5, 0);
  for (const arm of [rig.armL, rig.armR]) {
    arm.add(sphere(3.2, mat(C.shirt), 0, 0, 0, 12, 8)); // shoulder
    arm.add(cyl(2.3, 2.1, 9, mat(C.skin), 0, -5, 0, 10));
    arm.add(sphere(2.8, mat(C.skin), 0, -10, 0, 10, 8));
  }
  // bow in the left hand
  const bow = new THREE.Group();
  bow.position.set(0, -10, 1);
  const limb = mesh(new THREE.TorusGeometry(12, 1.5, 8, 24, Math.PI), mat(C.bow));
  limb.rotation.z = -Math.PI / 2;
  bow.add(limb);
  bow.add(cyl(1.9, 1.9, 6, mat(C.woodDark), 12, 0, 0, 8)); // grip
  const string = noInk(cyl(0.35, 0.35, 24, mat(C.white), -0.2, 0, 0, 4));
  rig.string = string;
  bow.add(string);
  rig.armL.add(bow);
  rig.bow = bow;
  const arrow = new THREE.Group();
  arrow.add(noInk(cyl(0.5, 0.5, 22, mat(C.woodDark), 0, 0, 0, 5)));
  arrow.add(noInk(cone(1.3, 3.5, mat(C.silver), 0, 12, 0, 5)));
  arrow.add(noInk(box(0.4, 3, 2.4, mat(C.red), 0, -9, 0)));
  arrow.rotation.x = Math.PI / 2;
  rig.arrow = arrow;
  root.add(arrow);
  return rig;
}

/** walk: t in [0,1) ; shoot: t in [0,1] (0 aim .. 1 release) */
export function poseArcher(rig, mode, t) {
  const { body, legL, legR, armL, armR, torso, bow, arrow, head } = rig;
  body.position.y = 0; torso.rotation.set(0, 0, 0); head.rotation.set(0, 0, 0);
  legL.rotation.set(0, 0, 0); legR.rotation.set(0, 0, 0);
  armL.rotation.set(0, 0, 0); armR.rotation.set(0, 0, 0);
  bow.rotation.set(0, 0, 0);
  arrow.visible = false;
  if (mode === 'walk') {
    const p = t * Math.PI * 2;
    legL.rotation.x = Math.sin(p) * 0.7;
    legR.rotation.x = -Math.sin(p) * 0.7;
    armL.rotation.x = -Math.sin(p) * 0.5;
    armR.rotation.x = Math.sin(p) * 0.5;
    body.position.y = Math.abs(Math.cos(p)) * 1.6;
    torso.rotation.z = Math.sin(p) * 0.05;
    head.rotation.z = -Math.sin(p) * 0.04;
  } else {
    armL.rotation.x = -Math.PI / 2;
    bow.rotation.y = Math.PI / 2;
    const pull = mode === 'shoot' ? Math.sin(Math.min(1, t) * Math.PI * 0.9) : 0;
    armR.rotation.x = -Math.PI / 2 + 0.2;
    armR.rotation.z = 0.5 + pull * 0.4;
    torso.rotation.y = -0.25;
    arrow.visible = t < 0.85;
    arrow.position.set(-6, 23.5, 6 + 6 * (1 - pull));
  }
}

// ------------------------------------------------------------------ knight / boss

export function makeKnight(boss = false) {
  const main = boss ? C.pale : C.red;
  const light = boss ? C.white : C.redLight;
  const dark = boss ? C.paleDark : C.redDark;
  const root = new THREE.Group();
  const body = pivot(root, 0, 0, 0);
  const rig = { root, body };
  rig.legL = pivot(body, -4.6, 12, 0);
  rig.legR = pivot(body, 4.6, 12, 0);
  for (const leg of [rig.legL, rig.legR]) {
    leg.add(rbox(6.6, 10, 6.8, 2.4, mat(C.steel), 0, -5, 0));
    leg.add(rbox(7.2, 3.8, 8.6, 1.8, mat(0x3a3d44), 0, -10.4, 1));
  }
  const torso = pivot(body, 0, 12, 0);
  rig.torso = torso;
  torso.add(rbox(22, 18, 16, 6, mat(main), 0, 9, 0));
  torso.add(rbox(16, 11, 2.6, 2, mat(light), 0, 10.5, 7.6)); // breastplate
  torso.add(rbox(22.6, 3, 16.6, 1.2, mat(0x2c2224), 0, 1.8, 0)); // belt
  torso.add(box(4, 2.4, 1, mat(C.gold, { shiny: true }), 0, 1.8, 8.3));
  for (const s of [-1, 1]) torso.add(rbox(8, 7, 11, 3, mat(dark), 12.5 * s, 15, 0)); // pauldrons
  const head = pivot(torso, 0, 18, 0);
  rig.head = head;
  head.add(rbox(22, 21, 21, boss ? 10 : 8, mat(light), 0, 10.5, 0));
  head.add(rbox(16, 9, 2.4, 2, mat(dark), 0, 8.5, 10.4)); // visor plate
  for (let i = -2; i <= 2; i++) noInk(head.add(box(1.4, 6, 1, mat(C.black), i * 2.7, 8, 11.6)) || head.children.at(-1));
  noInk(head.add(box(15, 2.2, 1, mat(C.black), 0, 13.2, 10.6)) || head.children.at(-1)); // eye slit
  if (!boss) head.add(box(23, 2.4, 21.6, mat(dark), 0, 16.2, 0)); // brim
  if (boss) {
    for (const s of [-1, 1]) {
      const horn = cone(2.8, 12, mat(0xf4ead6), 11.5 * s, 23, 0, 8);
      horn.rotation.z = -0.55 * s;
      head.add(horn);
    }
    const band = mesh(new THREE.TorusGeometry(11.6, 1.5, 8, 24), mat(C.gold, { shiny: true }), 0, 15, 0); // gold band
    band.rotation.x = Math.PI / 2;
    head.add(band);
  } else {
    // curved plume
    const plume = mesh(new THREE.TorusGeometry(9, 3, 8, 16, Math.PI * 0.85), mat(main));
    plume.rotation.y = Math.PI / 2;
    plume.rotation.x = 0.1;
    plume.position.set(0, 18, 0);
    head.add(plume);
  }
  rig.armL = pivot(torso, -13, 14, 0);
  rig.armR = pivot(torso, 13, 14, 0);
  for (const arm of [rig.armL, rig.armR]) {
    arm.add(rbox(6.4, 10, 6.4, 2.8, mat(main), 0, -5, 0));
    arm.add(sphere(3.4, mat(0x3a3d44), 0, -10.5, 0, 12, 8));
  }
  // weapon in the right hand
  const weapon = new THREE.Group();
  weapon.position.set(0, -10.5, 0);
  if (boss) {
    weapon.add(cyl(1.6, 1.6, 34, mat(C.woodDark), 0, 14, 0, 8));
    weapon.add(sphere(9, mat(C.steel), 0, 34, 0, 12, 8));
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * Math.PI * 2;
      const el = i % 2 ? 0.5 : -0.5;
      const sp = cone(1.8, 6, mat(C.silver), Math.cos(a) * Math.cos(el) * 9, 34 + Math.sin(el) * 9, Math.sin(a) * Math.cos(el) * 9, 5);
      sp.lookAt(new THREE.Vector3(Math.cos(a) * Math.cos(el) * 30, 34 + Math.sin(el) * 30, Math.sin(a) * Math.cos(el) * 30));
      sp.rotateX(Math.PI / 2);
      weapon.add(sp);
    }
  } else {
    weapon.add(cyl(1.4, 1.4, 5, mat(C.woodDark), 0, 0, 0, 8));
    weapon.add(box(9, 1.8, 2.6, mat(C.gold, { shiny: true }), 0, 3, 0));
    weapon.add(box(3, 24, 1.1, mat(C.silver), 0, 15, 0));
    weapon.add(cone(1.5, 4, mat(C.silver), 0, 29, 0, 4));
  }
  weapon.rotation.x = Math.PI / 2 - 0.3;
  rig.armR.add(weapon);
  rig.weapon = weapon;
  return rig;
}

/** walk: t in [0,1); attack: t in [0,1] wind-up -> strike */
export function poseKnight(rig, mode, t) {
  const { body, legL, legR, armL, armR, torso, head } = rig;
  body.position.y = 0; torso.rotation.set(0, 0, 0); head.rotation.set(0, 0, 0);
  legL.rotation.set(0, 0, 0); legR.rotation.set(0, 0, 0);
  armL.rotation.set(0, 0, 0); armR.rotation.set(0, 0, 0);
  if (mode === 'walk') {
    const p = t * Math.PI * 2;
    legL.rotation.x = Math.sin(p) * 0.6;
    legR.rotation.x = -Math.sin(p) * 0.6;
    armL.rotation.x = -Math.sin(p) * 0.45;
    armR.rotation.x = -0.5 + Math.sin(p) * 0.25;
    body.position.y = Math.abs(Math.cos(p)) * 1.5;
    torso.rotation.y = Math.sin(p) * 0.08;
  } else {
    const raise = t < 0.5 ? t / 0.5 : 1 - (t - 0.5) / 0.5 * 1.6;
    armR.rotation.x = -0.5 - raise * 2.2;
    armL.rotation.x = -0.3;
    torso.rotation.y = -0.2 + raise * 0.15;
    torso.rotation.x = Math.max(0, -raise) * 0.25;
    legL.rotation.x = 0.35; legR.rotation.x = -0.25;
  }
}

// ------------------------------------------------------------------ king on horse

export function makeKing() {
  const root = new THREE.Group();
  const body = pivot(root, 0, 0, 0);
  const rig = { root, body, legs: [] };
  const horseMat = mat(C.horse);
  for (const [x, z] of [[-6.8, 13], [6.8, 13], [-6.8, -13], [6.8, -13]]) {
    const leg = pivot(body, x, 22, z);
    leg.add(rbox(5.6, 18, 5.8, 2.4, horseMat, 0, -9, 0));
    leg.add(rbox(5.9, 5, 6.2, 2, mat(C.white), 0, -16, 0)); // white sock
    leg.add(rbox(6.2, 3.2, 6.8, 1.4, mat(0x3a2a20), 0, -19.6, 0.4));
    rig.legs.push(leg);
  }
  const horse = pivot(body, 0, 0, 0);
  rig.horse = horse;
  horse.add(rbox(20, 18, 42, 8, horseMat, 0, 28, 0));
  horse.add(rbox(20.6, 9, 15, 3.5, mat(C.blue), 0, 33, -2)); // saddle blanket
  horse.add(rbox(21, 2, 15.4, 0.8, mat(C.gold, { shiny: true }), 0, 29, -2));
  horse.add(rbox(14, 5, 12, 2.5, mat(C.woodDark), 0, 38.5, -3)); // saddle
  const neck = rbox(11, 22, 12, 4.5, horseMat, 0, 41, 17);
  neck.rotation.x = 0.45;
  horse.add(neck);
  const headH = rbox(11, 11, 20, 4.5, horseMat, 0, 50, 25.5);
  headH.rotation.x = 0.35;
  horse.add(headH);
  horse.add(rbox(6, 9, 14, 2.5, mat(C.white), 0, 51, 27)); // blaze
  horse.add(rbox(9, 6.5, 6.5, 2.8, mat(0xcfae84), 0, 46, 34)); // muzzle
  for (const s of [-1, 1]) {
    horse.add(cone(2, 5.5, horseMat, 3.4 * s, 57, 20, 6));
    noInk(horse.add(box(1.6, 1.8, 1, mat(C.black), 5.6 * s, 51, 29)) || horse.children.at(-1));
    noInk(horse.add(sphere(0.9, mat(C.black), 2.4 * s, 44.5, 37.2, 6, 5)) || horse.children.at(-1)); // nostrils
  }
  for (let i = 0; i < 5; i++) horse.add(rbox(3.2, 6.5, 4.5, 1.4, mat(C.mane), 0, 48.5 - i * 3.6, 17.5 - i * 2.7));
  // reins
  const rein = noInk(mesh(new THREE.TorusGeometry(9, 0.5, 5, 16, Math.PI), mat(C.woodDark)));
  rein.rotation.y = Math.PI / 2; rein.rotation.z = Math.PI; rein.position.set(0, 46, 24);
  horse.add(rein);
  const tail = rbox(5.5, 18, 5.5, 2.6, mat(C.mane), 0, 27, -23);
  tail.rotation.x = -0.5;
  horse.add(tail);
  rig.tail = tail;
  // rider
  const rider = pivot(horse, 0, 37, -2);
  rig.rider = rider;
  for (const s of [-1, 1]) rider.add(rbox(5.2, 12, 9.5, 2.2, mat(C.blueDark), 10.5 * s, -2, 2));
  for (const s of [-1, 1]) rider.add(rbox(5.4, 4, 6, 1.6, mat(C.boot), 10.5 * s, -8.5, 4));
  rider.add(rbox(17, 17, 13, 5.5, mat(C.blue), 0, 8.5, 0));
  rider.add(rbox(13, 11, 3, 2.5, mat(C.silver), 0, 9.5, 6.2));
  rider.add(rbox(16, 3, 13.4, 1, mat(C.white), 0, 16, 0)); // collar
  rider.add(rbox(17.6, 3, 13.4, 1, mat(C.gold, { shiny: true }), 0, 1.2, 0)); // belt
  rider.add(rbox(18, 16, 2.8, 1.2, mat(C.blueDark), 0, 6, -6.8)); // cape
  for (const s of [-1, 1]) {
    const arm = pivot(rider, 9.8 * s, 14, 0);
    arm.add(rbox(6.5, 3.5, 6.5, 1.6, mat(C.silver), 0, 0.5, 0)); // pauldron
    arm.add(rbox(4.8, 10, 4.8, 2.2, mat(C.blue), 0, -4.5, 1.5));
    arm.add(sphere(2.7, mat(C.skin), 0, -9, 4, 10, 8));
    arm.rotation.x = -0.7;
  }
  const head = pivot(rider, 0, 18, 0);
  head.add(sphere(10.5, mat(C.skin), 0, 8.5, 0));
  const hair = mesh(new THREE.SphereGeometry(11.1, 26, 18, 0, Math.PI * 2, 0, Math.PI * 0.5), mat(0x6b4225), 0, 9.2, -0.6);
  hair.rotation.x = -0.3;
  head.add(hair);
  head.add(rbox(13, 6.5, 6, 3, mat(0x6b4225), 0, 3.4, 6)); // beard
  for (const s of [-1, 1]) {
    noInk(head.add(box(2.1, 3, 0.8, mat(C.black), 3.6 * s, 8.6, 9.9)) || head.children.at(-1));
    const brow = box(4, 1.2, 0.8, mat(0x4a2a15), 3.6 * s, 11.2, 9.7);
    brow.rotation.z = -0.3 * s;
    noInk(brow); head.add(brow);
  }
  const crown = pivot(head, 0, 15.5, 0);
  crown.add(cyl(8.5, 7.8, 5.5, mat(C.gold, { shiny: true }), 0, 0, 0, 18));
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    crown.add(cone(2.2, 5.5, mat(C.gold, { shiny: true }), Math.sin(a) * 7.4, 5, Math.cos(a) * 7.4, 6));
  }
  noInk(crown.add(sphere(1.7, mat(0xe0283a, { emissive: 0x400000 }), 0, 0.5, 8.6, 8, 6)) || crown.children.at(-1));
  return rig;
}

export function poseKing(rig, mode, t) {
  const { body, legs, horse, rider, tail } = rig;
  const p = t * Math.PI * 2;
  for (const l of legs) l.rotation.set(0, 0, 0);
  body.position.y = 0; horse.rotation.set(0, 0, 0); rider.rotation.set(0, 0, 0); tail.rotation.x = -0.5;
  if (mode === 'walk') {
    legs[0].rotation.x = Math.sin(p) * 0.7;
    legs[1].rotation.x = Math.sin(p + 0.5) * 0.7;
    legs[2].rotation.x = Math.sin(p + Math.PI) * 0.7;
    legs[3].rotation.x = Math.sin(p + Math.PI + 0.5) * 0.7;
    body.position.y = Math.max(0, Math.sin(p * 2)) * 2.5;
    horse.rotation.x = Math.sin(p) * 0.06;
    rider.rotation.x = -Math.sin(p) * 0.05;
    tail.rotation.x = -0.8 + Math.sin(p) * 0.2;
  }
}

// ------------------------------------------------------------------ props

export function makeCoin(flat) {
  const g = new THREE.Group();
  const gold = mat(0xffcf3a, { emissive: 0x4a2a00 });
  const edge = mat(0xe39a1c, { emissive: 0x3a1c00 });
  const c = mesh(new THREE.CylinderGeometry(11, 11, 4, 28), [edge, gold, gold]);
  g.add(c);
  g.add(noInk(cyl(7.8, 7.8, 0.8, mat(0xf2a91e, { emissive: 0x3a1c00 }), 0, 2.1, 0, 24)));
  // crown emblem
  g.add(noInk(box(8, 0.9, 2.4, gold, 0, 2.8, 1.6)));
  for (const x of [-3, 0, 3]) g.add(noInk(box(1.6, 0.9, 4.2, gold, x, 2.8, -1)));
  if (!flat) { g.rotation.x = Math.PI / 2; g.position.y = 11; }
  else g.position.y = 2;
  const root = new THREE.Group();
  root.add(g);
  return { root, coin: g };
}

export function makePine(variant) {
  const g = new THREE.Group();
  g.add(cyl(3.5, 4.5, 24, mat(C.woodDark), 0, 12, 0, 8));
  const tiers = variant === 0 ? [[36, 36, 36], [28, 34, 62], [19, 32, 86]] : [[32, 32, 32], [25, 30, 56], [18, 30, 78], [10, 22, 98]];
  tiers.forEach(([r, h, y], i) => {
    const c = cone(r, h, mat(i % 2 ? C.leaf : C.leafDark), 0, y, 0, 7);
    c.rotation.y = i * 0.5;
    g.add(c);
  });
  return g;
}

export function makeRoundTree() {
  const g = new THREE.Group();
  g.add(cyl(3.5, 4.5, 28, mat(C.woodDark), 0, 14, 0, 8));
  const leaves = [[0, 46, 0, 23, C.leaf], [-13, 38, 6, 16, C.leafDark], [13, 40, 4, 17, C.leafLight], [2, 58, -3, 16, C.leafLight]];
  for (const [x, y, z, r, col] of leaves) {
    const m = mesh(new THREE.IcosahedronGeometry(r, 0), mat(col), x, y, z);
    m.rotation.set(x * 0.1, y * 0.1, z * 0.1);
    g.add(m);
  }
  return g;
}

export function makeRock(variant) {
  const g = new THREE.Group();
  const m = mesh(new THREE.DodecahedronGeometry(variant ? 14 : 18, 0), mat(C.rock), 0, variant ? 8 : 10, 0);
  m.scale.set(1.2, 0.75, 1);
  m.rotation.set(0.3, variant * 1.3, 0.2);
  g.add(m);
  if (!variant) g.add(mesh(new THREE.DodecahedronGeometry(9, 0), mat(C.rockLight), 16, 5, 6));
  return g;
}

/** One palisade log: tall, pointed, with a plank stub to chain into a wall. */
export function makeStake() {
  const g = new THREE.Group();
  g.add(cyl(6, 6.5, 64, mat(0x8a5a36), 0, 32, 0, 9));
  g.add(cone(6, 12, mat(0xa06a40), 0, 70, 0, 9));
  g.add(box(16, 3.5, 3, mat(C.woodDark), 0, 44, 5));
  g.add(box(16, 3.5, 3, mat(C.woodDark), 0, 20, 5));
  return g;
}

export function makeTower() {
  const g = new THREE.Group();
  const logM = mat(C.woodDark);
  const plankM = mat(C.plank);
  for (const [x, z] of [[-32, -28], [32, -28], [-32, 28], [32, 28]]) {
    const leg = cyl(4.5, 5.5, 110, logM, x, 55, z, 9);
    leg.rotation.z = x > 0 ? -0.06 : 0.06;
    g.add(leg);
  }
  for (const z of [-28, 28]) {
    const b1 = box(74, 4, 4, logM, 0, 50, z); b1.rotation.z = 0.75; g.add(b1);
    const b2 = box(74, 4, 4, logM, 0, 50, z); b2.rotation.z = -0.75; g.add(b2);
  }
  for (const x of [-32, 32]) { const b = box(4, 4, 64, logM, x, 50, 0); b.rotation.x = 0.7; g.add(b); }
  for (let i = 0; i < 6; i++) g.add(box(14.5, 5, 80, plankM, -37 + i * 14.8 + 7, 112, 0));
  g.add(box(90, 4.5, 4.5, logM, 0, 110, 40));
  g.add(box(90, 4.5, 4.5, logM, 0, 110, -40));
  for (const x of [-43, 43]) for (const z of [-38, 38]) g.add(cyl(2.6, 2.6, 16, logM, x, 122, z, 7));
  g.add(box(88, 3.2, 3.2, plankM, 0, 128, -38));
  g.add(box(3.2, 3.2, 78, plankM, -43, 128, 0));
  g.add(box(3.2, 3.2, 78, plankM, 43, 128, 0));
  return g;
}

export function makeHouse() {
  const g = new THREE.Group();
  const stone = mat(C.stone);
  g.add(box(150, 66, 96, stone, 0, 33, 0));
  g.add(box(152, 14, 98, mat(C.stoneDark), 0, 7, 0));
  // brick lines on the front wall
  for (let y = 20; y < 66; y += 14) for (let x = -66; x < 70; x += 22) noInk(g.add(box(18, 10, 1, mat(y % 28 ? 0xbcc1c8 : 0xa9aeb6), x + ((y / 14) % 2) * 11, y, 48.5)) || g.children.at(-1));
  for (const x of [-75, 75]) for (const z of [-48, 48]) g.add(box(10, 70, 10, mat(C.woodDark), x, 35, z));
  g.add(box(150, 8, 8, mat(C.woodDark), 0, 66, 48));
  g.add(rbox(30, 48, 5, 3, mat(C.woodDark), 0, 24, 50));
  for (const x of [-48, 48]) {
    g.add(box(24, 20, 3, mat(0x2e3440), x, 40, 50));
    g.add(box(28, 3, 5, mat(C.woodDark), x, 29, 51));
    g.add(box(28, 3, 3, mat(C.woodDark), x, 51, 51));
  }
  const roofM = mat(C.roof);
  const roofD = mat(C.roofDark);
  for (const s of [-1, 1]) {
    const r = box(176, 5, 70, s > 0 ? roofM : roofD, 0, 92, 27 * s);
    r.rotation.x = 0.72 * s;
    g.add(r);
    for (let i = 0; i < 6; i++) {
      const plank = noInk(box(176, 1.4, 1.4, mat(0x5c3a22), 0, 92, 27 * s));
      plank.rotation.x = 0.72 * s;
      plank.position.y += 2.8;
      plank.translateZ(-30 + i * 12);
      g.add(plank);
    }
  }
  g.add(box(180, 7, 7, mat(0x5c3a22), 0, 116, 0));
  const gable = new THREE.Shape();
  gable.moveTo(-48, 0); gable.lineTo(48, 0); gable.lineTo(0, 46); gable.lineTo(-48, 0);
  const ge = new THREE.ExtrudeGeometry(gable, { depth: 4, bevelEnabled: false });
  for (const x of [-74, 70]) {
    const m = mesh(ge, stone, x, 66, 0);
    m.rotation.y = Math.PI / 2;
    g.add(m);
  }
  const sign = new THREE.Group();
  sign.position.set(0, 99, 41);
  sign.rotation.x = -0.85;
  const limb = mesh(new THREE.TorusGeometry(18, 2.8, 8, 20, Math.PI), mat(C.bow));
  limb.rotation.z = Math.PI / 2 + 0.6;
  sign.add(limb);
  const arrow = box(48, 2.4, 2.4, mat(C.silver), 4, 0, 2);
  arrow.rotation.z = 0.6;
  sign.add(arrow);
  const tip = cone(4.5, 9, mat(C.white), 23, 15, 2, 4);
  tip.rotation.z = -0.97;
  sign.add(tip);
  g.add(sign);
  return g;
}

export function makeCastle() {
  const g = new THREE.Group();
  const st = mat(C.stone);
  const sd = mat(C.stoneDark);
  g.add(box(170, 170, 120, st, 0, 85, 0));
  g.add(box(174, 22, 124, sd, 0, 11, 0));
  for (let i = 0; i < 6; i++) g.add(box(18, 18, 18, st, -76 + i * 30.4, 179, 56));
  for (let i = 0; i < 6; i++) g.add(box(18, 18, 18, st, -76 + i * 30.4, 179, -56));
  for (let i = 1; i < 4; i++) { g.add(box(18, 18, 18, st, -76, 179, -56 + i * 28)); g.add(box(18, 18, 18, st, 76, 179, -56 + i * 28)); }
  for (let y = 44; y < 170; y += 32) for (let x = -70; x < 80; x += 30) noInk(g.add(box(24, 14, 1, mat(y % 64 ? 0xbcc1c8 : 0xa9aeb6), x + ((y / 32) % 2) * 15 - 8, y, 60.5)) || g.children.at(-1));
  g.add(rbox(46, 66, 6, 14, mat(C.woodDark), 0, 33, 60));
  for (const x of [-12, 0, 12]) noInk(g.add(box(1.6, 60, 1, mat(0x4a2c17), x, 32, 63.3)) || g.children.at(-1));
  g.add(box(50, 4, 4, mat(0x3a3d44), 0, 46, 63));
  for (const x of [-45, 45]) g.add(rbox(14, 26, 3, 6, mat(0x1f2430), x, 120, 60.5));
  for (const s of [-1, 1]) {
    g.add(cyl(28, 30, 160, st, 100 * s, 80, 30, 12));
    g.add(cyl(33, 33, 10, sd, 100 * s, 162, 30, 12));
    g.add(cone(37, 64, mat(C.blue), 100 * s, 199, 30, 12));
    g.add(rbox(10, 18, 3, 4, mat(0x1f2430), 100 * s, 120, 58.5));
  }
  g.add(cyl(1.8, 1.8, 90, mat(C.woodDark), 0, 225, 0, 6));
  g.add(box(46, 30, 1.8, mat(C.blue), 23, 252, 0));
  g.add(noInk(cone(5, 7, mat(C.gold, { shiny: true }), 23, 252, 1.8, 5)));
  return g;
}
