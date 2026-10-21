'use strict';
// Core game state and rules. No DOM or canvas access here, so the logic can
// also run headless (see tools/sim.js).

const SAVE_KEY = 'tapx-save-v1';

// Presentation layers override these to react to game events.
const Hooks = {
  onTap() {}, onKill() {}, onSpawn() {}, onStage() {}, onSkill() {}, onToast() {},
  onBossFail() {}, onPetAttack() {}, onChange() {},
};

function freshState() {
  return {
    v: 1, gold: 0, gems: 100, relics: 0,
    stage: 1, wave: 0, maxStage: 1, farming: false,
    tapLevel: 1, heroes: {}, skills: {}, artifacts: {},
    mana: 30, buffs: {},
    gear: {}, equipped: {},
    pets: {}, activePet: null, petCharge: 0,
    achievements: {},
    freeChestAt: 0, deals: null,
    stats: { taps: 0, kills: 0, bosses: 0, crits: 0, goldEarned: 0, prestiges: 0, casts: 0, playTime: 0, bestStage: 1, relicsEarned: 0, wisps: 0 },
    settings: { sound: true, mult: 1 },
    lastSave: Date.now(),
  };
}

let S = freshState();
// Runtime-only state (not saved).
const R = { monster: null, bossTimer: 0, autoTapAcc: 0, nextSpawn: 0 };

// ---------- lookups ----------
const art = (id) => S.artifacts[id] || 0;
const heroLvl = (id) => S.heroes[id] || 0;
const skillLvl = (id) => S.skills[id] || 0;
const buffOn = (id) => (S.buffs[id] || 0) > 0;
const gearDef = (id) => GEAR.find((g) => g.id === id);
const petDef = (id) => PETS.find((p) => p.id === id);

function gearValue(id) {
  const g = gearDef(id), lvl = S.gear[id] || 0;
  if (!g || !lvl) return 0;
  const slot = GEAR_SLOTS.find((s) => s.id === g.slot);
  return slot.base * RARITIES[g.r].mult * (1 + 0.5 * (lvl - 1));
}
function gearBonus(stat) {
  let b = 0;
  for (const slot of GEAR_SLOTS) {
    const id = S.equipped[slot.id];
    if (id && slot.stat === stat) b += gearValue(id);
  }
  return b;
}
function petLevel(id) { return (S.pets[id] && S.pets[id].lvl) || 0; }
function petBonus(stat) {
  const p = S.activePet && petDef(S.activePet);
  if (!p || p.stat !== stat) return 0;
  return p.per * petLevel(p.id);
}

// ---------- formulas ----------
function monsterHp(stage) { return 10 * Math.pow(1.39, stage - 1) * (1 + Math.floor((stage - 1) / 5) * 0.08); }
function bossHp(stage) { return monsterHp(stage) * 6 * (1 - 0.04 * art('crown')); }
function goldMult() {
  return (1 + 0.25 * art('idol')) * (1 + gearBonus('gold')) * (1 + petBonus('gold')) * (buffOn('midas') ? skillPower('midas', skillLvl('midas')) : 1);
}
function killGold(stage) { return Math.ceil(monsterHp(stage) * 0.2) * goldMult(); }

function allDamageMult() { return (1 + 0.25 * art('blade')) * (buffOn('surge') ? 2 : 1); }

function heroMilestoneMult(lvl) {
  let m = 1;
  for (const [l, x] of HERO_MILESTONES) if (lvl >= l) m *= x;
  return m;
}
function heroBaseDps(h, lvl) { return lvl > 0 ? h.dps * lvl * heroMilestoneMult(lvl) : 0; }
function heroCost(h, lvl) { return h.cost * Math.pow(1.075, lvl); }
function allyMult() {
  return (1 + 0.35 * art('banner')) * (1 + gearBonus('ally')) * (1 + petBonus('ally')) * allDamageMult();
}
function rawHeroDps() { let d = 0; for (const h of HEROES) d += heroBaseDps(h, heroLvl(h.id)); return d; }
function heroDps(buffed = true) {
  return rawHeroDps() * allyMult() * (buffed && buffOn('warcry') ? skillPower('warcry', skillLvl('warcry')) : 1);
}

