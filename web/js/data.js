'use strict';
// Static game content. Every visual is described by numbers/colors here and
// drawn in code by art.js — there are no image files.

// Bump this for every update; tools/build.js puts it in the bundled file's name.
const GAME_VERSION = '1.0.0';

const WAVES = 8;            // normal monsters per stage before the boss
const BOSS_TIME = 30;       // seconds to kill a boss
const PRESTIGE_STAGE = 25;  // stage needed to prestige

const WORLDS = [
  { name: 'Meadow Ruins', kind: 'castle', sky: ['#7cc6f2', '#e4f5ff'], far: '#b4cadb', mid: '#e7d8b8', midDark: '#c9b38c', ground: '#c9b48c', groundTop: '#e8dcc0', dirt: '#6d5640' },
  { name: 'Sunscorch Dunes', kind: 'dunes', sky: ['#f59a52', '#ffe3a6'], far: '#eab27a', mid: '#dc9c58', midDark: '#b97a3a', ground: '#e0b46c', groundTop: '#f3d397', dirt: '#8f6430' },
  { name: 'Frostpeak', kind: 'peaks', sky: ['#86b2e6', '#eef6ff'], far: '#c4d6ee', mid: '#f7fbff', midDark: '#a9c0de', ground: '#dde8f5', groundTop: '#ffffff', dirt: '#6f84a3' },
  { name: 'Gloomwood', kind: 'forest', sky: ['#262a52', '#6c5b8f'], far: '#3d3a68', mid: '#26284a', midDark: '#191a33', ground: '#3b4a3b', groundTop: '#56705a', dirt: '#211c1b' },
  { name: 'Ember Depths', kind: 'lava', sky: ['#2a0a0a', '#912e12'], far: '#4c1812', mid: '#2c100c', midDark: '#190807', ground: '#3a1c15', groundTop: '#5c2d1e', dirt: '#160807' },
  { name: 'Crystal Void', kind: 'void', sky: ['#0c0a29', '#3d1e72'], far: '#2c2463', mid: '#5c40b4', midDark: '#3a2780', ground: '#2a2152', groundTop: '#4a3a8c', dirt: '#110c26' },
];
const worldFor = (stage) => WORLDS[Math.floor((stage - 1) / 10) % WORLDS.length];

