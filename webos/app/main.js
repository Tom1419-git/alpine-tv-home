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
  var fxCanvas = document.getElementById('fx'), fxMix = null, fxTarget = null, fxLast = 0, fxSlow = 0, fxRunning = false;
  var FX_KEYS = ['rain', 'snow', 'wind', 'storm', 'showers'];
  var PHOTOS = ['riffelsee-lever', 'braies-matin'];
  var CREDITS = { 'riffelsee-lever': 'Photo : Deralpinbergsteiger, CC BY-SA 4.0', 'braies-matin': 'Photo : Eric Kilby, CC BY-SA 2.0' };
  var images = {}, weather = null, res = 'hd', busy = false, again = false;

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

  /**
   * Pluie et neige animees : la scene (photo, ciel, heure) est rendue une fois par minute, les gouttes et les flocons sont
   * redessines a part, sur un canevas transparent a demi-resolution, une trentaine de fois par seconde. L'intensite glisse
   * doucement vers la nouvelle valeur quand la meteo change. Si la TV est lente, la cadence tombe a 15 images par seconde.
   */
  function fxFrame(ts) {
    if (!fxTarget) { fxRunning = false; return; }
    var i, k;
    for (i = 0; i < FX_KEYS.length; i++) {
      k = FX_KEYS[i];
      fxMix[k] += (fxTarget[k] - fxMix[k]) * 0.04;
    }
    var active = fxMix.rain > 0.01 || fxMix.snow > 0.01 || fxMix.storm > 0.01 || fxTarget.rain > 0.01 || fxTarget.snow > 0.01 || fxTarget.storm > 0.01;
    var interval = fxSlow > 5 ? 66 : 33;
    if (active && ts - fxLast >= interval) {
      var t0 = Date.now();
      try { S.overlay(fxCanvas, { mix: fxMix, t: Date.now() / 1000 % 3600, res: 'half', seed: 7 }); } catch (e) { window.ALPINE_ERROR = String(e && e.stack || e); }
      fxLast = ts;
      fxSlow = Date.now() - t0 > 30 ? fxSlow + 1 : Math.max(0, fxSlow - 1);
    } else if (!active && fxCanvas.width > 1) {
      fxCanvas.getContext('2d').clearRect(0, 0, fxCanvas.width, fxCanvas.height);
      fxCanvas.width = 1; fxCanvas.height = 1;
    }
    requestAnimationFrame(fxFrame);
  }

  function setFx(mix) {
    var i, k;
    fxTarget = {};
    for (i = 0; i < FX_KEYS.length; i++) { k = FX_KEYS[i]; fxTarget[k] = mix[k] || 0; }
    fxTarget.wind = mix.wind === undefined ? 0.18 : mix.wind;
    if (!fxMix) { fxMix = {}; for (i = 0; i < FX_KEYS.length; i++) { k = FX_KEYS[i]; fxMix[k] = fxTarget[k]; } }
    if (!fxRunning) { fxRunning = true; requestAnimationFrame(fxFrame); }
  }

  /** La photo du jour : celle du reglage, ou une photo differente chaque jour avec "auto". */
  function photoFor(now) {
    if (C.photo === 'auto') {
      var day = Math.floor((now.getTime() - now.getTimezoneOffset() * 60000) / 86400000);
      return PHOTOS[day % PHOTOS.length];
    }
    return PHOTOS.indexOf(C.photo) >= 0 ? C.photo : PHOTOS[0];
  }

  /** Dessine la scene courante dans le canevas cache, puis la fond avec l'ancienne. */
  function draw() {
    if (busy) { again = true; return; }
    busy = true;
    var now = new Date(), t0 = Date.now(), cv = canvases[1 - front], pid = photoFor(now);
    try {
      var mix = mixNow(now);
      setFx(mix);
      S.render(cv, { mix: mix, noWeather: true, t: now.getTime() / 1000 % 3600, state: 'veille', res: res, data: { pack: D.packs[pid] },
        img: { photo: images[pid].photo, fg: images[pid].fg, cards: [] }, seed: 7, time: timeText(now), dateText: dateText(now) });
      canvases[front].className = '';
      cv.className = 'on';
      front = 1 - front;
      document.getElementById('credit').firstChild.nodeValue = CREDITS[pid] + ' · Météo : Open-Meteo';
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
    var ids = C.photo === 'auto' ? PHOTOS : [photoFor(new Date())], jobs = [];
    ids.forEach(function (id) { jobs.push(load(IM[id].photo), load(IM[id].fg)); });
    jobs.push(document.fonts && document.fonts.load ? document.fonts.load('200 100px Outfit') : null,
              document.fonts && document.fonts.load ? document.fonts.load('400 20px Outfit') : null);
    Promise.all(jobs)
      .then(function (r) {
        ids.forEach(function (id, i) { images[id] = { photo: r[2 * i], fg: r[2 * i + 1] }; });
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
