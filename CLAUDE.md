# TapX

Paper 1.21.11 plugin (Kotlin, Gradle + paperweight-userdev) — a Tap Titans-style idle game shown on a
2D map screen (fake glow item frames + map packets, per player, in a void "arcade" world). No resource
pack. `web/` is the old browser prototype, reference only.

## Every update
- Bump `version` in `gradle.properties` (patch for fixes, minor for new features, major for reworks).
- Build with `./gradlew build` → `build/libs/TapX-<version>.jar`.
- Check visuals with `./gradlew preview` (PNG renders in `build/preview`).
- Send that jar to the user.
