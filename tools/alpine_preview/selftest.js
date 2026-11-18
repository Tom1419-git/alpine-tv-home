/* Auto-test de l'interface (index.html?selftest, servie en http). Resultat dans <pre id="selftest"> ; ligne finale TERMINE OK ou ECHEC. */
(async function () {
  var A = window.AlpineApp, D = window.ALPINE_DATA, G = window.AlpineGrade, out = [], $ = function (s) { return document.querySelector(s); };
  function ok(name, cond, detail) { out.push((cond ? 'PASS ' : 'FAIL ') + name + (detail ? ' : ' + detail : '')); }
  var wait = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  function painted(c) {
    var x = c.getContext('2d'), d = x.getImageData(0, 0, c.width, c.height).data, n = 0;
    for (var i = 0; i < d.length; i += 4 * 997) if (d[i] + d[i + 1] + d[i + 2] > 30) n++;
    return n > 20;
  }
  try {
    await wait(1500);
    var cards = document.querySelectorAll('#grid .card');
    ok('grille par defaut : une carte par combinaison', cards.length === A.combos().length && cards.length === 1, cards.length + ' cartes');
    ok('les vignettes sont peintes', Array.prototype.every.call(cards, function (c) { return painted(c.querySelector('canvas')); }));

    A.sel.phase = A.PHASES.map(function (p) { return p[0]; }); A.sel.weather = ['clair', 'pluie', 'neige']; A.sel.state = ['repos', 'focus'];
    A.drawGrid(); await wait(3000);
    ok('filtres : 8 phases x 3 meteos x 2 etats', A.combos().length === 48 && document.querySelectorAll('#grid .card').length === 48, A.combos().length + ' combinaisons');
    var all = document.querySelectorAll('#grid .card canvas'), unpainted = 0;
    for (var q = 0; q < all.length; q++) { all[q].scrollIntoView(); await wait(120); }
    await wait(2500);
    all.forEach(function (c) { if (!painted(c)) unpainted++; });
    ok('48 vignettes peintes', unpainted === 0, unpainted + ' vides');
    var ids = {}; document.querySelectorAll('#grid .card').forEach(function (c) { ids[c.dataset.id] = 1; });
    ok('cartes toutes distinctes', Object.keys(ids).length === 48);
    A.sel.phase = ['nuit']; A.sel.weather = []; A.drawGrid();
    ok('filtre vide : message, pas d\'erreur', /Aucune combinaison/.test($('#grid').textContent));
    A.sel.weather = ['clair', 'neige', 'orage', 'brouillard', 'averses'];
    A.sel.phase = ['lever', 'nuit']; A.sel.state = ['repos', 'focus2', 'musique']; A.drawGrid(); await wait(300);
    ok('etats et meteos supplementaires', A.combos().length === 30 && document.querySelectorAll('#grid .card').length === 30);

    var missing = [], keys = Object.keys(D.pack.files).map(function (k) { return 'pack/' + k; }).concat([0, 1, 2, 3].map(function (n) { return 'assets/cards/card' + n + '.png'; }));
    for (var i = 0; i < keys.length; i++) { var r = await fetch(keys[i], { method: 'HEAD' }); if (!r.ok) missing.push(keys[i]); }
    ok('les ' + keys.length + ' fichiers references repondent', missing.length === 0, missing.join(', '));

    // comparaison UHD
    A.sel.phase = ['nuit', 'coucher']; A.sel.weather = ['clair']; A.sel.state = ['repos']; A.drawGrid(); await wait(400);
    document.querySelectorAll('#grid .card input[type=checkbox]').forEach(function (c) { c.click(); });
    ok('deux versions cochees pour comparer', A.compare().length === 2);
    A.tab('compare'); $('#cmp-res').value = 'uhd'; $('#cmp-zoom').value = 100; A.drawCompare(); await wait(4000);
    var pc = document.querySelectorAll('#panes canvas');
    ok('comparaison : 2 volets en 3840x2160', pc.length === 2 && pc[0].width === 3840 && pc[0].height === 2160, pc.length ? pc[0].width + 'x' + pc[0].height : 'aucun');
    ok('comparaison : pixels peints', pc.length === 2 && painted(pc[0]) && painted(pc[1]));
    var dpr = window.devicePixelRatio || 1;
    ok('zoom 100 % : un pixel image pour un pixel ecran', pc.length && Math.abs(parseFloat(pc[0].style.width) - 3840 / dpr) < 1, pc[0] && pc[0].style.width);
    ok('legende honnete par volet', /Aper.u calcul/.test($('#panes .foot').textContent) && /3840/.test($('#panes .foot').textContent));
    var a = pc[0].getContext('2d').getImageData(0, 300, 3840, 40).data, b = pc[1].getContext('2d').getImageData(0, 300, 3840, 40).data, diff = 0;
    for (var j = 0; j < a.length; j += 4 * 31) if (Math.abs(a[j] - b[j]) > 20) diff++;
    ok('nuit et coucher differents', diff > 30, diff + ' points differents');
    $('#cmp-mode').value = 'overlay'; A.drawCompare(); await wait(4000);
    ok('mode superpose : deux canvas empiles', document.querySelectorAll('#panes .stack canvas').length === 2);
    $('#cmp-mode').value = 'side';

    // fichiers 1080p
    $('#cmp-res').value = 'hd'; A.drawCompare(); await wait(2500);
    pc = document.querySelectorAll('#panes canvas');
    ok('comparaison en 1920x1080', pc.length === 2 && pc[0].width === 1920 && pc[0].height === 1080);

    // detail a 100 %
    A.showDetail(A.decode(A.compare()[0])); await wait(4000);
    var dt = [0, 1, 2].map(function (n) { return $('#dt-' + n); });
    ok('detail a 100 % : trois recadrages peints sans mise a l\'echelle', !$('#detail').hidden && dt.every(painted));
    $('#detail-close').click();

    // plein ecran TV
    A.openTv(A.compare().slice(), 0); await wait(300); $('#tv-res').click(); await wait(3000);
    var tc = $('#tv-canvas');
    ok('plein ecran TV en UHD : 3840x2160', tc.width === 3840 && tc.height === 2160, tc.width + 'x' + tc.height);
    $('#tv-close').click();

    // favoris et resume
    A.toggleFav(A.decode(A.compare()[0]));
    var sm = A.summary();
    ok('resume des choix', /Mes choix/.test(sm) && /Nuit/.test(sm), sm.split('\n').slice(2, 3).join(''));

    // toutes les meteos se dessinent
    var bad = [], sc = document.createElement('canvas');
    for (var w = 0; w < A.WEATHERS.length; w++) {
      try { await A.renderTo(sc, { phase: 'midi', weather: A.WEATHERS[w][0], state: 'musique' }, 'hd'); if (!painted(sc)) bad.push(A.WEATHERS[w][0]); } catch (e) { bad.push(A.WEATHERS[w][0] + ':' + e.message); }
    }
    ok('les 9 meteos se dessinent (etat musique)', bad.length === 0, bad.join(', '));

    // transitions : lecture, pause, reprise, instant precis, planche
    var N = window.AlpineAnim;
    await N.set('day', 0.30, 'half'); await wait(500);
    var ac = $('#an-canvas');
    ok('transitions : canvas de lecture 960x540', ac.width === 960 && ac.height === 540 && painted(ac), ac.width + 'x' + ac.height);
    var p0 = N.state.pos; $('#an-speed').value = 30; N.setPlaying(true); await wait(2500);
    var p1 = N.state.pos, frames = N.state.renderMs;
    ok('transitions : la lecture fait avancer la journee', p1 > p0 + 0.05 || p1 < p0, p0.toFixed(3) + ' -> ' + p1.toFixed(3) + ', ' + Math.round(frames) + ' ms par image');
    N.setPlaying(false); var pp = N.state.pos; await wait(600);
    ok('transitions : la pause fige la position', N.state.pos === pp);
    N.setPlaying(true); await wait(1200); N.setPlaying(false);
    ok('transitions : la reprise repart de la position de la pause', N.state.pos !== pp);
    await N.set('weather', 40 / N.T_END, 'hd');
    var s1 = N.stateAt(40 / N.T_END), s2 = N.stateAt(40 / N.T_END);
    ok('transitions : un instant precis est deterministe (meteo)', s1.mix.rain === s2.mix.rain && s1.mix.snow === s2.mix.snow && s1.mix.rain > 0.3, 'pluie ' + s1.mix.rain.toFixed(3));
    var series = [34, 35, 36, 37, 38, 40, 44].map(function (t) { return N.stateAt(t / N.T_END).mix.rain; }), mono = series.every(function (v, i) { return i === 0 || v >= series[i - 1] - 1e-9; });
    ok('transitions : la pluie monte regulierement apres la mise a jour a 36 s (la bruine precedente donne ~0,2)', mono && series[0] < 0.3 && series[6] > 0.5, series.map(function (v) { return v.toFixed(2); }).join(' '));
    var snow = [50, 52, 54, 56, 60].map(function (t) { var m = N.stateAt(t / N.T_END).mix; return m.snow.toFixed(2) + '/' + m.rain.toFixed(2); });
    out.push('INFO pluie->neige (neige/pluie) : ' + snow.join(' '));
    await N.set('weather', 38 / N.T_END, 'uhd'); var uc = $('#an-canvas');
    ok('transitions : rendu UHD 3840x2160 pendant une transition', uc.width === 3840 && uc.height === 2160 && painted(uc), uc.width + 'x' + uc.height);
    await N.strip(); await wait(300);
    ok('transitions : la planche d\'instants peint 8 images', document.querySelectorAll('#an-strip-out canvas').length === 8 && Array.prototype.every.call(document.querySelectorAll('#an-strip-out canvas'), painted));

    // contraste : modele (72) puis pixels rendus (72)
    var model = A.runModel(), mFail = [];
    model.forEach(function (r) { r.zones.forEach(function (z) { if (z.ratio + 0.02 < z.threshold) mFail.push(r.phase + '/' + r.weather + '/' + z.zone + ' ' + z.ratio.toFixed(2)); }); });
    ok('contraste, modele : 72 combinaisons x 7 zones', model.length === 72 && mFail.length === 0, model.length + ' combinaisons, ' + mFail.length + ' sous le seuil ' + mFail.slice(0, 6).join(' ; '));
    var pix = await A.runPixels(), pFail = [], minByZone = {};
    pix.forEach(function (r) { r.zones.forEach(function (z) {
      if (!(z.zone in minByZone) || z.ratio < minByZone[z.zone].r) minByZone[z.zone] = { r: z.ratio, at: r.phase + '/' + r.weather, thr: z.threshold };
      if (z.ratio + 0.02 < z.threshold) pFail.push(r.phase + '/' + r.weather + '/' + z.zone + ' ' + z.ratio.toFixed(2));
    }); });
    ok('contraste, pixels rendus : 72 combinaisons x 7 zones', pix.length === 72 && pFail.length === 0, pix.length + ' combinaisons, ' + pFail.length + ' sous le seuil ' + pFail.slice(0, 8).join(' ; '));
    window.__contrast = { model: model, pixels: pix };
    try { await fetch('/contrast', { method: 'POST', body: JSON.stringify({ model: model, pixels: pix }) }); } catch (e) { /* hors serveur d'essai */ }
    out.push('MIN-PAR-ZONE ' + JSON.stringify(minByZone));
    var mm = {}; model.forEach(function (r) { r.zones.forEach(function (z) { if (!(z.zone in mm) || z.ratio < mm[z.zone].r) mm[z.zone] = { r: z.ratio, at: r.phase + '/' + r.weather, thr: z.threshold }; }); });
    out.push('MIN-MODELE ' + JSON.stringify(mm));
    out.push('VEIL-MAX ' + Math.max.apply(null, model.map(function (r) { return Math.max.apply(null, r.zones.map(function (z) { return z.veil; })); })).toFixed(3));
  } catch (e) { ok('auto-test sans exception', false, e && e.stack || e); }
  var pre = document.createElement('pre'); pre.id = 'selftest'; pre.textContent = out.join('\n') + '\nTERMINE ' + (out.some(function (l) { return l.indexOf('FAIL') === 0; }) ? 'ECHEC' : 'OK');
  pre.style.cssText = 'position:fixed;inset:0;background:#000;color:#0f0;z-index:99;padding:16px;font:13px monospace;white-space:pre-wrap;overflow:auto';
  document.body.appendChild(pre);
  try { fetch('/result', { method: 'POST', body: pre.textContent }); } catch (e) { /* hors serveur d'essai */ }
})();
