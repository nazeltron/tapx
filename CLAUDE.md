# TapX

Paper 1.21.11 plugin (Kotlin, Gradle + paperweight-userdev) — a Tap Titans-style idle game shown on a
full-screen 2D map screen (fake glow item frames + map packets, per player, in a void "arcade" world).
Players are auto-started in ADVENTURE mode (the client alone is told CREATIVE, so no XP/locator bar), invisible, seated on an ItemDisplay camera entity with a
raw SetCamera packet (camera != player, so no hotbar/hearts; client is sent an empty inventory and 0 XP
so no hand/level). ScreenPack (served over HTTP on the MC port via a Paper channel listener) blanks the
crosshair, XP bar and locator bar sprites (waypoint ranges are also zeroed while playing). A Netty handler reads input: clicks = ServerboundInteractPacket, mouse =
head rotation (re-centred with relative rotation packets), wheel = ServerboundSetCarriedItemPacket;
it drops item/inventory packets. The cursor is overlaid on the map colours every 25 ms, separately
from the full frame (`screen-fps`, default 20). WASD/space/shift come from PlayerInputEvent.
Screen: 896x512 layout units drawn at Res.k (2) map pixels each (14x8 maps); cached sprites are made with
hiRes()/blit(). Only changed pixels are converted (Palette.convertChanged) and only dirty 128x16 bands sent;
the right panel is re-rendered only on input / every 4th frame.
Server features: ChatTools (ChatFilter, ChatGames, Welcome, /payment + /flashsale), Warns (/warn ladders), ServerEvents (/event), StoreTools (flash sale, payments/piggy bank, promo codes), Essentials,
TabList (+chat), Discord (DiscordSRV via reflection), SetStat (/tapx set), Cmds (code-registered commands).
Look: chunky pixel art (since 4.0, at the user's request): art is still drawn with Java2D, then PixelArt
turns each cached sprite into pixel art (1-unit blocks, dark outline, colours made vivid and snapped to exact
map colours so they're solid on the map); monsters are drawn at 8 fps straight at block resolution
(PixelArt.render). Widgets are pixel UI-kit style (stepped corners, black outline, light top row, darker lip);
text is Jersey 10 (GameFont) drawn crisp on its pixel grid. Ordered dithering (Palette) is left for the rest.
Pixel sprites are UnitImages (one pixel per block; blit draws a soft-referenced full-size copy). The player's
fighter is a beetle knight with shield + warhammer (Art.drawBeetleKnight, HERO_LOOK hairStyle "beetle").
`./gradlew perfCheck` plays 15 minutes headless and prints frame cost, heap and list sizes per minute (catch leaks).
Rendering budget: `./gradlew preview` prints the frame cost (~19 ms render + ~3 ms palette). Characters
(per quantised pose), pets, icons, coins, portraits, widgets and text are cached sprites; only visible
list rows are drawn. Gems are the paid currency (`/tapx gems ...` for the
web store). `web/` is the old
browser prototype, reference only.

Balance follows the browser prototype: compare `./gradlew sim` with `node web/tools/sim.js`.

## Every update
- Bump `version` in `gradle.properties` (patch for fixes, minor for new features, major for reworks).
- Build with `./gradlew build` → `build/libs/TapX-<version>.jar`.
- Check visuals with `./gradlew preview` (PNG renders in `build/preview`).
- Send that jar to the user.