function tapBase(L) { return L * Math.pow(1.05, L - 1); }
function tapCost(L) { return 5 * Math.pow(1.074, L - 1); }
function tapDamage(buffed = true) {
  const base = tapBase(S.tapLevel) + 0.03 * rawHeroDps() * allyMult();
  return base * (1 + 0.4 * art('gauntlet')) * (1 + gearBonus('tap')) * (1 + petBonus('tap')) * allDamageMult()
    * (buffed && buffOn('berserk') ? skillPower('berserk', skillLvl('berserk')) : 1);
}
function critChance() { return Math.min(0.9, 0.02 + 0.01 * art('clover') + (buffOn('eagle') ? skillPower('eagle', skillLvl('eagle')) / 100 : 0)); }
function critMult() { return 8 * (1 + 0.5 * art('fang')) * (1 + gearBonus('crit')) * (1 + petBonus('crit')); }
function bossTime() { return BOSS_TIME + 2 * art('hourglass'); }
function maxMana() { return 50 + 5 * art('crystal'); }
function manaRegen() { return (1 / 3) * (1 + 0.1 * art('crystal')) * (1 + petBonus('mana')); }
function spellDuration() { return 30 + 4 * art('tome'); }
function autoTapRate() {
  let r = 0;
  if (buffOn('frenzy')) r += skillPower('frenzy', skillLvl('frenzy'));
  if (buffOn('phantom')) r += 6;
  return r;
}
function relicsFor(stage) { return stage < PRESTIGE_STAGE ? 0 : Math.floor(Math.pow(stage - 15, 1.6) / 6 * (1 + 0.1 * S.stats.prestiges)); }
function artifactCost(a, lvl) { return Math.ceil(a.base * Math.pow(1.45, lvl)); }
function skillCost(sk, lvl) { return sk.cost * Math.pow(12, lvl); }
function petDamage() { const p = S.activePet; return p ? tapDamage() * (5 + 3 * petLevel(p)) : 0; }

// Bulk buying helpers for geometric costs.
function bulkCost(c0, r, n) { return c0 * (Math.pow(r, n) - 1) / (r - 1); }
function maxAffordable(c0, r, gold) {
  if (gold < c0) return 0;
  return Math.max(1, Math.floor(Math.log(1 + gold * (r - 1) / c0) / Math.log(r)));
}
function buyAmount(c0, r) {
  const m = S.settings.mult;
  if (m === 'max') return Math.max(1, maxAffordable(c0, r, S.gold));
  return m;
}

// ---------- combat ----------
function spawnMonster() {
  const boss = !S.farming && S.wave >= WAVES;
  const mimic = !boss && Math.random() < 0.025 + 0.01 * art('key');
  const hp = boss ? bossHp(S.stage) : monsterHp(S.stage);
  const seed = boss ? S.stage * 7919 + 13 : Math.floor(Math.random() * 1e9);
  R.monster = { hp, maxHp: hp, boss, mimic, seed, dead: false, deathT: 0, hitT: 0, age: 0, spec: null };
  if (boss) R.bossTimer = bossTime();
  Hooks.onSpawn(R.monster);
}

function dealDamage(amount, kind) {
  const m = R.monster;
  if (!m || m.dead) return;
  if (m.boss) amount *= 1 + petBonus('boss');
  m.hp -= amount;
  if (kind !== 'dps') m.hitT = 1;
  if (m.hp <= 0) { m.hp = 0; killMonster(); }
}

function addGold(g) { S.gold += g; S.stats.goldEarned += g; }

