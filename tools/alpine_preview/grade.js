/*
 * Port JavaScript du moteur de scene de l'accueil alpin (SceneGrade, SceneMix, ContrastPlan, SceneZones) : memes
 * tables, memes formules, pour que l'interface de comparaison montre ce que calcule l'application. Un test compare la
 * table des points et des valeurs de reference calculees ici et verifiees cote Java.
 */
(function (root) {
  'use strict';
  var L = root.AlpineLayout || require('./layout.js');

  // KEYFRAMES-BEGIN
  // hauteur du soleil (deg), exposition, contraste, saturation, gain R, G, B, voile R, G, B, opacite du voile
  var KEYS = [
    [-20, 0.30, 1.06, 0.78, 0.62, 0.78, 1.16, 0.05, 0.08, 0.20, 0.00],
    [-12, 0.38, 1.06, 0.84, 0.74, 0.82, 1.14, 0.20, 0.18, 0.45, 0.06],
    [-6, 0.60, 1.06, 0.96, 0.96, 0.88, 1.08, 0.50, 0.34, 0.62, 0.10],
    [-2, 0.84, 1.07, 1.10, 1.12, 0.97, 0.90, 0.95, 0.60, 0.45, 0.08],
    [0, 0.94, 1.08, 1.20, 1.18, 0.98, 0.84, 1.00, 0.62, 0.35, 0.06],
    [4, 0.98, 1.07, 1.15, 1.12, 0.99, 0.90, 1.00, 0.72, 0.50, 0.04],
    [12, 1.00, 1.05, 1.08, 1.04, 1.00, 0.97, 1.00, 0.85, 0.70, 0.02],
    [25, 1.00, 1.05, 1.05, 0.99, 1.00, 1.02, 0.80, 0.90, 1.00, 0.01],
    [45, 1.00, 1.06, 1.06, 0.96, 1.00, 1.06, 0.70, 0.85, 1.00, 0.00],
    [70, 1.00, 1.06, 1.06, 0.96, 1.00, 1.06, 0.70, 0.85, 1.00, 0.00]];
  // KEYFRAMES-END

  var LUMA = [0.2126, 0.7152, 0.0722];
  var CLOUD_ALPHA = 0.35, CLOUD_RGB = [0.62, 0.65, 0.70], FOG_ALPHA = 0.60, FOG_RGB = [0.82, 0.85, 0.88];
  var SUN_GLOW_ALPHA = 0.40, SUN_RGB = [1.00, 0.93, 0.80], STORM_ALPHA = 0.28, STORM_RGB = [0.04, 0.05, 0.07];
  var PILL_GLASS_ALPHA = 0.86, PILL_GLASS_RGB = [0.055, 0.078, 0.102];
  var DARK_L = 0.0074, MAX_VEIL = 0.60, TEXT_RATIO = 4.5, CLOCK_RATIO = 3.0;
  var GW = 32, GH = 18, PREFER_TEXT = 0.28, LOW_TEXT = 0.5;

  function clamp(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
  function smoothstep(a, b, x) { var t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); }

  /** Reglages de base a la hauteur du soleil : interpolation lisse entre les points de la table. */
  function base(elevation, evening) {
    var k = KEYS, e = Math.max(k[0][0], Math.min(k[k.length - 1][0], elevation)), i = 0, c;
    while (i < k.length - 2 && e > k[i + 1][0]) i++;
    var t = smoothstep(k[i][0], k[i + 1][0], e), out = [];
    for (c = 0; c < 10; c++) out[c] = k[i][c + 1] + (k[i + 1][c + 1] - k[i][c + 1]) * t;
    var warm = evening * smoothstep(-10, 0, e) * (1 - smoothstep(0, 14, e));
    out[3] *= 1 + 0.06 * warm;
    out[5] *= 1 - 0.07 * warm;
    return out;
  }

  // ---- meteo : intensites cibles par condition -------------------------------------------------------------
  var CONDITIONS = {
    clair: { cloud: 0, fog: 0, rain: 0, snow: 0, storm: 0, showers: 0 },
    partiel: { cloud: 0.35, fog: 0, rain: 0, snow: 0, storm: 0, showers: 0 },
    couvert: { cloud: 1, fog: 0, rain: 0, snow: 0, storm: 0, showers: 0 },
    brouillard: { cloud: 0.45, fog: 0.85, rain: 0, snow: 0, storm: 0, showers: 0 },
    bruine: { cloud: 0.85, fog: 0, rain: 0.30, snow: 0, storm: 0, showers: 0 },
    pluie: { cloud: 0.95, fog: 0, rain: 0.75, snow: 0, storm: 0, showers: 0 },
    averses: { cloud: 0.90, fog: 0, rain: 0.90, snow: 0, storm: 0, showers: 1 },
    neige: { cloud: 0.85, fog: 0, rain: 0, snow: 0.85, storm: 0, showers: 0 },
    orage: { cloud: 1, fog: 0, rain: 0.90, snow: 0, storm: 1, showers: 0 }
  };

  /** Etalonnage complet : la base modifiee par la meteo (m : cloud, fog, rain, snow, storm). */
  function compute(elevation, evening, m) {
    var b = base(elevation, evening), c = m.cloud, f = m.fog, r = m.rain, s = m.snow, t = m.storm;
    var exposure = b[0] * (1 - 0.16 * c - 0.18 * r - 0.06 * s - 0.52 * t - 0.05 * f);
    var contrast = b[1] * (1 - 0.20 * c - 0.25 * f - 0.18 * r - 0.22 * s - 0.10 * t);
    var sat = b[2] * Math.max(0.25, 1 - 0.35 * c - 0.45 * f - 0.38 * r - 0.40 * s - 0.50 * t);
    var cool = 0.05 * r + 0.04 * s + 0.04 * c + 0.06 * t;
    var gr = b[3] * (1 - cool), gg = b[4], gb = b[5] * (1 + 0.8 * cool);
    var a = b[9], vr = b[6], vg = b[7], vb = b[8];
    var layers = [[0.74, 0.77, 0.82, 0.12 * c], [0.80, 0.84, 0.88, 0.34 * f], [0.92, 0.95, 1.00, 0.30 * s], [0.20, 0.24, 0.32, 0.18 * t]];
    for (var l = 0; l < 4; l++) {
      var la = layers[l][3], na = 1 - (1 - a) * (1 - la);
      if (na > 0) {
        vr = (vr * a * (1 - la) + layers[l][0] * la) / na;
        vg = (vg * a * (1 - la) + layers[l][1] * la) / na;
        vb = (vb * a * (1 - la) + layers[l][2] * la) / na;
      }
      a = na;
    }
    return { exposure: exposure, contrast: contrast, saturation: sat, gainR: gr, gainG: gg, gainB: gb, veilR: vr, veilG: vg, veilB: vb, veilAlpha: a };
  }

  /** Matrice 3x4 (lignes R, G, B ; colonnes R, G, B, decalage en 0-1) : meme calcul que Grade.toMatrix cote Java. */
  function matrix(g) {
    var k = g.contrast * g.exposure, off = 0.5 * (1 - g.contrast), keep = 1 - g.veilAlpha, out = [];
    var gain = [g.gainR, g.gainG, g.gainB], veil = [g.veilR, g.veilG, g.veilB];
    for (var i = 0; i < 3; i++) {
      for (var j = 0; j < 3; j++) out.push(keep * gain[i] * ((1 - g.saturation) * LUMA[j] + (i === j ? g.saturation : 0)) * k);
      out.push(keep * gain[i] * off + veil[i] * g.veilAlpha);
    }
    return out;
  }

  /** Applique l'etalonnage a une couleur (0-1), voile compris. */
  function apply(g, rgb) {
    var k = g.contrast * g.exposure, off = 0.5 * (1 - g.contrast);
    var vr = k * rgb[0] + off, vg = k * rgb[1] + off, vb = k * rgb[2] + off, l = LUMA[0] * vr + LUMA[1] * vg + LUMA[2] * vb;
    var r = clamp(g.gainR * (g.saturation * vr + (1 - g.saturation) * l));
    var gg = clamp(g.gainG * (g.saturation * vg + (1 - g.saturation) * l));
    var b = clamp(g.gainB * (g.saturation * vb + (1 - g.saturation) * l));
    return [r + (g.veilR - r) * g.veilAlpha, gg + (g.veilG - gg) * g.veilAlpha, b + (g.veilB - b) * g.veilAlpha];
  }

  function dayFactor(e) { return 0.18 + 0.82 * smoothstep(-13, 6, e); }
  function sunStrength(e, m) { return smoothstep(-3, 3, e) * (1 - 0.92 * m.cloud) * (1 - 0.7 * m.rain) * (1 - 0.35 * m.fog) * (1 - 0.85 * m.snow) * (1 - m.storm); }
  function sunDiscPale(e, m) { return smoothstep(-2, 4, e) * m.fog * (1 - m.rain) * (1 - m.snow) * (1 - m.storm); }
  function starAlpha(e, m) { return (1 - smoothstep(-14, -5, e)) * (1 - m.cloud) * (1 - m.rain) * (1 - m.snow) * (1 - m.fog) * (1 - m.storm); }
  function skyCover(m) { return clamp(Math.max(m.cloud, 0.9 * m.storm)); }

  // ---- contraste ---------------------------------------------------------------------------------------------
  function lin(c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  function luminance(r, g, b) { return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b); }
  function ratio(a, b) { var hi = Math.max(a, b), lo = Math.min(a, b); return (hi + 0.05) / (lo + 0.05); }

  function srgb(l) { l = Math.max(0, Math.min(1, l)); return l <= 0.0031308 ? l * 12.92 : 1.055 * Math.pow(l, 1 / 2.4) - 0.055; }
  function decide(lMin, lMax, thr, preferWhite) {
    preferWhite = preferWhite === undefined ? 0.50 : preferWhite;
    var capWhite = 1.05 / thr - 0.05, sMax = srgb(lMax), sMin = srgb(lMin);
    var aWhite = lMax <= capWhite ? 0 : 1 - srgb(capWhite) / Math.max(sMax, 1e-4);
    var needMin = thr * (DARK_L + 0.05) - 0.05, aDark = lMin >= needMin ? 0 : (srgb(needMin) - sMin) / Math.max(1 - sMin, 1e-4);
    var dark = aWhite > preferWhite && aDark < aWhite - 1e-4, a = Math.max(0, dark ? aDark : aWhite);
    a = a === 0 ? 0 : Math.min(1, a + (dark ? 0.09 : 0.08));
    var lo = dark ? lin(sMin + a * (1 - sMin)) : lin(sMin * (1 - a)), hi = dark ? lin(sMax + a * (1 - sMax)) : lin(sMax * (1 - a));
    var r = dark ? ratio(lo, DARK_L) : ratio(hi, 1);
    return { darkText: dark, veilAlpha: a, ratio: r, ok: a <= MAX_VEIL && r + 1e-3 >= thr };
  }
  function decideWhite(lMin, lMax, thr) {
    var cap = 1.05 / thr - 0.05, a = lMax <= cap ? 0 : Math.min(1, 1 - srgb(cap) / Math.max(srgb(lMax), 1e-4) + 0.04);
    var r = ratio(lin(srgb(lMax) * (1 - a)), 1);
    return { darkText: false, veilAlpha: a, ratio: r, ok: a <= MAX_VEIL && r + 1e-3 >= thr };
  }

  var ZONES = ['CLOCK', 'DATE', 'GREETING', 'PILL_0', 'PILL_1', 'PILL_2', 'HERO'];
  function threshold(z) { return z === 'CLOCK' ? CLOCK_RATIO : TEXT_RATIO; }
  function whiteFixed(z) { return z.indexOf('PILL') === 0 || z === 'HERO'; }

  /** Rectangle {gauche, haut, droite, bas} de la zone de texte sur un ecran de w x h (comme SceneZones.rect). */
  function rect(z, w, h) {
    var clock = L.clockVisible(w, h), lines = L.dateLines(w, h), half = w * 0.20;
    switch (z) {
      case 'CLOCK': return [clock[0], clock[1], clock[2], clock[3] - 0.10 * (clock[3] - clock[1])];   // sans le fondu de la rive
      case 'DATE': return [w / 2 - half, lines[0] - lines[2] * 1.1, w / 2 + half, lines[0] + lines[2] * 0.3];
      case 'GREETING': return [w / 2 - half * 0.6, lines[1] - lines[3] * 1.1, w / 2 + half * 0.6, lines[1] + lines[3] * 0.3];
      case 'HERO': var top = L.cardTop(h); return [w * 0.05, top - h * 0.16, w * 0.27, top - h * 0.05];
      default: var p = L.pill(+z.slice(-1), w, h); return [p[0], p[1], p[0] + p[2], p[1] + p[3]];
    }
  }

  function crestY(crest, x, w, h) {
    if (!crest || crest.length < 2) return h * 0.4;
    var f = Math.max(0, Math.min(1, x / w)) * (crest.length - 1), i = Math.min(crest.length - 2, Math.floor(f));
    return (crest[i] + (crest[i + 1] - crest[i]) * (f - i)) * h;
  }

  function blend(c, rgb, a, k) { if (a <= 0) return; k = k === undefined ? 1 : k; c[0] += (rgb[0] * k - c[0]) * a; c[1] += (rgb[1] * k - c[1]) * a; c[2] += (rgb[2] * k - c[2]) * a; }

  /** Luminances relatives {min, max} du fond de la zone : pire cas dans les deux sens pour nuages, halo et brouillard. */
  function range(z, w, h, grid, crest, g, m) {
    var r = rect(z, w, h), cw = w / GW, ch = h / GH;
    var x0 = Math.max(0, Math.floor(r[0] / cw)), x1 = Math.min(GW - 1, Math.floor(r[2] / cw));
    var y0 = Math.max(0, Math.floor(r[1] / ch)), y1 = Math.min(GH - 1, Math.floor(r[3] / ch));
    var cover = skyCover(m) * CLOUD_ALPHA, glow = SUN_GLOW_ALPHA * sunStrength(m.elevation, m), fog = FOG_ALPHA * haze(m), storm = STORM_ALPHA * m.storm;
    var day = dayFactor(m.elevation), lo = 2, hi = -1, pill = z.indexOf('PILL') === 0;
    for (var y = y0; y <= y1; y++) for (var x = x0; x <= x1; x++) {
      var cy = crestY(crest, (x + 0.5) * cw, w, h), maybeSky = y * ch < cy, maybeGround = (y + 1) * ch > cy;
      for (var pass = 0; pass < 2; pass++) {
        var sky = pass === 0;
        if (sky ? !maybeSky : !maybeGround) continue;
        var o = (y * GW + x) * 3, base0 = apply(g, [grid[o] / 255, grid[o + 1] / 255, grid[o + 2] / 255]);
        for (var mask = 0; mask < 8; mask++) {
          var c = base0.slice();
          if ((mask & 1) && sky) blend(c, CLOUD_RGB, cover, day);
          if ((mask & 2) && sky) blend(c, SUN_RGB, glow);
          if (mask & 4) blend(c, FOG_RGB, fog, day);
          blend(c, STORM_RGB, storm);
          if (pill) blend(c, PILL_GLASS_RGB, PILL_GLASS_ALPHA);
          var l = luminance(c[0], c[1], c[2]);
          if (l < lo) lo = l;
          if (l > hi) hi = l;
        }
      }
    }
    var fixed = whiteFixed(z);   // texte blanc impose : les details clairs du premier plan comptent, la marge haute est large
    return [lo > hi ? 0 : lo * (fixed ? 0.40 : (z === 'CLOCK' ? 0 : LOW_TEXT)), hi < 0 ? 0 : Math.min(1, hi * (fixed ? 4 : 1.25))];
  }

  function plan(z, w, h, grid, crest, g, m) {
    var r = range(z, w, h, grid, crest, g, m);
    var p = whiteFixed(z) ? decideWhite(r[0], r[1], threshold(z)) : decide(r[0], r[1], threshold(z), z === 'CLOCK' ? 0.50 : PREFER_TEXT);
    p.zone = z; p.lMin = r[0]; p.lMax = r[1]; p.threshold = threshold(z);
    return p;
  }

  /** Les huit moments de la comparaison : {hauteur du soleil, azimut, matin 0 ou soir 1} (comme SolarPhase.previewSky). */
  var PHASES = {
    'avant-lever': [-7, 95, 0], 'lever': [0.5, 100, 0], 'matin': [18, 140, 0], 'midi': [40, 180, 0],
    'apres-midi': [20, 230, 1], 'coucher': [0.5, 260, 1], 'crepuscule': [-6, 265, 1], 'nuit': [-20, 180, 1]
  };

  /** Etat complet d'un apercu (phase, meteo) : mix, etalonnage, soleil. */
  function state(phase, condition) {
    var p = PHASES[phase], c = CONDITIONS[condition];
    var m = { elevation: p[0], azimuth: p[1], evening: p[2], moonElevation: p[0] < -8 ? 35 : -20, moonAzimuth: 200,
      cloud: c.cloud, fog: c.fog, rain: c.rain, snow: c.snow, storm: c.storm, showers: c.showers };
    return { mix: m, grade: compute(m.elevation, m.evening, m) };
  }

  // ---- meteo : donnees reelles vers cibles visuelles (meme calcul que SceneMix.targetWeather) -------------------------
    function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    /**
     * cond : condition tiree du code WMO ; data (facultatif) : {cloudPct, precipMm, windKmh, visibilityM} (-1 si inconnu).
     * Sans donnees, les valeurs par defaut de la condition ; avec donnees, les observations pilotent l'intensite.
     */
    function deriveTargets(cond, data) {
      var b = CONDITIONS[cond], t = { cloud: b.cloud, fog: b.fog, rain: b.rain, snow: b.snow, storm: b.storm, showers: b.showers, wind: 0.18 };
      if (!data) return t;
      var wet = b.rain > 0 || b.snow > 0;
      if (data.cloudPct >= 0) t.cloud = Math.max(clamp01(data.cloudPct / 100), wet ? 0.7 : 0, cond === 'couvert' ? 0.85 : 0);
      if (data.precipMm >= 0) {
        if (b.snow > 0) t.snow = clamp01(0.22 + 0.78 * (1 - Math.exp(-data.precipMm / 1.2)));
        else if (b.rain > 0) {
          var r = clamp01(0.12 + 0.88 * (1 - Math.exp(-data.precipMm / 3)));
          t.rain = cond === 'bruine' ? Math.min(0.35, r) : Math.max(0.4, r);
        }
      }
      if (data.visibilityM >= 0) {
        var f = 0.9 * (1 - smoothstep(200, 6000, data.visibilityM));
        t.fog = Math.max(b.fog > 0 ? 0.45 : 0, f * (wet ? 0.5 : 1));
      }
      if (data.windKmh >= 0) t.wind = clamp01(data.windKmh / 45);
      return t;
    }
    /** Brume ressentie : le brouillard, mais aussi le voile de la pluie et de la neige (la visibilite baisse). */
    function haze(m) { return Math.max(m.fog, 0.40 * m.rain, 0.35 * m.snow, 0.12 * m.cloud); }

    var TAU = 0.65, TAU_WEATHER = 3.0;   // le ciel suit vite, la meteo glisse en une dizaine de secondes
    function newMix() { return { elevation: 40, azimuth: 180, moonElevation: -20, moonAzimuth: 200, evening: 0, cloud: 0, fog: 0, rain: 0, snow: 0, storm: 0, showers: 0, wind: 0.18, target: null }; }
    /** Fait avancer le melange de dt secondes vers ses cibles (lissage exponentiel, comme SceneMix.step). */
    function stepMix(m, dt) {
      var d = Math.max(0, Math.min(dt, 0.25)), k = 1 - Math.exp(-d / TAU), kw = 1 - Math.exp(-d / TAU_WEATHER), t = m.target;
      if (!t) return m;
      ['elevation', 'moonElevation', 'evening'].forEach(function (f) { if (t[f] !== undefined) m[f] += (t[f] - m[f]) * k; });
    ['cloud', 'fog', 'rain', 'snow', 'storm', 'showers', 'wind'].forEach(function (f) { if (t[f] !== undefined) m[f] += (t[f] - m[f]) * kw; });
      ['azimuth', 'moonAzimuth'].forEach(function (f) { if (t[f] !== undefined) { var d = ((t[f] - m[f]) % 360 + 540) % 360 - 180; m[f] += d * k; } });
      return m;
    }

    // ---- soleil et lune : equations NOAA (comme SolarPhase.sunPosition / moonPosition) --------------------------------
    function dayOfYear(y, mo, d) { return Math.round((Date.UTC(y, mo - 1, d) - Date.UTC(y, 0, 1)) / 86400000) + 1; }
    function horizontal(ha, decl, lat) {
      var sinEl = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(ha), el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
      var az = Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(lat) - Math.tan(decl) * Math.cos(lat));
      return [(az * 180 / Math.PI + 180 + 360) % 360, el * 180 / Math.PI];
    }
    var RAD = Math.PI / 180;
    function sunPosition(y, mo, d, minute, lat, lon, offsetMin) {
      var g = 2 * Math.PI / 365 * (dayOfYear(y, mo, d) - 1 + (minute - 720) / 1440);
      var eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
      var decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
      var tst = minute + eq + 4 * lon - offsetMin;
      return horizontal((tst / 4 - 180) * RAD, decl, lat * RAD);
    }
    function moonPhase(y, mo, d, minute, offsetMin) {
      var days = (Date.UTC(y, mo - 1, d) - Date.UTC(2000, 0, 6)) / 86400000 + (minute - offsetMin) / 1440 - 18.2 / 24, p = (days / 29.530588853) % 1;
      return p < 0 ? p + 1 : p;
    }
    function moonPosition(y, mo, d, minute, lat, lon, offsetMin) {
      var day = dayOfYear(y, mo, d) - 1 + (minute - 720) / 1440, sunLon = (day - 79.5) * 360 / 365.25 * RAD, moonLon = sunLon + 2 * Math.PI * moonPhase(y, mo, d, minute, offsetMin);
      var obl = 23.44 * RAD, decl = Math.asin(Math.sin(obl) * Math.sin(moonLon)), raM = Math.atan2(Math.cos(obl) * Math.sin(moonLon), Math.cos(moonLon)), raS = Math.atan2(Math.cos(obl) * Math.sin(sunLon), Math.cos(sunLon));
      var sunHa = ((minute + 4 * lon - offsetMin) / 4 - 180) * RAD;
      return horizontal(sunHa - (raM - raS), decl, lat * RAD);
    }
    /** Ciel a une heure locale (minutes) d'un jour : {elevation, azimuth, moonElevation, moonAzimuth, evening}. */
    function skyAt(y, mo, d, minute, lat, lon, offsetMin) {
      var s = sunPosition(y, mo, d, minute, lat, lon, offsetMin), mo2 = moonPosition(y, mo, d, minute, lat, lon, offsetMin);
      return { elevation: s[1], azimuth: s[0], moonElevation: mo2[1], moonAzimuth: mo2[0], evening: minute >= 720 ? 1 : 0 };
    }
    /** Lever et coucher (minutes locales) : passage de la hauteur par -0,833 degre, cherche minute par minute. */
    function riseSet(y, mo, d, lat, lon, offsetMin) {
      var rise = -1, set = -1, prev = sunPosition(y, mo, d, 0, lat, lon, offsetMin)[1];
      for (var mi = 1; mi < 1440; mi++) {
        var e = sunPosition(y, mo, d, mi, lat, lon, offsetMin)[1];
        if (prev < -0.833 && e >= -0.833 && rise < 0) rise = mi;
        if (prev >= -0.833 && e < -0.833) set = mi;
        prev = e;
      }
      return [rise, set];
    }
    function phaseName(e, evening) { return e < -8 ? 'nuit' : (e < 6 ? (evening ? 'crepuscule' : 'avant-lever') : (e < 25 ? (evening ? 'coucher' : 'matin') : 'midi')); }

  var api = {
    deriveTargets: deriveTargets, haze: haze, newMix: newMix, stepMix: stepMix, skyAt: skyAt, riseSet: riseSet, sunPosition: sunPosition, moonPhase: moonPhase, phaseName: phaseName,
    KEYS: KEYS, CONDITIONS: CONDITIONS, PHASES: PHASES, ZONES: ZONES, GW: GW, GH: GH, base: base, compute: compute, matrix: matrix,
    apply: apply, smoothstep: smoothstep, sunStrength: sunStrength, dayFactor: dayFactor, sunDiscPale: sunDiscPale, starAlpha: starAlpha,
    skyCover: skyCover, luminance: luminance, ratio: ratio, decide: decide, decideWhite: decideWhite, threshold: threshold,
    whiteFixed: whiteFixed, rect: rect, crestY: crestY, range: range, plan: plan, state: state, DARK_L: DARK_L, MAX_VEIL: MAX_VEIL,
    consts: { CLOUD_ALPHA: CLOUD_ALPHA, CLOUD_RGB: CLOUD_RGB, FOG_ALPHA: FOG_ALPHA, FOG_RGB: FOG_RGB, SUN_GLOW_ALPHA: SUN_GLOW_ALPHA,
      SUN_RGB: SUN_RGB, STORM_ALPHA: STORM_ALPHA, STORM_RGB: STORM_RGB, PILL_GLASS_ALPHA: PILL_GLASS_ALPHA, PILL_GLASS_RGB: PILL_GLASS_RGB }
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.AlpineGrade = api;
})(typeof window !== 'undefined' ? window : this);
