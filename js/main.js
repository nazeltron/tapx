'use strict';
// Battle view rendering, effects, input and the main loop.

const canvas = document.getElementById('view');
const ctx = canvas.getContext('2d');
const V = { W: 0, H: 0, dpr: 1, gy: 0, u: 1, bg: null, bgWorld: null, t: 0, shake: 0, flash: 0, flashColor: '#fff' };
const FX = { floaters: [], parts: [], slashes: [], coins: [], shots: [], bolts: [], banners: [] };
const Hero = { atk: 0, look: null };
const Pet = { atk: 0, charge: 0 };
const Allies = {}; // id -> { timer, atk }
let wisp = null, wispTimer = 25;

function heroLook() {
  const L = { skin: '#f6cba4', hair: '#ffd23a', hairStyle: 'spiky', outfit: '#2f3448', trim: '#e8b33a', pants: '#2a2a36', boots: '#4a2f1f', weapon: 'sword', cape: '#d8302f' };
  const w = S.equipped.weapon && gearDef(S.equipped.weapon);
  if (w) { L.blade = w.blade; L.bladeGlow = w.glow; }
  const h = S.equipped.helm && gearDef(S.equipped.helm);
  if (h) { L.helmGear = h.helm; L.helmColor = h.color; }
  const a = S.equipped.armor && gearDef(S.equipped.armor);
  if (a) { L.outfit = a.outfit; L.trim = a.trim; }
  const u = S.equipped.aura && gearDef(S.equipped.aura);
  if (u) L.aura = u.aura;
  return L;
}

// ---------------------------------------------------------------- layout
function resize() {
  const r = canvas.getBoundingClientRect();
  V.dpr = Math.min(window.devicePixelRatio || 1, 2.5);
  V.W = r.width; V.H = r.height;
  canvas.width = Math.round(V.W * V.dpr); canvas.height = Math.round(V.H * V.dpr);
  V.gy = V.H * 0.88;
  V.u = Math.min(V.W / 400, V.H / 430);
  V.bg = null;
}
const heroPos = () => ({ x: V.W * 0.43, y: V.gy });
const monPos = () => ({ x: V.W * 0.68, y: V.gy });
const goldPos = () => ({ x: V.W / 2 - 34 * V.u, y: 104 * V.u });
function allyPos(i) {
  const row = i % 2, col = Math.floor(i / 2);
  return { x: V.W * (0.29 - col * 0.048) - row * 10 * V.u, y: V.gy - (row ? 20 : 4) * V.u, s: V.u * (row ? 0.66 : 0.76) };
}
function monsterTop() {
  const m = R.monster;
  if (!m || !m.spec) return V.gy - 150 * V.u;
  return V.gy - (m.spec.mimic ? 120 : m.spec.h * (m.spec.boss ? 1.22 : 1) + (m.spec.legs ? 16 : 0) + (m.spec.float ? 24 : 0)) * V.u * 0.95;
}
function monsterCenter() { const p = monPos(); return { x: p.x, y: (monsterTop() + V.gy) / 2 }; }

// ---------------------------------------------------------------- effects
function floater(text, x, y, color, size, opts = {}) {
  FX.floaters.push({ text, x, y, vx: opts.vx || rand(-20, 20), vy: opts.vy || -90, color, size, life: opts.life || 1, max: opts.life || 1 });
}
function burst(x, y, color, n, speed = 220, size = 4, grav = 500) {
  for (let i = 0; i < n; i++) {
    const a = rand(0, TAU), v = rand(speed * 0.3, speed);
    FX.parts.push({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - speed * 0.3, life: rand(0.4, 0.9), max: 0.9, color, size: rand(size * 0.5, size), grav });
  }
}
function spawnCoins(n, x, y) {
  for (let i = 0; i < n; i++) {
    FX.coins.push({ x, y, vx: rand(-160, 160), vy: rand(-380, -200), t: 0, fly: false, spin: rand(0, TAU), r: 9 });
  }
}

