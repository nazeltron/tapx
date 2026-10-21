'use strict';
// HTML panel UI: tabs, upgrade rows, shop, modals. Icons are small canvases
// painted by art.js.

const UI = { tab: 'hero', updaters: [], dirty: true };

const $ = (sel) => document.querySelector(sel);
function el(tag, cls, html) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html !== undefined) e.innerHTML = html;
  return e;
}
function iconCanvas(size, draw, cls = 'ico') {
  const c = document.createElement('canvas');
  const dpr = Math.min(window.devicePixelRatio || 1, 3);
  c.width = c.height = Math.round(size * dpr);
  c.style.width = c.style.height = size + 'px';
  c.className = cls;
  const x = c.getContext('2d');
  x.scale(dpr, dpr);
  draw(x, size);
  return c;
}
const coinHtml = () => '<i class="cur coin"></i>';
const gemHtml = () => '<i class="cur gem"></i>';
const relicHtml = () => '<i class="cur relic"></i>';

// Currency glyphs are rendered once into data URLs and used as CSS backgrounds.
function initCurrencyIcons() {
  const make = (fn) => iconCanvas(32, (x) => fn(x)).toDataURL();
  const style = el('style');
  style.textContent = `
    .cur.coin{background-image:url(${make((x) => drawCoin(x, 16, 16, 13))})}
    .cur.gem{background-image:url(${make((x) => drawGem(x, 16, 17, 13))})}
    .cur.relic{background-image:url(${make((x) => drawRelic(x, 16, 16, 12))})}`;
  document.head.appendChild(style);
}

function portrait(look, size) {
  return iconCanvas(size, (x, s) => {
    const g = x.createLinearGradient(0, 0, 0, s); g.addColorStop(0, '#5fb3d9'); g.addColorStop(1, '#2d6f9a');
    x.fillStyle = g; rrPath(x, 1, 1, s - 2, s - 2, s * 0.14); x.fill();
    x.save(); rrPath(x, 1, 1, s - 2, s - 2, s * 0.14); x.clip();
    drawChibi(x, Object.assign({}, look, { cape: null, aura: null }), s * 0.47, s * 1.32, s / 46, 0, 0);
    x.restore();
  });
}
function lockedPortrait(size) {
  return iconCanvas(size, (x, s) => {
    x.fillStyle = '#2a2d3d'; rrPath(x, 1, 1, s - 2, s - 2, s * 0.14); x.fill();
    x.fillStyle = '#4a4e66'; x.font = `900 ${s * 0.5}px ${FONT}`; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillText('?', s / 2, s / 2 + 2);
  });
}

// A standard upgrade row: icon | name/level/desc | action button.
function makeRow(icon, onClick) {
  const root = el('div', 'row');
  root.appendChild(icon);
  const info = el('div', 'info');
  const name = el('div', 'name'), lvl = el('div', 'lvl'), desc = el('div', 'desc');
  info.append(name, lvl, desc);
  const btn = el('button', 'buy');
  const top = el('span', 'top'), mid = el('span', 'mid'), bot = el('span', 'bot');
  btn.append(top, mid, bot);
  btn.addEventListener('click', () => { Sfx.init(); if (onClick() === false) { Sfx.error(); btn.classList.add('shake'); setTimeout(() => btn.classList.remove('shake'), 300); } else Sfx.buy(); refreshUI(); });
  root.append(info, btn);
  return { root, name, lvl, desc, btn, top, mid, bot, set(f, v) { if (this['_' + f] !== v) { this['_' + f] = v; this[f].innerHTML = v; } } };
}
function section(title) { return el('div', 'section', `<span>${title}</span>`); }