// Allies (the "Heroes" tab). `look` drives the procedural chibi renderer.
const HEROES = [
  { id: 'pip', name: 'Pip the Squire', cost: 50, dps: 4, look: { skin: '#f6c9a0', hair: '#7a4a24', hairStyle: 'bob', outfit: '#4f86d9', trim: '#f1d36b', pants: '#3a3f5c', weapon: 'spear' } },
  { id: 'wren', name: 'Wren the Archer', cost: 600, dps: 35, look: { skin: '#f3c39b', hair: '#d0482c', hairStyle: 'long', outfit: '#3f8a4a', trim: '#e9c46a', pants: '#4a3a2a', weapon: 'bow' } },
  { id: 'ember', name: 'Ember the Mage', cost: 7.2e3, dps: 300, look: { skin: '#f0c4a4', hair: '#e9e4f5', hairStyle: 'wizard', hat: '#7b3fbf', outfit: '#9b59d0', trim: '#f2c94c', pants: '#43305c', weapon: 'staff', glow: '#ff7a3a' } },
  { id: 'grom', name: 'Grom the Bruiser', cost: 8.6e4, dps: 2.6e3, look: { skin: '#9bc47a', hair: '#2f2a24', hairStyle: 'bald', beard: '#2f2a24', outfit: '#8a5a3a', trim: '#c0c0c0', pants: '#3b2f28', weapon: 'axe' } },
  { id: 'nyx', name: 'Nyx the Rogue', cost: 1e6, dps: 2.2e4, look: { skin: '#e9b993', hair: '#2b2b3a', hairStyle: 'hood', hood: '#2e3550', outfit: '#3a4466', trim: '#b23a48', pants: '#23263a', weapon: 'dagger' } },
  { id: 'sol', name: 'Sister Sol', cost: 1.2e7, dps: 1.9e5, look: { skin: '#f6d2b0', hair: '#f4e3a1', hairStyle: 'long', outfit: '#f3f1e8', trim: '#e0b23c', pants: '#c9c2ad', weapon: 'mace' } },
  { id: 'bolt', name: 'Bolt the Gunner', cost: 1.5e8, dps: 1.6e6, look: { skin: '#c98e63', hair: '#1f1f1f', hairStyle: 'bandana', band: '#e2463c', outfit: '#5c6b3a', trim: '#d9a441', pants: '#3a3a2a', weapon: 'gun' } },
  { id: 'moss', name: 'Moss the Druid', cost: 1.8e9, dps: 1.4e7, look: { skin: '#e8c29d', hair: '#5a8c3a', hairStyle: 'long', outfit: '#6b8f3a', trim: '#a8d672', pants: '#4a3a24', weapon: 'leaf' } },
  { id: 'dread', name: 'The Dread Knight', cost: 2.2e10, dps: 1.2e8, look: { skin: '#cfc8c0', hair: '#555', hairStyle: 'helmet', helm: '#596273', outfit: '#3d4452', trim: '#c23b3b', pants: '#2a2e38', weapon: 'sword', cape: '#7a1f2b' } },
  { id: 'vale', name: 'Vale the Valkyrie', cost: 3e11, dps: 1.05e9, look: { skin: '#f4d0b0', hair: '#f6f0d8', hairStyle: 'long', outfit: '#6fb0e6', trim: '#ffe08a', pants: '#3d5a80', weapon: 'spear', cape: '#ffffff' } },
  { id: 'wyrm', name: 'Wyrmcaller', cost: 4e12, dps: 9e9, look: { skin: '#d9a77c', hair: '#c93a2a', hairStyle: 'spiky', outfit: '#8a2a1f', trim: '#f2b134', pants: '#3a1f1a', weapon: 'scythe', cape: '#f2b134' } },
  { id: 'void', name: 'Oracle of the Void', cost: 5.5e13, dps: 8e10, look: { skin: '#b9a8e6', hair: '#2a1a4a', hairStyle: 'hood', hood: '#3a1f6e', outfit: '#5a3aa8', trim: '#7ef0ff', pants: '#24164a', weapon: 'staff', glow: '#7ef0ff' } },
];

// Hero damage multipliers unlocked at level milestones.
const HERO_MILESTONES = [[10, 2], [25, 2], [50, 2.5], [75, 2], [100, 3], [150, 2.5], [200, 2.5], [300, 3], [400, 3], [500, 4]];

// Active spells (the Sword Master tab). Mana-powered.
const SKILLS = [
  { id: 'thunder', name: 'Thunder Strike', req: 15, cost: 250, mana: 10, desc: (l) => `Deal ${fmt(skillPower('thunder', l))}x tap damage instantly` },
  { id: 'frenzy', name: 'Frenzy Blades', req: 40, cost: 5e3, mana: 25, timed: true, desc: (l) => `Auto-tap ${skillPower('frenzy', l)} times per second` },
  { id: 'warcry', name: 'War Cry', req: 75, cost: 8e4, mana: 30, timed: true, desc: (l) => `Increase ally DPS by ${skillPower('warcry', l)}x` },
  { id: 'midas', name: 'Midas Touch', req: 110, cost: 1.5e6, mana: 30, timed: true, desc: (l) => `Increase all gold by ${skillPower('midas', l)}x` },
  { id: 'berserk', name: 'Berserk', req: 150, cost: 3e7, mana: 35, timed: true, desc: (l) => `Increase tap damage by ${skillPower('berserk', l)}x` },
  { id: 'eagle', name: 'Eagle Eye', req: 200, cost: 6e8, mana: 40, timed: true, desc: (l) => `+${skillPower('eagle', l)}% critical chance` },
];
const SKILL_MAX = 10;
function skillPower(id, l) {
  l = Math.max(1, l);
  switch (id) {
    case 'thunder': return 50 + 30 * (l - 1);
    case 'frenzy': return 8 + 2 * (l - 1);
    case 'warcry': return +(1.5 + 0.35 * (l - 1)).toFixed(2);
    case 'midas': return +(1.5 + 0.3 * (l - 1)).toFixed(2);
    case 'berserk': return +(2 + 0.5 * (l - 1)).toFixed(2);
    case 'eagle': return 10 + 4 * (l - 1);
  }
  return 1;
}

