// Low-poly 3D models for Kingshot, rendered offline into isometric sprite atlases.
// Units are game world units; every model stands on y = 0 and faces +Z (toward the camera).
import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';

const matCache = new Map();
export function mat(color, { flat = false, rough = 0.75, metal = 0, emissive = 0x000000 } = {}) {
  const key = `${color}|${flat}|${rough}|${metal}|${emissive}`;
  if (!matCache.has(key)) {
    matCache.set(key, new THREE.MeshStandardMaterial({ color, flatShading: flat, roughness: rough, metalness: metal, emissive }));
  }
  return matCache.get(key);
}

function mesh(geo, material, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geo, material);
  m.position.set(x, y, z);
  m.castShadow = true;
  m.receiveShadow = true;
  return m;
}
const rbox = (w, h, d, r, material, x, y, z) => mesh(new RoundedBoxGeometry(w, h, d, 3, Math.min(r, w / 2, h / 2, d / 2) - 0.001), material, x, y, z);
const box = (w, h, d, material, x, y, z) => mesh(new THREE.BoxGeometry(w, h, d), material, x, y, z);
const sphere = (r, material, x, y, z, ws = 20, hs = 14) => mesh(new THREE.SphereGeometry(r, ws, hs), material, x, y, z);
const cyl = (rt, rb, h, material, x, y, z, seg = 12) => mesh(new THREE.CylinderGeometry(rt, rb, h, seg), material, x, y, z);
const cone = (r, h, material, x, y, z, seg = 8) => mesh(new THREE.ConeGeometry(r, h, seg), material, x, y, z);

/** A pivot group positioned at (x,y,z); children hang from it. */
function pivot(parent, x, y, z) {
  const g = new THREE.Group();
  g.position.set(x, y, z);
  parent.add(g);
  return g;
}

const C = {
  skin: 0xf3c29b, hair: 0x2a2627, shirt: 0xf1e8d8, pants: 0x6b4f3a, boot: 0x3f2c20,
  blue: 0x2f6ad8, blueDark: 0x1f449a, bow: 0x3f86ea, wood: 0xa4683a, woodDark: 0x6e4325,
  red: 0xd92a2e, redLight: 0xee3d3d, redDark: 0x9e1620, steel: 0x6f7480, silver: 0xd6dce4,
  gold: 0xffc531, goldDark: 0xd9901a, black: 0x1b1b1f, white: 0xffffff,
  horse: 0xe9d7b8, horseDark: 0x9a6a40, mane: 0x5a3a22,
  stone: 0xa7adb5, stoneDark: 0x80868e, roof: 0x8b5a36, roofDark: 0x6b4228,
  leaf: 0x67a83a, leafDark: 0x4f8a2c, leafLight: 0x86c446, rock: 0x9b918e,
};

// ------------------------------------------------------------------ archer