Hooks.onTap = (d, crit, auto) => {
  Hero.atk = 0.0001;
  const c = monsterCenter(), u = V.u;
  FX.slashes.push({ x: c.x + rand(-30, 30) * u, y: c.y + rand(-40, 40) * u, a: rand(-0.9, 0.9) + (Math.random() < 0.5 ? 0 : Math.PI), life: 0.22, crit });
  floater(fmt(d), c.x + rand(-50, 50) * u, c.y - 30 * u, crit ? '#ff8a1a' : '#fff', crit ? 30 : 20, { vy: crit ? -150 : -110 });
  if (crit) { V.shake = Math.max(V.shake, 6); floater('CRIT!', c.x, c.y - 70 * u, '#ffd23a', 18, { vy: -60, vx: 0, life: 0.6 }); Sfx.crit(); }
  else Sfx.hit();
  burst(c.x, c.y, crit ? '#ffb43a' : '#ffffff', crit ? 10 : 4, crit ? 260 : 160, 4);
};
Hooks.onKill = (m, g, gems) => {
  const c = monsterCenter();
  burst(c.x, c.y, m.spec ? m.spec.color : '#fff', 26, 340, 7);
  spawnCoins(m.boss ? 12 : m.mimic ? 16 : 4, c.x, c.y);
  floater('+' + fmt(g), c.x, c.y - 60 * V.u, '#ffd23a', 22, { vx: 0, vy: -70, life: 1.2 });
  if (gems) floater(`+${gems} gem${gems > 1 ? 's' : ''}`, c.x, c.y - 90 * V.u, '#6ae4ff', 20, { vx: 0, vy: -50, life: 1.4 });
  Sfx.kill();
  if (m.boss) { V.shake = 14; V.flash = 0.6; V.flashColor = '#fff'; }
};
Hooks.onSpawn = (m) => {
  m.spec = genMonster(m.seed, { boss: m.boss, mimic: m.mimic });
  if (m.boss) { Sfx.boss(); banner('BOSS BATTLE!', '#ff5a4a'); }
  if (m.mimic) banner('A treasure mimic appears!', '#ffd23a');
};
Hooks.onStage = (n) => {
  if (n > 1) toast(`Warp Boots! Skipped to stage ${S.stage}`);
  if ((S.stage - 1) % 10 === 0 || n > 1 && Math.floor((S.stage - 1) / 10) !== Math.floor((S.stage - 1 - n) / 10)) banner(worldFor(S.stage).name, '#fff');
  Sfx.levelUp();
};
Hooks.onBossFail = () => { toast('The boss escaped! Tap “Fight Boss” when you are stronger.'); };
Hooks.onSkill = (id, d) => {
  const sk = SKILLS.find((s) => s.id === id);
  Sfx.spell();
  if (id === 'thunder') {
    Sfx.thunder();
    const c = monsterCenter();
    FX.bolts.push({ x: c.x, y: c.y, life: 0.45 });
    V.flash = 0.7; V.flashColor = '#d8f0ff'; V.shake = 16;
    floater(fmt(d), c.x, c.y - 40 * V.u, '#8ad8ff', 36, { vx: 0, vy: -80, life: 1.4 });
  } else banner(sk.name + '!', '#ffe08a');
};
Hooks.onPetAttack = (d) => {
  Pet.atk = 0.0001; Sfx.pet();
  const c = monsterCenter();
  setTimeout(() => { burst(c.x, c.y, petDef(S.activePet) ? petDef(S.activePet).body : '#fff', 18, 280, 6); floater(fmt(d), c.x - 20, c.y - 50 * V.u, '#9bff7a', 28, { vx: 0, vy: -100 }); }, 160);
};
Hooks.onToast = (msg, kind) => toast(msg, kind);
Hooks.onChange = () => { Hero.look = null; };

function banner(text, color) { FX.banners.length = 0; FX.banners.push({ text, color, life: 1.8, max: 1.8 }); }