// Perks: consumables bought with gems.
const PERKS = [
  { id: 'surge', name: 'Power Surge', gems: 40, dur: 600, desc: 'Double ALL damage for 10 minutes.' },
  { id: 'phantom', name: 'Phantom Hand', gems: 50, dur: 600, desc: 'An invisible hand auto-taps 6 times per second for 10 minutes.' },
  { id: 'shower', name: 'Gold Shower', gems: 30, desc: 'Instantly receive gold worth 60 monster kills.' },
  { id: 'flask', name: 'Mana Flask', gems: 15, desc: 'Instantly refill your mana pool.' },
];

// Artifacts bought with relics (earned by prestiging).
const ARTIFACTS = [
  { id: 'blade', name: 'Blade of Ages', base: 2, max: 0, desc: (l) => `+${l * 25}% all damage` },
  { id: 'gauntlet', name: 'Titan Gauntlet', base: 2, max: 0, desc: (l) => `+${l * 40}% tap damage` },
  { id: 'banner', name: 'War Banner', base: 2, max: 0, desc: (l) => `+${l * 35}% ally DPS` },
  { id: 'idol', name: 'Golden Idol', base: 3, max: 0, desc: (l) => `+${l * 25}% gold from monsters` },
  { id: 'clover', name: 'Lucky Clover', base: 3, max: 25, desc: (l) => `+${l}% critical chance` },
  { id: 'fang', name: 'Dragon Fang', base: 3, max: 0, desc: (l) => `+${l * 50}% critical damage` },
  { id: 'hourglass', name: 'Hourglass of Ages', base: 4, max: 15, desc: (l) => `+${l * 2}s boss fight timer` },
  { id: 'crystal', name: 'Mana Crystal', base: 4, max: 20, desc: (l) => `+${l * 5} max mana, +${l * 10}% mana regen` },
  { id: 'tome', name: 'Arcane Tome', base: 4, max: 20, desc: (l) => `+${l * 4}s spell duration` },
  { id: 'boots', name: 'Warp Boots', base: 5, max: 20, desc: (l) => `${l * 2}% chance to skip a stage` },
  { id: 'crown', name: 'Tyrant Crown', base: 5, max: 15, desc: (l) => `-${l * 4}% boss HP` },
  { id: 'key', name: 'Lucky Key', base: 4, max: 15, desc: (l) => `+${l}% treasure mimic chance` },
];

