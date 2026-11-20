/*
 * Accueil alpin pour LG webOS : la scene de l'application Android (photo etalonnee par la hauteur reelle du soleil, meteo
 * d'Open-Meteo, heure et date) dessinee en plein ecran. Meme moteur que l'apercu (grade.js, scene.js). La scene est
 * recalculee chaque minute et fondue avec la precedente ; la meteo est relue toutes les 30 minutes.
 * ES5 exprès : webOS 4 embarque Chromium 53.
 */
(function () {
  'use strict';
  var C = window.ALPINE_CONFIG, G = window.AlpineGrade, S = window.AlpineScene, D = window.ALPINE_DATA, IM = window.ALPINE_IMAGES;
  var CODE = { 0: 'clair', 1: 'clair', 2: 'partiel', 3: 'couvert', 45: 'brouillard', 48: 'brouillard', 51: 'bruine', 53: 'bruine',
    55: 'bruine', 56: 'bruine', 57: 'bruine', 61: 'pluie', 63: 'pluie', 65: 'pluie', 66: 'pluie', 67: 'pluie', 71: 'neige', 73: 'neige',
    75: 'neige', 77: 'neige', 80: 'averses', 81: 'averses', 82: 'averses', 85: 'neige', 86: 'neige', 95: 'orage', 96: 'orage', 99: 'orage' };
  var DAYS = ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'];
  var MONTHS = ['JANVIER', 'FÉVRIER', 'MARS', 'AVRIL', 'MAI', 'JUIN', 'JUILLET', 'AOÛT', 'SEPTEMBRE', 'OCTOBRE', 'NOVEMBRE', 'DÉCEMBRE'];
  var WEATHER_MS = 30 * 60 * 1000;
  var canvases = [document.getElementById('a'), document.getElementById('b')], front = 1;
  var images = null, weather = null, res = 'hd', busy = false, again = false;

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function load(src) { return new Promise(function (ok, ko) { var i = new Image(); i.onload = function () { ok(i); }; i.onerror = ko; i.src = src; }); }

  function url() {
    return 'https://api.open-meteo.com/v1/forecast?latitude=' + C.lat + '&longitude=' + C.lon +
      '&current=temperature_2m,weather_code,cloud_cover,precipitation,visibility,wind_speed_10m&timezone=auto';
  }

  /** Meteo courante d'Open-Meteo. Sans reseau, on garde la derniere valeur (et celle du dernier lancement). */
  function fetchWeather() {
    return fetch(url()).then(function (r) { return r.json(); }).then(function (j) {
      var c = j.current;
      if (!c) return;
      weather = { code: c.weather_code, temp: c.temperature_2m, cloudPct: c.cloud_cover, precipMm: c.precipitation,
                  visibilityM: c.visibility, windKmh: c.wind_speed_10m };
      try { localStorage.setItem('alpine-weather', JSON.stringify(weather)); } catch (e) { /* stockage indisponible */ }
    }).catch(function () { /* hors ligne : on garde la derniere meteo */ });
  }

  function mixNow(now) {
    var offset = -now.getTimezoneOffset(), minute = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
    var sky = G.skyAt(now.getFullYear(), now.getMonth() + 1, now.getDate(), minute, C.lat, C.lon, offset);
    var cond = weather && CODE[weather.code] || 'clair';
    var tg = G.deriveTargets(cond, weather ? { cloudPct: weather.cloudPct, precipMm: weather.precipMm,
      visibilityM: weather.visibilityM, windKmh: weather.windKmh } : null);
    var mix = G.newMix(), k;
    for (k in tg) if (tg.hasOwnProperty(k)) mix[k] = tg[k];
    ['elevation', 'azimuth', 'moonElevation', 'moonAzimuth', 'evening'].forEach(function (key) { mix[key] = sky[key]; });
    return mix;
  }

  function timeText(now) {
    var h = now.getHours();
    if (!C.format24) { var h12 = h % 12 || 12; return pad(h12) + ':' + pad(now.getMinutes()); }
    return pad(h) + ':' + pad(now.getMinutes());
  }

  function dateText(now) {
    var t = DAYS[now.getDay()] + ' ' + now.getDate() + ' ' + MONTHS[now.getMonth()];
    if (weather && typeof weather.temp === 'number') {
      var v = C.fahrenheit ? weather.temp * 9 / 5 + 32 : weather.temp;
      t += '   ·   ' + Math.round(v) + '°';
    }
    return t;
  }

  /** Dessine la scene courante dans le canevas cache, puis la fond avec l'ancienne. */
  function draw() {
    if (busy) { again = true; return; }
    busy = true;
    var now = new Date(), t0 = Date.now(), cv = canvases[1 - front];
    try {
      S.render(cv, { mix: mixNow(now), t: now.getTime() / 1000 % 3600, state: 'veille', res: res, data: D,
        img: { photo: images.photo, fg: images.fg, cards: [] }, seed: 7, time: timeText(now), dateText: dateText(now) });
      canvases[front].className = '';
      cv.className = 'on';
      front = 1 - front;
    } catch (e) { window.ALPINE_ERROR = String(e && e.stack || e); }
    if (Date.now() - t0 > 4500 && res === 'hd') res = 'half';   // TV lente : on dessine en 960 x 540, etire a l'ecran
    busy = false;
    window.ALPINE_RENDERS = (window.ALPINE_RENDERS || 0) + 1;
    if (again) { again = false; setTimeout(draw, 50); }
  }

  function scheduleMinute() {
    var now = Date.now();
    setTimeout(function () { draw(); scheduleMinute(); }, 60000 - now % 60000 + 100);
  }

  function start() {
    try { weather = JSON.parse(localStorage.getItem('alpine-weather')); } catch (e) { weather = null; }
    Promise.all([load(IM.photo), load(IM.fg), document.fonts && document.fonts.load ? document.fonts.load('200 100px Outfit') : null,
                 document.fonts && document.fonts.load ? document.fonts.load('400 20px Outfit') : null])
      .then(function (r) {
        images = { photo: r[0], fg: r[1] };
        draw();
        scheduleMinute();
        fetchWeather().then(draw);
        setInterval(function () { fetchWeather().then(draw); }, WEATHER_MS);
      }).catch(function (e) { window.ALPINE_ERROR = String(e); });
  }

  document.addEventListener('keydown', function (e) {
    if (e.keyCode === 461 || e.keyCode === 27) {   // Retour de la telecommande LG, ou Echap
      if (window.webOS && window.webOS.platformBack) window.webOS.platformBack(); else window.close();
    }
  });
  start();
})();
