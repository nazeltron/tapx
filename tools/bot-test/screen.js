// Joins, opens /tapx, rebuilds the 2D screen from the frame + map packets the client receives.
const mineflayer = require('mineflayer');
const { PNG } = require('pngjs');
const fs = require('fs');
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const OUT = process.argv[2] || 'shots';
fs.mkdirSync(OUT, { recursive: true });
const bot = mineflayer.createBot({ host: 'localhost', port: 25599, username: 'TapBot', version: '1.21.11' });
const mcData = require('minecraft-data')('1.21.11');
// map colors: base colors * brightness (180,220,255,135)
const BASE = require('./mapcolors.json');
const MULT = [180, 220, 255, 135];
function rgb(id) { const b = BASE[id >> 2] || [0, 0, 0]; const m = MULT[id & 3]; return [b[0] * m / 255 | 0, b[1] * m / 255 | 0, b[2] * m / 255 | 0]; }
const maps = {}; const frames = {}; let mapBytes = 0, mapPackets = 0;
bot._client.on('map', p => {
  mapPackets++;
  const id = p.itemDamage;
  const buf = maps[id] || (maps[id] = new Uint8Array(128 * 128));
  if (p.columns > 0) { mapBytes += p.data.length; for (let y = 0; y < p.rows; y++) for (let x = 0; x < p.columns; x++) buf[(p.y + y) * 128 + p.x + x] = p.data[y * p.columns + x]; }
});
bot._client.on('spawn_entity', p => {
  const t = mcData.entitiesArray.find(e => e.id === p.type);
  if (t && t.name === 'glow_item_frame') frames[p.entityId] = { x: p.x, y: p.y, z: p.z, dir: p.objectData };
});
bot._client.on('entity_metadata', p => {
  const f = frames[p.entityId]; if (!f) return;
  for (const m of p.metadata) {
    if (m.value && m.value.itemId !== undefined) {
      const comp = (m.value.addedComponents || m.value.components || []).find(c => c.type === 'map_id');
      if (comp) f.map = comp.data;
    }
    if (m.key === 0) f.flags = m.value;
  }
});
bot.on('messagestr', m => { if (!m.includes('joined')) console.log('CHAT:', m.slice(0, 160)); });
bot.on('kicked', r => console.log('KICKED', r));
function shot(name) {
  const fs_ = Object.values(frames);
  if (!fs_.length) { console.log('no frames'); return; }
  const xs = [...new Set(fs_.map(f => f.x))].sort((a, b) => a - b), ys = [...new Set(fs_.map(f => f.y))].sort((a, b) => b - a);
  const png = new PNG({ width: xs.length * 128, height: ys.length * 128 });
  for (const f of fs_) {
    const cx = xs.indexOf(f.x), cy = ys.indexOf(f.y); const buf = maps[f.map];
    for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) {
      const [r, g, b] = buf ? rgb(buf[y * 128 + x]) : [255, 0, 255];
      const i = ((cy * 128 + y) * png.width + cx * 128 + x) * 4; png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
    }
  }
  fs.writeFileSync(`${OUT}/${name}.png`, PNG.sync.write(png));
}
// Keys are sent the way the vanilla client does: a player_input packet on press and release.
async function key(name, hold = 120) {
  const on = { forward: false, backward: false, left: false, right: false, jump: false, shift: false, sprint: false };
  on[name] = true;
  bot._client.write('player_input', { inputs: on });
  await sleep(hold);
  on[name] = false;
  bot._client.write('player_input', { inputs: on });
  await sleep(250);
}
// A spectator client doesn't swing: a left-click on the screen is an attack on the frame it looks at.
let clicks = 0;
async function click(wait = 120) {
  const target = Number(Object.keys(frames)[0]);
  bot._client.write('use_entity', { target, mouse: 1, sneaking: false }); clicks++;
  await sleep(wait);
}
let camera = null;
// Mouse: a seated vanilla client sends its head rotation every tick; we do the same.
let yaw = 180, pitch = 0;
async function mouse(dYaw, dPitch, steps = 6) {
  for (let i = 0; i < steps; i++) {
    yaw += dYaw / steps; pitch = Math.max(-90, Math.min(90, pitch + dPitch / steps));
    bot._client.write('look', { yaw, pitch, flags: { onGround: false, hasHorizontalCollision: false } });
    await sleep(50);
  }
  await sleep(250);
}
// cursor starts in the middle (448,256); 7 screen pixels per degree
let cx = 448, cy = 256;
async function pointAt(x, y) { await mouse((x - cx) / 7, (y - cy) / 7); cx = x; cy = y; }
bot._client.on('camera', p => { camera = p.cameraId; });
// Like the vanilla client: apply the server's (relative) rotation and echo it straight back.
let recentres = 0;
bot._client.on('player_rotation', p => {
  yaw = p.relativeYaw ? yaw + p.yaw : p.yaw; pitch = p.relativePitch ? pitch + p.pitch : p.pitch; recentres++;
  bot._client.write('look', { yaw, pitch, flags: { onGround: false, hasHorizontalCollision: false } });
});
bot.once('spawn', async () => {
  try {
    await sleep(4500); // auto-start: no /tapx needed
    console.log('gamemode', bot.game.gameMode, 'camera entity', camera, 'vehicle', !!bot.vehicle, 'frames', Object.keys(frames).length);
    shot('01-start');
    await pointAt(448 + 128, 256 + 98 - 30); await click(); // "+" in the screen-size popup? (just aims and clicks)
    await pointAt(448, 256 + 90); await click(300);        // DONE
    shot('02-after-done');
    await pointAt(356, 300);                               // the monster
    for (let i = 0; i < 15; i++) await click(100);
    await sleep(300); shot('03-taps');
    await pointAt(512 + 6 + 61 + 29, 30); await click(400); // Allies tab
    shot('04-allies');
    await pointAt(512 + 6 + 5 * 61 + 29, 30); await click(400); // Shop tab
    shot('05-shop');
    await pointAt(512 + 378 - 60, 200);                      // hover a shop button
    shot('06-hover');
    await pointAt(448, 256); shot('07-centre');
    await mouse(0, 70, 14); await mouse(0, -70, 14);          // far down and back: re-centres, cursor must end where it began
    shot('08-after-recentre');
    console.log('re-centres', recentres, 'pitch now', pitch.toFixed(1));
    console.log('total map packets', mapPackets, 'bytes', mapBytes);
  } catch (e) { console.log('TEST ERROR', e); }
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
bot._client.on('entity_destroy', p => { for (const id of p.entityIds) delete frames[id]; });