// ---------------------------------------------------------------- drawing
function drawHud() {
  const W = V.W, u = V.u;
  // stage pips
  const cy = 30 * u;
  const pip = (x, r, stage, cur) => {
    if (stage < 1) return;
    const w = worldFor(stage);
    ctx.save();
    ctx.beginPath(); ctx.arc(x, cy, r, 0, TAU); ctx.clip();
    const g = ctx.createLinearGradient(0, cy - r, 0, cy + r); g.addColorStop(0, w.sky[0]); g.addColorStop(1, w.sky[1]);
    ctx.fillStyle = g; ctx.fillRect(x - r, cy - r, r * 2, r * 2);
    ctx.fillStyle = w.mid; ctx.fillRect(x - r * 0.3, cy - r * 0.4, r * 0.6, r);
    ctx.fillStyle = w.ground; ctx.fillRect(x - r, cy + r * 0.45, r * 2, r);
    ctx.restore();
    ctx.lineWidth = cur ? 3.5 : 2.5; ctx.strokeStyle = cur ? '#fff' : 'rgba(255,255,255,.7)';
    ctx.beginPath(); ctx.arc(x, cy, r, 0, TAU); ctx.stroke();
    text(String(stage), x, cy + r * 0.85, cur ? 17 * u : 12 * u, '#fff', 'center');
  };
  ctx.fillStyle = 'rgba(255,255,255,.7)';
  for (const d of [-1, 1]) for (let i = 1; i <= 3; i++) { ctx.beginPath(); ctx.arc(W / 2 + d * (26 + i * 8) * u, cy, 2.2 * u, 0, TAU); ctx.fill(); }
  pip(W / 2 - 68 * u, 16 * u, S.stage - 1, false);
  pip(W / 2, 22 * u, S.stage, true);
  pip(W / 2 + 68 * u, 16 * u, S.stage + 1, false);

  // wave counter
  const m = R.monster;
  if (!(m && m.boss) && !S.farming) {
    drawSkull(W - 34 * u, 26 * u, 14 * u);
    text(`${Math.min(S.wave + 1, WAVES)}/${WAVES}`, W - 34 * u, 56 * u, 18 * u, '#fff', 'center');
  }

  // HP bar
  const bw = W * 0.6, bh = 22 * u, bx = W / 2 - bw / 2, by = 64 * u;
  const skew = 10 * u;
  const para = (x, w) => { ctx.beginPath(); ctx.moveTo(x, by + bh); ctx.lineTo(x + skew, by); ctx.lineTo(x + w + skew, by); ctx.lineTo(x + w, by + bh); ctx.closePath(); };
  ctx.fillStyle = '#1b2a44'; para(bx, bw); ctx.fill();
  if (m) {
    const f = clamp(m.hp / m.maxHp, 0, 1);
    const g = ctx.createLinearGradient(0, by, 0, by + bh); g.addColorStop(0, '#ff8a3a'); g.addColorStop(1, '#e0401a');
    ctx.fillStyle = g; para(bx, bw * f); ctx.fill();
    ctx.save(); para(bx, bw); ctx.clip();
    ctx.fillStyle = 'rgba(255,255,255,.25)'; ctx.fillRect(bx, by, bw + skew, bh * 0.35);
    ctx.restore();
    const name = m.spec ? m.spec.name : '';
    if (m.boss) drawSkull(bx - 4 * u, by + bh / 2, 12 * u);
    text(name, bx + 14 * u, by + bh * 0.72, 14 * u, '#fff', 'left', true);
    text(`${fmt(Math.ceil(m.hp))} HP`, bx + bw - 6 * u, by + bh * 0.72, 14 * u, '#fff', 'right', true);
    if (m.boss) {
      const tf = clamp(R.bossTimer / bossTime(), 0, 1);
      ctx.fillStyle = 'rgba(0,0,0,.35)'; ctx.fillRect(bx + skew, by + bh + 3 * u, bw - skew, 5 * u);
      ctx.fillStyle = '#fff'; ctx.fillRect(bx + skew, by + bh + 3 * u, (bw - skew) * tf, 5 * u);
      text(R.bossTimer.toFixed(1), bx - 4 * u, by + bh + 22 * u, 18 * u, R.bossTimer < 8 ? '#ff4a3a' : '#ffd0c8', 'center', true);
    }
  }
  ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(0,0,0,.5)'; para(bx, bw); ctx.stroke();

  // gold
  const gp = goldPos();
  drawCoin(ctx, gp.x, gp.y, 15 * u);
  text(fmt(S.gold), gp.x + 22 * u, gp.y + 10 * u, 28 * u, '#fff', 'left', true, '#2a5a8a');

  // active buffs
  let bxp = 10 * u, byp = 132 * u;
  for (const k in S.buffs) {
    const sk = SKILLS.find((s) => s.id === k) || PERKS.find((p) => p.id === k);
    if (!sk) continue;
    ctx.fillStyle = 'rgba(0,0,0,.45)'; rrPath(ctx, bxp, byp, 112 * u, 20 * u, 6 * u); ctx.fill();
    text(`${sk.name} ${fmtTime(S.buffs[k])}`, bxp + 6 * u, byp + 14 * u, 11 * u, '#ffe08a', 'left');
    byp += 24 * u;
  }
}