function multLabel() { const m = S.settings.mult; return m === 'max' ? 'BUY MAX' : `BUY x${m}`; }
function multButton() {
  const b = el('button', 'tool mult', multLabel());
  b.onclick = () => {
    const order = [1, 10, 100, 'max'];
    S.settings.mult = order[(order.indexOf(S.settings.mult) + 1) % order.length];
    b.textContent = multLabel(); refreshUI();
  };
  return b;
}
function toolButton(label, iconDraw, onClick, badgeFn) {
  const b = el('button', 'tool');
  if (iconDraw) b.appendChild(iconCanvas(30, iconDraw, 'tico'));
  b.appendChild(el('span', '', label));
  if (badgeFn) {
    const badge = el('i', 'badge');
    b.appendChild(badge);
    UI.updaters.push(() => { const n = badgeFn(); badge.textContent = n; badge.style.display = n ? '' : 'none'; });
  }
  b.onclick = onClick;
  return b;
}

// ---------------------------------------------------------------- tabs
const TABS = [
  { id: 'hero', color: '#e8643a', draw: (x, s) => drawIcon(x, 'tap', s) },
  { id: 'allies', color: '#3aa0b8', draw: (x, s) => { x.save(); rrPath(x, 1, 1, s - 2, s - 2, 6); x.clip(); drawChibi(x, Object.assign({}, HEROES[8].look, { cape: null }), s * 0.45, s * 1.3, s / 44, 0, 0); x.restore(); } },
  { id: 'gear', color: '#e3b72e', draw: (x, s) => drawGearIcon(x, GEAR.find((g) => g.id === 'a_dragon'), s, true) },
  { id: 'pets', color: '#58b04a', draw: (x, s) => drawPet(x, PETS[0], s / 2, s / 2 + 4, s / 70, 0.3) },
  { id: 'artifacts', color: '#8a5ad0', draw: (x, s) => drawRelic(x, s / 2, s / 2 + 2, s * 0.32) },
  { id: 'shop', color: '#2f8a86', draw: (x, s) => drawChest(x, CHESTS[2], s / 2, s / 2 + 4, s / 70) },
];
function buildTabs() {
  const nav = $('#tabs');
  nav.innerHTML = '';
  for (const t of TABS) {
    const b = el('button', 'tab');
    b.dataset.tab = t.id;
    b.style.setProperty('--tc', t.color);
    b.appendChild(iconCanvas(34, t.draw, 'tabico'));
    const dot = el('i', 'badge');
    b.appendChild(dot);
    b.onclick = () => { Sfx.init(); showTab(t.id); };
    nav.appendChild(b);
  }
}
function tabBadges() {
  for (const b of document.querySelectorAll('.tab')) {
    const dot = b.querySelector('.badge');
    let n = '';
    if (b.dataset.tab === 'shop' && freeChestReady()) n = '!';
    if (b.dataset.tab === 'artifacts' && ARTIFACTS.some((a) => (!a.max || art(a.id) < a.max) && S.relics >= artifactCost(a, art(a.id)))) n = '!';
    dot.textContent = n; dot.style.display = n ? '' : 'none';
    b.classList.toggle('active', b.dataset.tab === UI.tab);
  }
}

function showTab(id) {
  UI.tab = id;
  UI.updaters = [];
  const body = $('#panelBody'), tools = $('#panelTools');
  body.innerHTML = ''; tools.innerHTML = '';
  body.scrollTop = 0;
  ({ hero: renderHero, allies: renderAllies, gear: renderGear, pets: renderPets, artifacts: renderArtifacts, shop: renderShop })[id](body, tools);
  refreshUI();
}

function setHead(statFn, curFn) {
  UI.updaters.push(() => { $('#headStat').innerHTML = statFn(); $('#headCur').innerHTML = curFn(); });
}

