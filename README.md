# TapX

A Tap Titans-style idle clicker for **Paper 1.21.11**, written in Kotlin, played on a full-screen
**2D game screen inside Minecraft**: no chest menus and **no resource pack needed**.

## How the screen works

Players join straight into the game (no command needed). Each player is put in spectator mode and
their camera is locked onto an invisible camera point in a private, empty "arcade" world, facing a
wall of maps in invisible glow item frames that only they can see. Together the maps form an
896x512 pixel screen that the plugin draws every frame and streams to that player, sending only the
pixels that changed. Spectator mode hides the hotbar, hand, hearts and crosshair, so the game is
the whole view. Every player has their own screen, game and save; players don't see each other.

**Controls**
- **Mouse**: moves the on-screen cursor (speed adjustable in Settings)
- **Left-click**: press the button under the cursor, or attack when the cursor is over the battle
- **Mouse wheel**: scroll lists (also: drag the scroll bar, hold **W/S** over a list, or rest the
  cursor at the top/bottom edge of a list)
- **A D / Space**: optional keyboard navigation (jump the cursor between buttons, press)
- **Shift**: back / close pop-ups

The player sits on the invisible camera, so their own view stays locked to the screen but the
client still sends its head rotation; the plugin turns that into cursor movement (and quietly
re-centres the head before it reaches the up/down limit). The screen is sized to cover the whole
window; on first join a **Screen size** check appears (+/- until the yellow corners touch the
window edges), and it can be changed later in Settings.
Maps can only show about 240 colours; the plugin mixes pairs of them with a fixed dither pattern,
so the smooth, anti-aliased art and gradients still come through (and still areas never flicker).

**No crosshair**: players sit invisible, in adventure mode, on the camera entity (so the client
draws no hotbar, hearts or hand, and the mouse wheel reaches the server as hotbar-slot changes).
Only their client is told it is in creative mode (the server keeps adventure), so no XP/locator bar is drawn.
While playing they get a tiny resource pack that blanks the crosshair, XP bar and locator bar; the plugin serves
it on the Minecraft port itself, so there's nothing to set up. Players are asked once to accept it.
Behind a proxy (Velocity/BungeeCord), upload `plugins/TapX/tapx-screen-pack.zip` somewhere and set
`resource-pack-url`. Their real inventory and XP are untouched; the client is just shown them empty.

## Install

1. Drop `TapX-<version>.jar` into `plugins/` and restart. Requires Paper 1.21.11 and Java 21.
2. Players are put into the game when they join. With `auto-start: false` they type **`/tapx`** instead.

The plugin creates a void world called `tapx_arcade` for the screens; each player gets their own
spot (64 blocks apart). Leaving the game restores the player's game mode and location.

## The game

