/*
 * Onglet « Transitions » : lecture accelere d'une journee (de la nuit a la nuit) et d'une suite de conditions meteo, avec
 * pause, reprise, curseur d'instant precis, pas a pas et planche d'instants.
 *
 * Journee : le soleil et la lune viennent des equations NOAA (G.skyAt, comme SolarPhase.java) pour Lausanne et la date du jour ;
 * l'etalonnage est une fonction continue de la hauteur du soleil. Meteo : chaque jalon donne les donnees d'entree que
 * l'application recevrait d'Open-Meteo (code WMO, nuages, precipitations, visibilite, vent) ; G.deriveTargets en tire les cibles et
 * le melange lisse (G.stepMix, constante 0,65 s) glisse vers elles, exactement comme SceneMix. La photo ne change jamais.
 */
(function () {
  'use strict';
  var A = window.AlpineApp, S = window.AlpineScene, G = window.AlpineGrade, D = window.ALPINE_DATA;
  var $ = function (s) { return document.querySelector(s); };
  var LAT = 46.516, LON = 6.63282;   // Lausanne, les reglages par defaut de l'application
  var now = new Date(), Y = now.getFullYear(), MO = now.getMonth() + 1, DY = now.getDate(), OFFSET = -now.getTimezoneOffset();
  var CODE = { 0: 'clair', 1: 'clair', 2: 'partiel', 3: 'couvert', 45: 'brouillard', 48: 'brouillard', 51: 'bruine', 53: 'bruine', 55: 'bruine', 61: 'pluie', 63: 'pluie', 65: 'pluie', 71: 'neige', 73: 'neige', 75: 'neige', 80: 'averses', 95: 'orage' };
  // Scenario meteo : instant (s), libelle, donnees d'entree. Chaque jalon est une « mise a jour » des donnees, la scene glisse ensuite.
  var KEYS = [
    { t: 0, name: 'Ciel clair', code: 1, cloudPct: 5, precipMm: 0, visibilityM: 30000, windKmh: 8 },
    { t: 12, name: 'Couvert', code: 3, cloudPct: 100, precipMm: 0, visibilityM: 18000, windKmh: 12 },
    { t: 24, name: 'Bruine', code: 53, cloudPct: 95, precipMm: 0.3, visibilityM: 9000, windKmh: 10 },
    { t: 36, name: 'Pluie', code: 63, cloudPct: 100, precipMm: 3.0, visibilityM: 6000, windKmh: 28 },
    { t: 52, name: 'Neige', code: 73, cloudPct: 100, precipMm: 1.5, visibilityM: 3500, windKmh: 14 },
    { t: 68, name: 'Brouillard', code: 45, cloudPct: 70, precipMm: 0, visibilityM: 400, windKmh: 3 },
    { t: 84, name: 'Ciel clair', code: 0, cloudPct: 10, precipMm: 0, visibilityM: 30000, windKmh: 8 }];
  var T_END = 96;
  var st = { mode: 'day', playing: false, pos: 0.25, last: 0, busy: false, ready: null, t: 0 };
  var assets = {};

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function hhmm(min) { min = Math.round(min) % 1440; return pad(Math.floor(min / 60)) + ':' + pad(min % 60); }
  function keyAt(T) { var k = KEYS[0]; KEYS.forEach(function (x) { if (T >= x.t) k = x; }); return k; }

  function fillSelects() {
    var sp = $('#an-speed'), w = $('#an-weather');
    sp.innerHTML = '';
    if (st.mode === 'day') [['24 h en 30 s', 30], ['24 h en 60 s', 60], ['24 h en 120 s', 120]].forEach(function (o) { sp.add(new Option(o[0], o[1])); });
    else [['temps reel', 1], ['x0,5', 0.5], ['x2', 2]].forEach(function (o) { sp.add(new Option(o[0], o[1])); });
    sp.value = st.mode === 'day' ? 60 : 1;
    if (!w.options.length) A.WEATHERS.forEach(function (o) { w.add(new Option(o[1], o[0])); });
  }

  /** Etat de la scene a la position courante : {mix, minute, T, key, derived}. Deterministe : le meme instant donne la meme image. */
  function stateAt(pos) {
    var mix = G.newMix(), minute, T, key, tg;
    if (st.mode === 'day') {
      minute = pos * 1440; T = 0;
      key = null; tg = G.deriveTargets($('#an-weather').value, null);
    } else {
      minute = +$('#an-time').value; T = pos * T_END;
    }
    var sky = G.skyAt(Y, MO, DY, minute, LAT, LON, OFFSET);
    if (st.mode === 'day') {
      Object.keys(tg).forEach(function (k) { mix[k] = tg[k]; });   // une journee a meteo constante : pas de glissement meteo
    } else {
      var steps = Math.round(T * 30);   // on rejoue le scenario depuis le debut a 30 images par seconde
      for (var i = 0; i <= steps; i++) {
        key = keyAt(i / 30);
        var target = G.deriveTargets(CODE[key.code], key);
        mix.target = Object.assign({}, target); G.stepMix(mix, 1 / 30);
        if (i === 0) Object.keys(target).forEach(function (k) { mix[k] = target[k]; });
      }
      key = keyAt(T); tg = G.deriveTargets(CODE[key.code], key);
    }
    ['elevation', 'azimuth', 'moonElevation', 'moonAzimuth', 'evening'].forEach(function (k) { mix[k] = sky[k]; });
    return { mix: mix, minute: minute, T: T, key: key, targets: tg, sky: sky };
  }

  function loadAssets(res) {
    var hd = res !== 'uhd';
    return Promise.all([A.img('pack/' + (hd ? 'photo_1080.jpg' : 'photo.jpg')), A.img('pack/' + (hd ? 'foreground_1080.png' : 'foreground.png')), A.cardsP(), A.fontP()]).then(function (r) { assets[res] = { photo: r[0], fg: r[1], cards: r[2] }; return assets[res]; });
  }

  function info(s) {
    var m = s.mix, h = '';
    if (st.mode === 'day') {
      var rs = G.riseSet(Y, MO, DY, LAT, LON, OFFSET);
      h = 'Journee du ' + pad(DY) + '/' + pad(MO) + '/' + Y + ' a Lausanne (' + LAT + ', ' + LON + ') : lever ' + hhmm(rs[0]) + ', coucher ' + hhmm(rs[1]) + '. A ' + hhmm(s.minute) + ' : soleil ' + m.elevation.toFixed(1) + '° de hauteur, azimut ' + m.azimuth.toFixed(0) + '° ; lune ' + m.moonElevation.toFixed(0) + '°. Meteo constante : ' + $('#an-weather').selectedOptions[0].text + '.';
    } else {
      var k = s.key, next = KEYS.filter(function (x) { return x.t > s.T; })[0];
      h = '<b>Donnees d\'entree (mise a jour a ' + k.t + ' s)</b> : ' + k.name + ', code WMO ' + k.code + ', nuages ' + k.cloudPct + ' %, precipitations ' + k.precipMm + ' mm/h, visibilite ' + k.visibilityM + ' m, vent ' + k.windKmh + ' km/h' + (next ? ' ; prochaine mise a jour : ' + next.name + ' a ' + next.t + ' s' : '') +
        '.<br><b>Cibles</b> : nuages ' + s.targets.cloud.toFixed(2) + ', brume ' + s.targets.fog.toFixed(2) + ', pluie ' + s.targets.rain.toFixed(2) + ', neige ' + s.targets.snow.toFixed(2) + ', vent ' + s.targets.wind.toFixed(2) +
        '.<br><b>Valeurs affichees (lissees)</b> : nuages ' + m.cloud.toFixed(2) + ', brume ' + m.fog.toFixed(2) + ', pluie ' + m.rain.toFixed(2) + ', neige ' + m.snow.toFixed(2) + ', vent ' + m.wind.toFixed(2) + ', brume ressentie ' + G.haze(m).toFixed(2) + '. Soleil fixe a ' + hhmm(s.minute) + ' (' + m.elevation.toFixed(0) + '°).';
    }
    $('#an-info').innerHTML = h;
    $('#an-label').textContent = st.mode === 'day' ? hhmm(s.minute) : s.T.toFixed(1) + ' s / ' + T_END + ' s';
  }

  /** Dessine l'instant courant dans le canevas (synchrone une fois les images chargees). */
  function draw(res, canvas) {
    var a = assets[res];
    if (!a) return null;
    var s = stateAt(st.pos), cv = canvas || $('#an-canvas');
    var clock = st.mode === 'day' ? hhmm(s.minute) : hhmm(s.minute);
    st.t = st.mode === 'day' ? st.pos * 1440 * 0.5 : s.T;   // l'horloge d'animation des particules : continue en lecture
    var r = S.render(cv, { mix: s.mix, t: st.t, state: 'repos', res: res, data: D, img: a, seed: 7, time: clock, bolt: false });
    if (!canvas) info(s);
    return { state: s, render: r };
  }

  function frame(ts) {
    if (!st.playing) return;
    var dt = st.last ? Math.min(0.25, (ts - st.last) / 1000) : 0; st.last = ts;
    var speed = +$('#an-speed').value;
    if (st.mode === 'day') st.pos = (st.pos + dt / speed) % 1;
    else st.pos = Math.min(1, st.pos + dt * speed / T_END);
    $('#an-pos').value = Math.round(st.pos * 1000);
    var t0 = performance.now();
    draw($('#an-res').value === 'uhd' ? 'uhd' : $('#an-res').value);
    st.renderMs = performance.now() - t0;
    if (st.mode === 'weather' && st.pos >= 1) { setPlaying(false); return; }
    requestAnimationFrame(frame);
  }

  function setPlaying(on) {
    st.playing = on; st.last = 0;
    $('#an-play').innerHTML = on ? '&#10074;&#10074; Pause' : '&#9654; Lecture';
    if (on) requestAnimationFrame(frame);
  }

  function redraw() { var res = $('#an-res').value; return (assets[res] ? Promise.resolve() : loadAssets(res)).then(function () { draw(res); }); }

  function step(dir) {
    setPlaying(false);
    var d = st.mode === 'day' ? 15 / 1440 : 1 / T_END;   // 15 minutes ou 1 seconde
    st.pos = Math.max(0, Math.min(st.mode === 'day' ? 0.9999 : 1, st.pos + dir * d));
    $('#an-pos').value = Math.round(st.pos * 1000); redraw();
  }

  /** Planche d'instants : 12 images sur 24 h (une toutes les 2 h), ou 8 images de 3 s autour de la position courante. */
  function strip() {
    setPlaying(false);
    var out = $('#an-strip-out'); out.innerHTML = '';
    var saved = st.pos, list = [];
    if (st.mode === 'day') for (var i = 0; i < 12; i++) list.push(i / 12 + 1 / 24 * 0);
    else { var T0 = Math.max(0, saved * T_END - 3); for (var j = 0; j < 8; j++) list.push(Math.min(1, (T0 + j * 3) / T_END)); }
    return loadAssets('half').then(function () {
      list.forEach(function (p) {
        st.pos = p;
        var cv = document.createElement('canvas'), r = draw('half', cv), fig = document.createElement('figure'), cap = document.createElement('figcaption');
        cv.style.width = '320px'; cv.style.border = '1px solid var(--line)';
        cap.textContent = st.mode === 'day' ? hhmm(r.state.minute) + ', soleil ' + r.state.mix.elevation.toFixed(0) + '°' : r.state.T.toFixed(0) + ' s, ' + r.state.key.name;
        fig.style.margin = 0; fig.appendChild(cv); fig.appendChild(cap); out.appendChild(fig);
      });
      st.pos = saved; return redraw();
    });
  }

  function open() { if (!st.ready) { fillSelects(); st.ready = redraw(); } else redraw(); }

  $('#an-mode').addEventListener('change', function () {
    setPlaying(false); st.mode = this.value; st.pos = st.mode === 'day' ? 0.25 : 0;
    $('#an-weather-wrap').hidden = st.mode !== 'day'; $('#an-time-wrap').hidden = st.mode === 'day';
    fillSelects(); $('#an-pos').value = Math.round(st.pos * 1000); redraw();
  });
  $('#an-play').addEventListener('click', function () { if (!st.playing && st.mode === 'weather' && st.pos >= 1) st.pos = 0; setPlaying(!st.playing); });
  $('#an-back').addEventListener('click', function () { step(-1); });
  $('#an-fwd').addEventListener('click', function () { step(1); });
  $('#an-pos').addEventListener('input', function () { st.pos = this.value / 1000; if (!st.playing) redraw(); });
  ['an-res', 'an-weather', 'an-time'].forEach(function (id) { $('#' + id).addEventListener('change', function () { redraw(); }); });
  $('#an-still').addEventListener('click', function () { setPlaying(false); $('#an-res').value = 'hd'; redraw(); });
  $('#an-strip').addEventListener('click', strip);

  window.AlpineAnim = {
    open: open, KEYS: KEYS, T_END: T_END, state: st, stateAt: stateAt, draw: draw, strip: strip, setPlaying: setPlaying, step: step, loadAssets: loadAssets,
    set: function (mode, pos, res) { $('#an-mode').value = mode; $('#an-mode').dispatchEvent(new Event('change')); st.pos = pos; $('#an-pos').value = Math.round(pos * 1000); if (res) $('#an-res').value = res; return redraw(); }
  };
})();
