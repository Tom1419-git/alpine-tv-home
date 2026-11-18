/* Interface de comparaison de l'accueil alpin : photo unique, mise en page B ; grille filtrable par phase, meteo et etat,
   comparaison synchronisee, plein ecran TV 1080p ou UHD, detail a 100 %, contraste par combinaison, favoris. */
(function () {
  'use strict';
  var D = window.ALPINE_DATA, S = window.AlpineScene, L = window.AlpineLayout, G = window.AlpineGrade;
  var PHASES = [['avant-lever', 'Aube avant le lever'], ['lever', 'Lever du soleil'], ['matin', 'Matin'], ['midi', 'Midi'],
                ['apres-midi', 'Après-midi'], ['coucher', 'Coucher du soleil'], ['crepuscule', 'Crépuscule'], ['nuit', 'Nuit']];
  var WEATHERS = [['clair', 'Ciel clair'], ['partiel', 'Partiellement nuageux'], ['couvert', 'Couvert'], ['brouillard', 'Brouillard'],
                  ['bruine', 'Bruine'], ['pluie', 'Pluie'], ['averses', 'Averses'], ['neige', 'Neige'], ['orage', 'Orage']];
  var STATES = [['repos', 'Au repos'], ['focus', 'Focus sur le dock'], ['focus2', 'Focus sur la 2e rangée'], ['musique', 'Bloc musique']];
  var PRESETS = [['Nuit claire', 'nuit', 'clair'], ['Nuit neigeuse', 'nuit', 'neige'], ['Matin brumeux', 'matin', 'brouillard'],
                 ['Midi dégagé', 'midi', 'clair'], ['Coucher dégagé', 'coucher', 'clair'],
                 ['Coucher pluvieux', 'coucher', 'pluie'], ['Nuit pluvieuse', 'nuit', 'pluie'], ['Orage', 'apres-midi', 'orage']];
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var sel = { phase: ['midi'], weather: ['clair'], state: ['repos'] };
  var compare = [], favs = {}, page = 1, PER_PAGE = 48, bolt = false;

  try { favs = JSON.parse(localStorage.getItem('alpine-favs') || '{}') || {}; } catch (e) { favs = {}; }
  function saveFavs() { try { localStorage.setItem('alpine-favs', JSON.stringify(favs)); } catch (e) { /* stockage indisponible */ } $('#favs').textContent = Object.keys(favs).length; }
  if (location.protocol === 'file:') $('#http-warning').hidden = false;

  // ---- images ------------------------------------------------------------------------------------------------
  var cache = {};
  function img(url) {
    if (!cache[url]) cache[url] = new Promise(function (res) { var i = new Image(); i.onload = function () { res(i); }; i.onerror = function () { res(null); }; i.src = url; });
    return cache[url];
  }
  var cardsP = Promise.all([0, 1, 2, 3].map(function (i) { return img('assets/cards/card' + i + '.png'); })).then(function (a) { return a.filter(Boolean); });
  var fontP = Promise.all([document.fonts.load('200 100px Outfit'), document.fonts.load('400 20px Outfit')]).catch(function () {});

  function id(o) { return [o.phase, o.weather, o.state].join('|'); }
  function name(list, k) { for (var i = 0; i < list.length; i++) if (list[i][0] === k) return list[i][1]; return k; }
  function label(o) { return name(PHASES, o.phase) + ' · ' + name(WEATHERS, o.weather) + ' · ' + name(STATES, o.state).toLowerCase(); }
  function decode(s) { var p = s.split('|'); return { phase: p[0], weather: p[1], state: p[2] }; }

  function renderTo(canvas, o, res) {
    var hd = res !== 'uhd';
    return Promise.all([img('pack/' + (hd ? 'photo_1080.jpg' : 'photo.jpg')), img('pack/' + (hd ? 'foreground_1080.png' : 'foreground.png')), cardsP, fontP]).then(function (r) {
      if (!r[0] || !r[1]) throw new Error('fichier du pack absent');
      return S.render(canvas, { phase: o.phase, weather: o.weather, state: o.state, res: res, data: D, img: { photo: r[0], fg: r[1], cards: r[2] }, seed: 7, bolt: bolt });
    });
  }

  // ---- filtres -----------------------------------------------------------------------------------------------
  function buildFilters() {
    var defs = { phase: PHASES, weather: WEATHERS, state: STATES };
    Object.keys(defs).forEach(function (g) {
      var box = $('.group[data-group=' + g + ']'); box.innerHTML = '';
      defs[g].forEach(function (d) {
        var l = document.createElement('label'), c = document.createElement('input');
        c.type = 'checkbox'; c.value = d[0]; c.checked = sel[g].indexOf(d[0]) >= 0;
        c.addEventListener('change', function () { sel[g] = $$('input:checked', box).map(function (x) { return x.value; }); page = 1; drawGrid(); });
        l.appendChild(c); l.appendChild(document.createTextNode(d[1])); box.appendChild(l);
      });
    });
    var pb = $('#presets'); pb.innerHTML = '';
    PRESETS.forEach(function (p) {
      var b = document.createElement('button'); b.textContent = p[0];
      b.addEventListener('click', function () { sel.phase = [p[1]]; sel.weather = [p[2]]; page = 1; syncChecks(); drawGrid(); });
      pb.appendChild(b);
    });
  }
  function syncChecks() { Object.keys(sel).forEach(function (g) { $$('.group[data-group=' + g + '] input').forEach(function (c) { c.checked = sel[g].indexOf(c.value) >= 0; }); }); }
  function combos() {
    var out = [];
    sel.phase.forEach(function (ph) { sel.weather.forEach(function (w) { sel.state.forEach(function (s) { out.push({ phase: ph, weather: w, state: s }); }); }); });
    return out;
  }

  // ---- contraste (modele de l'application) ---------------------------------------------------------------------
  function plansFor(o) {
    var st = G.state(o.phase, o.weather), out = {};
    G.ZONES.forEach(function (z) { out[z] = G.plan(z, L.W, L.H, D.pack.grid, D.pack.crest, st.grade, st.mix); });
    return out;
  }
  function worstOf(plans) {
    var w = null;
    G.ZONES.forEach(function (z) { var p = plans[z]; if (!w || p.ratio / p.threshold < w.ratio / w.threshold) w = p; });
    return w;
  }

  // ---- grille ------------------------------------------------------------------------------------------------
  var observer = null;
  function drawGrid() {
    var list = combos(), grid = $('#grid'); grid.innerHTML = '';
    $('#count-grid').textContent = list.length;
    var shown = list.slice(0, page * PER_PAGE);
    $('#more-wrap').hidden = shown.length >= list.length;
    if (!list.length) { grid.innerHTML = '<p class="hint">Aucune combinaison : cochez au moins une valeur dans chaque groupe.</p>'; return; }
    if (observer) observer.disconnect();
    observer = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { observer.unobserve(e.target); paintThumb(e.target); } }); }, { rootMargin: '300px' });
    shown.forEach(function (o) { grid.appendChild(card(o)); });
  }
  function badge(t, cls, title) { var b = document.createElement('span'); b.className = 'badge ' + (cls || ''); b.textContent = t; if (title) b.title = title; return b; }
  function card(o) {
    var el = document.createElement('div'); el.className = 'card'; el.dataset.id = id(o);
    el.innerHTML = '<canvas width="640" height="360" aria-label="Aperçu"></canvas><div class="cap"><b></b><div class="d"></div><div class="row"></div></div>';
    $('b', el).textContent = name(PHASES, o.phase) + ' · ' + name(WEATHERS, o.weather);
    $('.d', el).textContent = name(STATES, o.state);
    var row = $('.row', el);
    var fav = document.createElement('button'); fav.className = 'fav' + (favs[id(o)] ? ' on' : ''); fav.textContent = '★'; fav.title = 'Marquer comme favori';
    fav.addEventListener('click', function () { toggleFav(o); fav.classList.toggle('on', !!favs[id(o)]); });
    var lab = document.createElement('label'), cb = document.createElement('input'); cb.type = 'checkbox'; cb.checked = compare.indexOf(id(o)) >= 0;
    cb.addEventListener('change', function () { toggleCompare(o, cb); });
    lab.appendChild(cb); lab.appendChild(document.createTextNode(' comparer')); lab.style.fontSize = '12px';
    var tv = document.createElement('button'); tv.textContent = 'Plein écran'; tv.addEventListener('click', function () { openTv([id(o)], 0); });
    row.appendChild(fav); row.appendChild(lab); row.appendChild(tv);
    var w = worstOf(plansFor(o)), pass = w.ok;
    var b = badge('contraste min ' + w.ratio.toFixed(1) + ':1' + (pass ? '' : ' (< ' + w.threshold + ':1)'), pass ? 'ok' : 'bad',
      'Zone la plus faible : ' + w.zone + ' ; seuil ' + w.threshold + ':1 ; voile ' + Math.round(w.veilAlpha * 100) + ' %');
    row.appendChild(b);
    row.appendChild(badge('aperçu calculé', '', "Moteur et géométrie de l'application, météo approchée, rendu navigateur"));
    observer.observe(el); return el;
  }
  function paintThumb(el) {
    var o = decode(el.dataset.id), c = $('canvas', el), tmp = document.createElement('canvas');
    renderTo(tmp, o, 'hd').then(function () { c.getContext('2d').drawImage(tmp, 0, 0, 640, 360); tmp.width = 0; })
      .catch(function (e) { var x = c.getContext('2d'); x.fillStyle = '#300'; x.fillRect(0, 0, 640, 360); x.fillStyle = '#fff'; x.fillText(String(e.message || e), 12, 24); });
  }
  function toggleFav(o) { var k = id(o); if (favs[k]) delete favs[k]; else favs[k] = 1; saveFavs(); }
  function toggleCompare(o, cb) {
    var k = id(o), i = compare.indexOf(k);
    if (i >= 0) compare.splice(i, 1);
    else if (compare.length >= 4) { cb.checked = false; alert('Quatre versions au maximum dans la comparaison.'); return; }
    else compare.push(k);
    $('#count-compare').textContent = compare.length;
  }

  // ---- comparaison -------------------------------------------------------------------------------------------
  var syncing = false;
  function legend(r) {
    return 'Aperçu calculé (moteur et géométrie de l’application, météo approchée) · rendu ' + r.render + ' · fichier ' + r.source + ' ' + r.sourcePixels.join('×') +
      ' issu du master ' + D.photo.source.width + '×' + D.photo.source.height + ' · contraste min ' + r.plans[r.worstZone].ratio.toFixed(1) + ':1 (' + r.worstZone + ')' + (r.issues.length ? ' · à noter : ' + r.issues.join(' ; ') : '');
  }
  function drawCompare() {
    var panes = $('#panes'); panes.innerHTML = '';
    $('#cmp-empty').hidden = compare.length > 0;
    var mode = $('#cmp-mode').value, res = $('#cmp-res').value, zoom = +$('#cmp-zoom').value / 100;
    $('#cmp-op-wrap').hidden = mode !== 'overlay';
    var dpr = window.devicePixelRatio || 1, base = (res === 'uhd' ? 3840 : 1920);
    function cssW() { return base * zoom / dpr; }
    panes.className = mode === 'overlay' ? 'overlay' : 'c' + Math.max(2, Math.min(compare.length, 4));
    if (!compare.length) return;
    var views = [];
    if (mode === 'overlay' && compare.length >= 2) {
      var a = decode(compare[0]), b = decode(compare[1]), p = document.createElement('div'); p.className = 'pane';
      p.innerHTML = '<div class="head"></div><div class="view"><div class="stack"></div></div><div class="foot"></div>';
      $('.head', p).textContent = 'Dessous : ' + label(a) + '  |  Dessus : ' + label(b);
      var st = $('.stack', p), c1 = document.createElement('canvas'), c2 = document.createElement('canvas'); st.appendChild(c1); st.appendChild(c2);
      panes.appendChild(p); views.push($('.view', p));
      Promise.all([renderTo(c1, a, res), renderTo(c2, b, res)]).then(function (r) {
        [c1, c2].forEach(function (c) { c.style.width = cssW() + 'px'; });
        c2.style.opacity = $('#cmp-op').value / 100; c2.className = 'top';
        $('.foot', p).textContent = legend(r[0]) + '  /  ' + legend(r[1]);
      });
    } else {
      compare.forEach(function (k) {
        var o = decode(k), p = document.createElement('div'); p.className = 'pane';
        p.innerHTML = '<div class="head"></div><div class="view"><canvas></canvas></div><div class="foot"></div>';
        $('.head', p).textContent = label(o);
        var c = $('canvas', p); panes.appendChild(p); views.push($('.view', p));
        renderTo(c, o, res).then(function (r) { c.style.width = cssW() + 'px'; $('.foot', p).textContent = legend(r); });
      });
    }
    views.forEach(function (v) {
      v.addEventListener('scroll', function () {
        if (syncing) return; syncing = true;
        views.forEach(function (w) { if (w !== v) { w.scrollLeft = v.scrollLeft; w.scrollTop = v.scrollTop; } });
        syncing = false;
      });
    });
  }

  // ---- plein ecran TV et detail a 100 % ------------------------------------------------------------------------
  var tvList = [], tvIndex = 0, tvRes = 'hd';
  function openTv(list, i) { tvList = list; tvIndex = i; $('#tv').hidden = false; drawTv(); }
  function drawTv() {
    var o = decode(tvList[tvIndex]), c = $('#tv-canvas'), dpr = window.devicePixelRatio || 1;
    $('#tv-res').textContent = tvRes === 'uhd' ? 'UHD 3840×2160' : 'TV 1920×1080';
    $('#tv-title').textContent = label(o) + '  (' + (tvIndex + 1) + '/' + tvList.length + ')';
    renderTo(c, o, tvRes).then(function (r) {
      c.style.width = c.width / dpr + 'px'; c.style.height = c.height / dpr + 'px';
      $('#tv-legend').textContent = legend(r) + ' · affichage à ' + (dpr === 1 ? '1 pixel image = 1 pixel écran' : 'dpr ' + dpr + ' (' + (c.width / dpr) + ' px CSS)');
    });
  }
  function closeTv() { $('#tv').hidden = true; }
  var CROPS = [[1500, 880], [1520, 280], [260, 1060]];   // coin haut gauche, en pixels UHD
  function showDetail(o) {
    var src = document.createElement('canvas');
    renderTo(src, o, 'uhd').then(function () {
      CROPS.forEach(function (c, i) {
        var dst = $('#dt-' + i), x = dst.getContext('2d'); x.clearRect(0, 0, 800, 450);
        x.drawImage(src, c[0], c[1], 800, 450, 0, 0, 800, 450);   // aucune mise a l'echelle : un pixel pour un pixel
      });
      $('#detail').hidden = false;
    });
  }

  // ---- onglet contraste ------------------------------------------------------------------------------------------
  function ratioCell(r, thr) { return '<td class="num ' + (r + 0.02 >= thr ? 'ok' : 'bad') + '">' + r.toFixed(1) + '</td>'; }
  function drawContrast(rows, kind) {
    var h = '<table><tr><th>Phase</th><th>Météo</th>' + G.ZONES.map(function (z) { return '<th>' + z + '<br><small>' + G.threshold(z) + ':1</small></th>'; }).join('') + '<th>voile max</th><th>résultat</th></tr>';
    rows.forEach(function (r) {
      var okAll = r.zones.every(function (z) { return z.ratio + 0.02 >= z.threshold; });
      h += '<tr><td>' + name(PHASES, r.phase) + '</td><td>' + name(WEATHERS, r.weather) + '</td>' + r.zones.map(function (z) { return ratioCell(z.ratio, z.threshold); }).join('') +
        '<td class="num">' + Math.round(Math.max.apply(null, r.zones.map(function (z) { return z.veil; })) * 100) + ' %</td><td class="' + (okAll ? 'ok' : 'bad') + '">' + (okAll ? 'OK' : 'ÉCHEC') + '</td></tr>';
    });
    $('#ct-table').innerHTML = h + '</table>';
    var bad = rows.filter(function (r) { return !r.zones.every(function (z) { return z.ratio + 0.02 >= z.threshold; }); }).length;
    $('#ct-status').textContent = rows.length + ' combinaisons (' + kind + ') : ' + (bad ? bad + ' en échec' : 'toutes au-dessus du seuil') + '.';
    window.AlpineContrast = rows;
  }
  function runModel() {
    var rows = [];
    PHASES.forEach(function (p) { WEATHERS.forEach(function (w) {
      var pl = plansFor({ phase: p[0], weather: w[0] });
      rows.push({ phase: p[0], weather: w[0], zones: G.ZONES.map(function (z) { return { zone: z, ratio: pl[z].ratio, threshold: pl[z].threshold, veil: pl[z].veilAlpha }; }) });
    }); });
    drawContrast(rows, 'modèle'); return rows;
  }
  function runPixels() {
    $('#ct-status').textContent = 'Mesure en cours...';
    return Promise.all([img('pack/photo_1080.jpg'), img('pack/foreground_1080.png'), cardsP, fontP]).then(function (r) {
      var rows = [];
      PHASES.forEach(function (p) { WEATHERS.forEach(function (w) {
        var m = S.measure({ phase: p[0], weather: w[0], state: 'repos', data: D, img: { photo: r[0], fg: r[1], cards: r[2] }, seed: 7 });
        rows.push({ phase: p[0], weather: w[0], zones: m });
      }); });
      drawContrast(rows, 'pixels rendus'); return rows;
    });
  }

  // ---- fichiers livres et legende ----------------------------------------------------------------------------
  function mb(b) { return (b / 1e6).toFixed(1) + ' Mo'; }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function drawFiles() {
    var s = D.photo.source, h = '<h3 class="sec">Photo unique : ' + esc(D.photo.title) + '</h3><table><tr><th>Master natif</th><th>Poids</th><th>Auteur</th><th>Licence</th><th>Fiche Commons</th></tr><tr><td class="n">' +
      s.width + '×' + s.height + '</td><td class="n">' + mb(s.bytes) + '</td><td>' + esc(s.author) + '</td><td><a href="' + esc(s.license_url) + '">' + esc(s.license) + '</a></td><td><a href="' + esc(s.commons_url) + '">' + esc(s.title) + '</a></td></tr></table>';
    h += '<h3 class="sec">Pack livré à l’application (weather_pack.zip, hors APK)</h3><p class="hint">Total ' + mb(D.pack.total) + ' · JPEG qualité 95, chrominance 4:4:4 · premiers plans PNG sans perte · recadrage natif ' + D.photo.crop_native.join('×') + ', aucun agrandissement.</p><table><tr><th>Fichier</th><th>Pixels</th><th>Poids</th><th>SHA-256</th></tr>';
    Object.keys(D.pack.files).sort().forEach(function (k) { var f = D.pack.files[k]; h += '<tr><td><a href="pack/' + k + '" target="_blank" rel="noopener">' + esc(k) + '</a></td><td class="n">' + f.pixels.join('×') + '</td><td class="n">' + mb(f.size) + '</td><td><code>' + f.sha256.slice(0, 16) + '…</code></td></tr>'; });
    $('#tab-files').innerHTML = h + '</table>';
  }
  function drawLegend() {
    var h = '<h3 class="sec">Ce que montrent ces aperçus</h3><ul>' +
      '<li><b>Calculé avec le moteur de l’application</b> : la même table d’étalonnage continu (température, luminosité, contraste, saturation, voile atmosphérique) fonction de la hauteur du soleil, la même matrice de couleur appliquée pixel par pixel, le même modèle de contraste par zone et les mêmes plans de lisibilité, la géométrie de la mise en page B (heure remontée de 52 px, pilules remontées de 54 px, dock en deux rangées), le premier plan PNG du pack.</li>' +
      '<li><b>Approximation</b> : les cartes (images tirées d’une capture de la TV), pilules, bloc musique et verre sont redessinés ici ; la pluie, la neige, le brouillard et les nuages sont une image fixe de ce que l’application anime (positions tirées au sort, pas celles de l’appareil) ; l’éclair est masqué par défaut ; le rendu du texte du navigateur diffère d’Android ; l’écart avec la TV n’a pas été mesuré.</li>' +
      '<li><b>Une seule photo, huit moments</b> : la lumière est calculée, pas photographiée. Les huit phases sont des hauteurs de soleil (de −20° à 40°), y compris des moments que la photo ne montre pas (midi à partir d’un lever de soleil). L’application glisse de l’un à l’autre, sans coupure.</li>' +
      '<li><b>Contraste</b> : chaque vignette indique le contraste minimal des zones de texte (heure 3:1, le reste 4,5:1). L’onglet « Contraste » calcule les 72 combinaisons et les mesure sur les pixels rendus (2e et 98e centiles de chaque zone).</li>' +
      '<li>Le mode UHD affiche les fichiers 3840×2160 sans redimensionnement du navigateur : un pixel de l’image pour un pixel de l’écran.</li></ul>' +
      '<label><input type="checkbox" id="bolt"> montrer un éclair dans les aperçus d’orage (l’application en déclenche un à deux par minute au plus, pendant 0,2 s, avec un halo local)</label>' +
      '<h3 class="sec">Positions avant et après</h3><table><tr><th></th><th>Avant (A)</th><th>Après (B)</th></tr>';
    var a = L.clockMetrics(L.LAYOUTS.A), b = L.clockMetrics(L.LAYOUTS.B);
    function row(t, x, y, u) { h += '<tr><td>' + t + '</td><td class="n">' + x.toFixed(0) + u + '</td><td class="n">' + y.toFixed(0) + u + '</td></tr>'; }
    row('Ligne de base de l’heure', a.baseline, b.baseline, ' px'); row('Haut des chiffres', a.top, b.top, ' px'); row('Date (ligne de base)', a.dateY, b.dateY, ' px');
    row('Salutation (ligne de base)', a.greetY, b.greetY, ' px'); row('Pilules, haut', L.LAYOUTS.A.pillsTop, L.LAYOUTS.B.pillsTop, ' px');
    row('Largeur de l’heure', L.LAYOUTS.A.clockWidth * 1920, L.LAYOUTS.B.clockWidth * 1920, ' px'); row('Bord de la rive sur les chiffres', L.LAYOUTS.A.occlusion * 100, L.LAYOUTS.B.occlusion * 100, ' %');
    $('#tab-legend').innerHTML = h + '</table>';
    $('#bolt').checked = bolt; $('#bolt').addEventListener('change', function (e) { bolt = e.target.checked; drawGrid(); });
  }

  // ---- resume des choix --------------------------------------------------------------------------------------
  function summary() {
    var keys = Object.keys(favs);
    if (!keys.length) return "Aucun favori pour l’instant. Marquez des vignettes avec l’étoile.";
    var out = ['Mes choix (accueil alpin, photo unique, mise en page B) :', ''];
    keys.forEach(function (k) { out.push('- ' + label(decode(k))); });
    return out.join('\n');
  }

  // ---- demarrage ---------------------------------------------------------------------------------------------
  function tab(t) {
    $$('#tabs button').forEach(function (b) { b.classList.toggle('on', b.dataset.tab === t); });
    ['grid', 'compare', 'anim', 'contrast', 'files', 'legend'].forEach(function (n) { $('#tab-' + n).hidden = n !== t; });
    $('#filters').hidden = t !== 'grid';
    $('#layout').style.gridTemplateColumns = t === 'grid' ? '' : '1fr';
    if (t === 'compare') drawCompare();
    if (t === 'anim' && window.AlpineAnim) window.AlpineAnim.open();
  }
  $$('#tabs button').forEach(function (b) { b.addEventListener('click', function () { tab(b.dataset.tab); }); });
  $('#more').addEventListener('click', function () { page++; drawGrid(); });
  ['cmp-mode', 'cmp-res', 'cmp-op'].forEach(function (i) { $('#' + i).addEventListener('input', drawCompare); });
  $('#cmp-zoom').addEventListener('input', function () { $('#cmp-zoom-v').textContent = this.value + ' %'; drawCompare(); });
  $('#cmp-pixel').addEventListener('click', function () { $('#cmp-zoom').value = 100; $('#cmp-zoom-v').textContent = '100 %'; drawCompare(); });
  $('#cmp-fit').addEventListener('click', function () {
    var n = $('#panes').children.length || 1, avail = ($('#panes').clientWidth / (n > 1 && $('#cmp-mode').value === 'side' ? Math.min(n, n > 2 ? 2 : n) : 1)) - 20;
    var base = $('#cmp-res').value === 'uhd' ? 3840 : 1920, dpr = window.devicePixelRatio || 1, z = Math.max(10, Math.min(400, Math.round(avail * dpr / base * 100)));
    $('#cmp-zoom').value = z; $('#cmp-zoom-v').textContent = z + ' %'; drawCompare();
  });
  $('#cmp-clear').addEventListener('click', function () { compare = []; $('#count-compare').textContent = 0; $$('.card input[type=checkbox]').forEach(function (c) { c.checked = false; }); drawCompare(); });
  $('#cmp-tv').addEventListener('click', function () { if (compare.length) openTv(compare.slice(), 0); });
  $('#cmp-detail').addEventListener('click', function () { if (compare.length) showDetail(decode(compare[0])); });
  $('#tv-close').addEventListener('click', closeTv);
  $('#tv-detail').addEventListener('click', function () { showDetail(decode(tvList[tvIndex])); });
  $('#detail-close').addEventListener('click', function () { $('#detail').hidden = true; });
  $('#tv-res').addEventListener('click', function () { tvRes = tvRes === 'hd' ? 'uhd' : 'hd'; drawTv(); });
  $('#tv-prev').addEventListener('click', function () { tvIndex = (tvIndex + tvList.length - 1) % tvList.length; drawTv(); });
  $('#tv-next').addEventListener('click', function () { tvIndex = (tvIndex + 1) % tvList.length; drawTv(); });
  $('#ct-model').addEventListener('click', runModel);
  $('#ct-pixels').addEventListener('click', runPixels);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') { closeTv(); $('#modal').hidden = true; $('#detail').hidden = true; }
    if (!$('#tv').hidden && e.key === 'ArrowRight') $('#tv-next').click();
    if (!$('#tv').hidden && e.key === 'ArrowLeft') $('#tv-prev').click();
  });
  $('#summary-btn').addEventListener('click', function () { $('#summary').value = summary(); $('#modal').hidden = false; });
  $('#fav-count').addEventListener('click', function () { var k = Object.keys(favs); if (k.length) openTv(k, 0); });
  $('#modal-close').addEventListener('click', function () { $('#modal').hidden = true; });
  $('#copy').addEventListener('click', function () {
    var t = $('#summary'); t.select();
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t.value).catch(function () { document.execCommand('copy'); }); else document.execCommand('copy');
    this.textContent = 'Copié'; var b = this; setTimeout(function () { b.textContent = 'Copier'; }, 1500);
  });

  buildFilters(); drawFiles(); drawLegend(); saveFavs(); drawGrid();
  var q = new URLSearchParams(location.search);
  if (q.get('tv')) { tvRes = q.get('res') === 'uhd' ? 'uhd' : 'hd'; openTv([q.get('tv')], 0); }
  if (q.has('selftest')) { var sc = document.createElement('script'); sc.src = 'selftest.js'; document.body.appendChild(sc); }
  window.AlpineApp = {
    page: function () { return page; }, drawGrid: drawGrid, drawCompare: drawCompare, openTv: openTv, toggleFav: toggleFav, PER_PAGE: PER_PAGE,
    sel: sel, combos: combos, compare: function () { return compare; }, favs: function () { return favs; }, summary: summary, tab: tab,
    runModel: runModel, runPixels: runPixels, showDetail: showDetail, renderTo: renderTo, id: id, decode: decode, PHASES: PHASES, WEATHERS: WEATHERS, STATES: STATES,
    img: img, cardsP: function () { return cardsP; }, fontP: function () { return fontP; }, toggleCompareId: function (k) { if (compare.indexOf(k) < 0) compare.push(k); $('#count-compare').textContent = compare.length; }
  };
})();