function killMonster() {
  const m = R.monster;
  m.dead = true; m.deathT = 0;
  let g = killGold(S.stage);
  if (m.boss) g *= 5;
  if (m.mimic) g *= 12;
  addGold(g);
  S.stats.kills++;
  let gems = 0;
  if (m.boss) {
    S.stats.bosses++;
    if (Math.random() < 0.35) gems = Math.random() < 0.3 ? 2 : 1;
  }
  S.gems += gems;
  Hooks.onKill(m, g, gems);
  if (m.boss) advanceStage();
  else if (!S.farming) S.wave = Math.min(WAVES, S.wave + 1);
  R.nextSpawn = 0.45;
}

function advanceStage() {
  const n = Math.random() < 0.02 * art('boots') ? 2 : 1;
  S.stage += n; S.wave = 0; S.farming = false;
  S.maxStage = Math.max(S.maxStage, S.stage);
  S.stats.bestStage = Math.max(S.stats.bestStage, S.stage);
  Hooks.onStage(n);
}

function tap(auto) {
  S.stats.taps++;
  const m = R.monster;
  if (!m || m.dead) return null;
  let d = tapDamage();
  const crit = Math.random() < critChance();
  if (crit) { d *= critMult(); S.stats.crits++; }
  Hooks.onTap(d, crit, auto);
  dealDamage(d, crit ? 'crit' : 'tap');
  if (S.activePet && !auto) {
    S.petCharge++;
    if (S.petCharge >= PET_CHARGE) {
      S.petCharge = 0;
      const pd = petDamage();
      Hooks.onPetAttack(pd);
      dealDamage(pd, 'pet');
    }
  }
  return { d, crit };
}

function fightBoss() {
  if (!S.farming) return;
  S.farming = false; S.wave = WAVES; R.monster = null; spawnMonster();
}
function leaveBoss() {
  const m = R.monster;
  if (!m || !m.boss || m.dead) return;
  S.farming = true; R.monster = null; spawnMonster();
}

function update(dt) {
  S.stats.playTime += dt;
  for (const k in S.buffs) { S.buffs[k] -= dt; if (S.buffs[k] <= 0) delete S.buffs[k]; }
  S.mana = Math.min(maxMana(), S.mana + manaRegen() * dt);
  const m = R.monster;
  if (!m || m.dead) {
    R.nextSpawn -= dt;
    if (!m || R.nextSpawn <= 0) spawnMonster();
    return;
  }
  m.age += dt;
  const dps = heroDps();
  if (dps > 0) dealDamage(dps * dt, 'dps');
  const rate = autoTapRate();
  if (rate > 0) {
    R.autoTapAcc += rate * dt;
    while (R.autoTapAcc >= 1) { R.autoTapAcc--; tap(true); }
  }
  if (m.boss && !m.dead) {
    R.bossTimer -= dt;
    if (R.bossTimer <= 0) {
      S.farming = true; S.wave = WAVES; R.monster = null;
      Hooks.onBossFail();
      spawnMonster();
    }
  }
}