function drawSkull(x, y, r) {
  ctx.save(); ctx.translate(x, y);
  ctx.fillStyle = '#1d1a26'; ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill();
  ctx.beginPath(); ctx.moveTo(-r * 0.9, -r * 0.3); ctx.lineTo(-r * 1.1, -r * 1.2); ctx.lineTo(-r * 0.3, -r * 0.8); ctx.fill();
  ctx.beginPath(); ctx.moveTo(r * 0.9, -r * 0.3); ctx.lineTo(r * 1.1, -r * 1.2); ctx.lineTo(r * 0.3, -r * 0.8); ctx.fill();
  ctx.fillStyle = '#ff3a2a';
  ctx.beginPath(); ctx.moveTo(-r * 0.65, -r * 0.1); ctx.lineTo(-r * 0.15, r * 0.15); ctx.lineTo(-r * 0.6, r * 0.25); ctx.fill();
  ctx.beginPath(); ctx.moveTo(r * 0.65, -r * 0.1); ctx.lineTo(r * 0.15, r * 0.15); ctx.lineTo(r * 0.6, r * 0.25); ctx.fill();
  ctx.restore();
}

function text(str, x, y, size, color, align = 'left', outline = true, outColor = '#1d1a26') {
  ctx.font = `900 ${Math.round(size)}px ${FONT}`;
  ctx.textAlign = align;
  if (outline) { ctx.lineWidth = Math.max(2, size * 0.18); ctx.strokeStyle = outColor; ctx.lineJoin = 'round'; ctx.strokeText(str, x, y); }
  ctx.fillStyle = color; ctx.fillText(str, x, y);
}

