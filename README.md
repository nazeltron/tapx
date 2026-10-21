# TapX

A 2D tap-to-fight idle RPG in the style of Tap Titans, playable in any browser.

**No texture pack, no images, no sound files.** Every character, monster, pet,
background, icon and sound effect is generated in code (HTML5 canvas paths and
the Web Audio API).

## Play

Open `index.html` in a browser. There is nothing to install or build.
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
| `tools/sim.js` | Headless balance simulator: `node tools/sim.js [hours] [tapsPerSecond]` |
