'use strict';
// Procedural art. Everything you see in the game is drawn here with canvas
// paths — no sprites, no texture packs.

const OUT = '#1d1a26';

function rrPath(ctx, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
function circlePath(ctx, x, y, r) { ctx.beginPath(); ctx.arc(x, y, r, 0, TAU); }
function fillStroke(ctx) { ctx.fill(); ctx.stroke(); }
function shade(hex, amt) {
  // lighten (amt > 0) or darken (amt < 0) a #rrggbb colour
  const n = parseInt(hex.slice(1), 16);
  let r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
  const f = (c) => clamp(Math.round(amt > 0 ? c + (255 - c) * amt : c * (1 + amt)), 0, 255);
  return '#' + ((1 << 24) | (f(r) << 16) | (f(g) << 8) | f(b)).toString(16).slice(1);
}

// ======================================================================
// Characters (hero + allies): chibi figures facing right.
// ======================================================================
function weaponAngle(kind, atk) {
  if (kind === 'bow' || kind === 'gun') return 0;
  if (atk <= 0 || atk >= 1) return 0.45;
  if (atk < 0.25) return lerp(0.45, -1.3, atk / 0.25);
  const k = (atk - 0.25) / 0.75;
  return lerp(-1.3, 2.1, 1 - Math.pow(1 - k, 3));
}

function drawWeapon(ctx, kind, L) {
  ctx.lineWidth = 2;
  ctx.strokeStyle = OUT;
  const wood = '#8a5a32', steel = L.blade || '#e6edf5';
  switch (kind) {
    case 'sword': {
      if (L.bladeGlow) { ctx.save(); ctx.shadowColor = L.bladeGlow; ctx.shadowBlur = 12; }
      ctx.fillStyle = steel;
      ctx.beginPath(); ctx.moveTo(-3, -5); ctx.lineTo(-3, -34); ctx.lineTo(0, -41); ctx.lineTo(3, -34); ctx.lineTo(3, -5); ctx.closePath(); fillStroke(ctx);
      if (L.bladeGlow) ctx.restore();
      ctx.fillStyle = 'rgba(255,255,255,.7)'; ctx.fillRect(-1, -33, 1.5, 26);
      ctx.fillStyle = '#e8b33a'; rrPath(ctx, -8, -7, 16, 4, 2); fillStroke(ctx);
      ctx.fillStyle = '#5a3a22'; rrPath(ctx, -2, -3, 4, 9, 1.5); fillStroke(ctx);
      break;
    }
    case 'spear':
      ctx.strokeStyle = OUT; ctx.lineWidth = 5; ctx.beginPath(); ctx.moveTo(0, 12); ctx.lineTo(0, -38); ctx.stroke();
      ctx.strokeStyle = wood; ctx.lineWidth = 3; ctx.stroke();
      ctx.lineWidth = 2; ctx.strokeStyle = OUT; ctx.fillStyle = steel;
      ctx.beginPath(); ctx.moveTo(-4, -37); ctx.lineTo(0, -52); ctx.lineTo(4, -37); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#d33'; ctx.beginPath(); ctx.moveTo(-1, -36); ctx.lineTo(-6, -30); ctx.lineTo(1, -33); ctx.fill();
      break;
    case 'axe':
      ctx.lineWidth = 5; ctx.beginPath(); ctx.moveTo(0, 10); ctx.lineTo(0, -30); ctx.stroke();
      ctx.strokeStyle = wood; ctx.lineWidth = 3; ctx.stroke();
      ctx.lineWidth = 2; ctx.strokeStyle = OUT; ctx.fillStyle = steel;
      ctx.beginPath(); ctx.moveTo(0, -30); ctx.quadraticCurveTo(16, -36, 14, -18); ctx.quadraticCurveTo(8, -22, 0, -20); ctx.closePath(); fillStroke(ctx);
      ctx.beginPath(); ctx.moveTo(0, -30); ctx.quadraticCurveTo(-10, -32, -9, -21); ctx.quadraticCurveTo(-5, -23, 0, -22); ctx.closePath(); fillStroke(ctx);
      break;
    case 'dagger':
      ctx.fillStyle = steel; ctx.beginPath(); ctx.moveTo(-2.5, -4); ctx.lineTo(0, -20); ctx.lineTo(2.5, -4); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#b23a48'; rrPath(ctx, -5, -6, 10, 3, 1.5); fillStroke(ctx);
      break;
    case 'mace':
      ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(0, 8); ctx.lineTo(0, -22); ctx.stroke();
      ctx.strokeStyle = '#c9a24a'; ctx.lineWidth = 2.5; ctx.stroke();
      ctx.strokeStyle = OUT; ctx.lineWidth = 2; ctx.fillStyle = '#f2d55a';
      for (let i = 0; i < 6; i++) {
        const a = (i / 6) * TAU;
        ctx.beginPath(); ctx.moveTo(Math.cos(a) * 5, -28 + Math.sin(a) * 5); ctx.lineTo(Math.cos(a) * 10, -28 + Math.sin(a) * 10); ctx.stroke();
      }
      circlePath(ctx, 0, -28, 6.5); fillStroke(ctx);
      break;
    case 'staff':
    case 'leaf':
      ctx.lineWidth = 5; ctx.beginPath(); ctx.moveTo(0, 14); ctx.quadraticCurveTo(2, -10, 0, -34); ctx.stroke();
      ctx.strokeStyle = kind === 'leaf' ? '#6b4a2a' : '#7a4a8a'; ctx.lineWidth = 3; ctx.stroke();
      ctx.strokeStyle = OUT; ctx.lineWidth = 2;
      if (kind === 'leaf') {
        ctx.fillStyle = '#7bd65a';
        ctx.beginPath(); ctx.moveTo(0, -34); ctx.quadraticCurveTo(12, -44, 4, -54); ctx.quadraticCurveTo(-8, -44, 0, -34); fillStroke(ctx);
        ctx.fillStyle = '#5ab33f';
        ctx.beginPath(); ctx.moveTo(0, -32); ctx.quadraticCurveTo(-12, -36, -12, -46); ctx.quadraticCurveTo(-3, -42, 0, -32); fillStroke(ctx);
      } else {
        ctx.save(); ctx.shadowColor = L.glow || '#fff'; ctx.shadowBlur = 14;
        ctx.fillStyle = L.glow || '#9cf'; circlePath(ctx, 0, -39, 6); fillStroke(ctx);
        ctx.restore();
        ctx.fillStyle = 'rgba(255,255,255,.8)'; circlePath(ctx, -2, -41, 2); ctx.fill();
      }
      break;
    case 'scythe':
      ctx.lineWidth = 5; ctx.beginPath(); ctx.moveTo(0, 14); ctx.lineTo(0, -38); ctx.stroke();
      ctx.strokeStyle = '#3a2a2a'; ctx.lineWidth = 3; ctx.stroke();
      ctx.strokeStyle = OUT; ctx.lineWidth = 2; ctx.fillStyle = '#f2b134';
      ctx.beginPath(); ctx.moveTo(0, -38); ctx.quadraticCurveTo(22, -44, 30, -26); ctx.quadraticCurveTo(18, -36, 0, -32); ctx.closePath(); fillStroke(ctx);
      break;
    case 'bow':
      ctx.lineWidth = 4; ctx.strokeStyle = OUT; ctx.beginPath(); ctx.arc(-4, 0, 17, -1.25, 1.25); ctx.stroke();
      ctx.strokeStyle = '#a0662f'; ctx.lineWidth = 2.5; ctx.stroke();
      ctx.strokeStyle = '#eee'; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(-4 + Math.cos(-1.25) * 17, Math.sin(-1.25) * 17); ctx.lineTo(-6, 0); ctx.lineTo(-4 + Math.cos(1.25) * 17, Math.sin(1.25) * 17); ctx.stroke();
      break;
    case 'gun':
      ctx.fillStyle = '#4a4f5a'; rrPath(ctx, -2, -4, 24, 6, 2); fillStroke(ctx);
      ctx.fillStyle = '#7a5230'; rrPath(ctx, -6, -2, 8, 10, 2); fillStroke(ctx);
      ctx.fillStyle = '#d9a441'; ctx.fillRect(6, -3, 6, 2);
      break;
  }
}

function drawHair(ctx, L, hy, back) {
  ctx.fillStyle = L.hair;
  const st = L.hairStyle;
  if (back) {
    if (st === 'long') {
      ctx.beginPath(); ctx.moveTo(-4, hy - 10); ctx.quadraticCurveTo(-18, hy - 4, -14, hy + 20); ctx.lineTo(-4, hy + 18); ctx.quadraticCurveTo(-8, hy + 6, 2, hy - 6); ctx.closePath(); fillStroke(ctx);
    }
    return;
  }
  switch (st) {
    case 'spiky': {
      const p = [[-12, 3], [-21, -2], [-12, -6], [-19, -15], [-7, -12], [-6, -23], [1, -13], [9, -20], [8, -11], [16, -9], [13, -3], [5, -8], [-3, -6], [-7, 5]];
      ctx.beginPath(); p.forEach(([x, y], i) => (i ? ctx.lineTo(x, hy + y) : ctx.moveTo(x, hy + y))); ctx.closePath(); fillStroke(ctx);
      break;
    }
    case 'bob':
    case 'long':
      ctx.beginPath(); ctx.arc(0, hy, 13.5, Math.PI * 0.92, Math.PI * 2.02); ctx.quadraticCurveTo(6, hy - 4, 2, hy - 3); ctx.quadraticCurveTo(-6, hy - 2, -12, hy + 6); ctx.closePath(); fillStroke(ctx);
      break;
    case 'wizard':
      ctx.fillStyle = L.hair; ctx.beginPath(); ctx.moveTo(-12, hy); ctx.quadraticCurveTo(-10, hy + 22, -4, hy + 14); ctx.lineTo(-4, hy); fillStroke(ctx); // beard-ish white hair
      ctx.fillStyle = L.hat;
      ctx.beginPath(); ctx.moveTo(-15, hy - 6); ctx.lineTo(15, hy - 6); ctx.quadraticCurveTo(0, hy - 18, -14, hy - 34); ctx.quadraticCurveTo(-8, hy - 18, -15, hy - 6); ctx.closePath(); fillStroke(ctx);
      ctx.beginPath(); ctx.ellipse(0, hy - 6, 18, 4, 0, 0, TAU); fillStroke(ctx);
      ctx.fillStyle = '#f2c94c'; circlePath(ctx, -14, hy - 34, 3); fillStroke(ctx);
      break;
    case 'bald':
      ctx.fillStyle = 'rgba(255,255,255,.35)'; ctx.beginPath(); ctx.ellipse(-3, hy - 7, 5, 3, -0.4, 0, TAU); ctx.fill();
      break;
    case 'bandana':
      ctx.beginPath(); ctx.arc(0, hy, 13, Math.PI, TAU); ctx.closePath(); ctx.fillStyle = L.hair; fillStroke(ctx);
      ctx.fillStyle = L.band; rrPath(ctx, -13, hy - 7, 26, 5, 2); fillStroke(ctx);
      ctx.beginPath(); ctx.moveTo(-12, hy - 5); ctx.lineTo(-22, hy - 1); ctx.lineTo(-19, hy - 7); ctx.closePath(); fillStroke(ctx);
      break;
    case 'helmet':
      ctx.fillStyle = L.helm; ctx.beginPath(); ctx.arc(0, hy, 14, Math.PI * 0.85, Math.PI * 2.15); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = OUT; ctx.fillRect(1, hy - 1, 12, 3);
      ctx.fillStyle = L.trim; ctx.beginPath(); ctx.moveTo(-2, hy - 14); ctx.quadraticCurveTo(-14, hy - 26, -20, hy - 18); ctx.quadraticCurveTo(-10, hy - 18, -6, hy - 12); fillStroke(ctx);
      break;
  }
  if (L.beard) {
    ctx.fillStyle = L.beard;
    ctx.beginPath(); ctx.moveTo(-4, hy + 4); ctx.quadraticCurveTo(4, hy + 22, 13, hy + 4); ctx.quadraticCurveTo(6, hy + 8, -4, hy + 4); fillStroke(ctx);
  }
}

function drawHelmGear(ctx, kind, color, hy) {
  ctx.fillStyle = color;
  switch (kind) {
    case 'cap':
      ctx.beginPath(); ctx.arc(0, hy - 1, 13.5, Math.PI, TAU); ctx.closePath(); fillStroke(ctx);
      rrPath(ctx, 2, hy - 4, 16, 4, 2); fillStroke(ctx);
      break;
    case 'visor':
    case 'horned':
      ctx.beginPath(); ctx.arc(0, hy, 14.5, Math.PI * 0.85, Math.PI * 2.15); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = OUT; ctx.fillRect(2, hy - 1, 12, 3);
      if (kind === 'horned') {
        ctx.fillStyle = '#f1e6c8';
        ctx.beginPath(); ctx.moveTo(-10, hy - 9); ctx.quadraticCurveTo(-22, hy - 14, -20, hy - 28); ctx.quadraticCurveTo(-14, hy - 16, -5, hy - 13); fillStroke(ctx);
        ctx.beginPath(); ctx.moveTo(8, hy - 11); ctx.quadraticCurveTo(18, hy - 18, 14, hy - 30); ctx.quadraticCurveTo(12, hy - 18, 3, hy - 13); fillStroke(ctx);
      }
      break;
    case 'circlet':
      rrPath(ctx, -13, hy - 9, 26, 4, 2); fillStroke(ctx);
      ctx.fillStyle = '#ff4a6a'; circlePath(ctx, 8, hy - 7, 2.5); fillStroke(ctx);
      break;
    case 'crown': {
      ctx.beginPath(); ctx.moveTo(-11, hy - 8); ctx.lineTo(-13, hy - 22); ctx.lineTo(-6, hy - 15); ctx.lineTo(0, hy - 25); ctx.lineTo(6, hy - 15); ctx.lineTo(13, hy - 22); ctx.lineTo(11, hy - 8); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#3ad1ff'; circlePath(ctx, 0, hy - 12, 2.5); fillStroke(ctx);
      break;
    }
  }
}

// L: look object. (x, y) = feet. atk: attack progress 0..1 (0 = idle).
function drawChibi(ctx, L, x, y, s, t, atk = 0) {
  ctx.save();
  ctx.translate(x, y); ctx.scale(L.flip ? -s : s, s);
  ctx.lineJoin = 'round'; ctx.lineCap = 'round'; ctx.strokeStyle = OUT; ctx.lineWidth = 2.2;
  const ph = L.phase || 0;
  const bob = Math.sin(t * 5 + ph) * 1.2;
  const lean = atk > 0 && atk < 1 ? Math.sin(atk * Math.PI) * 4 : 0;
  ctx.fillStyle = 'rgba(0,0,0,.22)'; ctx.beginPath(); ctx.ellipse(0, 0, 17, 4, 0, 0, TAU); ctx.fill();
  ctx.translate(lean, 0);
  if (L.cape) {
    const w = Math.sin(t * 6 + ph) * 4;
    ctx.fillStyle = L.cape;
    ctx.beginPath(); ctx.moveTo(-5, -33 + bob); ctx.quadraticCurveTo(-22, -28 + bob, -34 + w, -18 + w * 0.5);
    ctx.lineTo(-30 + w, -10); ctx.quadraticCurveTo(-18, -16, -2, -24 + bob); ctx.closePath(); fillStroke(ctx);
  }
  if (L.aura) {
    ctx.save(); ctx.globalAlpha = 0.5 + Math.sin(t * 3) * 0.2;
    ctx.fillStyle = L.aura;
    for (let i = 0; i < 6; i++) {
      const a = t * 1.6 + (i / 6) * TAU;
      circlePath(ctx, Math.cos(a) * 22, -28 + Math.sin(a) * 8 + Math.sin(t * 4 + i) * 3, 2.2); ctx.fill();
    }
    ctx.restore();
  }
  // legs
  const step = atk > 0 && atk < 1 ? Math.sin(atk * Math.PI) * 3 : 0;
  ctx.fillStyle = L.pants || '#3a3a4a';
  rrPath(ctx, -8 - step, -14, 6, 12, 2); fillStroke(ctx);
  rrPath(ctx, 2 + step, -14, 6, 12, 2); fillStroke(ctx);
  ctx.fillStyle = L.boots || '#4a2f1f';
  rrPath(ctx, -9 - step, -4, 9, 5, 2); fillStroke(ctx);
  rrPath(ctx, 1 + step, -4, 10, 5, 2); fillStroke(ctx);
  // body
  ctx.fillStyle = L.outfit; rrPath(ctx, -10, -35 + bob, 20, 23, 6); fillStroke(ctx);
  ctx.fillStyle = L.trim || '#e8c547'; ctx.fillRect(-9, -18 + bob, 18, 3);
  ctx.fillStyle = 'rgba(255,255,255,.18)'; ctx.fillRect(-8, -33 + bob, 4, 13);
  // head
  const hy = -47 + bob;
  if (L.hairStyle === 'hood') {
    ctx.fillStyle = L.hood; circlePath(ctx, -1, hy, 15); fillStroke(ctx);
    ctx.fillStyle = L.skin; ctx.beginPath(); ctx.ellipse(3, hy + 2, 9, 9.5, 0, 0, TAU); fillStroke(ctx);
  } else {
    drawHair(ctx, L, hy, true);
    ctx.fillStyle = L.skin; circlePath(ctx, 0, hy, 12.5); fillStroke(ctx);
  }
  // face
  if (L.hairStyle !== 'helmet' && L.helmGear !== 'visor' && L.helmGear !== 'horned') {
    ctx.fillStyle = OUT;
    ctx.beginPath(); ctx.ellipse(3, hy + 1, 1.6, 2.6, 0, 0, TAU); ctx.fill();
    ctx.beginPath(); ctx.ellipse(9, hy + 1, 1.5, 2.5, 0, 0, TAU); ctx.fill();
    ctx.fillStyle = 'rgba(255,120,120,.35)'; circlePath(ctx, 8, hy + 6, 2.5); ctx.fill();
  }
  if (L.hairStyle !== 'hood') drawHair(ctx, L, hy, false);
  if (L.helmGear) drawHelmGear(ctx, L.helmGear, L.helmColor, hy);
  // weapon arm
  ctx.save();
  ctx.translate(7, -25 + bob);
  ctx.rotate(weaponAngle(L.weapon, atk));
  drawWeapon(ctx, L.weapon, L);
  ctx.strokeStyle = OUT; ctx.lineWidth = 2;
  ctx.fillStyle = L.skin; circlePath(ctx, 0, 0, 3.4); fillStroke(ctx);
  ctx.restore();
  ctx.restore();
}

// ======================================================================
// Monsters: generated from a seed so every one looks different.
// ======================================================================
const NAME_A = ['Gor', 'Bub', 'Zar', 'Mok', 'Fen', 'Skul', 'Gri', 'Vex', 'Plo', 'Drak', 'Nib', 'Hux', 'Kra', 'Lum', 'Ooz', 'Ral', 'Bor', 'Snag', 'Wib', 'Qua'];
const NAME_B = ['ble', 'gax', 'rin', 'zle', 'mog', 'ix', 'ox', 'ath', 'uk', 'ling', 'bert', 'ra', 'nok', 'pip', 'dor', 'goo'];
const TITLES = ['the Vile', 'the Huge', 'the Unkind', 'Lord of Muck', 'the Ancient', 'the Gluttonous', 'Bonecrusher', 'the Eternal', 'the Dreadful'];
const SHAPES = ['blob', 'tower', 'cone', 'orb', 'brute', 'slime'];

function genMonster(seed, opts = {}) {
  const r = mulberry32(seed);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const shape = pick(SHAPES);
  const hue = Math.floor(r() * 360);
  const sat = 40 + r() * 35, lit = 42 + r() * 14;
  const hsl = (l, s = sat, h = hue) => `hsl(${h},${s}%,${clamp(l, 4, 96)}%)`;
  const dims = { blob: [130, 125], tower: [92, 185], cone: [140, 180], orb: [112, 112], brute: [165, 150], slime: [150, 95] }[shape];
  const k = 0.88 + r() * 0.2;
  const spec = {
    shape, hue,
    w: dims[0] * k, h: dims[1] * k,
    color: hsl(lit), dark: hsl(lit - 16), light: hsl(lit + 18), belly: hsl(lit + 28, sat - 10),
    accent: hsl(55 + r() * 20, 70, 55, (hue + 150 + r() * 60) % 360),
    eyes: pick([1, 2, 2, 2, 3]), eyeR: 9 + r() * 6,
    mouth: pick(['grin', 'fangs', 'o', 'teeth', 'smile']),
    horns: r() < 0.5 ? pick(['curl', 'spike', 'antler']) : null,
    spikes: shape !== 'orb' && r() < 0.35,
    arms: shape !== 'slime' && shape !== 'orb' && r() < 0.6,
    legs: shape !== 'orb' && shape !== 'slime' && r() < 0.55,
    belly2: r() < 0.5, spots: r() < 0.35,
    float: shape === 'orb',
    name: pick(NAME_A) + pick(NAME_B),
    boss: !!opts.boss, mimic: !!opts.mimic,
  };
  if (spec.boss) spec.name += ' ' + pick(TITLES);
  if (spec.mimic) spec.name = 'Treasure Mimic';
  spec.spotSeed = Math.floor(r() * 1e6);
  return spec;
}

function bodyPath(ctx, sp) {
  const w = sp.w, h = sp.h;
  ctx.beginPath();
  switch (sp.shape) {
    case 'blob':
      ctx.moveTo(-w / 2, -h * 0.12);
      ctx.bezierCurveTo(-w / 2, -h * 1.02, w / 2, -h * 1.02, w / 2, -h * 0.12);
      ctx.quadraticCurveTo(w / 2, 0, w * 0.3, 0); ctx.lineTo(-w * 0.3, 0);
      ctx.quadraticCurveTo(-w / 2, 0, -w / 2, -h * 0.12);
      break;
    case 'tower':
      rrPath(ctx, -w / 2, -h, w, h, w * 0.42); return;
    case 'cone':
      ctx.moveTo(-w / 2, 0);
      ctx.quadraticCurveTo(-w * 0.28, -h * 0.55, -w * 0.05, -h * 0.97);
      ctx.quadraticCurveTo(0, -h * 1.02, w * 0.05, -h * 0.97);
      ctx.quadraticCurveTo(w * 0.28, -h * 0.55, w / 2, 0);
      ctx.closePath();
      break;
    case 'orb':
      ctx.arc(0, -h / 2, w / 2, 0, TAU); break;
    case 'brute':
      ctx.moveTo(-w * 0.36, 0);
      ctx.bezierCurveTo(-w * 0.62, -h * 0.5, -w * 0.5, -h * 0.92, -w * 0.12, -h * 0.95);
      ctx.quadraticCurveTo(0, -h * 1.08, w * 0.12, -h * 0.95);
      ctx.bezierCurveTo(w * 0.5, -h * 0.92, w * 0.62, -h * 0.5, w * 0.36, 0);
      ctx.closePath();
      break;
    case 'slime':
      ctx.moveTo(-w / 2, 0);
      ctx.bezierCurveTo(-w * 0.55, -h * 1.3, w * 0.55, -h * 1.3, w / 2, 0);
      for (let i = 4; i >= 0; i--) {
        const x0 = -w / 2 + (w * i) / 5;
        ctx.quadraticCurveTo(x0 + w / 10, 8 * ((i % 2) + 0.5), x0, 0);
      }
      ctx.closePath();
      break;
  }
}

function faceY(sp) {
  return { blob: 0.6, tower: 0.72, cone: 0.42, orb: 0.52, brute: 0.68, slime: 0.55 }[sp.shape] * sp.h;
}

function drawEye(ctx, x, y, r, blink, angry) {
  ctx.lineWidth = 2.5;
  if (blink) { ctx.beginPath(); ctx.moveTo(x - r, y); ctx.lineTo(x + r, y); ctx.stroke(); return; }
  ctx.fillStyle = '#fff'; circlePath(ctx, x, y, r); fillStroke(ctx);
  ctx.fillStyle = OUT; circlePath(ctx, x - r * 0.3, y + r * 0.05, r * 0.52); ctx.fill();
  ctx.fillStyle = '#fff'; circlePath(ctx, x - r * 0.45, y - r * 0.2, r * 0.17); ctx.fill();
  if (angry) {
    ctx.lineWidth = 3.5; ctx.beginPath(); ctx.moveTo(x - r * 1.1, y - r * 1.25); ctx.lineTo(x + r * 1.1, y - r * 0.75); ctx.stroke();
  }
}

function drawMonster(ctx, sp, x, gy, t, m) {
  const hitT = m ? m.hitT : 0, deathT = m && m.dead ? m.deathT : 0;
  const spawn = m ? clamp(m.age * 4, 0, 1) : 1;
  if (sp.mimic) return drawMimic(ctx, sp, x, gy, t, hitT, deathT, spawn);
  const scale = (sp.boss ? 1.22 : 1) * (1 - deathT * 0.6) * (0.4 + 0.6 * spawn);
  const breathe = Math.sin(t * 2.4 + sp.hue) * 0.03;
  const sq = hitT * 0.09;
  const legH = sp.legs ? 16 : 0;
  const floatY = sp.float ? -24 + Math.sin(t * 2) * 6 : 0;
  ctx.save();
  ctx.globalAlpha = 1 - deathT;
  ctx.translate(x, gy);
  ctx.fillStyle = 'rgba(0,0,0,.25)';
  ctx.beginPath(); ctx.ellipse(0, 0, sp.w * 0.45 * scale, 8 * scale, 0, 0, TAU); ctx.fill();
  ctx.scale(scale * (1 + sq - breathe), scale * (1 - sq + breathe));
  ctx.rotate(deathT * 0.4);
  ctx.translate(hitT * 6, floatY - legH);
  ctx.lineJoin = 'round'; ctx.lineCap = 'round'; ctx.strokeStyle = OUT; ctx.lineWidth = 4;
  const w = sp.w, h = sp.h, fy = -faceY(sp);

  if (sp.boss) {
    ctx.save(); ctx.globalAlpha *= 0.35 + Math.sin(t * 4) * 0.1;
    const g = ctx.createRadialGradient(0, -h / 2, 10, 0, -h / 2, w);
    g.addColorStop(0, '#ff3a3a'); g.addColorStop(1, 'rgba(255,0,0,0)');
    ctx.fillStyle = g; circlePath(ctx, 0, -h / 2, w); ctx.fill();
    ctx.restore();
  }
  // legs (behind body)
  if (sp.legs) {
    ctx.fillStyle = sp.dark;
    const step = Math.sin(t * 3) * 2;
    rrPath(ctx, -w * 0.3, -6, w * 0.2, legH + 8 + step, 6); fillStroke(ctx);
    rrPath(ctx, w * 0.1, -6, w * 0.2, legH + 8 - step, 6); fillStroke(ctx);
  }
  // back spikes
  if (sp.spikes) {
    ctx.fillStyle = sp.accent;
    for (let i = 0; i < 7; i++) {
      const a = Math.PI * (1.08 + i * 0.14);
      const cx = Math.cos(a) * w * 0.47, cy = -h * 0.5 + Math.sin(a) * h * 0.5;
      const ox = Math.cos(a) * 18, oy = Math.sin(a) * 18;
      const px = -Math.sin(a) * 8, py = Math.cos(a) * 8;
      ctx.beginPath(); ctx.moveTo(cx - px, cy - py); ctx.lineTo(cx + ox, cy + oy); ctx.lineTo(cx + px, cy + py); ctx.closePath(); fillStroke(ctx);
    }
  }
  // horns
  if (sp.horns) {
    const hx = sp.shape === 'cone' ? w * 0.13 : w * 0.26;
    const hy = sp.shape === 'cone' ? -h * 0.78 : sp.shape === 'tower' ? -h * 0.95 : -h * 0.86;
    ctx.fillStyle = sp.horns === 'antler' ? '#c9a27a' : '#e9c48a';
    ctx.lineWidth = 3.5;
    for (const d of [-1, 1]) {
      ctx.beginPath();
      if (sp.horns === 'curl') {
        ctx.moveTo(d * (hx - 8), hy + 6);
        ctx.quadraticCurveTo(d * (hx + 4), hy - 42, d * (hx + 26), hy - 34);
        ctx.quadraticCurveTo(d * (hx + 32), hy - 20, d * (hx + 20), hy - 22);
        ctx.quadraticCurveTo(d * (hx + 14), hy - 24, d * (hx + 8), hy + 4);
      } else if (sp.horns === 'spike') {
        ctx.moveTo(d * (hx - 9), hy + 4); ctx.quadraticCurveTo(d * (hx + 2), hy - 20, d * (hx + 10), hy - 40); ctx.quadraticCurveTo(d * (hx + 10), hy - 14, d * (hx + 9), hy + 4);
      } else {
        ctx.moveTo(d * (hx - 3), hy + 4); ctx.lineTo(d * (hx - 1), hy - 30); ctx.lineTo(d * (hx - 12), hy - 44); ctx.lineTo(d * (hx + 3), hy - 34); ctx.lineTo(d * (hx + 16), hy - 46); ctx.lineTo(d * (hx + 6), hy - 26); ctx.lineTo(d * (hx + 7), hy + 4);
      }
      ctx.closePath(); fillStroke(ctx);
    }
    ctx.lineWidth = 4;
  }
  // arms
  if (sp.arms) {
    ctx.lineWidth = 13; ctx.strokeStyle = OUT;
    const sway = Math.sin(t * 3 + 1) * 6;
    for (const d of [-1, 1]) {
      const sx = d * w * 0.4, sy = -h * 0.45;
      const path = () => { ctx.beginPath(); ctx.moveTo(sx, sy); ctx.quadraticCurveTo(d * (w * 0.68), sy - 10 + sway * d, d * (w * 0.62), sy + 38 + sway); };
      path(); ctx.stroke();
      ctx.lineWidth = 8; ctx.strokeStyle = sp.dark; path(); ctx.stroke();
      ctx.lineWidth = 3; ctx.strokeStyle = OUT; ctx.fillStyle = '#efe6d2';
      const cx = d * (w * 0.62), cy = sy + 38 + sway;
      for (let c = -1; c <= 1; c++) { ctx.beginPath(); ctx.moveTo(cx + c * 5, cy); ctx.lineTo(cx + c * 7, cy + 11); ctx.lineTo(cx + c * 5 + 3, cy + 1); ctx.closePath(); fillStroke(ctx); }
      ctx.lineWidth = 13;
    }
    ctx.lineWidth = 4;
  }
  // body
  bodyPath(ctx, sp);
  ctx.fillStyle = sp.color; ctx.fill();
  ctx.save(); ctx.clip();
  ctx.fillStyle = sp.dark; ctx.beginPath(); ctx.ellipse(w * 0.42, -h * 0.35, w * 0.4, h * 0.75, 0, 0, TAU); ctx.fill();
  ctx.fillStyle = sp.light; ctx.globalAlpha *= 0.55; ctx.beginPath(); ctx.ellipse(-w * 0.25, -h * 0.78, w * 0.18, h * 0.1, -0.5, 0, TAU); ctx.fill();
  ctx.globalAlpha = 1 - deathT;
  if (sp.belly2) { ctx.fillStyle = sp.belly; ctx.beginPath(); ctx.ellipse(0, -h * 0.12, w * 0.32, h * 0.32, 0, 0, TAU); ctx.fill(); }
  if (sp.spots) {
    const r2 = mulberry32(sp.spotSeed); ctx.fillStyle = sp.dark;
    for (let i = 0; i < 6; i++) { circlePath(ctx, (r2() - 0.5) * w * 0.8, -r2() * h * 0.9, 4 + r2() * 7); ctx.fill(); }
  }
  if (hitT > 0) { ctx.fillStyle = `rgba(255,255,255,${hitT * 0.55})`; ctx.fillRect(-w, -h * 1.2, w * 2, h * 1.4); }
  ctx.restore();
  bodyPath(ctx, sp); ctx.stroke();
  // eyes
  const blink = (t + sp.hue * 0.01) % 3.7 < 0.12;
  const er = sp.eyeR;
  if (sp.eyes === 1) drawEye(ctx, 0, fy, er * 1.5, blink, sp.boss);
  else if (sp.eyes === 2) { drawEye(ctx, -er * 1.25, fy, er, blink, sp.boss); drawEye(ctx, er * 1.25, fy, er, blink, sp.boss); }
  else { drawEye(ctx, -er * 1.9, fy + 2, er * 0.85, blink, sp.boss); drawEye(ctx, 0, fy - er * 0.6, er, blink, sp.boss); drawEye(ctx, er * 1.9, fy + 2, er * 0.85, blink, sp.boss); }
  // mouth
  const my = fy + er * 2 + 4, open = 1 + hitT * 0.8;
  ctx.lineWidth = 3.5; ctx.fillStyle = '#4a1020';
  switch (sp.mouth) {
    case 'grin':
      ctx.beginPath(); ctx.moveTo(-20, my); ctx.quadraticCurveTo(0, my + 22 * open, 20, my); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#fff'; ctx.fillRect(-9, my + 1, 7, 6); ctx.fillRect(2, my + 1, 7, 6);
      break;
    case 'fangs':
      ctx.beginPath(); ctx.moveTo(-18, my); ctx.lineTo(18, my); ctx.stroke();
      ctx.fillStyle = '#fff';
      for (const d of [-1, 1]) { ctx.beginPath(); ctx.moveTo(d * 12, my); ctx.lineTo(d * 8, my + 11 * open); ctx.lineTo(d * 4, my); ctx.closePath(); fillStroke(ctx); }
      break;
    case 'o':
      ctx.beginPath(); ctx.ellipse(0, my + 4, 7, 8 * open, 0, 0, TAU); fillStroke(ctx);
      break;
    case 'teeth':
      rrPath(ctx, -22, my - 2, 44, 14 * open, 5); fillStroke(ctx);
      ctx.fillStyle = '#fff';
      for (let i = 0; i < 5; i++) { ctx.beginPath(); ctx.moveTo(-20 + i * 8.5, my - 1); ctx.lineTo(-16 + i * 8.5, my + 6); ctx.lineTo(-12 + i * 8.5, my - 1); ctx.fill(); }
      break;
    default:
      ctx.beginPath(); ctx.arc(0, my - 4, 12, 0.2, Math.PI - 0.2); ctx.stroke();
  }
  // boss crown
  if (sp.boss) {
    const top = sp.shape === 'orb' ? -h : -h - (sp.shape === 'cone' ? -4 : 2);
    ctx.fillStyle = '#ffcf3a'; ctx.lineWidth = 3;
    ctx.beginPath(); ctx.moveTo(-20, top + 4); ctx.lineTo(-24, top - 22); ctx.lineTo(-10, top - 10); ctx.lineTo(0, top - 28); ctx.lineTo(10, top - 10); ctx.lineTo(24, top - 22); ctx.lineTo(20, top + 4); ctx.closePath(); fillStroke(ctx);
    ctx.fillStyle = '#e63950'; circlePath(ctx, 0, top - 6, 4); fillStroke(ctx);
  }
  ctx.restore();
}

function drawMimic(ctx, sp, x, gy, t, hitT, deathT, spawn) {
  const s = (1 - deathT * 0.6) * (0.4 + 0.6 * spawn);
  const chomp = Math.abs(Math.sin(t * 3)) * 0.35 + hitT * 0.3;
  ctx.save(); ctx.globalAlpha = 1 - deathT;
  ctx.translate(x, gy);
  ctx.fillStyle = 'rgba(0,0,0,.25)'; ctx.beginPath(); ctx.ellipse(0, 0, 70 * s, 8 * s, 0, 0, TAU); ctx.fill();
  ctx.scale(s * (1 + hitT * 0.08), s * (1 - hitT * 0.08));
  ctx.translate(0, -Math.abs(Math.sin(t * 6)) * 6);
  ctx.strokeStyle = OUT; ctx.lineWidth = 4; ctx.lineJoin = 'round';
  // base
  ctx.fillStyle = '#a8662f'; rrPath(ctx, -70, -70, 140, 70, 8); fillStroke(ctx);
  ctx.fillStyle = '#7a4520'; for (let i = -1; i <= 1; i++) ctx.fillRect(-68, -60 + i * 0 + i * 18 + 18, 136, 3);
  ctx.fillStyle = '#e8b83a'; rrPath(ctx, -74, -72, 14, 72, 4); fillStroke(ctx); rrPath(ctx, 60, -72, 14, 72, 4); fillStroke(ctx);
  // mouth interior
  ctx.fillStyle = '#3a0a14'; rrPath(ctx, -64, -86, 128, 20, 6); ctx.fill();
  ctx.fillStyle = '#ff6a8a'; ctx.beginPath(); ctx.ellipse(10, -74, 24, 8, 0.1, 0, TAU); ctx.fill();
  // lid
  ctx.save(); ctx.translate(-70, -72); ctx.rotate(-chomp);
  ctx.fillStyle = '#b8743a'; ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(0, -34); ctx.quadraticCurveTo(70, -70, 140, -34); ctx.lineTo(140, 0); ctx.closePath(); fillStroke(ctx);
  ctx.fillStyle = '#e8b83a'; rrPath(ctx, 60, -16, 20, 22, 4); fillStroke(ctx);
  ctx.fillStyle = '#fff';
  for (let i = 0; i < 8; i++) { ctx.beginPath(); ctx.moveTo(10 + i * 16, 0); ctx.lineTo(18 + i * 16, 12); ctx.lineTo(26 + i * 16, 0); ctx.closePath(); fillStroke(ctx); }
  ctx.restore();
  // eyes in the dark
  ctx.fillStyle = '#ffe34a'; circlePath(ctx, -24, -80, 6); ctx.fill(); circlePath(ctx, 24, -80, 6); ctx.fill();
  ctx.fillStyle = OUT; circlePath(ctx, -26, -80, 2.5); ctx.fill(); circlePath(ctx, 22, -80, 2.5); ctx.fill();
  if (hitT > 0) { ctx.fillStyle = `rgba(255,255,255,${hitT * 0.5})`; ctx.fillRect(-76, -140, 152, 140); }
  ctx.restore();
}

// ======================================================================
// Pets: little winged critters.
// ======================================================================
function drawPet(ctx, p, x, y, s, t, attack = 0) {
  ctx.save(); ctx.translate(x, y); ctx.scale(s, s);
  ctx.strokeStyle = OUT; ctx.lineWidth = 2.5; ctx.lineJoin = 'round';
  const flap = Math.sin(t * 14) * 0.5;
  // wings
  ctx.fillStyle = p.wing;
  for (const d of [-1, 1]) {
    ctx.save(); ctx.scale(d, 1); ctx.rotate(flap * 0.6);
    ctx.beginPath(); ctx.moveTo(10, -4); ctx.quadraticCurveTo(30, -26, 40, -10); ctx.quadraticCurveTo(30, -8, 32, 2); ctx.quadraticCurveTo(22, 0, 10, 6); ctx.closePath(); fillStroke(ctx);
    ctx.restore();
  }
  // ears / horns
  ctx.fillStyle = p.ears === 'horns' ? '#f1e3c0' : p.body;
  if (p.ears !== 'none') for (const d of [-1, 1]) {
    ctx.beginPath();
    if (p.ears === 'fox') { ctx.moveTo(d * 6, -14); ctx.lineTo(d * 14, -30); ctx.lineTo(d * 17, -10); }
    else { ctx.moveTo(d * 5, -14); ctx.quadraticCurveTo(d * 10, -28, d * 16, -30); ctx.quadraticCurveTo(d * 14, -18, d * 13, -10); }
    ctx.closePath(); fillStroke(ctx);
  }
  // body
  ctx.fillStyle = p.body;
  if (p.fuzzy) {
    ctx.beginPath();
    for (let i = 0; i <= 20; i++) { const a = (i / 20) * TAU, rr = i % 2 ? 17 : 20; ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
    ctx.closePath(); fillStroke(ctx);
  } else { ctx.beginPath(); ctx.ellipse(0, 0, 18, 19, 0, 0, TAU); fillStroke(ctx); }
  ctx.fillStyle = p.belly; ctx.beginPath(); ctx.ellipse(0, 8, 10, 8, 0, 0, TAU); ctx.fill();
  // face
  ctx.fillStyle = OUT;
  if (p.sleepy) { ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(-9, -3); ctx.lineTo(-3, -3); ctx.moveTo(3, -3); ctx.lineTo(9, -3); ctx.stroke(); }
  else { circlePath(ctx, -6, -3, 2.6 + attack); ctx.fill(); circlePath(ctx, 6, -3, 2.6 + attack); ctx.fill(); ctx.fillStyle = '#fff'; circlePath(ctx, -6.8, -4, 0.9); ctx.fill(); circlePath(ctx, 5.2, -4, 0.9); ctx.fill(); }
  ctx.fillStyle = '#fff'; ctx.lineWidth = 1.5;
  ctx.beginPath(); ctx.moveTo(-3, 3); ctx.lineTo(-1.5, 6); ctx.lineTo(0, 3); ctx.lineTo(1.5, 6); ctx.lineTo(3, 3); ctx.closePath(); fillStroke(ctx);
  if (p.bow) {
    ctx.fillStyle = '#ffd23a'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(0, 10); ctx.lineTo(-8, 5); ctx.lineTo(-8, 15); ctx.closePath(); fillStroke(ctx);
    ctx.beginPath(); ctx.moveTo(0, 10); ctx.lineTo(8, 5); ctx.lineTo(8, 15); ctx.closePath(); fillStroke(ctx);
  }
  ctx.restore();
}

// ======================================================================
// Backgrounds (static layers cached to an offscreen canvas).
// ======================================================================
function makeBackground(world, W, H, gy, dpr) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.floor(W * dpr)); c.height = Math.max(1, Math.floor(H * dpr));
  const ctx = c.getContext('2d');
  ctx.scale(dpr, dpr);
  const r = mulberry32(world.name.length * 1013 + 7);
  const sky = ctx.createLinearGradient(0, 0, 0, gy);
  sky.addColorStop(0, world.sky[0]); sky.addColorStop(1, world.sky[1]);
  ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
  // sun / moon
  const dark = ['forest', 'lava', 'void'].includes(world.kind);
  ctx.save(); ctx.globalAlpha = 0.85;
  const sx = W * 0.8, sy = gy * 0.22;
  const sg = ctx.createRadialGradient(sx, sy, 4, sx, sy, 60);
  sg.addColorStop(0, dark ? '#fff8e0' : '#fffbe8'); sg.addColorStop(0.35, dark ? 'rgba(255,240,200,.6)' : 'rgba(255,250,220,.7)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = sg; circlePath(ctx, sx, sy, 60); ctx.fill();
  ctx.restore();
  if (world.kind === 'void' || world.kind === 'forest') {
    ctx.fillStyle = '#fff';
    for (let i = 0; i < 70; i++) { ctx.globalAlpha = 0.3 + r() * 0.6; ctx.fillRect(r() * W, r() * gy * 0.7, 1.5, 1.5); }
    ctx.globalAlpha = 1;
  }
  // far mountains
  ctx.fillStyle = world.far;
  ctx.beginPath(); ctx.moveTo(0, gy);
  for (let x = 0; x <= W + 40; x += 40) ctx.lineTo(x, gy * (0.5 + r() * 0.18));
  ctx.lineTo(W, gy); ctx.closePath(); ctx.fill();

  const mid = world.mid, md = world.midDark;
  const horizon = gy * 0.62;
  ctx.lineWidth = 1.5; ctx.strokeStyle = shade(md, -0.2);
  switch (world.kind) {
    case 'castle': {
      for (let i = 0; i < 4; i++) { // towers
        const tw = 34 + r() * 30, th = 70 + r() * 80, tx = r() * W;
        ctx.fillStyle = shade(mid, -0.06); ctx.fillRect(tx, horizon - th, tw, th + gy);
        for (let k = 0; k < tw; k += 10) ctx.fillRect(tx + k, horizon - th - 8, 6, 8);
        ctx.fillStyle = shade(md, -0.25);
        for (let k = 0; k < 3; k++) rrPath(ctx, tx + tw / 2 - 4, horizon - th + 16 + k * 24, 8, 12, 4), ctx.fill();
        ctx.strokeStyle = shade(md, -0.2); ctx.beginPath(); ctx.moveTo(tx + tw / 2, horizon - th - 8); ctx.lineTo(tx + tw / 2, horizon - th - 26); ctx.stroke();
        ctx.fillStyle = '#d14b4b'; ctx.beginPath(); ctx.moveTo(tx + tw / 2, horizon - th - 26); ctx.lineTo(tx + tw / 2 + 12, horizon - th - 22); ctx.lineTo(tx + tw / 2, horizon - th - 18); ctx.fill();
      }
      // colonnade wall
      const wy = gy - gy * 0.32;
      ctx.fillStyle = mid; ctx.fillRect(0, wy, W, gy - wy);
      ctx.fillStyle = shade(mid, 0.25); ctx.fillRect(0, wy - 8, W, 10);
      ctx.fillStyle = md;
      for (let x = 10; x < W; x += 54) {
        rrPath(ctx, x, wy + 14, 36, gy - wy - 14, 18); ctx.fill();
      }
      ctx.fillStyle = shade(mid, 0.12);
      for (let x = 0; x < W; x += 54) ctx.fillRect(x, wy + 6, 9, gy - wy);
      ctx.strokeStyle = shade(md, -0.1); ctx.globalAlpha = 0.35;
      for (let y = wy + 12; y < gy; y += 12) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke(); }
      ctx.globalAlpha = 1;
      break;
    }
    case 'dunes': {
      for (let i = 0; i < 3; i++) {
        const px = r() * W, pw = 70 + r() * 70;
        ctx.fillStyle = mid; ctx.beginPath(); ctx.moveTo(px - pw, horizon + 30); ctx.lineTo(px, horizon - pw * 0.8); ctx.lineTo(px + pw, horizon + 30); ctx.fill();
        ctx.fillStyle = md; ctx.beginPath(); ctx.moveTo(px, horizon - pw * 0.8); ctx.lineTo(px + pw, horizon + 30); ctx.lineTo(px + pw * 0.2, horizon + 30); ctx.fill();
      }
      for (let k = 0; k < 2; k++) {
        ctx.fillStyle = k ? mid : shade(mid, 0.1);
        ctx.beginPath(); ctx.moveTo(0, gy);
        for (let x = 0; x <= W; x += 10) ctx.lineTo(x, gy - 40 - k * 20 + Math.sin(x / 70 + k * 2) * 18);
        ctx.lineTo(W, gy); ctx.fill();
      }
      for (let i = 0; i < 4; i++) { // cacti
        const cx = r() * W, ch = 30 + r() * 25;
        ctx.fillStyle = '#4f8a3a'; ctx.strokeStyle = shade('#4f8a3a', -0.4);
        rrPath(ctx, cx, gy - ch, 10, ch, 5); ctx.fill(); ctx.stroke();
        rrPath(ctx, cx - 10, gy - ch * 0.7, 8, 16, 4); ctx.fill(); ctx.stroke();
      }
      break;
    }
    case 'peaks': {
      for (let i = 0; i < 6; i++) {
        const px = (i / 5) * W + (r() - 0.5) * 60, ph = 90 + r() * 90, pw = 70 + r() * 50;
        ctx.fillStyle = md; ctx.beginPath(); ctx.moveTo(px - pw, gy - 20); ctx.lineTo(px, gy - 20 - ph); ctx.lineTo(px + pw, gy - 20); ctx.fill();
        ctx.fillStyle = mid; ctx.beginPath(); ctx.moveTo(px - pw * 0.35, gy - 20 - ph * 0.65); ctx.lineTo(px, gy - 20 - ph); ctx.lineTo(px + pw * 0.35, gy - 20 - ph * 0.65);
        ctx.lineTo(px + 10, gy - 20 - ph * 0.55); ctx.lineTo(px - 8, gy - 20 - ph * 0.68); ctx.closePath(); ctx.fill();
      }
      for (let i = 0; i < 9; i++) { // snowy pines
        const tx = r() * W, th = 26 + r() * 20;
        ctx.fillStyle = '#2f5a4a'; ctx.beginPath(); ctx.moveTo(tx, gy - th - 8); ctx.lineTo(tx - th * 0.4, gy - 6); ctx.lineTo(tx + th * 0.4, gy - 6); ctx.fill();
        ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.moveTo(tx, gy - th - 8); ctx.lineTo(tx - th * 0.16, gy - th * 0.6); ctx.lineTo(tx + th * 0.16, gy - th * 0.6); ctx.fill();
      }
      break;
    }
    case 'forest': {
      for (let k = 0; k < 2; k++) {
        for (let i = 0; i < 14; i++) {
          const tx = r() * W, th = (k ? 90 : 130) + r() * 60;
          ctx.fillStyle = k ? mid : shade(world.far, -0.15);
          for (let j = 0; j < 3; j++) {
            ctx.beginPath(); ctx.moveTo(tx, gy - th + j * th * 0.22); ctx.lineTo(tx - th * (0.2 + j * 0.07), gy - th * 0.45 + j * th * 0.2); ctx.lineTo(tx + th * (0.2 + j * 0.07), gy - th * 0.45 + j * th * 0.2); ctx.fill();
          }
          ctx.fillRect(tx - 3, gy - th * 0.2, 6, th * 0.2);
        }
      }
      for (let i = 0; i < 6; i++) { // glowing mushrooms
        const mx = r() * W;
        ctx.fillStyle = '#d8d0c0'; ctx.fillRect(mx - 2, gy - 10, 4, 10);
        ctx.fillStyle = '#7ef0c8'; ctx.beginPath(); ctx.arc(mx, gy - 10, 7, Math.PI, TAU); ctx.fill();
      }
      break;
    }
    case 'lava': {
      const lg = ctx.createLinearGradient(0, horizon, 0, gy);
      lg.addColorStop(0, '#ff9a2a'); lg.addColorStop(1, '#c2280e');
      ctx.fillStyle = lg; ctx.fillRect(0, horizon + 20, W, gy - horizon);
      for (let i = 0; i < 8; i++) {
        const rx = r() * W, rh = 60 + r() * 110, rw = 30 + r() * 40;
        ctx.fillStyle = mid; ctx.beginPath(); ctx.moveTo(rx - rw, gy); ctx.lineTo(rx - rw * 0.3, gy - rh); ctx.lineTo(rx + rw * 0.2, gy - rh * 0.8); ctx.lineTo(rx + rw, gy); ctx.fill();
        ctx.strokeStyle = '#ff6a1a'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(rx - rw * 0.3, gy - rh * 0.8); ctx.lineTo(rx - rw * 0.1, gy - rh * 0.5); ctx.lineTo(rx - rw * 0.3, gy - rh * 0.25); ctx.stroke();
      }
      break;
    }
    case 'void': {
      for (let i = 0; i < 10; i++) {
        const cx = r() * W, cy = horizon - r() * horizon * 0.6, cs = 8 + r() * 22;
        ctx.fillStyle = r() < 0.5 ? mid : '#7ef0ff'; ctx.globalAlpha = 0.6 + r() * 0.4;
        ctx.beginPath(); ctx.moveTo(cx, cy - cs); ctx.lineTo(cx + cs * 0.45, cy); ctx.lineTo(cx, cy + cs * 0.7); ctx.lineTo(cx - cs * 0.45, cy); ctx.closePath(); ctx.fill();
      }
      ctx.globalAlpha = 1;
      for (let i = 0; i < 7; i++) {
        const cx = r() * W, ch = 40 + r() * 90;
        ctx.fillStyle = md; ctx.beginPath(); ctx.moveTo(cx - 14, gy); ctx.lineTo(cx - 6, gy - ch); ctx.lineTo(cx + 4, gy - ch - 14); ctx.lineTo(cx + 14, gy); ctx.fill();
        ctx.fillStyle = mid; ctx.beginPath(); ctx.moveTo(cx - 6, gy - ch); ctx.lineTo(cx + 4, gy - ch - 14); ctx.lineTo(cx + 2, gy); ctx.lineTo(cx - 10, gy); ctx.fill();
      }
      break;
    }
  }
  // ground
  ctx.fillStyle = world.ground; ctx.fillRect(0, gy, W, H - gy);
  ctx.fillStyle = world.groundTop; ctx.fillRect(0, gy - 3, W, 7);
  ctx.fillStyle = world.dirt; ctx.fillRect(0, gy + 14, W, H - gy);
  ctx.fillStyle = shade(world.dirt, 0.15);
  for (let x = 0; x < W; x += 22) { ctx.fillRect(x + r() * 6, gy + 20 + r() * (H - gy - 26), 10 + r() * 8, 4); }
  ctx.fillStyle = shade(world.ground, -0.15);
  for (let x = 0; x < W; x += 28) ctx.fillRect(x, gy + 6, 16, 3);
  return c;
}