// ---------------------------------------------------------------- hero tab
function renderHero(body, tools) {
  setHead(() => `<b>${fmt(tapDamage())}</b> Tap Damage`, () => `${coinHtml()} <b>${fmt(S.gold)}</b>`);
  tools.append(
    toolButton('Achievements', (x, s) => drawIcon(x, 'idol', s), openAchievements),
    toolButton('Stats', (x, s) => drawIcon(x, 'tome', s), openSettings),
    multButton(),
  );
  const heroRow = makeRow(iconCanvas(56, (x, s) => {
    const g = x.createLinearGradient(0, 0, 0, s); g.addColorStop(0, '#e8955a'); g.addColorStop(1, '#a2412a');
    x.fillStyle = g; rrPath(x, 1, 1, s - 2, s - 2, 8); x.fill(); x.save(); rrPath(x, 1, 1, s - 2, s - 2, 8); x.clip();
    drawChibi(x, Object.assign(heroLook(), { cape: null, aura: null }), s * 0.47, s * 1.32, s / 46, 0, 0); x.restore();
  }), buyTap);
  heroRow.set('name', 'Sword Master');
  body.appendChild(heroRow.root);
  UI.updaters.push(() => {
    const c0 = tapCost(S.tapLevel), n = buyAmount(c0, 1.074), cost = bulkCost(c0, 1.074, n);
    heroRow.set('lvl', `Lv. <em>${S.tapLevel}</em>`);
    heroRow.set('desc', `Tap Damage: ${fmt(tapDamage(false))} · Crit ${Math.round(critChance() * 100)}% x${fmt(critMult())}`);
    heroRow.set('top', `${coinHtml()}${fmt(cost)}`);
    heroRow.set('mid', n > 1 ? `Level Up x${n}` : 'Level Up');
    heroRow.set('bot', `+${fmt((tapBase(S.tapLevel + n) - tapBase(S.tapLevel)) * (tapDamage(false) / Math.max(1e-9, tapBase(S.tapLevel) + 0.03 * rawHeroDps() * allyMult())))} dmg`);
    heroRow.btn.disabled = S.gold < cost;
  });

  body.appendChild(section('Prestige'));
  const pr = makeRow(iconCanvas(56, (x, s) => drawIcon(x, 'prestige', s)), () => { if (!canPrestige()) return false; confirmPrestige(); });
  pr.set('name', 'Prestige'); pr.set('lvl', '');
  body.appendChild(pr.root);
  UI.updaters.push(() => {
    const ok = canPrestige();
    pr.set('desc', ok ? `Restart from stage 1 and earn <b>${fmt(relicsFor(S.maxStage))}</b> relics for artifacts.` : 'Restart your adventure in exchange for precious relics.');
    pr.set('top', ok ? `${relicHtml()}+${fmt(relicsFor(S.maxStage))}` : '');
    pr.set('mid', ok ? 'Prestige' : `Reach Stage ${PRESTIGE_STAGE}`);
    pr.set('bot', '');
    pr.btn.classList.toggle('gold', ok);
    pr.btn.disabled = !ok;
  });

  body.appendChild(section('Spells'));
  for (const sk of SKILLS) {
    const row = makeRow(iconCanvas(56, (x, s) => drawIcon(x, sk.id, s)), () => buySkill(sk.id));
    row.set('name', sk.name);
    body.appendChild(row.root);
    UI.updaters.push(() => {
      const l = skillLvl(sk.id);
      row.set('lvl', `Lv. <em>${l}</em> <span class="mana">${sk.mana} Mana</span>`);
      row.set('desc', sk.desc(Math.max(1, l)) + (sk.timed ? ` for ${spellDuration()}s` : ''));
      if (l >= SKILL_MAX) { row.set('top', ''); row.set('mid', 'MAX'); row.set('bot', ''); row.btn.disabled = true; return; }
      const cost = skillCost(sk, l);
      row.set('top', `${coinHtml()}${fmt(cost)}`);
      if (S.tapLevel < sk.req) { row.set('mid', `Unlock at Lv. ${sk.req}`); row.set('bot', ''); row.btn.disabled = true; return; }
      row.set('mid', l ? 'Upgrade' : 'Learn');
      row.set('bot', l ? `Lv. ${l + 1}` : '');
      row.btn.disabled = S.gold < cost;
    });
  }

  body.appendChild(section('Perks'));
  for (const p of PERKS) {
    const row = makeRow(iconCanvas(56, (x, s) => drawIcon(x, p.id, s)), () => buyPerk(p.id));
    row.set('name', p.name); row.set('lvl', ''); row.set('desc', p.desc);
    row.btn.classList.add('green');
    body.appendChild(row.root);
    UI.updaters.push(() => {
      const active = p.dur && buffOn(p.id);
      row.set('top', `${gemHtml()}${p.gems}`);
      row.set('mid', active ? fmtTime(S.buffs[p.id]) : 'Use');
      row.set('bot', active ? 'active' : '');
      row.btn.disabled = active || S.gems < p.gems;
    });
  }
}

