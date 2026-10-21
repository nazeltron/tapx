# TapX

A Tap Titans-style idle clicker for **Paper 1.21.11**, written in Kotlin, played on a full-screen
**2D game screen inside Minecraft**: no chest menus and **no resource pack needed**.

## How the screen works

`/tapx` seats the player in a private, empty "arcade" world, facing a wall of maps in invisible
glow item frames that only they can see. Together the maps form a 896×512 pixel screen that
the plugin draws every frame (characters, generated monsters, worlds, menus, effects) and streams
to the player, sending only the pixels that changed.

- **Look around** to move the cursor (the crosshair is the mouse pointer)
- **Left-click** to tap the monster and press buttons
- **Mouse wheel** scrolls lists
- **Sneak (shift)** or the **EXIT** button leaves and sends you back where you were
- Press **F1** to hide the hotbar (a yellow cursor is drawn on the screen)

The screen is sized for the default 70° field of view (change `screen-fov` in the config to match).
Maps can only show about 240 colours, so the art is slightly posterised.

## Install

1. Drop `TapX-<version>.jar` into `plugins/` and restart. Requires Paper 1.21.11 and Java 21.
2. Players type **`/tapx`**.

The plugin creates a void world called `tapx_arcade` for the screens. Each player gets their own
seat (64 blocks apart), and players there are hidden from each other.

## The game

- Tap monsters; defeat 8, then beat the **boss** before its timer runs out. Every 10 stages is a new world.
- **Hero** tab: level up tap damage, prestige, learn 6 mana **spells** (cast from the left of the battle), gem **perks**
- **Allies**: 12 heroes who deal damage every second (and keep earning while you're away)
- **Gear**: weapons, helmets, armor and auras from chests; they change how your hero looks
- **Pets**: a companion that strikes every 15 taps
- **Relics**: spend relics from prestiging on 12 artifacts
- **Shop**: free chest every 4 hours, gem chests, pet-egg daily deals (**gems only, no real money**)
- Achievements, a server leaderboard, treasure mimics, wisps, offline earnings, per-player saves

## Commands & permissions

| Command | Permission | Default |
| --- | --- | --- |
| `/tapx` (aliases `/tap`, `/taptitans`): open the game screen | `tapx.play` | everyone |
| `/tapx leave`: leave the screen | `tapx.play` | everyone |
| `/tapx top`: leaderboard in chat | `tapx.play` | everyone |
| `/tapx admin give <player> <gold\|gems\|relics> <amount>` | `tapx.admin` | op |
| `/tapx admin stage <player> <stage>` | `tapx.admin` | op |
| `/tapx admin reset <player>` | `tapx.admin` | op |

## Config (`plugins/TapX/config.yml`)

```yaml
starting-gems: 100        # gems for new players
max-offline-hours: 12     # cap on "while you were away" earnings
max-taps-per-second: 20   # clicks per second that count (stops auto-clickers)
autosave-minutes: 5
arcade-world: tapx_arcade # the void world used for screens
screen-fov: 70            # field of view the screen is sized for
fps: 10                   # screen updates per second (more = smoother, more bandwidth)
render-threads: 2         # background threads that draw screens
```

Player progress is saved to `plugins/TapX/players/<uuid>.json`.

## Building & testing

```sh
./gradlew build     # -> build/libs/TapX-<version>.jar
./gradlew preview   # renders the screen to build/preview/*.png through the real map palette
```

The version lives in `gradle.properties`; the jar name follows it. `tools/bot-test` has a
mineflayer bot that plays on a local server and rebuilds the screen from the packets it receives.

## Code layout

| File | Purpose |
| --- | --- |
| `Content.kt` | Game content: worlds, allies, spells, perks, artifacts, gear, pets, chests, achievements |
| `Game.kt` | Rules and formulas for one player |
| `PlayerData.kt` | What gets saved per player |
| `Session.kt` | One player's game loop on a background thread (input → update → draw → send) |
| `Arcade.kt` | The void world, seats, cursor from view direction, input and protection events |
| `screen/GameScreen.kt` | The whole 2D UI: battle, tabs, lists, pop-ups, effects, hit-testing |
| `screen/Art.kt` | Procedural art (characters, monster generator, pets, backgrounds, icons) |
| `screen/MapScreen.kt` | The per-player wall of fake item frames and partial map updates |
| `screen/Palette.kt` | RGB → map colour lookup table |
| `screen/PixelFont.kt` | Built-in 5×7 pixel font (no server fonts needed) |

`web/` holds the original browser prototype, kept for reference.
