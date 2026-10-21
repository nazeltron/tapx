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
// aim the bot's look at screen pixel (px, py); screen plane z = 1/16, top y = 103, left x = -3 (slot 0)
async function aimAt(px, py) {
  const e = bot.entity.position.offset(0, bot.entity.eyeHeight ?? 1.62, 0);
  const tx = -3 + px / 128, ty = 103 - py / 128, tz = 1 / 16;
  const dx = tx - e.x, dy = ty - e.y, dz = tz - e.z;
  const yaw = Math.atan2(-dx, -dz), pitch = Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
  await bot.look(yaw, pitch, true);
  // vanilla yaw: degrees, 0 = south, clockwise; mineflayer yaw: radians, 0 = north, counter-clockwise
  const myaw = (180 - yaw * 180 / Math.PI), mpitch = -pitch * 180 / Math.PI;
  bot._client.write('look', { yaw: myaw, pitch: mpitch, flags: { onGround: false, hasHorizontalCollision: false } });
  await sleep(150);
}
async function clickAt(px, py, wait = 300) { await aimAt(px, py); bot.swingArm('right'); await sleep(wait); }
bot.once('spawn', async () => {
  try {
    await sleep(1000);
    bot.chat('/tapx'); await sleep(3500);
    await clickAt(448, 336, 400); // close the welcome-back popup if there is one
    const ent = bot.entity;
    console.log('bot eye pos', ent.position.x.toFixed(3), (ent.position.y + 1.62).toFixed(3), ent.position.z.toFixed(3), 'vehicle', !!bot.vehicle, 'frames', Object.keys(frames).length);
    console.log('frame sample', JSON.stringify(Object.values(frames).slice(0, 2)));
    console.log('map packets', mapPackets, 'bytes', mapBytes);
    shot('01-start');
    const b0 = mapBytes; await sleep(2000); console.log('idle bandwidth bytes/s ~', (mapBytes - b0) / 2);
    // tap the monster 15 times (battle panel monster around x=6+350, y=6+330)
    for (let i = 0; i < 15; i++) await clickAt(356, 330, 120);
    await sleep(400); shot('02-after-taps');
    await clickAt(512 + 6 + 61 + 29, 6 + 20, 600); shot('03-allies-tab');
    await clickAt(512 + 6 + 5 * 61 + 29, 6 + 20, 600); shot('04-shop-tab');
    bot.setQuickBarSlot(1); await sleep(400); shot('05-scrolled');
    await clickAt(6 + 72, 6 + 18, 600); shot('06-achievements');
    console.log('total map packets', mapPackets, 'bytes', mapBytes);
    bot.setControlState('sneak', true); await sleep(1200); bot.setControlState('sneak', false);
    console.log('after sneak: pos', bot.entity.position.toString(), 'frames still known', Object.keys(frames).length);
  } catch (e) { console.log('TEST ERROR', e); }
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
bot._client.on('entity_destroy', p => { for (const id of p.entityIds) delete frames[id]; });