// ---------------------------------------------------------------- allies tab
function renderAllies(body, tools) {
  setHead(() => `<b>${fmt(heroDps())}</b> Ally DPS`, () => `${coinHtml()} <b>${fmt(S.gold)}</b>`);
  const note = el('div', 'tool note', 'Allies attack for you — even while you are away!');
  tools.append(note, multButton());
  HEROES.forEach((h, i) => {
    const rowBox = el('div');
    body.appendChild(rowBox);
    let shownLocked = null;
    let row = null;
    const build = () => {
      const unlocked = heroUnlocked(i);
      if (shownLocked === !unlocked && row) return;
      shownLocked = !unlocked;
      rowBox.innerHTML = '';
      row = makeRow(unlocked ? portrait(h.look, 56) : lockedPortrait(56), () => buyHero(h.id));
      rowBox.appendChild(row.root);
    };
    UI.updaters.push(() => {
      build();
      const unlocked = heroUnlocked(i), l = heroLvl(h.id);
      rowBox.style.display = !unlocked && i > 0 && !heroUnlocked(i - 1) ? 'none' : '';
      row.set('name', unlocked ? h.name : '???');
      const share = heroDps() > 0 ? Math.round((heroBaseDps(h, l) / Math.max(1e-9, rawHeroDps())) * 100) : 0;
      const nextMs = HERO_MILESTONES.find(([ml]) => ml > l);
      row.set('lvl', `Lv. <em>${l}</em>` + (l ? ` <span class="dim">DPS: ${fmt(heroBaseDps(h, l) * allyMult())} (${share}%)</span>` : ''));
      row.set('desc', unlocked ? (nextMs ? `x${nextMs[1]} damage at Lv. ${nextMs[0]}` : 'Fully mastered!') : 'Hire the previous ally to reveal');
      const c0 = heroCost(h, l), n = buyAmount(c0, 1.075), cost = bulkCost(c0, 1.075, n);
      row.set('top', `${coinHtml()}${fmt(cost)}`);
      row.set('mid', (l ? 'Level Up' : 'Hire') + (n > 1 ? ` x${n}` : ''));
      row.set('bot', `+${fmt((heroBaseDps(h, l + n) - heroBaseDps(h, l)) * allyMult())} DPS`);
      row.btn.disabled = !unlocked || S.gold < cost;
    });
  });
}