function render(dt) {
  const t = V.t, u = V.u, W = V.W, H = V.H;
  ctx.setTransform(V.dpr, 0, 0, V.dpr, 0, 0);
  const world = worldFor(S.stage);
  if (!V.bg || V.bgWorld !== world) { V.bg = makeBackground(world, W, H, V.gy, V.dpr); V.bgWorld = world; }
  ctx.save();
  if (V.shake > 0) ctx.translate(rand(-V.shake, V.shake), rand(-V.shake, V.shake));
  ctx.drawImage(V.bg, 0, 0, W, H);
  drawAmbience(ctx, world, W, V.gy, t);

  // allies
  const look = Hero.look || (Hero.look = heroLook());
  HEROES.forEach((h, i) => {
    if (!heroLvl(h.id)) return;
    const a = Allies[h.id] || (Allies[h.id] = { timer: rand(0, 1.5), atk: 0 });
    const p = allyPos(i);
    drawChibi(ctx, Object.assign({ phase: i }, h.look), p.x, p.y, p.s, t, a.atk);
  });
  // monster
  const m = R.monster;
  const mp = monPos();
  if (m && m.spec) drawMonster(ctx, m.spec, mp.x, mp.y, t, m);
  // hero
  const hp = heroPos();
  drawChibi(ctx, look, hp.x, hp.y, u * 1.15, t, Hero.atk);
  // pet
  if (S.activePet) {
    const p = petDef(S.activePet);
    const home = { x: hp.x - 34 * u, y: V.gy - 105 * u + Math.sin(t * 3) * 6 * u };
    let px = home.x, py = home.y;
    if (Pet.atk > 0) { const k = Math.sin(Pet.atk * Math.PI); const c = monsterCenter(); px = lerp(home.x, c.x - 30 * u, k); py = lerp(home.y, c.y, k); }
    drawPet(ctx, p, px, py, u * 0.8, t, Pet.atk > 0 ? 1 : 0);
    // charge ring
    const f = S.petCharge / PET_CHARGE;
    ctx.lineWidth = 3 * u; ctx.strokeStyle = 'rgba(0,0,0,.3)'; ctx.beginPath(); ctx.arc(home.x, home.y + 30 * u, 7 * u, 0, TAU); ctx.stroke();
    ctx.strokeStyle = '#9bff7a'; ctx.beginPath(); ctx.arc(home.x, home.y + 30 * u, 7 * u, -Math.PI / 2, -Math.PI / 2 + TAU * f); ctx.stroke();
  }

  // projectiles
  for (const s of FX.shots) {
    const k = s.t / s.dur;
    const x = lerp(s.x0, s.x1, k), y = lerp(s.y0, s.y1, k) - Math.sin(k * Math.PI) * s.arc;
    ctx.save(); ctx.translate(x, y);
    if (s.kind === 'bow') { ctx.rotate(Math.atan2(s.y1 - s.y0 - Math.cos(k * Math.PI) * s.arc * Math.PI, s.x1 - s.x0)); ctx.strokeStyle = '#6a4a2a'; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(-10, 0); ctx.lineTo(8, 0); ctx.stroke(); ctx.fillStyle = '#ddd'; ctx.beginPath(); ctx.moveTo(8, -3); ctx.lineTo(13, 0); ctx.lineTo(8, 3); ctx.fill(); }
    else if (s.kind === 'gun') { ctx.fillStyle = '#ffe08a'; ctx.fillRect(-6, -1.5, 12, 3); }
    else if (s.kind === 'leaf') { ctx.rotate(t * 10); ctx.fillStyle = '#7bd65a'; ctx.beginPath(); ctx.ellipse(0, 0, 7, 3, 0, 0, TAU); ctx.fill(); }
    else { ctx.save(); ctx.shadowColor = s.color; ctx.shadowBlur = 12; ctx.fillStyle = s.color; ctx.beginPath(); ctx.arc(0, 0, 6, 0, TAU); ctx.fill(); ctx.restore(); ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(0, 0, 2.5, 0, TAU); ctx.fill(); }
    ctx.restore();
  }
  // slashes
  for (const s of FX.slashes) {
    const k = 1 - s.life / 0.22;
    ctx.save(); ctx.translate(s.x, s.y); ctx.rotate(s.a);
    ctx.globalAlpha = 1 - k;
    ctx.strokeStyle = s.crit ? '#ffb43a' : '#ffffff'; ctx.lineCap = 'round';
    ctx.lineWidth = (s.crit ? 9 : 6) * u * (1 - k * 0.5);
    ctx.beginPath(); ctx.arc(0, 0, 50 * u, -0.9 + k * 0.6, 0.9 * k + 0.1); ctx.stroke();
    ctx.restore();
  }
  // lightning
  for (const b of FX.bolts) {
    ctx.save(); ctx.globalAlpha = b.life / 0.45;
    ctx.strokeStyle = '#e8f6ff'; ctx.shadowColor = '#5ab8ff'; ctx.shadowBlur = 20; ctx.lineWidth = 6 * u;
    ctx.beginPath(); let x = b.x + rand(-30, 30), y = 0; ctx.moveTo(x, y);
    while (y < b.y) { y += rand(20, 40) * u; x += rand(-25, 25) * u; ctx.lineTo(x, y); }
    ctx.stroke(); ctx.lineWidth = 2 * u; ctx.strokeStyle = '#fff'; ctx.stroke();
    ctx.restore();
  }
  // particles
  for (const p of FX.parts) {
    ctx.globalAlpha = clamp(p.life / p.max, 0, 1);
    ctx.fillStyle = p.color; ctx.fillRect(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
  }
  ctx.globalAlpha = 1;
  // coins
  for (const c of FX.coins) drawCoin(ctx, c.x, c.y, c.r * u, Math.cos(c.spin));
  // wisp
  if (wisp) drawWisp(ctx, wisp.x, wisp.y, t);
  // floaters
  for (const f of FX.floaters) {
    ctx.globalAlpha = clamp(f.life / f.max * 2, 0, 1);
    text(f.text, f.x, f.y, f.size * u, f.color, 'center');
  }
  ctx.globalAlpha = 1;
  ctx.restore();

  drawHud();

  // banners
  for (const b of FX.banners) {
    const k = 1 - b.life / b.max;
    const a = k < 0.15 ? k / 0.15 : k > 0.8 ? (1 - k) / 0.2 : 1;
    ctx.globalAlpha = a;
    ctx.fillStyle = 'rgba(20,24,40,.65)';
    const y = H * 0.36;
    ctx.beginPath(); ctx.moveTo(W * 0.1, y - 24 * u); ctx.lineTo(W * 0.92, y - 24 * u); ctx.lineTo(W * 0.9, y + 18 * u); ctx.lineTo(W * 0.08, y + 18 * u); ctx.closePath(); ctx.fill();
    text(b.text, W / 2, y + 6 * u, 22 * u, b.color, 'center');
    ctx.globalAlpha = 1;
  }
  if (V.flash > 0) { ctx.globalAlpha = V.flash; ctx.fillStyle = V.flashColor; ctx.fillRect(0, 0, W, H); ctx.globalAlpha = 1; }
}

// ---------------------------------------------------------------- simulation of visuals
function updateFx(dt) {
  V.t += dt;
  V.shake = Math.max(0, V.shake - dt * 40);
  V.flash = Math.max(0, V.flash - dt * 2);
  if (Hero.atk > 0) { Hero.atk += dt * 4.5; if (Hero.atk >= 1) Hero.atk = 0; }
  if (Pet.atk > 0) { Pet.atk += dt * 2.5; if (Pet.atk >= 1) Pet.atk = 0; }
  const m = R.monster;
  if (m) { m.hitT = Math.max(0, m.hitT - dt * 6); if (m.dead) m.deathT = Math.min(1, m.deathT + dt * 2.6); }

  const life = (arr) => { for (let i = arr.length - 1; i >= 0; i--) if ((arr[i].life -= dt) <= 0) arr.splice(i, 1); };
  for (const f of FX.floaters) { f.x += f.vx * dt; f.y += f.vy * dt; f.vy += 60 * dt; }
  life(FX.floaters);
  for (const p of FX.parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.grav * dt; p.vx *= 0.98; }
  life(FX.parts); life(FX.slashes); life(FX.bolts); life(FX.banners);

  const gp = goldPos();
  for (let i = FX.coins.length - 1; i >= 0; i--) {
    const c = FX.coins[i];
    c.t += dt; c.spin += dt * 8;
    if (!c.fly) {
      c.vy += 900 * dt; c.x += c.vx * dt; c.y += c.vy * dt;
      if (c.y > V.gy - 6) { c.y = V.gy - 6; c.vy *= -0.45; c.vx *= 0.7; }
      if (c.t > 0.9) c.fly = true;
    } else {
      const dx = gp.x - c.x, dy = gp.y - c.y, d = Math.hypot(dx, dy);
      const sp = 900 + c.t * 600;
      if (d < 20) { FX.coins.splice(i, 1); Sfx.coin(); continue; }
      c.x += (dx / d) * sp * dt; c.y += (dy / d) * sp * dt;
    }
  }
  for (let i = FX.shots.length - 1; i >= 0; i--) {
    const s = FX.shots[i];
    s.t += dt;
    if (s.t >= s.dur) { FX.shots.splice(i, 1); burst(s.x1, s.y1, s.color, 4, 120, 3); }
  }

  // allies swing / shoot (visual only — damage is continuous DPS)
  if (m && !m.dead) {
    HEROES.forEach((h, i) => {
      if (!heroLvl(h.id)) return;
      const a = Allies[h.id] || (Allies[h.id] = { timer: rand(0, 1.5), atk: 0 });
      if (a.atk > 0) { a.atk += dt * 3.5; if (a.atk >= 1) a.atk = 0; }
      a.timer -= dt;
      if (a.timer <= 0) {
        a.timer = rand(1.1, 1.9); a.atk = 0.0001;
        const p = allyPos(i), c = monsterCenter();
        const w = h.look.weapon;
        const color = h.look.glow || ({ bow: '#ddd', gun: '#ffe08a', leaf: '#7bd65a' }[w]) || '#ffffff';
        FX.shots.push({ kind: w, x0: p.x + 10 * V.u, y0: p.y - 30 * p.s, x1: c.x + rand(-25, 25) * V.u, y1: c.y + rand(-30, 30) * V.u, t: 0, dur: w === 'gun' ? 0.18 : 0.45, arc: w === 'bow' ? 40 * V.u : w === 'gun' ? 0 : 20 * V.u, color });
      }
    });
  }

  // wisp
  if (!wisp) {
    wispTimer -= dt;
    if (wispTimer <= 0) wisp = { x: -30, y: 0, base: V.H * rand(0.3, 0.5), t: 0 };
  } else {
    wisp.t += dt; wisp.x += 70 * V.u * dt; wisp.y = wisp.base + Math.sin(wisp.t * 2.5) * 30 * V.u;
    if (wisp.x > V.W + 40) { wisp = null; wispTimer = rand(45, 90); }
  }
}

// ---------------------------------------------------------------- skill bar
const skillButtons = [];
function buildSkillBar() {
  const bar = document.getElementById('skills');
  bar.innerHTML = '';
  for (const sk of SKILLS) {
    const b = document.createElement('button');
    b.className = 'skill';
    b.appendChild(iconCanvas(40, (x, s) => drawIcon(x, sk.id, s), 'sico'));
    const cover = document.createElement('i'); cover.className = 'cover';
    const lab = document.createElement('span'); lab.className = 'slab';
    const lock = document.createElement('span'); lock.className = 'slock'; lock.textContent = '🔒';
    b.append(cover, lab, lock);
    b.onclick = () => { Sfx.init(); if (!castSkill(sk.id)) Sfx.error(); updateSkillBar(); };
    bar.appendChild(b);
    skillButtons.push({ sk, b, cover, lab, lock });
  }
}
function updateSkillBar() {
  document.getElementById('manaFill').style.width = (S.mana / maxMana()) * 100 + '%';
  document.getElementById('manaText').textContent = `${Math.floor(S.mana)} / ${maxMana()}`;
  for (const s of skillButtons) {
    const l = skillLvl(s.sk.id), active = buffOn(s.sk.id);
    s.lock.style.display = l ? 'none' : '';
    s.b.classList.toggle('locked', !l);
    s.b.classList.toggle('active', active);
    s.b.disabled = !l || active || S.mana < s.sk.mana;
    s.cover.style.height = active ? (S.buffs[s.sk.id] / spellDuration()) * 100 + '%' : '0';
    s.lab.textContent = active ? Math.ceil(S.buffs[s.sk.id]) + 's' : l ? s.sk.mana : '';
  }
  const m = R.monster;
  const bossBtn = document.getElementById('bossBtn');
  if (S.farming) { bossBtn.hidden = false; bossBtn.textContent = 'FIGHT BOSS'; bossBtn.className = 'fight'; }
  else if (m && m.boss && !m.dead) { bossBtn.hidden = false; bossBtn.textContent = 'LEAVE BATTLE'; bossBtn.className = 'leave'; }
  else bossBtn.hidden = true;
}

// ---------------------------------------------------------------- input
function onPointer(e) {
  e.preventDefault();
  Sfx.init();
  const r = canvas.getBoundingClientRect();
  const x = e.clientX - r.left, y = e.clientY - r.top;
  if (wisp && Math.hypot(x - wisp.x, y - wisp.y) < 40 * V.u) {
    const msg = wispReward();
    burst(wisp.x, wisp.y, '#bff', 24, 260, 5, 100);
    floater(msg, wisp.x, wisp.y - 20, '#bff8ff', 20, { vx: 0, vy: -50, life: 1.6 });
    Sfx.chest();
    wisp = null; wispTimer = rand(45, 90);
    return;
  }
  tap(false);
}

// ---------------------------------------------------------------- loop & boot
let last = performance.now(), uiTimer = 0, saveTimer = 0, achTimer = 0;
function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  update(dt);
  updateFx(dt);
  render(dt);
  uiTimer -= dt; saveTimer -= dt; achTimer -= dt;
  if (uiTimer <= 0) { uiTimer = 0.2; refreshUI(); updateSkillBar(); }
  if (achTimer <= 0) { achTimer = 1; checkAchievements(); }
  if (saveTimer <= 0) { saveTimer = 10; saveGame(); }
  requestAnimationFrame(frame);
}