export function makeArcher() {
  const root = new THREE.Group();
  const body = pivot(root, 0, 0, 0);
  const rig = { root, body };
  // legs (pivot at hip)
  rig.legL = pivot(body, -3.6, 11, 0);
  rig.legR = pivot(body, 3.6, 11, 0);
  for (const leg of [rig.legL, rig.legR]) {
    leg.add(rbox(5.2, 9, 5.6, 1.5, mat(C.pants), 0, -4.5, 0));
    leg.add(rbox(5.8, 3.4, 7, 1.4, mat(C.boot), 0, -9.4, 0.8));
  }
  const torso = pivot(body, 0, 11, 0);
  rig.torso = torso;
  torso.add(rbox(16, 14, 11, 4, mat(C.shirt), 0, 7, 0));
  torso.add(box(16.4, 2.2, 11.4, mat(C.woodDark), 0, 2.4, 0));
  torso.add(rbox(15, 12, 2.4, 1, mat(C.blue), 0, 7, -5.8)); // cape
  torso.add(rbox(4, 4, 1.2, 1, mat(C.gold), 0, 2.4, 5.8)); // buckle
  // quiver
  const q = cyl(2.6, 2.2, 13, mat(C.woodDark), 4, 9, -7);
  q.rotation.z = -0.35;
  torso.add(q);
  for (let i = 0; i < 3; i++) torso.add(box(1.4, 3, 1.4, mat(C.white), 5.6 + i * 1.3, 16.5 - i * 0.5, -7 + i * 0.4));
  // head
  const head = pivot(torso, 0, 14, 0);
  rig.head = head;
  head.add(sphere(10.5, mat(C.skin), 0, 9, 0));
  const hair = sphere(11.1, mat(C.hair), 0, 9.8, -0.8, 22, 14);
  hair.geometry = new THREE.SphereGeometry(11.1, 22, 14, 0, Math.PI * 2, 0, Math.PI * 0.52);
  hair.rotation.x = -0.35;
  head.add(hair);
  head.add(box(2, 3, 1, mat(C.black), -3.6, 8.4, 10));
  head.add(box(2, 3, 1, mat(C.black), 3.6, 8.4, 10));
  const browL = box(3.6, 1.1, 1, mat(C.hair), -3.6, 11.2, 9.9); browL.rotation.z = -0.35; head.add(browL);
  const browR = box(3.6, 1.1, 1, mat(C.hair), 3.6, 11.2, 9.9); browR.rotation.z = 0.35; head.add(browR);
  head.add(sphere(1.5, mat(0xe8a580), 0, 6.8, 10.3, 8, 6));
  // arms (pivot at shoulder)
  rig.armL = pivot(torso, -9, 11.5, 0);
  rig.armR = pivot(torso, 9, 11.5, 0);
  for (const arm of [rig.armL, rig.armR]) {
    arm.add(rbox(4.6, 9, 4.6, 2, mat(C.shirt), 0, -4, 0));
    arm.add(sphere(2.7, mat(C.skin), 0, -9.2, 0, 10, 8));
  }
  // bow in left hand
  const bow = new THREE.Group();
  bow.position.set(0, -9.2, 1);
  const limb = mesh(new THREE.TorusGeometry(11, 1.3, 6, 18, Math.PI), mat(C.bow, { rough: 0.5 }));
  limb.rotation.z = -Math.PI / 2;
  bow.add(limb);
  const string = cyl(0.3, 0.3, 22, mat(C.white), -0.2, 0, 0, 4);
  rig.string = string;
  bow.add(string);
  rig.armL.add(bow);
  rig.bow = bow;
  // arrow (visible while drawing)
  const arrow = new THREE.Group();
  arrow.add(cyl(0.45, 0.45, 20, mat(C.woodDark), 0, 0, 0, 5));
  arrow.add(cone(1.2, 3, mat(C.silver), 0, 11, 0, 5));
  arrow.rotation.x = Math.PI / 2;
  rig.arrow = arrow;
  root.add(arrow);
  return rig;
}

/** walk: t in [0,1) ; shoot: s in [0,1] (0 aim .. 1 release) */
export function poseArcher(rig, mode, t) {
  const { body, legL, legR, armL, armR, torso, bow, arrow, head } = rig;
  body.position.y = 0; torso.rotation.set(0, 0, 0); head.rotation.set(0, 0, 0);
  legL.rotation.set(0, 0, 0); legR.rotation.set(0, 0, 0);
  armL.rotation.set(0, 0, 0); armR.rotation.set(0, 0, 0);
  bow.rotation.set(0, 0, 0);
  arrow.visible = false;
  if (mode === 'walk') {
    const p = t * Math.PI * 2;
    legL.rotation.x = Math.sin(p) * 0.65;
    legR.rotation.x = -Math.sin(p) * 0.65;
    armL.rotation.x = -Math.sin(p) * 0.5;
    armR.rotation.x = Math.sin(p) * 0.5;
    body.position.y = Math.abs(Math.cos(p)) * 1.4;
    torso.rotation.z = Math.sin(p) * 0.04;
  } else {
    // aim forward (+Z): left arm straight out holding the bow, right arm pulls the string
    armL.rotation.x = -Math.PI / 2;
    bow.rotation.y = Math.PI / 2;
    const pull = mode === 'shoot' ? Math.sin(Math.min(1, t) * Math.PI * 0.9) : 0;
    armR.rotation.x = -Math.PI / 2 + 0.2;
    armR.rotation.z = 0.5 + pull * 0.4;
    torso.rotation.y = -0.25;
    arrow.visible = t < 0.85;
    arrow.position.set(-6, 22.5, 6 + 6 * (1 - pull));
  }
}