// Animated ambience drawn on top of the cached background.
function drawAmbience(ctx, world, W, gy, t) {
  if (['castle', 'dunes', 'peaks'].includes(world.kind)) {
    ctx.fillStyle = 'rgba(255,255,255,.8)';
    for (let i = 0; i < 4; i++) {
      const x = ((t * (6 + i * 3) + i * 170) % (W + 160)) - 80, y = 30 + i * 26;
      ctx.beginPath(); ctx.ellipse(x, y, 30, 9, 0, 0, TAU); ctx.ellipse(x + 18, y - 6, 18, 9, 0, 0, TAU); ctx.ellipse(x - 16, y - 3, 14, 7, 0, 0, TAU); ctx.fill();
    }
  }
  if (world.kind === 'peaks') {
    ctx.fillStyle = 'rgba(255,255,255,.85)';
    for (let i = 0; i < 40; i++) { const x = (i * 97 + Math.sin(t + i) * 20 + t * 10) % W, y = (i * 53 + t * (20 + (i % 5) * 6)) % gy; ctx.fillRect(x, y, 2.5, 2.5); }
  }
  if (world.kind === 'lava') {
    for (let i = 0; i < 25; i++) {
      const x = (i * 83) % W + Math.sin(t * 2 + i) * 8, y = gy - ((t * (25 + (i % 7) * 8) + i * 40) % gy);
      ctx.fillStyle = `rgba(255,${120 + (i % 4) * 30},40,${0.4 + (i % 3) * 0.2})`; ctx.fillRect(x, y, 3, 3);
    }
  }
  if (world.kind === 'forest' || world.kind === 'void') {
    for (let i = 0; i < 18; i++) {
      const x = (i * 71 + Math.sin(t * 0.7 + i) * 30) % W, y = gy * 0.4 + ((i * 37) % (gy * 0.55)) + Math.cos(t + i) * 10;
      ctx.globalAlpha = 0.4 + 0.4 * Math.sin(t * 3 + i);
      ctx.fillStyle = world.kind === 'forest' ? '#d8ff7a' : '#9ff4ff'; circlePath(ctx, x, y, 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
}

// ======================================================================
// Small items & icons
// ======================================================================
function drawCoin(ctx, x, y, r, spin = 1) {
  ctx.save(); ctx.translate(x, y); ctx.scale(Math.max(0.15, Math.abs(spin)), 1);
  ctx.lineWidth = Math.max(1.2, r * 0.18); ctx.strokeStyle = '#a86a0a';
  ctx.fillStyle = '#ffcd3a'; circlePath(ctx, 0, 0, r); fillStroke(ctx);
  ctx.fillStyle = '#f2a81e'; circlePath(ctx, 0, 0, r * 0.65); ctx.fill();
  ctx.fillStyle = '#ffe58a';
  ctx.beginPath(); ctx.moveTo(-r * 0.4, r * 0.25); ctx.lineTo(-r * 0.45, -r * 0.3); ctx.lineTo(-r * 0.18, -r * 0.05); ctx.lineTo(0, -r * 0.38); ctx.lineTo(r * 0.18, -r * 0.05); ctx.lineTo(r * 0.45, -r * 0.3); ctx.lineTo(r * 0.4, r * 0.25); ctx.closePath(); ctx.fill();
  ctx.restore();
}
function drawGem(ctx, x, y, r) {
  ctx.save(); ctx.translate(x, y);
  ctx.lineWidth = Math.max(1, r * 0.12); ctx.strokeStyle = '#0a5a8a'; ctx.lineJoin = 'round';
  ctx.fillStyle = '#4ad8ff';
  ctx.beginPath(); ctx.moveTo(-r, -r * 0.3); ctx.lineTo(-r * 0.5, -r * 0.8); ctx.lineTo(r * 0.5, -r * 0.8); ctx.lineTo(r, -r * 0.3); ctx.lineTo(0, r); ctx.closePath(); fillStroke(ctx);
  ctx.fillStyle = '#b5f1ff'; ctx.beginPath(); ctx.moveTo(-r * 0.5, -r * 0.8); ctx.lineTo(-r * 0.15, -r * 0.3); ctx.lineTo(-r, -r * 0.3); ctx.closePath(); ctx.fill();
  ctx.fillStyle = '#1aa6e0'; ctx.beginPath(); ctx.moveTo(r * 0.15, -r * 0.3); ctx.lineTo(r, -r * 0.3); ctx.lineTo(0, r); ctx.closePath(); ctx.fill();
  ctx.restore();
}
function drawRelic(ctx, x, y, r) {
  ctx.save(); ctx.translate(x, y); ctx.lineWidth = Math.max(1, r * 0.12); ctx.strokeStyle = OUT;
  ctx.fillStyle = '#fff'; for (const d of [-1, 1]) { ctx.beginPath(); ctx.ellipse(d * r * 0.7, -r * 0.35, r * 0.55, r * 0.28, d * -0.5, 0, TAU); fillStroke(ctx); }
  ctx.fillStyle = '#e8283a'; ctx.beginPath(); ctx.moveTo(0, r); ctx.bezierCurveTo(-r * 1.1, 0, -r * 0.6, -r * 0.9, 0, -r * 0.35); ctx.bezierCurveTo(r * 0.6, -r * 0.9, r * 1.1, 0, 0, r); fillStroke(ctx);
  ctx.fillStyle = '#ffd23a'; circlePath(ctx, 0, -r * 0.45, r * 0.25); fillStroke(ctx);
  ctx.restore();
}
function drawChest(ctx, c, x, y, s) {
  ctx.save(); ctx.translate(x, y); ctx.scale(s, s); ctx.strokeStyle = OUT; ctx.lineWidth = 2.5; ctx.lineJoin = 'round';
  ctx.fillStyle = c.body; rrPath(ctx, -26, -6, 52, 26, 3); fillStroke(ctx);
  ctx.beginPath(); ctx.moveTo(-26, -6); ctx.lineTo(-26, -16); ctx.quadraticCurveTo(0, -34, 26, -16); ctx.lineTo(26, -6); ctx.closePath(); ctx.fillStyle = shade(c.body, 0.12); fillStroke(ctx);
  ctx.fillStyle = c.band;
  rrPath(ctx, -20, -26, 7, 46, 2); fillStroke(ctx); rrPath(ctx, 13, -26, 7, 46, 2); fillStroke(ctx);
  rrPath(ctx, -6, -10, 12, 12, 2); fillStroke(ctx);
  ctx.fillStyle = OUT; ctx.fillRect(-1.5, -6, 3, 5);
  ctx.restore();
}
function drawWisp(ctx, x, y, t) {
  ctx.save(); ctx.translate(x, y);
  const g = ctx.createRadialGradient(0, 0, 2, 0, 0, 26);
  g.addColorStop(0, 'rgba(255,255,220,1)'); g.addColorStop(0.4, 'rgba(160,255,240,.7)'); g.addColorStop(1, 'rgba(120,200,255,0)');
  ctx.fillStyle = g; circlePath(ctx, 0, 0, 26); ctx.fill();
  ctx.fillStyle = 'rgba(255,255,255,.75)';
  for (const d of [-1, 1]) { ctx.save(); ctx.scale(d, 1); ctx.rotate(Math.sin(t * 20) * 0.4); ctx.beginPath(); ctx.ellipse(10, -6, 10, 5, -0.5, 0, TAU); ctx.fill(); ctx.restore(); }
  ctx.fillStyle = '#fff'; circlePath(ctx, 0, 0, 6); ctx.fill();
  ctx.restore();
}

// Icon glyphs on a coloured tile, used throughout the UI.
const ICON_BG = {
  thunder: ['#2a6fe0', '#8fd0ff'], frenzy: ['#d04a2a', '#ffb06a'], warcry: ['#b8861a', '#ffe08a'], midas: ['#c9a01a', '#fff0a0'],
  berserk: ['#b3202a', '#ff7a4a'], eagle: ['#2a8a6a', '#a8ffd8'], surge: ['#d0502a', '#ffd23a'], phantom: ['#5a3ab0', '#c8a8ff'],
  shower: ['#c98a1a', '#ffe58a'], flask: ['#1a7ab0', '#8ae8ff'], blade: ['#5a6a80', '#d8e2f0'], gauntlet: ['#7a4a2a', '#e0b080'],
  banner: ['#9a2a2a', '#ff8a6a'], idol: ['#a88a1a', '#ffe680'], clover: ['#2a8a3a', '#a8f08a'], fang: ['#6a2a8a', '#e0a8ff'],
  hourglass: ['#6a5a3a', '#f0dca0'], crystal: ['#2a5ab0', '#8ad8ff'], tome: ['#5a2a8a', '#d0a0ff'], boots: ['#3a5a2a', '#b0e08a'],
  crown: ['#8a1a3a', '#ffb0c0'], key: ['#8a6a1a', '#ffe08a'], prestige: ['#2a3a8a', '#d8e8ff'], tap: ['#c0402a', '#ffc080'],
};
function drawIcon(ctx, key, size) {
  const s = size;
  ctx.save();
  ctx.clearRect(0, 0, s, s);
  const bg = ICON_BG[key] || ['#3a3f55', '#7a809a'];
  const g = ctx.createLinearGradient(0, 0, s, s); g.addColorStop(0, bg[1]); g.addColorStop(1, bg[0]);
  ctx.fillStyle = g; rrPath(ctx, 1, 1, s - 2, s - 2, s * 0.16); ctx.fill();
  ctx.strokeStyle = 'rgba(0,0,0,.35)'; ctx.lineWidth = 2; ctx.stroke();
  ctx.translate(s / 2, s / 2); ctx.scale(s / 64, s / 64);
  ctx.lineJoin = 'round'; ctx.lineCap = 'round'; ctx.strokeStyle = OUT; ctx.lineWidth = 3;
  const W_ = '#fff';
  switch (key) {
    case 'thunder': case 'surge':
      ctx.fillStyle = '#fff36a'; ctx.beginPath(); ctx.moveTo(4, -26); ctx.lineTo(-14, 4); ctx.lineTo(-1, 4); ctx.lineTo(-6, 26); ctx.lineTo(14, -6); ctx.lineTo(1, -6); ctx.closePath(); fillStroke(ctx);
      if (key === 'surge') { ctx.fillStyle = '#ff5a2a'; ctx.beginPath(); ctx.moveTo(14, 4); ctx.lineTo(24, 18); ctx.lineTo(4, 18); ctx.closePath(); fillStroke(ctx); }
      break;
    case 'frenzy': case 'tap': case 'blade': case 'berserk': {
      ctx.save(); ctx.rotate(0.75);
      if (key === 'berserk') { ctx.save(); ctx.shadowColor = '#ff4a1a'; ctx.shadowBlur = 12; }
      ctx.fillStyle = key === 'berserk' ? '#ffb07a' : '#eef3fa';
      ctx.beginPath(); ctx.moveTo(-4, 12); ctx.lineTo(-4, -22); ctx.lineTo(0, -30); ctx.lineTo(4, -22); ctx.lineTo(4, 12); ctx.closePath(); fillStroke(ctx);
      if (key === 'berserk') ctx.restore();
      ctx.fillStyle = '#e8b33a'; rrPath(ctx, -11, 10, 22, 5, 2); fillStroke(ctx);
      ctx.fillStyle = '#6a3a22'; rrPath(ctx, -3, 14, 6, 12, 2); fillStroke(ctx);
      ctx.restore();
      if (key === 'frenzy') { ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; for (let i = 0; i < 3; i++) { ctx.beginPath(); ctx.arc(0, 0, 18 + i * 6, -2.6 + i * 0.2, -1.6 + i * 0.2); ctx.stroke(); } }
      break;
    }
    case 'warcry': case 'banner':
      ctx.strokeStyle = OUT; ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(-14, 26); ctx.lineTo(-14, -26); ctx.stroke();
      ctx.lineWidth = 3; ctx.fillStyle = key === 'banner' ? '#e83a3a' : '#ffd23a';
      ctx.beginPath(); ctx.moveTo(-14, -24); ctx.lineTo(18, -24); ctx.lineTo(10, -12); ctx.lineTo(18, 0); ctx.lineTo(-14, 0); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = W_; circlePath(ctx, 0, -12, 4); ctx.fill();
      break;
    case 'midas': case 'shower': case 'idol':
      if (key === 'idol') {
        ctx.fillStyle = '#ffd23a'; circlePath(ctx, 0, -14, 9); fillStroke(ctx); rrPath(ctx, -12, -4, 24, 26, 6); fillStroke(ctx);
        ctx.fillStyle = OUT; circlePath(ctx, -3, -15, 1.8); ctx.fill(); circlePath(ctx, 3, -15, 1.8); ctx.fill();
      } else {
        drawCoin(ctx, -8, 6, 14); drawCoin(ctx, 10, -4, 14);
        if (key === 'shower') drawCoin(ctx, 0, -18, 10);
        ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.moveTo(18, -24); ctx.lineTo(20, -18); ctx.lineTo(26, -16); ctx.lineTo(20, -14); ctx.lineTo(18, -8); ctx.lineTo(16, -14); ctx.lineTo(10, -16); ctx.lineTo(16, -18); ctx.closePath(); ctx.fill();
      }
      break;
    case 'eagle':
      ctx.fillStyle = W_; ctx.beginPath(); ctx.moveTo(-26, 0); ctx.quadraticCurveTo(0, -24, 26, 0); ctx.quadraticCurveTo(0, 24, -26, 0); fillStroke(ctx);
      ctx.fillStyle = '#2a9a6a'; circlePath(ctx, 0, 0, 10); fillStroke(ctx); ctx.fillStyle = OUT; circlePath(ctx, 0, 0, 4.5); ctx.fill();
      break;
    case 'phantom':
      ctx.fillStyle = 'rgba(255,255,255,.9)';
      rrPath(ctx, -14, -4, 28, 26, 8); fillStroke(ctx);
      for (let i = 0; i < 4; i++) { rrPath(ctx, -14 + i * 7.5, -24 + Math.abs(i - 1.5) * 4, 6, 24, 3); fillStroke(ctx); }
      break;
    case 'flask': case 'crystal':
      if (key === 'flask') {
        ctx.fillStyle = '#5ad8ff'; ctx.beginPath(); ctx.moveTo(-6, -22); ctx.lineTo(-6, -8); ctx.quadraticCurveTo(-22, 0, -16, 16); ctx.quadraticCurveTo(0, 28, 16, 16); ctx.quadraticCurveTo(22, 0, 6, -8); ctx.lineTo(6, -22); ctx.closePath(); fillStroke(ctx);
        ctx.fillStyle = '#c98a4a'; rrPath(ctx, -8, -28, 16, 7, 2); fillStroke(ctx);
      } else {
        ctx.fillStyle = '#7ae0ff'; ctx.beginPath(); ctx.moveTo(0, -28); ctx.lineTo(16, -6); ctx.lineTo(0, 28); ctx.lineTo(-16, -6); ctx.closePath(); fillStroke(ctx);
        ctx.fillStyle = 'rgba(255,255,255,.6)'; ctx.beginPath(); ctx.moveTo(0, -28); ctx.lineTo(-6, -6); ctx.lineTo(0, 22); ctx.lineTo(-16, -6); ctx.closePath(); ctx.fill();
      }
      break;
    case 'gauntlet':
      ctx.fillStyle = '#c0c8d4'; rrPath(ctx, -14, -8, 28, 24, 6); fillStroke(ctx);
      for (let i = 0; i < 4; i++) { rrPath(ctx, -14 + i * 7, -20, 7, 14, 3); fillStroke(ctx); }
      ctx.fillStyle = '#8a5a3a'; rrPath(ctx, -12, 16, 24, 10, 3); fillStroke(ctx);
      break;
    case 'clover':
      ctx.fillStyle = '#5ad84a';
      for (let i = 0; i < 4; i++) { const a = i * Math.PI / 2; circlePath(ctx, Math.cos(a) * 10, Math.sin(a) * 10 - 4, 10); fillStroke(ctx); }
      ctx.strokeStyle = '#2a6a1a'; ctx.lineWidth = 4; ctx.beginPath(); ctx.moveTo(0, 4); ctx.quadraticCurveTo(4, 18, 12, 26); ctx.stroke();
      break;
    case 'fang':
      ctx.fillStyle = '#f6efe0'; ctx.beginPath(); ctx.moveTo(-12, -24); ctx.quadraticCurveTo(14, -24, 12, -20); ctx.quadraticCurveTo(10, 6, -6, 28); ctx.quadraticCurveTo(0, 0, -12, -24); fillStroke(ctx);
      break;
    case 'hourglass':
      ctx.fillStyle = '#c98a4a'; rrPath(ctx, -18, -28, 36, 6, 2); fillStroke(ctx); rrPath(ctx, -18, 22, 36, 6, 2); fillStroke(ctx);
      ctx.fillStyle = 'rgba(220,240,255,.85)'; ctx.beginPath(); ctx.moveTo(-14, -22); ctx.lineTo(14, -22); ctx.lineTo(2, 0); ctx.lineTo(14, 22); ctx.lineTo(-14, 22); ctx.lineTo(-2, 0); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#f2c94c'; ctx.beginPath(); ctx.moveTo(-8, 21); ctx.lineTo(8, 21); ctx.lineTo(0, 10); ctx.fill(); ctx.beginPath(); ctx.moveTo(-8, -16); ctx.lineTo(8, -16); ctx.lineTo(0, -4); ctx.fill();
      break;
    case 'tome':
      ctx.fillStyle = '#7a3ab0'; rrPath(ctx, -18, -22, 36, 44, 4); fillStroke(ctx);
      ctx.fillStyle = '#f2e8d0'; ctx.fillRect(12, -18, 5, 36);
      ctx.fillStyle = '#ffd23a'; ctx.beginPath(); ctx.moveTo(-2, -10); ctx.lineTo(2, -2); ctx.lineTo(10, 0); ctx.lineTo(2, 2); ctx.lineTo(-2, 10); ctx.lineTo(-6, 2); ctx.lineTo(-14, 0); ctx.lineTo(-6, -2); ctx.closePath(); ctx.fill();
      break;
    case 'boots':
      ctx.fillStyle = '#8a5a2a'; ctx.beginPath(); ctx.moveTo(-10, -24); ctx.lineTo(6, -24); ctx.lineTo(6, 8); ctx.lineTo(22, 12); ctx.lineTo(22, 22); ctx.lineTo(-10, 22); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.moveTo(-10, -18); ctx.lineTo(-26, -24); ctx.lineTo(-22, -14); ctx.lineTo(-10, -10); ctx.fill();
      break;
    case 'crown':
      ctx.fillStyle = '#ffcf3a'; ctx.beginPath(); ctx.moveTo(-22, 16); ctx.lineTo(-24, -14); ctx.lineTo(-10, 0); ctx.lineTo(0, -20); ctx.lineTo(10, 0); ctx.lineTo(24, -14); ctx.lineTo(22, 16); ctx.closePath(); fillStroke(ctx);
      ctx.fillStyle = '#e63950'; circlePath(ctx, 0, 6, 4); fillStroke(ctx);
      break;
    case 'key':
      ctx.fillStyle = '#ffd23a'; circlePath(ctx, -10, -10, 11); fillStroke(ctx);
      ctx.fillStyle = bg[0]; circlePath(ctx, -10, -10, 4); ctx.fill();
      ctx.fillStyle = '#ffd23a'; ctx.save(); ctx.translate(-2, -2); ctx.rotate(0.785); rrPath(ctx, 0, -3, 30, 6, 2); fillStroke(ctx); rrPath(ctx, 18, 2, 5, 8, 1); fillStroke(ctx); ctx.restore();
      break;
    case 'prestige':
      ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.moveTo(0, -26); ctx.lineTo(10, 0); ctx.lineTo(4, 0); ctx.lineTo(4, 26); ctx.lineTo(-4, 26); ctx.lineTo(-4, 0); ctx.lineTo(-10, 0); ctx.closePath(); fillStroke(ctx);
      ctx.strokeStyle = '#8ad0ff'; for (let i = 0; i < 3; i++) { ctx.beginPath(); ctx.moveTo(-20 + i * 20, 26); ctx.lineTo(-20 + i * 20, 8 - i * 3); ctx.stroke(); }
      break;
  }
  ctx.restore();
}

function drawGearIcon(ctx, g, size, owned) {
  const s = size;
  ctx.save(); ctx.clearRect(0, 0, s, s);
  const rc = RARITIES[g.r].color;
  const bg = ctx.createLinearGradient(0, 0, 0, s); bg.addColorStop(0, shade(rc, -0.35)); bg.addColorStop(1, shade(rc, -0.65));
  ctx.fillStyle = bg; rrPath(ctx, 1, 1, s - 2, s - 2, s * 0.14); ctx.fill();
  ctx.strokeStyle = rc; ctx.lineWidth = 2.5; ctx.stroke();
  ctx.translate(s / 2, s / 2); ctx.scale(s / 64, s / 64);
  if (!owned) ctx.globalAlpha = 0.25;
  ctx.strokeStyle = OUT; ctx.lineWidth = 2.5; ctx.lineJoin = 'round';
  if (g.slot === 'weapon') { ctx.rotate(0.78); ctx.translate(0, 18); drawWeapon(ctx, 'sword', { blade: g.blade, bladeGlow: g.glow }); }
  else if (g.slot === 'helm') { ctx.translate(0, 12); ctx.fillStyle = '#f2c9a0'; circlePath(ctx, 0, 0, 13); fillStroke(ctx); drawHelmGear(ctx, g.helm, g.color, 0); }
  else if (g.slot === 'armor') { ctx.fillStyle = g.outfit; rrPath(ctx, -16, -18, 32, 36, 9); fillStroke(ctx); ctx.fillStyle = g.trim; ctx.fillRect(-15, 4, 30, 5); rrPath(ctx, -22, -18, 10, 14, 5); fillStroke(ctx); rrPath(ctx, 12, -18, 10, 14, 5); fillStroke(ctx); }
  else { const gg = ctx.createRadialGradient(0, 0, 2, 0, 0, 24); gg.addColorStop(0, '#fff'); gg.addColorStop(0.4, g.aura); gg.addColorStop(1, 'rgba(0,0,0,0)'); ctx.fillStyle = gg; circlePath(ctx, 0, 0, 24); ctx.fill(); ctx.fillStyle = g.aura; for (let i = 0; i < 6; i++) { const a = (i / 6) * TAU; circlePath(ctx, Math.cos(a) * 20, Math.sin(a) * 20, 3); ctx.fill(); } }
  ctx.restore();
}
