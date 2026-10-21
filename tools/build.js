// Bundles the whole game into one standalone HTML file: dist/TapX-v<version>.html
// The version comes from GAME_VERSION in js/data.js. Usage: node tools/build.js
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const read = (f) => fs.readFileSync(path.join(root, f), 'utf8');

const version = (read('js/data.js').match(/GAME_VERSION = '([^']+)'/) || [])[1];
if (!version) throw new Error('GAME_VERSION not found in js/data.js');

let html = read('index.html');
html = html.replace(/<link rel="stylesheet" href="([^"]+)">/g, (_, f) => `<style>\n${read(f)}\n</style>`);
html = html.replace(/<script src="([^"]+)"><\/script>/g, (_, f) => `<script>\n${read(f).replace(/<\/script/gi, '<\\/script')}\n</script>`);
html = html.replace('<title>TapX</title>', `<title>TapX v${version}</title>`);

const dist = path.join(root, 'dist');
fs.mkdirSync(dist, { recursive: true });
for (const f of fs.readdirSync(dist)) if (/^TapX-v.*\.html$/.test(f)) fs.unlinkSync(path.join(dist, f));
const out = path.join(dist, `TapX-v${version}.html`);
fs.writeFileSync(out, html);
console.log(`Built ${path.relative(root, out)} (${(html.length / 1024).toFixed(0)} KB)`);
