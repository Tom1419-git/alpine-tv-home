/*
 * Rendu d'un apercu de l'accueil alpin dans un canvas. Meme ordre de calques que AlpineHomeView.java : la photo etalonnee
 * (meme matrice de couleur que l'application, appliquee pixel par pixel), le ciel decoupe par la crete (soleil, lune,
 * etoiles, nuages), la brume, les voiles de lisibilite, l'heure et la date, le premier plan (la rive, qui coupe le bas des
 * chiffres), la meteo de premier plan, un voile sous le dock, puis les vues de l'application (pilules, dock, bloc musique).
 *
 * C'est une APPROXIMATION du rendu Android : les formes de l'interface sont redessinees ici, la meteo est une image fixe
 * de ce que l'application anime, et le navigateur anti-crenele le texte autrement. Le rendu du canvas demande un serveur
 * http local (un navigateur interdit de relire les pixels d'un fichier ouvert directement).
 */
(function (root) {
  'use strict';
  var L = root.AlpineLayout, G = root.AlpineGrade;
  var W = L.W, H = L.H;

  var GREETING = { 'avant-lever': 'BONJOUR', 'lever': 'BONJOUR', 'matin': 'BONNE JOURNÉE', 'midi': 'BONNE JOURNÉE',
    'apres-midi': 'BONNE JOURNÉE', 'coucher': 'BONSOIR', 'crepuscule': 'BONSOIR', 'nuit': 'BONNE NUIT' };
  /** Meme regle que HomeState.greetingPhase : l'heure de la journee, pas la lumiere. */
  function greetingAt(h) { return h >= 5 && h < 12 ? 'BONJOUR' : h >= 12 && h < 17 ? 'BONNE JOURNÉE' : h >= 17 && h < 22 ? 'BONSOIR' : 'BONNE NUIT'; }
  var FONT = 'Outfit, sans-serif';

  function rng(seed) {
    var a = seed >>> 0;
    return function () {
      a |= 0; a = a + 0x6D2B79F5 | 0;
      var t = Math.imul(a ^ a >>> 15, 1 | a);
      t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
      return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
  }
  function layer(w, h) { var c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
  function rr(ctx, x, y, w, h, r) {
    ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
  }
  function rgba(c, a) { return 'rgba(' + Math.round(c[0] * 255) + ',' + Math.round(c[1] * 255) + ',' + Math.round(c[2] * 255) + ',' + a + ')'; }
  function mix(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }
  var smooth = G.smoothstep;

  /** Applique la matrice d'etalonnage a l'image du canvas, pixel par pixel (meme calcul que ColorMatrix cote Android). */
  function gradePixels(ctx, w, h, g) {
    var mt = G.matrix(g), img = ctx.getImageData(0, 0, w, h), d = img.data, n = d.length, i, r, gg, b;
    for (i = 0; i < n; i += 4) {
      r = d[i] / 255; gg = d[i + 1] / 255; b = d[i + 2] / 255;
      d[i] = (mt[0] * r + mt[1] * gg + mt[2] * b + mt[3]) * 255;
      d[i + 1] = (mt[4] * r + mt[5] * gg + mt[6] * b + mt[7]) * 255;
      d[i + 2] = (mt[8] * r + mt[9] * gg + mt[10] * b + mt[11]) * 255;
    }
    ctx.putImageData(img, 0, 0);
  }

  function radial(ctx, x, y, rx, ry, color, a) {
    if (a <= 0.004) return;
    ctx.save(); ctx.translate(x, y); ctx.scale(rx, ry);
    var g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
    g.addColorStop(0, rgba(color, Math.min(1, a))); g.addColorStop(1, rgba(color, 0));
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, 1, 0, 6.2832); ctx.fill(); ctx.restore();
  }

  function bodyX(az) { var d = ((az - 180) % 360 + 540) % 360 - 180; return W * (0.5 + Math.max(-0.7, Math.min(0.7, d / 130))); }
  function bodyY(el) { var hz = 0.46 * H; return hz - Math.max(-10, el) / 55 * hz * 0.85; }
  function moonY(el) { return Math.min(0.09 * H, bodyY(el)); }   // la lune reste au-dessus de la salutation : jamais derriere un texte

  // ---- ciel (decoupe par la crete : derriere les montagnes) ---------------------------------------------------------

  function skyClip(ctx, crest) {
    ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(W, 0);
    for (var i = crest.length - 1; i >= 0; i--) ctx.lineTo(W * i / (crest.length - 1), crest[i] * H);
    ctx.closePath(); ctx.clip();
  }

  function drawSky(ctx, m, st, seed, t) {
    var r = rng(seed), i, a;
    a = G.starAlpha(m.elevation, m);
    if (a > 0.01) for (i = 0; i < 110; i++) {
      var sx = r() * W, sy = r() * 0.40 * H, ss = 0.5 + r() * 1.5, tw = 0.65 + 0.35 * Math.sin(r() * 6.28);
      ctx.fillStyle = 'rgba(255,255,255,' + (a * tw * 0.84) + ')'; ctx.beginPath(); ctx.arc(sx, sy, ss * H / 1080, 0, 6.2832); ctx.fill();
    }
    var vis = smooth(-1, 5, m.moonElevation) * (1 - smooth(-2, 8, m.elevation)) * (1 - m.cloud) * (1 - m.rain) * (1 - m.snow) * (1 - m.storm) * (1 - 0.8 * m.fog);
    if (vis > 0.02) {
      var lit = (1 - Math.cos(2 * Math.PI * 0.45)) / 2, ma = vis * (0.3 + 0.7 * lit), mx = bodyX(m.moonAzimuth), my = moonY(m.moonElevation);
      radial(ctx, mx, my, H * 0.09, H * 0.09, [1, 1, 1], 0.35 * ma);
      ctx.fillStyle = 'rgba(243,241,228,' + (0.92 * ma) + ')'; ctx.beginPath(); ctx.arc(mx, my, H * 0.024, 0, 6.2832); ctx.fill();
    }
    var strength = G.sunStrength(m.elevation, m), pale = G.sunDiscPale(m.elevation, m);
    if (strength > 0.01 || pale > 0.01) {
      var x = bodyX(m.azimuth), y = bodyY(m.elevation), hi = smooth(4, 25, m.elevation), glow = G.consts.SUN_GLOW_ALPHA * strength;
      radial(ctx, x, y, H * 0.62, H * 0.62, [1, 0.74, 0.52], glow * (1 - hi));
      radial(ctx, x, y, H * 0.50, H * 0.50, [1, 1, 1], glow * hi);
      if (strength > 0.05 && m.elevation < 14) {   // haut dans le ciel, le disque ne se voit pas : seul reste le halo
        ctx.fillStyle = (hi > 0.5 ? 'rgba(255,255,240,' : 'rgba(255,226,184,') + Math.min(1, strength * 1.2) + ')'; ctx.beginPath(); ctx.arc(x, y, H * 0.020, 0, 6.2832); ctx.fill(); }
      if (pale > 0.01) radial(ctx, x, y, H * 0.045, H * 0.045, [1, 1, 1], 0.55 * pale);
    }
    // Couvert : pas de formes de nuages (elles ressemblent a des taches posees sur la photo), seulement un voile gris froid
    // progressif, plus dense en haut du ciel, qui s'efface avant la crete. La luminosite vient surtout de l'etalonnage.
    var cover = G.skyCover(m);
    if (cover > 0.01) {
      var k = G.dayFactor(m.elevation), tone = G.consts.CLOUD_RGB, g = ctx.createLinearGradient(0, 0, 0, H * 0.5);
      g.addColorStop(0, rgba([tone[0] * k, tone[1] * k, tone[2] * k], G.consts.CLOUD_ALPHA * cover * (1 + 0.06 * Math.sin((t || 0) * 0.11) + 0.04 * Math.sin((t || 0) * 0.043 + 2))));
      g.addColorStop(0.55, rgba([tone[0] * k, tone[1] * k, tone[2] * k], G.consts.CLOUD_ALPHA * cover * 0.55));
      g.addColorStop(1, rgba([tone[0] * k, tone[1] * k, tone[2] * k], 0));
      ctx.fillStyle = g; ctx.fillRect(0, 0, W, H * 0.5);
    }
  }

  /**
   * Atmosphere : brouillard, voile de la pluie et de la neige (G.haze). Elle suit le relief et la profondeur de la photo :
   * epaisse dans la vallee du fond (autour du Cervin et des cretes lointaines), mince sur le rocher proche a gauche, sur le
   * haut du ciel et au bas du lac ; elle respire tres lentement (deux sinus de 40 et 130 s), sans forme ni bord.
   */
  function drawHaze(ctx, m, t) {
    var dawn = 0.18 * (1 - smooth(-2, 14, m.elevation)) * smooth(-14, -4, m.elevation);
    var h = Math.max(G.haze(m), dawn) * G.dayFactor(m.elevation);
    if (h <= 0.01) return;
    var a = Math.min(1, h * (1 + 0.07 * Math.sin(t * 0.157) + 0.05 * Math.sin(t * 0.048 + 1.7))), f = G.consts.FOG_RGB;
    ctx.save(); ctx.translate(W * 0.60, H * 0.50); ctx.scale(W * 0.78, H * 0.40);   // la vallee du fond
    var g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
    [[0, 1], [0.35, 0.82], [0.65, 0.42], [0.88, 0.12], [1, 0]].forEach(function (st) { g.addColorStop(st[0], rgba(f, a * 0.60 * st[1])); });
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, 1, 0, 6.2832); ctx.fill(); ctx.restore();
    var g2 = ctx.createLinearGradient(0, H * 0.30, 0, H * 0.78);   // le niveau de la rive et du lac lointain
    [[0, 0], [0.4, 0.22], [0.6, 0.30], [1, 0]].forEach(function (st) { g2.addColorStop(st[0], rgba(f, a * st[1])); });
    ctx.fillStyle = g2; ctx.fillRect(0, H * 0.30, W, H * 0.48);
  }

  // ---- meteo de premier plan (image fixe de ce que WeatherLayer anime) --------------------------------------------

  function frac(v) { return v - Math.floor(v); }

  /**
   * Pluie et neige animees (t en secondes ; les positions sont une fonction de t, comme dans WeatherLayer). Trois plans de
   * profondeur : au loin fin, court, lent et pale ; au premier plan plus long, plus rapide, plus net. Le vent incline les
   * traits et pousse les flocons. Aucune particule devant l'heure.
   */
  function weatherFront(ctx, m, seed, bolt, clockRect, t) {
    var r = rng(seed), i;
    function hidden(x, y) { return x > clockRect[0] && x < clockRect[2] && y > clockRect[1] - 0.1 * H && y < clockRect[3]; }
    if (m.storm > 0.01) { ctx.fillStyle = 'rgba(5,8,15,' + (G.consts.STORM_ALPHA * 0.6 * m.storm) + ')'; ctx.fillRect(0, 0, W, H); }
    var gust = Math.sin(t * 0.37) * 0.7 + Math.sin(t * 0.91 + 1) * 0.3, sk = H / 1080;
    var wind = (0.06 + 0.34 * m.wind) * (1 + 0.35 * gust) + 0.10 * (m.showers + m.storm) * gust;
    var rain = m.rain * (1 - m.showers * 0.45 * (1 - (0.5 + 0.5 * Math.sin(6.2832 * t / 9 + 0.8 * Math.sin(6.2832 * t / 23)))));
    if (rain > 0.01) {
      var N = 230, visible = rain * N, nearK = smooth(0.28, 0.7, rain);   // la bruine n'a que des traits lointains, fins et courts
      ctx.lineCap = 'round';
      for (var g = 0; g < 3; g++) {
        var d = (g + 0.5) / 3, layerK = g === 0 ? 1 : (g === 1 ? 0.35 + 0.65 * nearK : nearK);
        if (layerK <= 0.01) continue;
        var len = H * (0.010 + 0.040 * d) * (0.6 + 0.6 * Math.min(1, rain)), speed = 0.7 + 1.5 * d;
        ctx.lineWidth = Math.max(0.8, (0.6 + 1.0 * d) * sk); ctx.strokeStyle = g === 2 ? '#E1EAF6' : '#C9D8EA';
        for (i = g; i < N; i += 3) {
          var k = Math.min(1, visible - i), sx = r(), sy = r(), sj = r();
          if (k <= 0) continue;
          var yy = frac(sy + t * speed * (0.85 + 0.3 * sj)), xx = frac(sx + wind * yy * 0.14) * W, y = yy * H, tilt = wind * (0.85 + 0.3 * sj);
          if (hidden(xx, y)) continue;
          ctx.globalAlpha = k * layerK * (0.08 + 0.22 * d) * Math.min(1, 0.4 + rain);
          ctx.beginPath(); ctx.moveTo(xx, y); ctx.lineTo(xx - len * tilt, y + len); ctx.stroke();
        }
      }
      ctx.globalAlpha = 1;
    }
    if (m.snow > 0.01) {
      // Neige : trois profondeurs. Loin : points d'un demi-pixel a un pixel, lents, pales, nombreux. Pres : flocons de 2 a 3 px
      // avec une courte trainee dans le sens de la chute, qui tombent vite, derivent avec le vent et vacillent.
      var NS = 360, vis = m.snow * NS;
      for (i = 0; i < NS; i++) {
        var kk = Math.min(1, vis - i), dd = Math.pow(r(), 1.7), sx2 = r(), sy2 = r(), ph = r() * 6.28;   // peu de flocons proches
        if (kk <= 0) continue;
        var sp = 0.020 + 0.085 * dd, y2 = frac(sy2 + t * sp * (0.85 + 0.3 * Math.sin(ph))), x2 = frac(sx2 + wind * 0.06 * t * (0.4 + dd)) * W + Math.sin(t * (0.6 + dd) + ph) * W * (0.002 + 0.008 * dd);
        var py = y2 * H, rad = (0.55 + 2.5 * dd) * sk;
        if (hidden(x2, py)) continue;
        var al = kk * (0.22 + 0.62 * dd);
        if (dd > 0.4) radial(ctx, x2, py, rad * 1.5, rad * 1.5, [1, 1, 1], Math.min(1, al * 1.05));   // proches : ronds et doux, comme un flocon hors de la mise au point
        else { ctx.fillStyle = 'rgba(255,255,255,' + al + ')'; ctx.beginPath(); ctx.arc(x2, py, rad, 0, 6.2832); ctx.fill(); }
      }
    }
    if (bolt && m.storm > 0.6) {
      var bx = W * (0.2 + 0.6 * r()), by = 0;
      radial(ctx, bx, H * 0.12, H * 0.45, H * 0.30, [0.86, 0.9, 1], 0.22 * m.storm);
      ctx.strokeStyle = 'rgba(234,240,255,0.82)'; ctx.lineWidth = 4; ctx.lineJoin = 'round'; ctx.beginPath(); ctx.moveTo(bx, by);
      while (by < H * 0.34) { bx += (r() - 0.5) * W * 0.03; by += H * (0.03 + 0.03 * r()); ctx.lineTo(bx, by); }
      ctx.stroke();
    }
  }

  // ---- interface (vues Android) -------------------------------------------------------------------------------

  function pills(ctx, noText) {
    ctx.font = '400 30px ' + FONT;
    L.PILLS.forEach(function (p) {
      rr(ctx, p.x, p.y, p.w, p.h, p.h / 2); ctx.fillStyle = 'rgba(14,20,26,0.86)'; ctx.fill();
      ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(255,255,255,0.22)'; ctx.stroke();
      if (noText) return;
      ctx.fillStyle = 'rgba(255,255,255,0.95)'; ctx.beginPath(); ctx.arc(p.x + 54, p.y + p.h / 2, 11, 0, 6.2832); ctx.fill();
      ctx.textAlign = 'left'; ctx.textBaseline = 'middle'; ctx.fillText(p.label, p.x + 86, p.y + p.h / 2 + 2);
    });
    ctx.textBaseline = 'alphabetic';
  }

  var COLORS = ['#f4f4f4', '#202125', '#f6f6f8', '#f2f2f2', '#d3283a', '#203a8a', '#3d1a49', '#17494e'];
  var TILES = [['#2f6fd6', '#1c3f86'], ['#1f9d8f', '#0f5b63'], ['#8a4fd0', '#4a2a82'], ['#e0793a', '#a8431f'], ['#d6486a', '#8c2342'], ['#3d9a4f', '#1f5a2e'], ['#d9a62e', '#8f6410'], ['#5d6b82', '#2f394b']];
  var LABELS = ['TV', 'Films', 'Sport', 'Vidéo', 'Musique', 'Jeux', 'Photos', 'Réglages'];

  function dock(ctx, layout, state, cards, noText, generic) {
    var band = L.dockBand(layout);
    rr(ctx, band.x, band.y, band.w, band.h, 40); ctx.fillStyle = 'rgba(14,16,20,' + L.BAND_ALPHA + ')'; ctx.fill();
    ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(255,255,255,0.14)'; ctx.stroke();
    var all = L.dockCards(layout), focusIndex = state === 'focus' ? 1 : state === 'focus2' ? 5 : -1;
    all.forEach(function (c, i) {
      var idx = c.row * 4 + c.col, art = cards && cards[(c.row ? idx + 1 : idx) % cards.length], hot = i === focusIndex;
      ctx.save();
      if (hot) { ctx.translate(c.x + c.w / 2, c.y + c.h / 2); ctx.scale(1.10, 1.10); ctx.translate(-c.w / 2, -c.h / 2); } else ctx.translate(c.x, c.y);
      if (hot) { ctx.shadowColor = 'rgba(255,255,255,0.45)'; ctx.shadowBlur = 36; }
      rr(ctx, 0, 0, c.w, c.h, 18); ctx.save(); ctx.clip();
      if (c.below && !hot) ctx.globalAlpha = L.BELOW_ALPHA;
      if (generic) {   // tuiles neutres, sans logo : pour les images publiques
        var g2 = ctx.createLinearGradient(0, 0, c.w, c.h), pal = TILES[idx % TILES.length]; g2.addColorStop(0, pal[0]); g2.addColorStop(1, pal[1]);
        ctx.fillStyle = g2; ctx.fillRect(0, 0, c.w, c.h);
        ctx.fillStyle = 'rgba(255,255,255,0.92)'; ctx.textAlign = 'center'; ctx.font = '500 ' + Math.round(c.h * 0.17) + 'px ' + FONT;
        ctx.fillText(LABELS[idx % 8], c.w / 2, c.h * 0.58);
      } else if (art && art.complete && art.naturalWidth) ctx.drawImage(art, 0, 0, c.w, c.h);
      else { ctx.fillStyle = COLORS[idx % 8]; ctx.fillRect(0, 0, c.w, c.h); }
      ctx.restore(); ctx.shadowBlur = 0;
      if (hot) { rr(ctx, 0, 0, c.w, c.h, 18); ctx.lineWidth = 4; ctx.strokeStyle = 'rgba(255,255,255,0.95)'; ctx.stroke(); }
      ctx.restore();
    });
    if (focusIndex >= 0 && !noText) {   // le nom de l'application survolee, au-dessus du dock (le titre du dock)
      ctx.font = '200 96px ' + FONT; ctx.fillStyle = '#fff'; ctx.textAlign = 'left'; 
      ctx.fillText(focusIndex === 1 ? (generic ? 'Films' : 'Canal+') : 'Musique', 136, L.cardTop(H) - 100);
    }
  }

  function music(ctx, layout, accent, noText) {
    var b = layout.music; if (!b) return;
    var pad = Math.round(b.h * 0.12), cover = b.h - 2 * pad;
    rr(ctx, b.x, b.y, b.w, b.h, 28); ctx.fillStyle = 'rgba(12,14,18,0.40)'; ctx.fill();
    var g = ctx.createLinearGradient(0, b.y, 0, b.y + b.h); g.addColorStop(0, 'rgba(255,255,255,0.22)'); g.addColorStop(1, 'rgba(255,255,255,0.08)');
    ctx.fillStyle = g; ctx.fill(); ctx.lineWidth = 3; ctx.strokeStyle = 'rgba(255,255,255,0.4)'; ctx.stroke();
    rr(ctx, b.x + pad, b.y + pad, cover, cover, 18);
    var cg = ctx.createLinearGradient(b.x, b.y, b.x + cover, b.y + cover); cg.addColorStop(0, accent[0]); cg.addColorStop(1, accent[1]); ctx.fillStyle = cg; ctx.fill();
    if (noText) return;
    var tx = b.x + pad + cover + Math.round(b.h * 0.14), fs = Math.round(b.h * 0.21);
    ctx.fillStyle = '#fff'; ctx.textAlign = 'left'; ctx.font = '400 ' + fs + 'px ' + FONT; ctx.fillText('Titre du morceau', tx, b.y + b.h * 0.44);
    ctx.fillStyle = 'rgba(255,255,255,0.7)'; ctx.font = '400 ' + Math.round(fs * 0.78) + 'px ' + FONT; ctx.fillText('Nom de l’artiste', tx, b.y + b.h * 0.66);
    var bw = b.x + b.w - pad - 24 - tx - 200;
    rr(ctx, tx, b.y + b.h * 0.80, bw, 5, 2.5); ctx.fillStyle = 'rgba(255,255,255,0.22)'; ctx.fill();
    rr(ctx, tx, b.y + b.h * 0.80, bw * 0.38, 5, 2.5); ctx.fillStyle = accent[2]; ctx.fill();
  }

  // ---- texte avec plan de lisibilite ------------------------------------------------------------------------

  /**
   * Lisibilite : un eclaircissement ou assombrissement tres progressif, une grande ellipse sans bord perceptible (comme un
   * filtre degrade de photographe), jamais une plaque. Les rectangles de texte tiennent dans son plateau.
   */
  function veil(ctx, p, rect, fixedWhite) {
    if (p.veilAlpha <= 0.004) return;
    var light = !fixedWhite && p.darkText, a = Math.min(G.MAX_VEIL, p.veilAlpha), c = light ? '255,255,255' : '10,18,32';
    var cx = (rect[0] + rect[2]) / 2, cy = (rect[1] + rect[3]) / 2, rx = (rect[2] - rect[0]) / 2 * 2.3 + 140, ry = (rect[3] - rect[1]) / 2 * 2.3 + 90;
    ctx.save(); ctx.translate(cx, cy); ctx.scale(rx, ry);
    var g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1);
    [[0, 1], [0.42, 0.97], [0.55, 0.84], [0.68, 0.58], [0.8, 0.30], [0.9, 0.10], [1, 0]].forEach(function (s) { g.addColorStop(s[0], 'rgba(' + c + ',' + (a * s[1]) + ')'); });
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, 1, 0, 6.2832); ctx.fill(); ctx.restore();
  }

  function spaced(ctx, text, cx, y, em) {
    var size = parseFloat(ctx.font.match(/(\d+(\.\d+)?)px/)[1]), sp = size * em, w = 0, i, ws = [];
    for (i = 0; i < text.length; i++) { ws[i] = ctx.measureText(text[i]).width; w += ws[i] + (i < text.length - 1 ? sp : 0); }
    var x = cx - w / 2; ctx.textAlign = 'left';
    for (i = 0; i < text.length; i++) { ctx.fillText(text[i], x, y); x += ws[i] + sp; }
  }

  function line(ctx, plan, drawFn, alpha) {
    var dark = plan.darkText && !G.whiteFixed(plan.zone);
    ctx.fillStyle = dark ? '#0b1620' : '#ffffff'; ctx.globalAlpha = alpha; drawFn(); ctx.globalAlpha = 1;   // aucun contour ni halo
  }

  // ---- composition ----------------------------------------------------------------------------------------

  /**
   * o : {phase, weather, state ('repos', 'focus', 'focus2', 'musique', 'veille', 'veille-musique'), res ('uhd'|'hd'), data, img: {photo, fg, cards},
   *      layout ('B' par defaut), time, seed, bolt, noText}. Retourne la legende et les plans de lisibilite.
   */
  function render(canvas, o) {
    var scale = o.res === 'uhd' ? 2 : (o.res === 'half' ? 0.5 : 1), cw = W * scale, ch = H * scale, layout = L.LAYOUTS[o.layout || 'B'];
    canvas.width = cw; canvas.height = ch;
    var t = o.t || 0, st = o.mix ? { mix: o.mix, grade: G.compute(o.mix.elevation, o.mix.evening, o.mix) } : G.state(o.phase, o.weather);
    var ctx = canvas.getContext('2d'), m = st.mix, grade = st.grade, pack = o.data.pack;
    if (m.wind === undefined) m.wind = 0.18;
    var phaseKey = o.phase || G.phaseName(m.elevation, m.evening);
    var seed = o.seed || 7, cm = L.clockMetrics(layout);

    // 1. photo etalonnee (la matrice de couleur de l'application, voile atmospherique compris)
    var baseCv = layer(cw, ch), bctx = baseCv.getContext('2d');
    bctx.drawImage(o.img.photo, 0, 0, cw, ch);
    gradePixels(bctx, cw, ch, grade);
    ctx.save(); ctx.scale(scale, scale);
    ctx.drawImage(baseCv, 0, 0, W, H);

    // 2. ciel decoupe par la crete, brume, scintillement de la pluie
    ctx.save(); skyClip(ctx, pack.crest); drawSky(ctx, m, st, seed, t); ctx.restore();

    // 3. premier plan (la photo etalonnee decoupee par le PNG du pack), PUIS les voiles : ils recouvrent photo et premier plan sans couture
    var fgCv = layer(cw, ch), fctx = fgCv.getContext('2d');
    fctx.drawImage(baseCv, 0, 0); fctx.globalCompositeOperation = 'destination-in'; fctx.drawImage(o.img.fg, 0, 0, cw, ch);
    ctx.drawImage(fgCv, 0, 0, W, H);
    drawHaze(ctx, m, t);   // l'atmosphere recouvre ciel, relief et lac ; l'heure et les textes restent au-dessus, nets
    var plans = {}, zones = G.ZONES;
    function softVeils() { zones.forEach(function (z) { if (!G.whiteFixed(z)) veil(ctx, plans[z], G.rect(z, W, H), false); }); }
    zones.forEach(function (z) { plans[z] = G.plan(z, W, H, pack.grid, pack.crest, grade, m); });
    softVeils();

    // 4. heure, date, salutation
    if (!o.noText) {
      // l'heure vit dans un calque : son bas s'efface dans le fondu de la rive, sans redessiner photo ni voiles (aucune couture)
      var main = ctx, tl = layer(cw, ch); ctx = tl.getContext('2d'); ctx.scale(scale, scale);
      ctx.textAlign = 'center';
      ctx.font = '200 ' + cm.textSize + 'px ' + FONT;
      line(ctx, plans.CLOCK, function () { ctx.fillText(o.time || '20:52', W / 2, cm.baseline); }, 1);
      ctx.font = '400 ' + cm.dateSize + 'px ' + FONT;
      line(ctx, plans.DATE, function () { spaced(ctx, o.dateText || 'MARDI 17 NOVEMBRE   ·   12°', W / 2, cm.dateY, 0.3); }, 0.92);
      ctx.font = '400 ' + cm.greetSize + 'px ' + FONT;
      line(ctx, plans.GREETING, function () { spaced(ctx, (o.time ? greetingAt(+o.time.split(':')[0]) : GREETING[phaseKey]) || '', W / 2, cm.greetY, 0.3); }, 0.65);
      var cap = cm.baseline - cm.digitH * layout.occlusion, fadeTop = cap - cm.digitH * layout.soft, er = ctx.createLinearGradient(0, fadeTop, 0, cap);
      er.addColorStop(0, 'rgba(0,0,0,0)'); er.addColorStop(1, 'rgba(0,0,0,1)');
      ctx.globalCompositeOperation = 'destination-out'; ctx.fillStyle = er; ctx.fillRect(0, fadeTop, W, H - fadeTop); ctx.globalCompositeOperation = 'source-over';
      ctx = main; ctx.drawImage(tl, 0, 0, W, H);
    }

    var dream = o.state === 'veille' || o.state === 'veille-musique';   // screensaver : ni dock, ni pilules
    if (!dream) zones.forEach(function (z) { if (G.whiteFixed(z)) veil(ctx, plans[z], G.rect(z, W, H), true); });   // titre du dock et pilules : au-dessus du premier plan

    // 6. meteo de premier plan, voile sous le dock
    weatherFront(ctx, m, seed, !!o.bolt, L.clockVisible(W, H), t);
    if (!dream) {
      var sg = ctx.createLinearGradient(0, H * 0.62, 0, H); sg.addColorStop(0, 'rgba(0,0,0,0)'); sg.addColorStop(1, 'rgba(0,0,0,0.5)');
      ctx.fillStyle = sg; ctx.fillRect(0, H * 0.62, W, H * 0.38);
    }

    // 7. vues de l'application
    if (!dream) {
      pills(ctx, !!o.noText);
      dock(ctx, layout, o.state, o.img.cards, !!o.noText, !!o.generic);
    }
    if (o.state === 'veille-musique') music(ctx, { music: { x: 96, y: H - 216, w: 640, h: 120 } }, o.accent || ['#5b6c9b', '#2c3350', '#9fb4ff'], !!o.noText);
    if (o.state === 'musique') music(ctx, layout, o.accent || ['#5b6c9b', '#2c3350', '#9fb4ff'], !!o.noText);
    ctx.restore();

    var worst = Infinity, worstZone = '';
    zones.forEach(function (z) { if (plans[z].ratio - plans[z].threshold < worst) { worst = plans[z].ratio - plans[z].threshold; worstZone = z; } });
    return {
      render: Math.round(cw) + ' × ' + Math.round(ch), source: o.res === 'uhd' ? 'pack/photo.jpg' : 'pack/photo_1080.jpg', t: t,
      sourcePixels: o.res === 'uhd' ? pack.files['photo.jpg'].pixels : pack.files['photo_1080.jpg'].pixels,
      plans: plans, margin: worst, worstZone: worstZone, issues: L.checks(layout, function (t, s) { ctx.font = '400 ' + s + 'px ' + FONT; return ctx.measureText(t).width; }),
      grade: grade, mix: m
    };
  }

  /**
   * Contraste MESURE sur les pixels rendus : on dessine la scene sans les textes, puis, pour chaque zone, on prend la
   * luminance des pixels (2e et 98e centiles) et on calcule le rapport avec la couleur de texte choisie par le plan.
   */
  function measure(o) {
    var cv = layer(1, 1), oo = {};
    for (var k in o) oo[k] = o[k];
    oo.noText = true; oo.res = 'hd';
    var info = render(cv, oo), ctx = cv.getContext('2d'), out = [];
    G.ZONES.forEach(function (z) {
      var r = G.rect(z, W, H);
      if (z.indexOf('PILL') === 0) r = [r[0] + (r[3] - r[1]) / 2, r[1] + 6, r[2] - (r[3] - r[1]) / 2, r[3] - 6];   // l'interieur de la pilule, sans ses coins arrondis
      var x0 = Math.max(0, Math.floor(r[0])), y0 = Math.max(0, Math.floor(r[1])), w = Math.min(W - x0, Math.ceil(r[2] - r[0])), h = Math.min(H - y0, Math.ceil(r[3] - r[1]));
      var d = ctx.getImageData(x0, y0, w, h).data, ls = [], i;
      for (i = 0; i < d.length; i += 4 * 3) ls.push(G.luminance(d[i] / 255, d[i + 1] / 255, d[i + 2] / 255));
      ls.sort(function (a, b) { return a - b; });
      var lo = ls[Math.floor(ls.length * 0.02)], hi = ls[Math.min(ls.length - 1, Math.floor(ls.length * 0.98))], p = info.plans[z];
      var dark = p.darkText && !G.whiteFixed(z), rat = dark ? G.ratio(lo, G.DARK_L) : G.ratio(hi, 1);
      out.push({ zone: z, ratio: rat, threshold: G.threshold(z), ok: rat + 0.02 >= G.threshold(z), lo: lo, hi: hi, dark: dark, veil: p.veilAlpha });
    });
    return out;
  }

  root.AlpineScene = { render: render, measure: measure, GREETING: GREETING };
})(window);