function welcomeBack(secs) {
  const g = applyOffline(secs);
  if (g > 0) openModal('Welcome back!', `<p>You were away for <b>${fmtTime(secs)}</b>.</p><p>Your allies kept fighting and earned</p><p class="big">${coinHtml()} ${fmt(g)}</p>`, [{ label: 'Collect' }]);
}

function boot() {
  const had = loadGame();
  Sfx.on = S.settings.sound;
  initCurrencyIcons();
  resize();
  buildTabs();
  buildSkillBar();
  showTab('hero');
  canvas.addEventListener('pointerdown', onPointer);
  window.addEventListener('resize', () => { resize(); });
  document.getElementById('bossBtn').onclick = () => { Sfx.init(); if (S.farming) fightBoss(); else leaveBoss(); updateSkillBar(); };
  document.getElementById('gearBtn').appendChild(iconCanvas(34, (x, s) => {
    x.translate(s / 2, s / 2); x.fillStyle = '#5a5e6e'; x.strokeStyle = '#1d1a26'; x.lineWidth = 2;
    x.beginPath(); for (let i = 0; i < 16; i++) { const a = (i / 16) * TAU, r = i % 2 ? 11 : 15; x.lineTo(Math.cos(a) * r, Math.sin(a) * r); } x.closePath(); x.fill(); x.stroke();
    x.fillStyle = '#c8ccd6'; x.beginPath(); x.arc(0, 0, 5, 0, TAU); x.fill(); x.stroke();
  }, 'gico'));
  document.getElementById('gearBtn').onclick = () => { Sfx.init(); openSettings(); };
  document.getElementById('modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) saveGame();
    else { const secs = (Date.now() - S.lastSave) / 1000; S.lastSave = Date.now(); last = performance.now(); if (secs > 30) welcomeBack(secs); }
  });
  window.addEventListener('pagehide', saveGame);
  if (had) { const secs = (Date.now() - S.lastSave) / 1000; S.lastSave = Date.now(); if (secs > 30) welcomeBack(secs); }
  else openModal('Welcome to TapX!', `<p>Tap the monster to attack it.</p><p>Spend gold to level up your <b>Sword Master</b> and hire <b>Allies</b> who fight for you — even while you're away.</p><p>Defeat ${WAVES} monsters, then beat the <b>boss</b> before time runs out to reach the next stage.</p>`, [{ label: "Let's go!", cls: 'gold' }]);
  requestAnimationFrame(frame);
}
boot();