// ---------- purchases ----------
function buyTap() {
  const c0 = tapCost(S.tapLevel), n = buyAmount(c0, 1.074), cost = bulkCost(c0, 1.074, n);
  if (S.gold < cost) return false;
  S.gold -= cost; S.tapLevel += n; Hooks.onChange(); return true;
}
function heroUnlocked(i) { return i === 0 || heroLvl(HEROES[i - 1].id) > 0; }
function buyHero(id) {
  const i = HEROES.findIndex((h) => h.id === id), h = HEROES[i];
  if (!heroUnlocked(i)) return false;
  const lvl = heroLvl(id), c0 = heroCost(h, lvl), n = buyAmount(c0, 1.075), cost = bulkCost(c0, 1.075, n);
  if (S.gold < cost) return false;
  S.gold -= cost; S.heroes[id] = lvl + n; Hooks.onChange(); return true;
}
function buySkill(id) {
  const sk = SKILLS.find((s) => s.id === id), lvl = skillLvl(id);
  if (lvl >= SKILL_MAX || S.tapLevel < sk.req) return false;
  const cost = skillCost(sk, lvl);
  if (S.gold < cost) return false;
  S.gold -= cost; S.skills[id] = lvl + 1; Hooks.onChange(); return true;
}
function castSkill(id) {
  const sk = SKILLS.find((s) => s.id === id), lvl = skillLvl(id);
  if (!lvl || S.mana < sk.mana || buffOn(id)) return false;
  S.mana -= sk.mana; S.stats.casts++;
  if (id === 'thunder') {
    const d = tapDamage() * skillPower('thunder', lvl);
    Hooks.onSkill(id, d);
    dealDamage(d, 'skill');
  } else {
    S.buffs[id] = spellDuration();
    Hooks.onSkill(id);
  }
  return true;
}
function buyArtifact(id) {
  const a = ARTIFACTS.find((x) => x.id === id), lvl = art(id);
  if (a.max && lvl >= a.max) return false;
  const cost = artifactCost(a, lvl);
  if (S.relics < cost) return false;
  S.relics -= cost; S.artifacts[id] = lvl + 1; Hooks.onChange(); return true;
}
function buyPerk(id) {
  const p = PERKS.find((x) => x.id === id);
  if (S.gems < p.gems) return false;
  if (p.dur && buffOn(id)) return false;
  S.gems -= p.gems;
  if (id === 'shower') addGold(killGold(S.stage) * 60);
  else if (id === 'flask') S.mana = maxMana();
  else S.buffs[id] = p.dur;
  Hooks.onChange(); return true;
}

// ---------- equipment & chests ----------
function rollRarity(odds) {
  let r = Math.random();
  for (let i = 0; i < odds.length; i++) { if (r < odds[i]) return i; r -= odds[i]; }
  return 0;
}
function rollGear(odds) {
  let r = rollRarity(odds);
  let pool = GEAR.filter((g) => g.r === r);
  while (!pool.length && r > 0) pool = GEAR.filter((g) => g.r === --r);
  return pool[Math.floor(Math.random() * pool.length)];
}
function grantGear(g) {
  const isNew = !S.gear[g.id];
  S.gear[g.id] = (S.gear[g.id] || 0) + 1;
  // auto-equip new pieces into empty slots
  if (!S.equipped[g.slot]) S.equipped[g.slot] = g.id;
  return isNew;
}
function openChest(id) {
  const c = CHESTS.find((x) => x.id === id);
  if (S.gems < c.gems) return null;
  S.gems -= c.gems;
  const items = [];
  for (let i = 0; i < c.items; i++) {
    const odds = id === 'titan' && i === 0 ? [0, 0, 0.75, 0.25] : c.odds;
    const g = rollGear(odds);
    items.push({ g, isNew: grantGear(g) });
  }
  Hooks.onChange();
  return items;
}
function freeChestReady() { return Date.now() >= S.freeChestAt; }
function openFreeChest() {
  if (!freeChestReady()) return null;
  S.freeChestAt = Date.now() + FREE_CHEST_SECS * 1000;
  const g = rollGear(CHESTS[0].odds);
  const items = [{ g, isNew: grantGear(g) }];
  S.gems += 5;
  Hooks.onChange();
  return items;
}
function equip(id) { const g = gearDef(id); if (S.gear[id]) { S.equipped[g.slot] = id; Hooks.onChange(); } }

// ---------- pets ----------
function grantEggs(id, n) {
  const p = S.pets[id] || (S.pets[id] = { lvl: 0, eggs: 0 });
  p.eggs += n;
  if (p.lvl === 0 && p.eggs > 0) { p.lvl = 1; p.eggs--; if (!S.activePet) S.activePet = id; }
  while (p.eggs >= petEggsForLevel(p.lvl)) { p.eggs -= petEggsForLevel(p.lvl); p.lvl++; }
}
function setActivePet(id) { if (petLevel(id)) { S.activePet = id; S.petCharge = 0; Hooks.onChange(); } }

