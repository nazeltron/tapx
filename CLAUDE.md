# TapX

Browser tap-to-fight idle game; all art and sound are procedural (no image/audio files).

## Every update
- Bump `GAME_VERSION` in `js/data.js` (patch for fixes, minor for new features).
- Run `node tools/build.js` to regenerate the single-file bundle `dist/TapX-v<version>.html`.
- Commit the bundle along with the source, and send the bundled file to the user.