// Equipment: collect from chests, equip one per slot. Changes the hero's look.
const RARITIES = [
  { name: 'Common', color: '#b9c2cc', mult: 1 },
  { name: 'Rare', color: '#4fa3ff', mult: 2.4 },
  { name: 'Epic', color: '#b46cff', mult: 5 },
  { name: 'Legendary', color: '#ffb43a', mult: 11 },
];
const GEAR_SLOTS = [
  { id: 'weapon', name: 'Weapon', stat: 'tap', label: 'tap damage', base: 0.3 },
  { id: 'helm', name: 'Helmet', stat: 'crit', label: 'critical damage', base: 0.35 },
  { id: 'armor', name: 'Armor', stat: 'ally', label: 'ally DPS', base: 0.3 },
  { id: 'aura', name: 'Aura', stat: 'gold', label: 'gold', base: 0.25 },
];
const GEAR = [
  // weapons: blade color, glow
  { id: 'w_rusty', slot: 'weapon', name: 'Rusty Blade', r: 0, blade: '#b98b6b', glow: null },
  { id: 'w_iron', slot: 'weapon', name: 'Iron Claymore', r: 0, blade: '#dfe6ee', glow: null },
  { id: 'w_frost', slot: 'weapon', name: 'Frost Edge', r: 1, blade: '#bff1ff', glow: '#5fd7ff' },
  { id: 'w_ember', slot: 'weapon', name: 'Ember Saber', r: 1, blade: '#ffb27a', glow: '#ff5a2a' },
  { id: 'w_thunder', slot: 'weapon', name: 'Thunder Fang', r: 2, blade: '#fff6a8', glow: '#ffe53a' },
  { id: 'w_void', slot: 'weapon', name: 'Void Reaper', r: 3, blade: '#d6b5ff', glow: '#9b4dff' },
  // helmets
  { id: 'h_cap', slot: 'helm', name: 'Leather Cap', r: 0, helm: 'cap', color: '#8a5a3a' },
  { id: 'h_iron', slot: 'helm', name: 'Iron Visor', r: 0, helm: 'visor', color: '#9aa6b5' },
  { id: 'h_horn', slot: 'helm', name: 'Horned Helm', r: 1, helm: 'horned', color: '#6c7686' },
  { id: 'h_circlet', slot: 'helm', name: 'Sun Circlet', r: 2, helm: 'circlet', color: '#ffd24a' },
  { id: 'h_crown', slot: 'helm', name: 'Crown of Kings', r: 3, helm: 'crown', color: '#ffc21a' },
  // armor: outfit colors
  { id: 'a_tunic', slot: 'armor', name: 'Traveler Tunic', r: 0, outfit: '#3f6fb5', trim: '#e9d27a' },
  { id: 'a_chain', slot: 'armor', name: 'Chainmail', r: 0, outfit: '#8e99a8', trim: '#5b6575' },
  { id: 'a_forest', slot: 'armor', name: 'Ranger Garb', r: 1, outfit: '#3f8a4a', trim: '#c9a24a' },
  { id: 'a_royal', slot: 'armor', name: 'Royal Plate', r: 2, outfit: '#e8eef5', trim: '#ffc83a' },
  { id: 'a_dragon', slot: 'armor', name: 'Dragonscale', r: 3, outfit: '#a8242a', trim: '#ffb43a' },
  // auras: particle color
  { id: 'u_spark', slot: 'aura', name: 'Sparkle Aura', r: 0, aura: '#ffffff' },
  { id: 'u_leaf', slot: 'aura', name: 'Verdant Aura', r: 1, aura: '#7dff7a' },
  { id: 'u_flame', slot: 'aura', name: 'Flame Aura', r: 2, aura: '#ff7a2a' },
  { id: 'u_star', slot: 'aura', name: 'Starfall Aura', r: 3, aura: '#ffe34a' },
];

// Pets: one active at a time, attacks every few taps, gives a passive bonus.
const PETS = [
  { id: 'nova', name: 'Nova', body: '#ffbf2e', belly: '#fff1d0', wing: '#f0902a', ears: 'fox', stat: 'tap', label: 'tap damage', per: 0.15 },
  { id: 'bubbles', name: 'Bubbles', body: '#4a6ee0', belly: '#9fb5ff', wing: '#2f4aa8', ears: 'horns', fuzzy: true, stat: 'ally', label: 'ally DPS', per: 0.15 },
  { id: 'toto', name: 'Toto', body: '#7a9a3a', belly: '#d8e0a0', wing: '#4f6a22', ears: 'fox', sleepy: true, stat: 'gold', label: 'gold', per: 0.12 },
  { id: 'soot', name: 'Soot', body: '#3fbf4a', belly: '#f2d24a', wing: '#2a8f34', ears: 'horns', stat: 'crit', label: 'critical damage', per: 0.25 },
  { id: 'hamy', name: 'Hamy', body: '#c9372c', belly: '#ffe6c8', wing: '#ffffff', ears: 'fox', bow: true, stat: 'mana', label: 'mana regen', per: 0.08 },
  { id: 'gloop', name: 'Gloop', body: '#b06cff', belly: '#e8d0ff', wing: '#7a3ad0', ears: 'none', stat: 'boss', label: 'boss damage', per: 0.2 },
];
const PET_CHARGE = 15; // taps per pet attack
const petEggsForLevel = (lvl) => lvl * 2; // eggs needed to go from lvl -> lvl+1

