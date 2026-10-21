# TapX

A 2D tap-to-fight idle RPG in the style of Tap Titans, playable in any browser.

**No texture pack, no images, no sound files.** Every character, monster, pet,
background, icon and sound effect is generated in code (HTML5 canvas paths and
the Web Audio API).

## Play

The easiest way: download the single file in `dist/` (e.g. `dist/TapX-v1.0.0.html`)
and open it in any browser. It contains the whole game, so it works on its own.

From the source you can also open `index.html` directly. There is nothing to install.
On a phone, put the folder on any static host (e.g. GitHub Pages) and open it.

Or run a local server:

```sh
python3 -m http.server 8000   # then open http://localhost:8000
```

## How it plays

- **Tap the monster** to attack. Defeat 8 monsters, then beat the **boss** before
  the timer runs out to reach the next stage. If the boss escapes, farm and
  press **Fight Boss** when you're stronger (or **Leave Battle** anytime).
- Every 10 stages takes you to a new world: Meadow Ruins, Sunscorch Dunes,
  Frostpeak, Gloomwood, Ember Depths, Crystal Void.
- Monsters are generated from a random seed, so they all look different: blobs,
  towers, cones, orbs, brutes and slimes with random horns, eyes, mouths, arms,
  spikes and colours. Bosses wear crowns. Watch out for **treasure mimics** (12x gold).
- Catch the **wisp** that sometimes flies across the screen for a bonus.

### Tabs

| Tab | What it does |
| --- | --- |
| ⚔️ Sword Master | Level up tap damage, prestige, learn mana **spells** (Thunder Strike, Frenzy Blades, War Cry, Midas Touch, Berserk, Eagle Eye) and buy gem **perks** |
| 🛡️ Allies | Hire 12 allies who deal damage per second, even while you're away |
| 👕 Equipment | Weapons, helmets, armor and auras from chests. They change how your hero looks and give bonuses |
| 🐾 Pets | One active pet charges up as you tap and attacks every 15 taps |
| ❤️ Artifacts | Spend relics from prestiging on permanent upgrades |
| 🧰 Shop | Free chest every 4 hours, gem chests, and pet egg daily deals (gems only, no real money) |

Also included: achievements (gem rewards), offline earnings, autosave, and
save export/import (⚙ button).

## Code layout

| File | Purpose |
| --- | --- |
| `js/util.js` | Math, seeded RNG, number formatting (K, M, B, T, Qa...), synthesized sound effects |
| `js/data.js` | All game content: worlds, allies, spells, artifacts, gear, pets, achievements |
| `js/game.js` | Game state, formulas and rules (no DOM, so it can run headless) |
| `js/art.js` | Procedural art: chibi characters, monster generator, pets, backgrounds, icons |
| `js/ui.js` | Bottom panel: tabs, upgrade rows, shop, modals |
| `js/main.js` | Battle view rendering, effects, input, main loop |
| `tools/build.js` | Bundles everything into one file: `node tools/build.js` → `dist/TapX-v<version>.html` |
| `tools/sim.js` | Headless balance simulator: `node tools/sim.js [hours] [tapsPerSecond]` |

## Releasing an update

1. Bump `GAME_VERSION` in `js/data.js` (e.g. `1.0.0` → `1.1.0`).
2. Run `node tools/build.js`. It writes `dist/TapX-v<version>.html` and removes the old bundle.