function currentDeals() {
  const block = Math.floor(Date.now() / (DEAL_SECS * 1000));
  if (!S.deals || S.deals.block !== block) {
    const r = mulberry32(block * 977 + 3);
    const deals = [];
    for (let i = 0; i < 3; i++) {
      const pet = PETS[Math.floor(r() * PETS.length)];
      const big = r() < 0.25;
      deals.push({ pet: pet.id, eggs: big ? 10 : 3, gems: big ? 250 : 90, bought: false });
    }
    S.deals = { block, deals };
  }
  return S.deals;
}
function buyDeal(i) {
  const d = currentDeals().deals[i];
  if (!d || d.bought || S.gems < d.gems) return false;
  S.gems -= d.gems; d.bought = true; grantEggs(d.pet, d.eggs); Hooks.onChange(); return true;
}

// ---------- prestige ----------
function canPrestige() { return S.maxStage >= PRESTIGE_STAGE; }
function prestige() {
  if (!canPrestige()) return 0;
  const gained = relicsFor(S.maxStage);
  const keep = {
    gems: S.gems, relics: S.relics + gained, artifacts: S.artifacts, achievements: S.achievements,
    stats: S.stats, settings: S.settings, gear: S.gear, equipped: S.equipped, pets: S.pets,
    activePet: S.activePet, freeChestAt: S.freeChestAt, deals: S.deals,
  };
  S = Object.assign(freshState(), keep);
  S.stats.prestiges++; S.stats.relicsEarned += gained;
  S.mana = maxMana();
  R.monster = null;
  Hooks.onChange();
  return gained;
}

// ---------- achievements ----------
function checkAchievements() {
  const done = [];
  for (const a of ACHIEVEMENTS) {
    if (S.achievements[a.id]) continue;
    if (a.group.stat(S) >= a.target) { S.achievements[a.id] = 1; S.gems += a.gems; done.push(a); }
  }
  for (const a of done) Hooks.onToast(`🏆 ${a.group.name}: ${a.group.fmt(a.target)}  +${a.gems} gems`, 'ach');
  return done;
}

// ---------- wisps (flying bonus you can tap) ----------
function wispReward() {
  S.stats.wisps++;
  const r = Math.random();
  if (r < 0.15) { const n = 3 + Math.floor(Math.random() * 5); S.gems += n; return `+${n} gems`; }
  if (r < 0.35) { S.mana = maxMana(); return 'Mana refilled'; }
  const g = killGold(S.stage) * 25; addGold(g); return `+${fmt(g)} gold`;
}

// ---------- offline progress ----------
function applyOffline(secs) {
  secs = Math.min(secs, 12 * 3600);
  for (const k in S.buffs) { S.buffs[k] -= secs; if (S.buffs[k] <= 0) delete S.buffs[k]; }
  S.mana = Math.min(maxMana(), S.mana + manaRegen() * secs);
  const dps = heroDps(false);
  if (dps <= 0 || secs < 30) return 0;
  const kills = Math.min(secs * dps / monsterHp(S.stage), secs * 2);
  const g = kills * killGold(S.stage) * 0.6;
  addGold(g);
  return g;
}

// ---------- persistence ----------
function serialize() { S.lastSave = Date.now(); return JSON.stringify(S); }
function deserialize(json) {
  const data = JSON.parse(json);
  const base = freshState();
  S = Object.assign(base, data);
  S.stats = Object.assign(freshState().stats, data.stats || {});
  S.settings = Object.assign(freshState().settings, data.settings || {});
  R.monster = null;
}
function saveGame() { try { localStorage.setItem(SAVE_KEY, serialize()); } catch (e) { /* storage unavailable */ } }
function loadGame() {
  try {
    const json = localStorage.getItem(SAVE_KEY);
    if (!json) return false;
    deserialize(json);
    return true;
  } catch (e) { return false; }
}
function resetGame() {
  try { localStorage.removeItem(SAVE_KEY); } catch (e) { /* ignore */ }
  S = freshState(); R.monster = null; Hooks.onChange();
}

if (typeof module !== 'undefined') module.exports = { get S() { return S; } };