// ---------------------------------------------------------------- gear tab
function renderGear(body, tools) {
  setHead(() => `<b>${Object.keys(S.gear).length} / ${GEAR.length}</b> Equipment`, () => `${gemHtml()} <b>${fmt(S.gems)}</b>`);
  tools.appendChild(el('div', 'tool note', 'Open chests in the Shop to find equipment. Duplicates level it up.'));
  const eq = el('div', 'equipped');
  body.appendChild(eq);
  const detail = el('div', 'gear-detail', 'Tap a piece of equipment to equip it.');
  body.appendChild(detail);
  const describe = (g) => {
    const slot = GEAR_SLOTS.find((s) => s.id === g.slot), l = S.gear[g.id] || 0;
    return `<b style="color:${RARITIES[g.r].color}">${g.name}</b> · ${RARITIES[g.r].name} ${slot.name}${l ? ` · Lv. ${l}` : ''}<br>` +
      (l ? `+${Math.round(gearValue(g.id) * 100)}% ${slot.label}` : `<span class="dim">Not found yet</span>`);
  };
  const drawEquipped = () => {
    eq.innerHTML = '';
    for (const slot of GEAR_SLOTS) {
      const id = S.equipped[slot.id], g = id && gearDef(id);
      const box = el('div', 'eqslot');
      box.appendChild(g ? iconCanvas(60, (x, s) => drawGearIcon(x, g, s, true)) : iconCanvas(60, (x, s) => { x.fillStyle = '#2a2d3d'; rrPath(x, 1, 1, s - 2, s - 2, 8); x.fill(); }));
      box.appendChild(el('div', 'eqlabel', g ? `+${Math.round(gearValue(id) * 100)}% ${slot.label}` : slot.name));
      eq.appendChild(box);
    }
  };
  drawEquipped();
  for (const slot of GEAR_SLOTS) {
    body.appendChild(section(slot.name + 's'));
    const grid = el('div', 'grid');
    for (const g of GEAR.filter((x) => x.slot === slot.id)) {
      const cell = el('button', 'cell');
      const paint = () => {
        cell.innerHTML = '';
        cell.appendChild(iconCanvas(58, (x, s) => drawGearIcon(x, g, s, !!S.gear[g.id])));
        if (S.gear[g.id]) cell.appendChild(el('i', 'lv', 'Lv' + S.gear[g.id]));
        cell.classList.toggle('on', S.equipped[g.slot] === g.id);
      };
      paint();
      cell.onclick = () => { detail.innerHTML = describe(g); if (S.gear[g.id]) { equip(g.id); Sfx.buy(); showTab('gear'); detail.innerHTML = describe(g); } };
      grid.appendChild(cell);
    }
    body.appendChild(grid);
  }
}

// ---------------------------------------------------------------- pets tab
function renderPets(body, tools) {
  setHead(() => S.activePet ? `<b>${petDef(S.activePet).name}</b> is fighting with you` : 'No pet yet', () => `${gemHtml()} <b>${fmt(S.gems)}</b>`);
  tools.appendChild(el('div', 'tool note', `Your active pet charges up as you tap and strikes every ${PET_CHARGE} taps. Get eggs from Daily Deals in the Shop.`));
  for (const p of PETS) {
    const icon = iconCanvas(56, (x, s) => { const g = x.createLinearGradient(0, 0, 0, s); g.addColorStop(0, '#4aa3d0'); g.addColorStop(1, '#2a5f8a'); x.fillStyle = g; rrPath(x, 1, 1, s - 2, s - 2, 8); x.fill(); drawPet(x, p, s / 2, s / 2 + 4, s / 72, 0.2); });
    const row = makeRow(icon, () => { if (!petLevel(p.id)) return false; setActivePet(p.id); });
    row.set('name', p.name);
    body.appendChild(row.root);
    UI.updaters.push(() => {
      const l = petLevel(p.id), eggs = (S.pets[p.id] && S.pets[p.id].eggs) || 0;
      if (!l) icon.style.filter = 'grayscale(1) brightness(.6)'; else icon.style.filter = '';
      row.set('lvl', `Lv. <em>${l}</em>` + (l ? ` <span class="dim">eggs ${eggs}/${petEggsForLevel(l)}</span>` : ''));
      row.set('desc', `+${Math.round(p.per * Math.max(1, l) * 100)}% ${p.label} · attack ${fmt(5 + 3 * Math.max(1, l))}x tap damage`);
      const active = S.activePet === p.id;
      row.set('top', l ? '' : 'Locked');
      row.set('mid', active ? 'Active' : l ? 'Equip' : 'Find eggs');
      row.set('bot', '');
      row.btn.disabled = active || !l;
    });
  }
}

