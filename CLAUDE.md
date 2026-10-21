# TapX

Paper 1.21.11 plugin (Kotlin, Gradle) — a Tap Titans-style idle game played in chest menus using vanilla
items only (no resource pack). `web/` is the old browser prototype, reference only.

## Every update
- Bump `version` in `gradle.properties` (patch for fixes, minor for new features).
- Build with `./gradlew build` → `build/libs/TapX-<version>.jar`.
- Send that jar to the user.
