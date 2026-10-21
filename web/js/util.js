'use strict';
// Shared helpers: math, seeded RNG, number formatting, synthesized sound.

const TAU = Math.PI * 2;
const FONT = '"Trebuchet MS", "Segoe UI", system-ui, sans-serif';
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const lerp = (a, b, t) => a + (b - a) * t;
const rand = (a, b) => a + Math.random() * (b - a);

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const SUFFIXES = ['', 'K', 'M', 'B', 'T', 'Qa', 'Qi', 'Sx', 'Sp', 'Oc', 'No', 'Dc'];
function fmt(n) {
  if (!isFinite(n)) return '∞';
  if (n < 0) return '-' + fmt(-n);
  if (n < 1000) {
    if (n < 10 && n % 1 !== 0) return (Math.floor(n * 10) / 10).toString();
    return Math.floor(n).toString();
  }
  const e = Math.floor(Math.log10(n) / 3);
  let v = n / Math.pow(1000, e);
  let s;
  if (e < SUFFIXES.length) s = SUFFIXES[e];
  else {
    const i = e - SUFFIXES.length;
    s = String.fromCharCode(97 + (Math.floor(i / 26) % 26)) + String.fromCharCode(97 + (i % 26));
  }
  const d = v >= 100 ? 0 : v >= 10 ? 1 : 2;
  const p = Math.pow(10, d);
  v = Math.floor(v * p) / p;
  return v.toFixed(d) + s;
}

function fmtTime(s) {
  s = Math.max(0, Math.floor(s));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  if (h > 0) return `${h}h ${String(m).padStart(2, '0')}m`;
  if (m > 0) return `${m}:${String(sec).padStart(2, '0')}`;
  return `${sec}s`;
}

// Tiny Web Audio synth so the game needs no sound files either.
const Sfx = {
  ctx: null, on: true, last: {},
  init() {
    if (this.ctx) return;
    try { this.ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { this.ctx = null; }
  },
  tone(freq, dur, type = 'square', vol = 0.04, slide = 0, delay = 0) {
    if (!this.on || !this.ctx) return;
    const t = this.ctx.currentTime + delay;
    const o = this.ctx.createOscillator(), g = this.ctx.createGain();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(20, freq * slide), t + dur);
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g).connect(this.ctx.destination);
    o.start(t); o.stop(t + dur + 0.02);
  },
  throttle(key, ms) {
    const now = performance.now();
    if (this.last[key] && now - this.last[key] < ms) return false;
    this.last[key] = now; return true;
  },
  hit() { if (this.throttle('hit', 45)) this.tone(160 + Math.random() * 80, 0.07, 'square', 0.03, 0.5); },
  crit() { if (this.throttle('crit', 60)) { this.tone(520, 0.09, 'sawtooth', 0.04, 0.4); this.tone(260, 0.15, 'square', 0.03, 0.5, 0.02); } },
  coin() { if (this.throttle('coin', 70)) { this.tone(988, 0.05, 'square', 0.025); this.tone(1319, 0.09, 'square', 0.025, 0, 0.05); } },
  kill() { this.tone(220, 0.18, 'triangle', 0.06, 0.4); },
  boss() { this.tone(110, 0.4, 'sawtooth', 0.05, 0.6); this.tone(82, 0.5, 'square', 0.03, 0.7, 0.1); },
  levelUp() { [523, 659, 784, 1047].forEach((f, i) => this.tone(f, 0.12, 'square', 0.03, 0, i * 0.06)); },
  buy() { if (this.throttle('buy', 40)) { this.tone(660, 0.06, 'triangle', 0.05); this.tone(990, 0.08, 'triangle', 0.04, 0, 0.04); } },
  error() { this.tone(140, 0.12, 'square', 0.03, 0.8); },
  thunder() { this.tone(90, 0.6, 'sawtooth', 0.08, 0.3); this.tone(1800, 0.1, 'square', 0.03, 0.2); },
  spell() { this.tone(440, 0.3, 'triangle', 0.05, 2); },
  chest() { [392, 523, 659, 784, 1047, 1319].forEach((f, i) => this.tone(f, 0.15, 'triangle', 0.045, 0, i * 0.07)); },
  pet() { this.tone(700, 0.12, 'sine', 0.06, 1.8); },
};