// ---------------------------------------------------------------- artifacts tab
function renderArtifacts(body, tools) {
  setHead(() => `Max stage <b>${S.maxStage}</b>`, () => `${relicHtml()} <b>${fmt(S.relics)}</b>`);
  tools.appendChild(el('div', 'tool note', 'Spend relics to unlock and upgrade powerful artifacts. Relics come from prestiging.'));
  const pr = makeRow(iconCanvas(56, (x, s) => drawIcon(x, 'prestige', s)), () => { if (!canPrestige()) return false; confirmPrestige(); });
  pr.set('name', 'Prestige'); pr.set('lvl', '');
  body.appendChild(pr.root);
  UI.updaters.push(() => {
    const ok = canPrestige();
    pr.set('desc', `Relics earned so far: ${fmt(S.stats.relicsEarned)} · prestiges: ${S.stats.prestiges}`);
    pr.set('top', ok ? `${relicHtml()}+${fmt(relicsFor(S.maxStage))}` : '');
    pr.set('mid', ok ? 'Prestige' : `Reach Stage ${PRESTIGE_STAGE}`);
    pr.btn.classList.toggle('gold', ok);
    pr.btn.disabled = !ok;
  });
  body.appendChild(section('Artifacts'));
  for (const a of ARTIFACTS) {
    const row = makeRow(iconCanvas(56, (x, s) => drawIcon(x, a.id, s)), () => buyArtifact(a.id));
    row.set('name', a.name);
    body.appendChild(row.root);
    UI.updaters.push(() => {
      const l = art(a.id), maxed = a.max && l >= a.max;
      row.set('lvl', `Lv. <em>${l}</em>${a.max ? ` <span class="dim">/ ${a.max}</span>` : ''}`);
      row.set('desc', l ? a.desc(l) : `<span class="dim">${a.desc(1)}</span>`);
      row.set('top', maxed ? '' : `${relicHtml()}${fmt(artifactCost(a, l))}`);
      row.set('mid', maxed ? 'MAX' : l ? 'Upgrade' : 'Unlock');
      row.set('bot', '');
      row.btn.disabled = maxed || S.relics < artifactCost(a, l);
    });
  }
}

// ---------------------------------------------------------------- shop tab
function renderShop(body, tools) {
  setHead(() => '<b>Shop</b>', () => `${gemHtml()} <b>${fmt(S.gems)}</b>`);
  tools.appendChild(el('div', 'tool note', 'Earn gems from bosses, achievements and wisps. Everything here is free to play.'));
  body.appendChild(section('Free Chest'));
  const free = el('div', 'cards one');
  const fc = el('button', 'card');
  fc.appendChild(el('div', 'ctitle', 'Free Chest'));
  fc.appendChild(iconCanvas(96, (x, s) => drawChest(x, { body: '#8a5a2a', band: '#c0c0c8' }, s / 2, s / 2 + 8, s / 64)));
  const fcLabel = el('div', 'cprice');
  fc.appendChild(fcLabel);
  fc.onclick = () => { const items = openFreeChest(); if (items) showChestResult('Free Chest', items, '+5 gems'); else Sfx.error(); };
  free.appendChild(fc); body.appendChild(free);
  UI.updaters.push(() => {
    const ready = freeChestReady();
    fcLabel.innerHTML = ready ? 'Collect!' : `Ready in ${fmtTime((S.freeChestAt - Date.now()) / 1000)}`;
    fc.disabled = !ready;
  });

  body.appendChild(section('Chests'));
  const chests = el('div', 'cards');
  for (const c of CHESTS) {
    const card = el('button', 'card');
    card.appendChild(el('div', 'ctitle', c.name));
    card.appendChild(iconCanvas(96, (x, s) => drawChest(x, c, s / 2, s / 2 + 8, s / 64)));
    card.appendChild(el('div', 'csub', `${c.items} items`));
    card.appendChild(el('div', 'cprice', `${gemHtml()} ${c.gems}`));
    card.onclick = () => { const items = openChest(c.id); if (items) showChestResult(c.name, items); else Sfx.error(); };
    chests.appendChild(card);
    UI.updaters.push(() => { card.disabled = S.gems < c.gems; });
  }
  body.appendChild(chests);

  body.appendChild(section('Daily Deals'));
  const timer = el('div', 'deal-timer');
  body.appendChild(timer);
  const deals = el('div', 'cards');
  body.appendChild(deals);
  let block = -1;
  UI.updaters.push(() => {
    const d = currentDeals();
    timer.textContent = `New deals in ${fmtTime(DEAL_SECS - (Date.now() / 1000) % DEAL_SECS)}`;
    if (d.block !== block) {
      block = d.block; deals.innerHTML = '';
      d.deals.forEach((deal, i) => {
        const p = petDef(deal.pet);
        const card = el('button', 'card');
        card.appendChild(el('div', 'ctitle', p.name));
        card.appendChild(iconCanvas(96, (x, s) => drawPet(x, p, s / 2, s / 2 + 6, s / 64, 0.25)));
        card.appendChild(el('div', 'csub', `x ${deal.eggs} eggs`));
        const price = el('div', 'cprice');
        card.appendChild(price);
        card.onclick = () => { if (buyDeal(i)) { Sfx.pet(); toast(`${p.name} +${deal.eggs} eggs!`); } else Sfx.error(); refreshUI(); };
        deals.appendChild(card);
        card._update = () => { price.innerHTML = deal.bought ? 'Sold out' : `${gemHtml()} ${deal.gems}`; card.disabled = deal.bought || S.gems < deal.gems; };
      });
    }
    for (const c of deals.children) c._update();
  });
}