// ------------------------------------------------------------------ knight

export function makeKnight(boss = false) {
  const main = boss ? 0x8e1b1f : C.red;
  const light = boss ? 0xb3262b : C.redLight;
  const dark = boss ? 0x5e0f13 : C.redDark;
  const root = new THREE.Group();
  const body = pivot(root, 0, 0, 0);
  const rig = { root, body };
  rig.legL = pivot(body, -4.4, 12, 0);
  rig.legR = pivot(body, 4.4, 12, 0);
  for (const leg of [rig.legL, rig.legR]) {
    leg.add(rbox(6.4, 10, 6.6, 2, mat(C.steel, { rough: 0.45, metal: 0.5 }), 0, -5, 0));
    leg.add(rbox(7, 3.6, 8.4, 1.6, mat(0x3a3d44, { rough: 0.5, metal: 0.4 }), 0, -10.2, 1));
  }
  const torso = pivot(body, 0, 12, 0);
  rig.torso = torso;
  torso.add(rbox(21, 17, 15, 5, mat(main, { rough: 0.45, metal: 0.25 }), 0, 8.5, 0));
  torso.add(rbox(16, 11, 2.5, 2, mat(light, { rough: 0.4, metal: 0.3 }), 0, 10, 7.2));
  torso.add(box(21.6, 2.6, 15.6, mat(0x2c2224), 0, 2, 0));
  if (boss) {
    torso.add(rbox(10, 6, 12, 2.5, mat(C.gold, { rough: 0.35, metal: 0.6 }), -12, 15, 0));
    torso.add(rbox(10, 6, 12, 2.5, mat(C.gold, { rough: 0.35, metal: 0.6 }), 12, 15, 0));
    for (const s of [-1, 1]) {
      const spike = cone(2, 6, mat(C.silver, { metal: 0.6, rough: 0.3 }), 13 * s, 20, 0, 6);
      torso.add(spike);
    }
  }
  // helmet (covers the head completely)
  const head = pivot(torso, 0, 17, 0);
  rig.head = head;
  head.add(rbox(21, 20, 20, 7, mat(light, { rough: 0.4, metal: 0.3 }), 0, 10, 0));
  head.add(box(14, 1.6, 1, mat(dark), 0, 10.8, 10.2));
  for (let i = -2; i <= 2; i++) head.add(box(1.3, 7, 1, mat(C.black), i * 2.6, 7.5, 10.1));
  head.add(box(15, 2.2, 1, mat(C.black), 0, 13.2, 10.05));
  if (boss) {
    head.add(box(21.6, 2.4, 20.6, mat(C.gold, { rough: 0.35, metal: 0.6 }), 0, 16.5, 0));
    for (const s of [-1, 1]) {
      const horn = cone(2.6, 11, mat(0xf0e6d0), 11 * s, 22, 0, 7);
      horn.rotation.z = -0.6 * s;
      head.add(horn);
    }
  } else {
    // plume crest
    const plume = mesh(new THREE.TorusGeometry(8, 2.6, 6, 12, Math.PI * 0.9), mat(main, { rough: 0.8 }));
    plume.rotation.y = Math.PI / 2;
    plume.position.set(0, 17, -1);
    plume.rotation.x = 0.15;
    head.add(plume);
  }
  rig.armL = pivot(torso, -12, 14, 0);
  rig.armR = pivot(torso, 12, 14, 0);
  for (const arm of [rig.armL, rig.armR]) {
    arm.add(rbox(6, 10, 6, 2.5, mat(main, { rough: 0.45, metal: 0.25 }), 0, -4.5, 0));
    arm.add(sphere(3.2, mat(0x3a3d44, { metal: 0.4, rough: 0.5 }), 0, -10, 0, 10, 8));
  }
  // sword in right hand, pointing forward
  const sword = new THREE.Group();
  sword.position.set(0, -10, 0);
  sword.add(box(2.2, 4, 2.2, mat(C.woodDark), 0, 0, 0));
  sword.add(box(8, 1.6, 2.4, mat(C.gold, { metal: 0.6, rough: 0.35 }), 0, 2.4, 0));
  sword.add(box(2.6, boss ? 30 : 22, 1, mat(C.silver, { metal: 0.85, rough: 0.2 }), 0, boss ? 18 : 14, 0));
  sword.rotation.x = Math.PI / 2 - 0.3;
  rig.armR.add(sword);
  rig.sword = sword;
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
    // raise the sword high then slash down
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
  const dark = mat(C.horseDark);
  // legs: [x, z]
  for (const [x, z] of [[-6.5, 13], [6.5, 13], [-6.5, -13], [6.5, -13]]) {
    const leg = pivot(body, x, 22, z);
    leg.add(rbox(5.4, 18, 5.6, 2, horseMat, 0, -9, 0));
    leg.add(rbox(6, 3.2, 6.6, 1.2, mat(0x3a2a20), 0, -19.6, 0.4));
    rig.legs.push(leg);
  }
  const horse = pivot(body, 0, 0, 0);
  rig.horse = horse;
  horse.add(rbox(19, 17, 40, 7, horseMat, 0, 28, 0));
  horse.add(rbox(19.4, 8, 14, 3, mat(C.blue), 0, 33, -2)); // saddle blanket
  horse.add(box(19.8, 1.6, 14.4, mat(C.gold, { metal: 0.5, rough: 0.4 }), 0, 29.2, -2));
  const neck = rbox(10, 20, 11, 4, horseMat, 0, 40, 17);
  neck.rotation.x = 0.45;
  horse.add(neck);
  const headH = rbox(10, 10, 19, 4, horseMat, 0, 49, 25);
  headH.rotation.x = 0.35;
  horse.add(headH);
  horse.add(rbox(8.6, 6, 6, 2.5, mat(0xc8a87e), 0, 45.5, 33));
  for (const s of [-1, 1]) {
    horse.add(cone(1.8, 5, horseMat, 3.2 * s, 56, 20, 5));
    horse.add(box(1.4, 1.6, 1, mat(C.black), 5.1 * s, 50, 28));
  }
  for (let i = 0; i < 4; i++) horse.add(rbox(3, 6, 4, 1.2, mat(C.mane), 0, 47 - i * 3.6, 17 - i * 2.6));
  const tail = rbox(5, 17, 5, 2.4, mat(C.mane), 0, 27, -22);
  tail.rotation.x = -0.5;
  horse.add(tail);
  rig.tail = tail;
  // rider
  const rider = pivot(horse, 0, 36, -2);
  rig.rider = rider;
  for (const s of [-1, 1]) rider.add(rbox(5, 12, 9, 2, mat(C.blueDark), 10 * s, -2, 2));
  rider.add(rbox(16, 16, 12, 5, mat(C.blue), 0, 8, 0));
  rider.add(rbox(13, 10, 3, 2.5, mat(C.silver, { metal: 0.7, rough: 0.3 }), 0, 9, 5.6));
  rider.add(rbox(17, 15, 2.6, 1.2, mat(C.blueDark), 0, 6, -6.4)); // cape
  for (const s of [-1, 1]) {
    const arm = pivot(rider, 9.4 * s, 13, 0);
    arm.add(rbox(4.6, 10, 4.6, 2, mat(C.blue), 0, -4, 1.5));
    arm.add(sphere(2.6, mat(C.skin), 0, -8.5, 4, 10, 8));
    arm.rotation.x = -0.7;
    arm.add(rbox(6, 3, 6, 1.4, mat(C.silver, { metal: 0.7, rough: 0.3 }), 0, 0.5, 0)); // pauldron
  }
  const head = pivot(rider, 0, 17, 0);
  head.add(sphere(10, mat(C.skin), 0, 8, 0));
  head.add(rbox(12, 6, 6, 3, mat(0x7a4a2a), 0, 3.6, 5.5)); // beard
  head.add(box(1.9, 2.8, 1, mat(C.black), -3.4, 9, 9.4));
  head.add(box(1.9, 2.8, 1, mat(C.black), 3.4, 9, 9.4));
  const hairBack = sphere(10.4, mat(0x7a4a2a), 0, 9, -1.6, 18, 12);
  hairBack.scale.set(1, 0.95, 0.9);
  head.add(hairBack);
  head.add(sphere(9.8, mat(C.skin), 0, 8, 0.8, 18, 12));
  // crown
  const crown = pivot(head, 0, 16, 0);
  crown.add(cyl(8, 7.4, 5, mat(C.gold, { metal: 0.7, rough: 0.3 }), 0, 0, 0, 16));
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    crown.add(cone(2, 5, mat(C.gold, { metal: 0.7, rough: 0.3 }), Math.sin(a) * 7.2, 4.6, Math.cos(a) * 7.2, 5));
  }
  crown.add(sphere(1.6, mat(0xe0283a, { rough: 0.2 }), 0, 0.5, 8, 8, 6));
  return rig;
}