// Shop chests (gems only, no real money).
const CHESTS = [
  { id: 'wood', name: 'Wooden Chest', gems: 120, items: 2, odds: [0.75, 0.21, 0.035, 0.005], body: '#c98a3a', band: '#7d7d86' },
  { id: 'silver', name: 'Silver Chest', gems: 300, items: 3, odds: [0.45, 0.4, 0.13, 0.02], body: '#2f6fd0', band: '#d8dde5' },
  { id: 'titan', name: 'Titan Chest', gems: 900, items: 5, odds: [0.2, 0.42, 0.28, 0.1], body: '#f2b81e', band: '#b5781a' },
];
const FREE_CHEST_SECS = 4 * 3600;
const DEAL_SECS = 6 * 3600;

// Achievements, auto-claimed for gems.
const ACH_GROUPS = [
  { key: 'kills', name: 'Monster Slayer', stat: (S) => S.stats.kills, tiers: [50, 500, 5000, 50000, 500000], gems: [5, 10, 20, 40, 80], fmt: (v) => `Defeat ${fmt(v)} monsters` },
  { key: 'stage', name: 'Explorer', stat: (S) => S.stats.bestStage, tiers: [10, 25, 50, 100, 150, 250], gems: [5, 10, 25, 50, 80, 120], fmt: (v) => `Reach stage ${v}` },
  { key: 'taps', name: 'Tap Master', stat: (S) => S.stats.taps, tiers: [100, 1000, 10000, 100000, 1000000], gems: [5, 10, 20, 40, 80], fmt: (v) => `Tap ${fmt(v)} times` },
  { key: 'crits', name: 'Sharp Eye', stat: (S) => S.stats.crits, tiers: [25, 250, 2500, 25000], gems: [5, 15, 30, 60], fmt: (v) => `Land ${fmt(v)} critical hits` },
  { key: 'bosses', name: 'Boss Hunter', stat: (S) => S.stats.bosses, tiers: [5, 25, 100, 500, 2000], gems: [10, 20, 40, 80, 150], fmt: (v) => `Defeat ${fmt(v)} bosses` },
  { key: 'gold', name: 'Hoarder', stat: (S) => S.stats.goldEarned, tiers: [1e4, 1e8, 1e12, 1e18, 1e24], gems: [5, 15, 30, 60, 120], fmt: (v) => `Earn ${fmt(v)} gold` },
  { key: 'allies', name: 'Recruiter', stat: (S) => Object.keys(S.heroes).length, tiers: [3, 6, 9, 12], gems: [10, 20, 40, 80], fmt: (v) => `Hire ${v} different allies` },
  { key: 'prestige', name: 'Reborn', stat: (S) => S.stats.prestiges, tiers: [1, 5, 20, 50], gems: [25, 50, 100, 200], fmt: (v) => `Prestige ${v} time${v > 1 ? 's' : ''}` },
  { key: 'casts', name: 'Spellslinger', stat: (S) => S.stats.casts, tiers: [10, 100, 1000, 5000], gems: [10, 20, 40, 80], fmt: (v) => `Cast ${fmt(v)} spells` },
  { key: 'gear', name: 'Collector', stat: (S) => Object.keys(S.gear).length, tiers: [3, 8, 14, GEAR.length], gems: [10, 25, 50, 150], fmt: (v) => `Collect ${v} pieces of equipment` },
  { key: 'wisps', name: 'Wisp Catcher', stat: (S) => S.stats.wisps, tiers: [5, 50, 250], gems: [10, 25, 60], fmt: (v) => `Catch ${v} wisps` },
];
const ACHIEVEMENTS = [];
ACH_GROUPS.forEach((g) => g.tiers.forEach((t, i) => ACHIEVEMENTS.push({ id: `${g.key}_${i}`, group: g, tier: i, target: t, gems: g.gems[i] })));