// ---------------------------------------------------------------- modals
function openModal(title, contentEl, buttons = [{ label: 'Close' }]) {
  const m = $('#modal'), box = $('#modalBox');
  box.innerHTML = '';
  const head = el('div', 'mhead');
  head.appendChild(el('h2', '', title));
  const x = el('button', 'mclose', '✕');
  x.onclick = closeModal;
  head.appendChild(x);
  box.appendChild(head);
  const content = el('div', 'mbody');
  if (typeof contentEl === 'string') content.innerHTML = contentEl; else content.appendChild(contentEl);
  box.appendChild(content);
  const foot = el('div', 'mfoot');
  for (const b of buttons) {
    const btn = el('button', 'mbtn ' + (b.cls || ''), b.label);
    btn.onclick = () => { if (b.fn) b.fn(); if (!b.keep) closeModal(); };
    foot.appendChild(btn);
  }
  box.appendChild(foot);
  m.hidden = false;
}
function closeModal() { $('#modal').hidden = true; }

function confirmPrestige() {
  const n = relicsFor(S.maxStage);
  openModal('Prestige', `<p>Return to stage 1 and lose your gold, tap level, allies and spells.</p>
    <p>You keep gems, artifacts, equipment, pets and achievements, and receive</p>
    <p class="big">${relicHtml()} ${fmt(n)} relics</p>`, [
    { label: 'Cancel' },
    { label: 'Prestige!', cls: 'gold', fn: () => { const g = prestige(); Sfx.levelUp(); toast(`Reborn! +${fmt(g)} relics`); showTab('artifacts'); } },
  ]);
}

function showChestResult(title, items, extra) {
  Sfx.chest();
  const wrap = el('div', 'chest-result');
  for (const it of items) {
    const box = el('div', 'loot');
    box.appendChild(iconCanvas(64, (x, s) => drawGearIcon(x, it.g, s, true)));
    box.appendChild(el('div', '', `<b style="color:${RARITIES[it.g.r].color}">${it.g.name}</b>`));
    box.appendChild(el('div', 'dim', it.isNew ? 'NEW!' : `Lv. ${S.gear[it.g.id]}`));
    wrap.appendChild(box);
  }
  if (extra) wrap.appendChild(el('div', 'loot-extra', extra));
  openModal(title, wrap, [{ label: 'Awesome!' }]);
  refreshUI();
}