export function poseKing(rig, mode, t) {
  const { body, legs, horse, rider, tail } = rig;
  const p = t * Math.PI * 2;
  for (const l of legs) l.rotation.set(0, 0, 0);
  body.position.y = 0; horse.rotation.set(0, 0, 0); rider.rotation.set(0, 0, 0); tail.rotation.x = -0.5;
  if (mode === 'walk') {
    // gallop: front pair and back pair out of phase
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
  const gold = mat(0xffcf3a, { metal: 0.15, rough: 0.35, emissive: 0x3a2400 });
  const edge = mat(0xe39a1c, { metal: 0.15, rough: 0.4, emissive: 0x2a1600 });
  const c = mesh(new THREE.CylinderGeometry(11, 11, 3.6, 24), [edge, gold, gold]);
  g.add(c);
  const inner = cyl(7.6, 7.6, 0.8, mat(0xf5b021, { metal: 0.15, rough: 0.4, emissive: 0x2a1600 }), 0, 1.9, 0, 20);
  g.add(inner);
  // crown emblem
  g.add(box(8, 0.8, 2.4, gold, 0, 2.5, 1.6));
  for (const x of [-3, 0, 3]) g.add(box(1.6, 0.8, 4, gold, x, 2.5, -1));
  if (!flat) { g.rotation.x = Math.PI / 2; g.position.y = 11; }
  else g.position.y = 1.8;
  const root = new THREE.Group();
  root.add(g);
  return { root, coin: g };
}

export function makePine(variant) {
  const g = new THREE.Group();
  g.add(cyl(3.5, 4.5, 22, mat(C.woodDark, { flat: true }), 0, 11, 0, 7));
  const tiers = variant === 0 ? [[34, 34, 34], [27, 32, 60], [19, 30, 84]] : [[30, 30, 30], [24, 28, 54], [17, 28, 76], [10, 20, 96]];
  tiers.forEach(([r, h, y], i) => {
    const c = cone(r, h, mat(i % 2 ? C.leaf : C.leafDark, { flat: true, rough: 0.9 }), 0, y, 0, 7);
    c.rotation.y = i * 0.5;
    g.add(c);
  });
  return g;
}

export function makeRoundTree() {
  const g = new THREE.Group();
  g.add(cyl(3.5, 4.5, 26, mat(C.woodDark, { flat: true }), 0, 13, 0, 7));
  const leaves = [[0, 44, 0, 22, C.leaf], [-12, 36, 6, 15, C.leafDark], [12, 38, 4, 16, C.leafLight], [2, 56, -3, 15, C.leafLight]];
  for (const [x, y, z, r, col] of leaves) {
    const m = mesh(new THREE.IcosahedronGeometry(r, 0), mat(col, { flat: true, rough: 0.9 }), x, y, z);
    m.rotation.set(x * 0.1, y * 0.1, z * 0.1);
    g.add(m);
  }
  return g;
}

export function makeRock(variant) {
  const g = new THREE.Group();
  const m = mesh(new THREE.DodecahedronGeometry(variant ? 14 : 18, 0), mat(C.rock, { flat: true, rough: 0.95 }), 0, variant ? 8 : 10, 0);
  m.scale.set(1.2, 0.75, 1);
  m.rotation.set(0.3, variant * 1.3, 0.2);
  g.add(m);
  if (!variant) {
    const s = mesh(new THREE.DodecahedronGeometry(9, 0), mat(0xb5aba7, { flat: true, rough: 0.95 }), 16, 5, 6);
    g.add(s);
  }
  return g;
}

export function makeStake() {
  const g = new THREE.Group();
  g.add(cyl(5.4, 6, 40, mat(0x7a4a2a, { flat: true, rough: 0.9 }), 0, 20, 0, 7));
  g.add(cone(5.4, 11, mat(0x8a5a36, { flat: true, rough: 0.9 }), 0, 45.5, 0, 7));
  g.add(box(13, 3, 3, mat(C.woodDark), 0, 30, 4.5));
  return g;
}

export function makeTower() {
  const g = new THREE.Group();
  const logM = mat(C.woodDark, { flat: true, rough: 0.9 });
  const plankM = mat(C.wood, { flat: true, rough: 0.85 });
  for (const [x, z] of [[-30, -26], [30, -26], [-30, 26], [30, 26]]) {
    const leg = cyl(4.2, 5, 108, logM, x, 54, z, 7);
    leg.rotation.z = x > 0 ? -0.06 : 0.06;
    g.add(leg);
  }
  for (const z of [-26, 26]) {
    const b1 = box(70, 3.5, 3.5, logM, 0, 50, z); b1.rotation.z = 0.75; g.add(b1);
    const b2 = box(70, 3.5, 3.5, logM, 0, 50, z); b2.rotation.z = -0.75; g.add(b2);
  }
  for (const x of [-30, 30]) {
    const b = box(3.5, 3.5, 60, logM, x, 50, 0); b.rotation.x = 0.7; g.add(b);
  }
  // platform made of planks
  for (let i = 0; i < 6; i++) g.add(box(14, 5, 76, plankM, -35 + i * 14 + 7, 110, 0));
  g.add(box(86, 4, 4, logM, 0, 108, 38));
  g.add(box(86, 4, 4, logM, 0, 108, -38));
  // rails
  for (const x of [-41, 41]) for (const z of [-36, 36]) g.add(cyl(2.4, 2.4, 14, logM, x, 119, z, 6));
  g.add(box(84, 3, 3, plankM, 0, 124, -36));
  g.add(box(3, 3, 74, plankM, -41, 124, 0));
  g.add(box(3, 3, 74, plankM, 41, 124, 0));
  return g;
}

export function makeHouse() {
  const g = new THREE.Group();
  g.add(box(150, 66, 96, mat(C.stone, { flat: true }), 0, 33, 0));
  g.add(box(152, 14, 98, mat(C.stoneDark, { flat: true }), 0, 7, 0));
  for (const x of [-75, 75]) for (const z of [-48, 48]) g.add(box(10, 70, 10, mat(C.woodDark), x, 35, z));
  g.add(box(150, 8, 8, mat(C.woodDark), 0, 66, 48));
  g.add(rbox(30, 48, 4, 3, mat(C.woodDark), 0, 24, 49));
  for (const x of [-48, 48]) {
    g.add(box(24, 20, 3, mat(0x2e3440), x, 40, 49));
    g.add(box(28, 3, 4, mat(C.woodDark), x, 29, 50));
  }
  // gable roof
  const roofM = mat(C.roof, { flat: true, rough: 0.85 });
  const roofD = mat(C.roofDark, { flat: true, rough: 0.85 });
  for (const s of [-1, 1]) {
    const r = box(176, 5, 68, s > 0 ? roofM : roofD, 0, 92, 26 * s);
    r.rotation.x = 0.72 * s;
    g.add(r);
    for (let i = 0; i < 5; i++) {
      const plank = box(176, 1.2, 1.2, mat(0x5c3a22), 0, 92, 26 * s);
      plank.rotation.x = 0.72 * s;
      plank.position.y += 2.6;
      plank.translateZ(-28 + i * 14);
      g.add(plank);
    }
  }
  g.add(box(180, 7, 7, mat(0x5c3a22), 0, 116, 0));
  // gable walls
  const gable = new THREE.Shape();
  gable.moveTo(-48, 0); gable.lineTo(48, 0); gable.lineTo(0, 46); gable.lineTo(-48, 0);
  const ge = new THREE.ExtrudeGeometry(gable, { depth: 4, bevelEnabled: false });
  for (const x of [-74, 70]) {
    const m = mesh(ge, mat(C.stone, { flat: true }), x, 66, 0);
    m.rotation.y = Math.PI / 2;
    g.add(m);
  }
  // bow emblem on the front roof
  const sign = new THREE.Group();
  sign.position.set(0, 98, 40);
  sign.rotation.x = -0.85;
  const limb = mesh(new THREE.TorusGeometry(17, 2.6, 6, 16, Math.PI), mat(C.bow, { rough: 0.4 }));
  limb.rotation.z = Math.PI / 2 + 0.6;
  sign.add(limb);
  const arrow = box(46, 2.2, 2.2, mat(C.silver, { metal: 0.6, rough: 0.3 }), 4, 0, 2);
  arrow.rotation.z = 0.6;
  sign.add(arrow);
  const tip = cone(4, 9, mat(C.white), 23, 15, 2, 4);
  tip.rotation.z = -0.97;
  sign.add(tip);
  g.add(sign);
  return g;
}

export function makeCastle() {
  const g = new THREE.Group();
  const st = mat(C.stone, { flat: true, rough: 0.9 });
  const sd = mat(C.stoneDark, { flat: true, rough: 0.9 });
  g.add(box(170, 170, 120, st, 0, 85, 0));
  g.add(box(174, 22, 124, sd, 0, 11, 0));
  for (let i = 0; i < 6; i++) g.add(box(18, 18, 18, st, -76 + i * 30.4, 179, 56));
  for (let i = 0; i < 6; i++) g.add(box(18, 18, 18, st, -76 + i * 30.4, 179, -56));
  for (let i = 1; i < 4; i++) { g.add(box(18, 18, 18, st, -76, 179, -56 + i * 28)); g.add(box(18, 18, 18, st, 76, 179, -56 + i * 28)); }
  // stone courses
  for (let y = 40; y < 170; y += 32) g.add(box(171, 2, 121, sd, 0, y, 0));
  // door
  g.add(rbox(46, 66, 6, 14, mat(C.woodDark), 0, 33, 60));
  for (const x of [-12, 0, 12]) g.add(box(1.6, 60, 1, mat(0x4a2c17), x, 32, 63.2));
  g.add(box(50, 4, 4, mat(0x3a3d44, { metal: 0.6, rough: 0.4 }), 0, 46, 63));
  // windows
  for (const x of [-45, 45]) g.add(rbox(14, 26, 3, 6, mat(0x1f2430), x, 120, 60));
  // side towers with blue cone roofs
  for (const s of [-1, 1]) {
    g.add(cyl(28, 30, 160, st, 100 * s, 80, 30, 10));
    g.add(cyl(32, 32, 10, sd, 100 * s, 162, 30, 10));
    g.add(cone(36, 62, mat(C.blue, { flat: true, rough: 0.6 }), 100 * s, 198, 30, 10));
    g.add(rbox(10, 18, 3, 4, mat(0x1f2430), 100 * s, 120, 58));
  }
  // flag
  g.add(cyl(1.8, 1.8, 90, mat(C.woodDark), 0, 225, 0, 6));
  const flag = box(46, 30, 1.6, mat(C.blue, { rough: 0.7 }), 23, 252, 0);
  g.add(flag);
  g.add(cone(5, 7, mat(C.gold, { metal: 0.6, rough: 0.3 }), 23, 252, 1.6, 5));
  return g;
}
