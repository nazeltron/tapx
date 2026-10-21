# Bot screen test

Checks the real server -> client path without a Minecraft client:

1. Start a local Paper 1.21.11 test server on port 25599 (`online-mode=false`) with the TapX jar.
2. `../../gradlew -p ../.. dumpMapColors -Pout=$PWD/mapcolors.json` (map colour table)
3. `npm install && node screen.js shots`

The bot is auto-started into the game, rebuilds the screen from the glow-item-frame and map packets it
receives, then clicks (taps) and uses WASD/space/shift input packets to move around the menus.
Screens are saved in `shots/`.
