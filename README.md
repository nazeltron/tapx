# TapX

A Tap Titans-style idle clicker game for **Paper 1.21.11** servers, written in Kotlin.
The whole game is played in chest menus built from **vanilla items, so players need no resource pack**.

## Install

1. Build it (see below) or take the released `TapX-<version>.jar`.
2. Drop the jar into your server's `plugins/` folder and restart.
3. Players type **`/tapx`** to play.

Requires Paper 1.21.11 and Java 21. Kotlin is bundled inside the jar.

## How it plays

- **Click the monster** (or anywhere in the battle area) to attack. Damage shows on the action bar and the
  monster's health shows on the boss bar.
- Defeat 8 monsters, then beat the **boss** before its timer runs out. If it escapes, farm and press
  **Fight Boss** when ready (or **Leave Battle**).
- Every 10 stages is a new world (Meadow Ruins → Sunscorch Dunes → Frostpeak → Gloomwood → Ember Depths →
  Crystal Void), each with its own monsters and colours. Bosses get crowns of glint, and sometimes a
  **Treasure Mimic** shows up for 12x gold.
- Catch the glowing **wisp** when it appears for a bonus.
- Close the menu any time. Allies keep earning gold while you're away (collected when you reopen).

### Tabs (bottom row)

| Tab | What it does |
| --- | --- |
| ⚔ Sword Master | Level up tap damage, prestige, learn 6 mana **spells**, buy gem **perks** |
| Allies | Hire 12 allies who deal damage every second |
| Equipment | Weapons, helmets, armor and auras from chests: bonuses + shown on your hero |
| Pets | One active pet charges as you tap and strikes every 15 taps |
| Artifacts | Spend relics (from prestiging) on permanent upgrades |
| Shop | Free chest every 4 hours, gem chests, pet-egg daily deals. **Gems only, no real money** |

Also: achievements (gem rewards), a server leaderboard, sound toggle, and per-player saves.

## Commands & permissions

| Command | Permission | Default |
| --- | --- | --- |
| `/tapx` (aliases `/tap`, `/taptitans`) — open the game | `tapx.play` | everyone |
| `/tapx top` — leaderboard in chat | `tapx.play` | everyone |
| `/tapx admin give <player> <gold\|gems\|relics> <amount>` | `tapx.admin` | op |
| `/tapx admin stage <player> <stage>` | `tapx.admin` | op |
| `/tapx admin reset <player>` | `tapx.admin` | op |

## Config (`plugins/TapX/config.yml`)

```yaml
starting-gems: 100          # gems for new players
max-offline-hours: 12       # cap on "while you were away" earnings
max-taps-per-second: 20     # stops auto-clickers from going wild
autosave-minutes: 5
```

Player progress is saved to `plugins/TapX/players/<uuid>.json`.

## Building

```sh
./gradlew build     # -> build/libs/TapX-<version>.jar
```

The version lives in `gradle.properties`. Bump it for every update; the jar name follows it.

## Code layout

| File | Purpose |
| --- | --- |
| `Content.kt` | All game content: worlds, allies, spells, perks, artifacts, gear, pets, chests, achievements |
| `Game.kt` | Rules and formulas for one player (combat, buying, prestige, offline earnings) |
| `PlayerData.kt` | What gets saved per player |
| `Session.kt` | One online player's game: ticking, sounds, boss bar, titles, effects |
| `Menus.kt` | Every chest menu (battle screen and all tabs) |
| `Items.kt` | Builds the vanilla items used in menus (clean tooltips, glint, heads) |
| `TapXPlugin.kt` | Plugin entry: commands, events, saving, leaderboard |

`web/` holds the original browser prototype of the game, kept for reference.
