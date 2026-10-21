# TapX

Paper 1.21.11 plugin (Kotlin, Gradle + paperweight-userdev) — a Tap Titans-style idle game shown on a
full-screen 2D map screen (fake glow item frames + map packets, per player, in a void "arcade" world).
Players are auto-started in spectator mode, seated on an ItemDisplay camera entity with a raw
SetCamera packet. Input: left-click (spectators send an attack interact packet on the fake frame, not a swing), mouse cursor (the seated player's head-rotation
packets are read by a Netty handler and turned into cursor movement, re-centred with relative rotation
packets; the cursor is overlaid on the map colours every 50 ms, separately from the full frame), WASD/space/shift (PlayerInputEvent). Gems are the paid currency (`/tapx gems ...` for the
web store). No resource pack. `web/` is the old
browser prototype, reference only.

## Every update
- Bump `version` in `gradle.properties` (patch for fixes, minor for new features, major for reworks).
- Build with `./gradlew build` → `build/libs/TapX-<version>.jar`.
- Check visuals with `./gradlew preview` (PNG renders in `build/preview`).
- Send that jar to the user.