function openAchievements() {
  const list = el('div', 'ach-list');
  for (const g of ACH_GROUPS) {
    const cur = g.stat(S);
    const nextI = g.tiers.findIndex((t, i) => !S.achievements[`${g.key}_${i}`]);
    const done = nextI === -1;
    const target = done ? g.tiers[g.tiers.length - 1] : g.tiers[nextI];
    const item = el('div', 'ach');
    const stars = g.tiers.map((t, i) => `<i class="${S.achievements[`${g.key}_${i}`] ? 'on' : ''}">★</i>`).join('');
    item.innerHTML = `<div class="aname">${g.name} <span class="stars">${stars}</span></div>
      <div class="adesc">${g.fmt(target)}${done ? ' ✓' : ` · reward ${gemHtml()} ${g.gems[nextI]}`}</div>
      <div class="bar"><i style="width:${Math.min(100, (cur / target) * 100)}%"></i><span>${fmt(Math.min(cur, target))} / ${fmt(target)}</span></div>`;
    list.appendChild(item);
  }
  openModal('Achievements', list);
}

function openSettings() {
  const st = S.stats;
  const box = el('div', 'settings');
  box.innerHTML = `
    <div class="stats">
      <div><span>Highest stage</span><b>${st.bestStage}</b></div>
      <div><span>Monsters defeated</span><b>${fmt(st.kills)}</b></div>
      <div><span>Bosses defeated</span><b>${fmt(st.bosses)}</b></div>
      <div><span>Total taps</span><b>${fmt(st.taps)}</b></div>
      <div><span>Critical hits</span><b>${fmt(st.crits)}</b></div>
      <div><span>Gold earned</span><b>${fmt(st.goldEarned)}</b></div>
      <div><span>Spells cast</span><b>${fmt(st.casts)}</b></div>
      <div><span>Prestiges</span><b>${st.prestiges}</b></div>
      <div><span>Play time</span><b>${fmtTime(st.playTime)}</b></div>
    </div>`;
  const opts = el('div', 'opts');
  const sound = el('button', 'mbtn', `Sound: ${S.settings.sound ? 'ON' : 'OFF'}`);
  sound.onclick = () => { S.settings.sound = !S.settings.sound; Sfx.on = S.settings.sound; sound.textContent = `Sound: ${S.settings.sound ? 'ON' : 'OFF'}`; };
  const exp = el('button', 'mbtn', 'Export save');
  exp.onclick = () => { ta.value = btoa(unescape(encodeURIComponent(serialize()))); ta.hidden = false; ta.select(); };
  const imp = el('button', 'mbtn', 'Import save');
  imp.onclick = () => {
    if (ta.hidden || !ta.value.trim()) { ta.hidden = false; ta.value = ''; ta.placeholder = 'Paste a save here, then press Import again'; return; }
    try { deserialize(decodeURIComponent(escape(atob(ta.value.trim())))); saveGame(); closeModal(); toast('Save imported!'); showTab(UI.tab); } catch (e) { toast('That save code is not valid'); }
  };
  const reset = el('button', 'mbtn danger', 'Reset game');
  reset.onclick = () => openModal('Reset game?', '<p>This wipes <b>all</b> progress, including relics, gems and equipment. This cannot be undone.</p>', [
    { label: 'Cancel' }, { label: 'Wipe everything', cls: 'danger', fn: () => { resetGame(); showTab('hero'); toast('Progress reset'); } }]);
  const ta = el('textarea', 'savebox'); ta.hidden = true; ta.rows = 3;
  opts.append(sound, exp, imp, reset);
  box.append(opts, ta);
  box.appendChild(el('div', 'version', `TapX v${GAME_VERSION}`));
  openModal('Settings & Stats', box);
}

// ---------------------------------------------------------------- toasts
function toast(msg, kind) {
  const t = el('div', 'toast ' + (kind || ''), msg);
  const box = $('#toasts');
  box.appendChild(t);
  while (box.children.length > 3) box.firstChild.remove();
  setTimeout(() => t.classList.add('out'), 2600);
  setTimeout(() => t.remove(), 3100);
}

function refreshUI() {
  for (const u of UI.updaters) u();
  tabBadges();
}
