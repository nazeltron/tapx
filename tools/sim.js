// Headless balance check: plays the game with a simple greedy strategy.
// Usage: node tools/sim.js [hours] [tapsPerSecond]
const fs = require('fs'), path = require('path'), vm = require('vm');
const ctx = { console, Math, Date, JSON, performance: { now: () => 0 }, localStorage: null };
vm.createContext(ctx);
for (const f of ['util.js', 'data.js', 'game.js']) {
  vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', f), 'utf8').replace(/^const /gm, 'var ').replace(/^let /gm, 'var '), ctx);
}
const hours = +(process.argv[2] || 3), tps = +(process.argv[3] || 5);
vm.runInContext(`
  var dt = 0.1, t = 0, tapAcc = 0, log = [], marks = {}, prestiges = 0;
  S.settings.mult = 1;
  function shop() {
    for (let k = 0; k < 50; k++) {
      // best value per gold among tap upgrade and heroes
      let best = null, bestV = 0;
      const tc = tapCost(S.tapLevel), tv = (tapBase(S.tapLevel + 1) - tapBase(S.tapLevel)) * ${tps} / tc;
      if (S.gold >= tc) { best = 'tap'; bestV = tv; }
      HEROES.forEach((h, i) => {
        if (!heroUnlocked(i)) return;
        const l = heroLvl(h.id), c = heroCost(h, l);
        if (S.gold < c) return;
        const v = (heroBaseDps(h, l + 1) - heroBaseDps(h, l)) / c;
        if (v > bestV) { bestV = v; best = h.id; }
      });
      for (const sk of SKILLS) if (S.tapLevel >= sk.req && skillLvl(sk.id) < 3 && S.gold >= skillCost(sk, skillLvl(sk.id)) * 3) { buySkill(sk.id); }
      if (!best) return;
      if (best === 'tap') buyTap(); else buyHero(best);
    }
  }
  let failT = 0;
  Hooks.onBossFail = () => { failT = 45; };
  while (t < ${hours} * 3600) {
    t += dt;
    tapAcc += ${tps} * dt;
    while (tapAcc >= 1) { tapAcc--; tap(false); }
    update(dt);
    for (const sk of SKILLS) castSkill(sk.id);
    if (Math.floor(t * 10) % 10 === 0) shop();
    if (failT > 0) { failT -= dt; if (failT <= 0) fightBoss(); }
    for (const m of [10, 25, 50, 75, 100, 150, 200]) if (S.stage >= m && !marks[m]) { marks[m] = 1; log.push('stage ' + m + ' at ' + (t / 60).toFixed(1) + ' min, tapLv ' + S.tapLevel + ', dps ' + fmt(heroDps(false)) + ', relics now ' + relicsFor(S.maxStage)); }
    if (S.stage >= 60 && S.farming && prestiges < 3) { const g = prestige(); prestiges++; marks = {}; log.push('PRESTIGE +' + g + ' at ' + (t/60).toFixed(1)+' min'); for (const a of ARTIFACTS) while (buyArtifact(a.id)); }
  }
  log.push('final stage ' + S.stage + ' max ' + S.maxStage + ' gold ' + fmt(S.gold));
`, ctx);
console.log(ctx.log.join('\n'));