- Tap monsters; defeat 8, then beat the **boss** before its timer runs out. Every 10 stages is a new world.
- 8 worlds; every 25th stage is a **Titan Lord** (3x HP, big gold and relic reward, small gem chance)
- **Hero** tab: level up tap damage, prestige, learn 12 mana **spells** (cast from the spell bar under the battle) and buy **training** upgrades (sword, leadership, precision, treasure, titan hunter, meditation; reset on prestige). Hero damage doubles only every 50 levels
- **Allies**: 16 heroes; each one attacks once per second (and they keep earning while you're away)
- **Skills**: a 16-node skill tree in 4 branches (Knight, Warlord, Sorcerer, Rogue); points from stages and prestige
- **Gear**: weapons, helmets, armor and auras from chests; they change how your hero looks
- **Pets**: 8 companions that strike every 15 taps, each with its own bonus
- **Relics**: spend relics from prestiging on 18 artifacts
- **Quests**: 7-day login calendar plus 3 daily quests (and a bonus for finishing all three)
- **Shop**: free chest, 4 gem chests, starter pack, permanent upgrades (x2 gold, x2 damage, auto
  tapper, offline master, mana well, skill points), timed boosts, instant gold/relics/stage warp,
  skill respec, pet eggs and hero skins
- Achievements, a server leaderboard, treasure mimics, wisps, offline earnings, per-player saves

### Gems (paid currency)

Gems are meant to be sold on your web store. In game they are rare: 25 to start, a 3% chance
from bosses, 15% from Titan Lords, day 7 of the login calendar, the daily quest bonus, and the
top tier of each achievement. Everything in the shop costs gems.

To connect a store (e.g. PayNow), have it run a console command on purchase:

```
tapx gems give {username} 500
```

It works whether the player is online (they get a thank-you pop-up), offline (their save is
updated), or has never joined (the gems are kept in `pending-gems.yml` and delivered on first
join). Every change is logged to `plugins/TapX/gems.log`.

## Commands & permissions

| Command | Permission | Default |
| --- | --- | --- |
| `/tapx` (aliases `/tap`, `/taptitans`): open the game screen | `tapx.play` | everyone |
| `/tapx leave`: leave the game screen | `tapx.admin` | op |
| (not put into the game on join) | `tapx.bypass` | nobody |
| `/tapx top`: leaderboard in chat | `tapx.play` | everyone |
| `/tapx gems <give\|take\|set\|check> <player> [amount]` (console too) | `tapx.admin` | op |
| `/tapx admin give <player> <gold\|gems\|relics> <amount>` | `tapx.admin` | op |
| `/tapx admin stage <player> <stage>` | `tapx.admin` | op |
| `/tapx admin reset <player>` | `tapx.admin` | op |

## Server features (3.1)

**Events ("ADMIN ABUSE")** - server-wide boosts shown on the game screen itself (a banner, then a strip under the HP bar), plus a chat announcement.
| Command | What it does |
| --- | --- |
| `/event start <gold\|damage\|relics\|mana\|luck\|all> <minutes> [multiplier]` | Start a boost (default x2) |
| `/event stop <type\|all>` | End it early |
| `/event gems <amount>` | Every online player gets gems |
| `/event giveaway <gems> [winners]` | Random online winners after a 3-2-1 countdown |
| `/events` | Anyone: what's running |

**Store (everything costs gems)** - a big GET GEMS & RANKS button at the top of the shop opens a link to the web store (`store-url`, default tapx.paynow.store); ranks VIP 500, PRO 1200, MVP 3000, ELITE 6000, TITAN 15000, LEGEND 35000
(more gold and damage, auto taps, longer offline earnings, bigger daily rewards, extra mana, faster free chests, extra wheel spins, login-calendar gems, free hero skins on ELITE+, chat/tab tags; click a rank in the shop to see every reward; upgrades
cost only the difference; LuckPerms groups of the same names are given if LuckPerms is installed), gamepasses
(Global x2, Relic Hunter, Ally Fury, Crit Master, Boss Slayer, Pet Trainer, Arcane Flow, Lucky Charm, Auto Boss,
plus the earlier ones) and a 25%-off bundle of every pass you don't own.

**For your web store (console commands)**
| Command | What it does |
| --- | --- |
| `tapx gems give {username} <amount>` | Gems (online, offline or before their first join) |
| `tapx rank set {username} <rank>` / `tapx pass add {username} <pass>` | Give a rank / gamepass |
| `tapx payment {username} <usd> [package]` | Thank-you broadcast, "type GG" gold, fills the piggy bank |
| `tapx flashsale <percent> <minutes>` | Real in-game discount on the gem shop + announcement |
| `tapx promo create <code> <uses\|0> <hours\|0> gems:5 eggs:3 relics:10 gold:100 boost:surge:10` | Promo codes (`/promo <code>`) |

When the piggy bank fills (default $100 of purchases) everyone gets a 2x EVERYTHING event.

**Admin**
| Command | What it does |
| --- | --- |
| `/tapx reset <player>` | Wipe one player's progress; gems, gamepasses, ranks and skins are kept |
| `/tapx resetall confirm` | New season: the same for every player (online and offline) |
| `/tapx set <player> <stat> <value>` | Any stat: gold, gems, relics, stage, taplevel, prestiges, rank, ally.<id>, artifact.<id>, pet.<id>, pass.<id>, ... |
| `/tapx stats <player>` | Show a player's numbers |

**Essentials** (permission `tapx.cmd.<name>`, ops by default; EssentialsX's own commands win if it's installed):
`/gmc /gms /gma /gmsp /gm`, `/fly`, `/heal`, `/feed`, `/god`, `/speed`, `/tp /tphere /tpall /back`, `/spawn /setspawn`,
`/warp /setwarp /delwarp /warps`, `/home /sethome /delhome /homes`, `/broadcast`, `/clearchat`, `/mutechat`, `/mute /unmute`,
`/kick /ban /tempban /unban`, `/vanish`, `/invsee`, `/ec`, `/ci`, `/hat`, `/repair`, `/workbench`, `/day /night /sun /rain`,
`/seen`, `/sudo`, `/nick`. For everyone: `/msg /r`, `/ping`, `/list`, `/afk`, `/rules`, `/store`, `/discord`, `/link`, `/promo`.

**Tab list and chat** - DrillSim-style tab: every online player (also those on the game screen) as
`RANK Name ✦best-stage [prestiges]`, sorted staff > rank > stage; header with the title, season and any running
event; footer with ONLINE / TPS / PING and the Discord invite (`tab.header`, `tab.season`, `tab.address`). Chat uses the
same tags plus a stats card on hover. Default Discord invite: discord.gg/drillnetwork.

**Ranks and gamepasses (admin)**

| Command | What it does |
| --- | --- |
| `/rank set <player> <rank>` | Give a store rank (vip, pro, mvp, elite, titan, legend) or a staff rank (owner, admin, mod, helper) |
| `/rank remove <player> [rank]` | Take one rank away, or all ranks when none is named |
| `/rank list <player>` | Show a player's store and staff rank |
| `/gamepass add\|remove <player> <pass\|all>` | Give or take gamepasses |
| `/gamepass set <player> <pass> <true\|false>` / `/gamepass list <player>` | Set one gamepass / list them |

Staff ranks are separate from store ranks and kept on resets. OWNER also gets every LEGEND perk. Ops without a
staff rank show as ADMIN; the `tapx.tag.owner/admin/mod/helper` permissions also work. These commands work on offline
players too, and `/tapx rank ...` / `/tapx pass ...` do the same.

**Discord** - `/discord`, `/link`, `/unlink` through DiscordSRV (link codes like DrillSim). Linked players get x1.25
gold forever and 5 pet eggs once.

**In the game** - a 6-step tutorial for new players and a daily wheel (Quest tab).

## Config (`plugins/TapX/config.yml`)

```yaml
starting-gems: 25         # gems for new players (gems are the paid currency)
max-offline-hours: 12     # cap on "while you were away" earnings
max-taps-per-second: 20   # clicks per second that count (stops auto-clickers)
autosave-minutes: 5
auto-start: true          # put players into the game when they join
arcade-world: tapx_arcade # the void world used for screens
screen-fov: 70            # field of view the screen is sized for (players can fine-tune in-game)
screen-aspect: 1.778      # window shape the screen is sized for (16:9)
screen-fps: 20            # screen updates per second, 2-40 (idle players drop to 10 then 5)
screen-resolution: 2      # 2 = sharp 1792x1024 (14x8 maps), 1 = 896x512 (a quarter of the cost)
render-threads: 2         # background threads that draw screens
hide-crosshair: true      # send the crosshair-hiding pack to players on the game screen
resource-pack-url: ""     # host the pack yourself (needed behind a proxy); empty = served on the game port
```

Player progress is saved to `plugins/TapX/players/<uuid>.json`.

## Building & testing

```sh
./gradlew build     # -> build/libs/TapX-<version>.jar
./gradlew preview   # renders the screen to build/preview/*.png through the real map palette
./gradlew sim -Pargs="3 5"   # balance check: 3 hours at 5 taps/sec, same bot as web/tools/sim.js
```

The core formulas match the browser prototype in `web/`; `sim` and `node web/tools/sim.js` should
give similar pacing.

The version lives in `gradle.properties`; the jar name follows it. `tools/bot-test` has a
mineflayer bot that plays on a local server and rebuilds the screen from the packets it receives.

## Code layout

| File | Purpose |
| --- | --- |
| `Content.kt` | Game content: worlds, allies, spells, skill tree, shop, skins, quests, artifacts, gear, pets, chests, achievements |
| `Game.kt` | Rules and formulas for one player |
| `PlayerData.kt` | What gets saved per player |
| `Session.kt` | One player's game loop on a background thread (input → update → draw → send) |
| `Arcade.kt` | The void world, seated spectator cameras, mouse/keyboard/click input, protection events |
| `screen/GameScreen.kt` | The whole 2D UI: battle, tabs, lists, pop-ups, effects, hit-testing |
| `screen/Art.kt` | Procedural art (characters, monster generator, pets, backgrounds, icons) |
| `screen/MapScreen.kt` | The per-player wall of fake item frames and partial map updates |
| `screen/Palette.kt` | RGB → map colour lookup table |
| `screen/GameFont.kt` | Game text: Lilita One with outline, shadow and shine (bundled, SIL OFL; see `resources/fonts`) |
| `screen/Widgets.kt` | Glossy buttons, panels, cards, ribbons and tabs (drawn once, cached) |

`web/` holds the original browser prototype, kept for reference.
