/*
 * Geometrie de l'accueil alpin (mise en page B, validee) pour l'interface de comparaison. Tout est exprime dans le repere
 * de la TV en 1080p (1920 x 1080) ; le rendu applique ensuite un facteur d'echelle (1 en 1080p, 2 en UHD).
 *
 * Les memes valeurs que AlpineMetrics.java et AlpineDock.java (un test les compare au code Java) : largeur de l'heure,
 * ligne de base remontee de 52 px (0,524 au lieu de 0,572), police Outfit poids 200 ("00:00" = 2,902 em), date, salutation,
 * pilules remontees de 54 px, dock en deux rangees (les quatre applications, puis les autres en dessous).
 * La disposition A (avant la mise en page B) est gardee pour comparer les positions avant et apres dans le rapport.
 */
(function (root) {
  'use strict';

  var W = 1920, H = 1080;
  var ADVANCE_EM = 2.902, DIGIT_EM = 0.70;
  var DATE_SIZE = 0.024, GREETING_SCALE = 0.78, GREETING_GAP = 1.9;
  var SAFE_X = W * 0.05, SAFE_Y = H * 0.05;

  var PILLS_TOP = 64, PILLS_H = 82;
  var PILLS = [
    { x: 872, y: PILLS_TOP, w: 242, h: PILLS_H, label: 'Sources' },
    { x: 1134, y: PILLS_TOP, w: 360, h: PILLS_H, label: 'Wi-Fi' },
    { x: 1514, y: PILLS_TOP, w: 293, h: PILLS_H, label: 'Paramètres' }
  ];
  // Dock (AlpineDock.java) : premiere rangee a 56,7 % de la hauteur, derniere tuile avant la marge basse de 5 %.
  var DOCK_TOP = 0.567, DOCK_BOTTOM = 0.95, ROW_GAP_DP = 37, DENSITY = 2, DESIGN_W = 180, DESIGN_H = 101;
  var BELOW_ALPHA = 0.72, BAND_ALPHA = 0.35;

  var LAYOUTS = {
    A: {
      id: 'A', name: 'A : avant', reference: true,
      about: "La mise en page precedente : horloge a 0,572 de la hauteur, pilules a 118 px, dock sur une rangee.",
      clockWidth: 0.50, baseline: 0.572, occlusion: 0.30, soft: 0.10, dateGap: 0.05, pillsTop: 118, rows: 1
    },
    B: {
      id: 'B', name: 'B : validee',
      about: "Horloge de 58 % remontee de 52 px, rive sur 22 % des chiffres, pilules remontees de 54 px, les quatre applications puis les autres en dessous, bloc musique compact en haut a gauche.",
      clockWidth: 0.58, baseline: 0.524, occlusion: 0.22, soft: 0.10, dateGap: 0.06, pillsTop: PILLS_TOP, rows: 2,
      music: { x: 96, y: 96, w: 640, h: 120 }
    }
  };

  function clockMetrics(layout) {
    layout = layout || LAYOUTS.B;
    var textSize = layout.clockWidth * W / ADVANCE_EM, digitH = textSize * DIGIT_EM, baseline = layout.baseline * H;
    var top = baseline - digitH, small = H * DATE_SIZE, dateY = top - H * layout.dateGap;
    return {
      textSize: textSize, digitH: digitH, baseline: baseline, top: top,
      left: W * (0.5 - layout.clockWidth / 2), right: W * (0.5 + layout.clockWidth / 2),
      dateSize: small, dateY: dateY, greetSize: small * GREETING_SCALE, greetY: dateY - small * GREETING_GAP,
      cap: baseline - layout.occlusion * digitH, soft: layout.soft * digitH
    };
  }

  function cardTop(h) { return Math.round(DOCK_TOP * (h || H)); }
  function tileHeight(h, rowsBelow, density) {
    var room = DOCK_BOTTOM * h - cardTop(h) - rowsBelow * ROW_GAP_DP * density;
    return Math.max(Math.round(40 * density), Math.floor(room / (rowsBelow + 1)));
  }
  function tileWidth(h, rowsBelow, fit, density) {
    var byHeight = Math.round(tileHeight(h, rowsBelow, density) * DESIGN_W / DESIGN_H);
    return Math.max(Math.round(80 * density), Math.min(byHeight, fit));
  }

  /** Cartes du dock (disposition B : quatre colonnes, `rows` rangees) : {x, y, w, h, row, col, below}. */
  function dockCards(layout, rows) {
    layout = layout || LAYOUTS.B;
    rows = rows || layout.rows;
    var cols = 4, below = rows - 1, th = tileHeight(H, below, DENSITY);
    var tw = tileWidth(H, below, Math.round(DESIGN_W * DENSITY), DENSITY), inner = 1657, cell = inner / cols, start = (W - inner) / 2;
    var out = [];
    for (var r = 0; r < rows; r++) for (var c = 0; c < cols; c++) {
      out.push({ x: Math.round(start + c * cell + (cell - tw) / 2), y: cardTop(H) + r * (th + Math.round(ROW_GAP_DP * DENSITY)), w: tw, h: th, row: r, col: c, below: r > 0 });
    }
    return out;
  }
  function dockBand(layout) {
    var c = dockCards(layout, 1);
    return { x: c[0].x - 26, y: c[0].y - 26, w: c[3].x + c[3].w - c[0].x + 52, h: c[0].h + 52 };
  }

  function clockVisible(w, h) {
    var text = LAYOUTS.B.clockWidth * w / ADVANCE_EM, digit = text * DIGIT_EM, base = LAYOUTS.B.baseline * h;
    return [w * (0.5 - LAYOUTS.B.clockWidth / 2), base - digit, w * (0.5 + LAYOUTS.B.clockWidth / 2), base - LAYOUTS.B.occlusion * digit];
  }
  function dateLines(w, h) {
    var text = LAYOUTS.B.clockWidth * w / ADVANCE_EM, digit = text * DIGIT_EM, top = LAYOUTS.B.baseline * h - digit;
    var size = DATE_SIZE * h, date = top - LAYOUTS.B.dateGap * h;
    return [date, date - size * GREETING_GAP, size, size * GREETING_SCALE];
  }
  function pill(i, w, h) {
    var sx = w / W, sy = h / H, p = PILLS[i];
    return [p.x * sx, PILLS_TOP * sy, p.w * sx, PILLS_H * sy];
  }

  function overlap(a, b) { return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h; }

  /** Controles de la disposition : liste lisible des points a noter (vide pour la mise en page B). */
  function checks(layout, measure) {
    layout = layout || LAYOUTS.B;
    measure = measure || function (t, s) { return t.length * s * 0.62; };
    var m = clockMetrics(layout), issues = [];
    var clock = { x: m.left, y: m.top, w: m.right - m.left, h: m.digitH };
    var dateW = measure('MARDI 17 NOVEMBRE   ·   12°', m.dateSize) * 1.3;
    var date = { x: W / 2 - dateW / 2, y: m.dateY - m.dateSize, w: dateW, h: m.dateSize * 1.2 };
    var greet = { x: W / 2 - 160, y: m.greetY - m.greetSize, w: 320, h: m.greetSize * 1.2 };
    var pills = PILLS.map(function (p) { return { x: p.x, y: layout.pillsTop, w: p.w, h: p.h }; });
    pills.forEach(function (p) { if (overlap(p, greet) || overlap(p, date)) issues.push("La salutation ou la date touche les pilules d'état"); });
    if (overlap(date, clock)) issues.push("La date chevauche l'horloge");
    if (clock.x < SAFE_X || clock.x + clock.w > W - SAFE_X) issues.push("L'horloge dépasse la marge de 5 %");
    if (m.greetY - m.greetSize < SAFE_Y) issues.push("La salutation dépasse la marge haute de 5 %");
    if (layout.id === 'B') {
      var cards = dockCards(layout), last = cards[cards.length - 1];
      if (last.y + last.h > H - SAFE_Y + 1) issues.push("La dernière rangée du dock dépasse la marge de 5 %");
      if (cards[0].x < SAFE_X) issues.push("Le dock dépasse la marge de 5 %");
      if (m.baseline > cardTop(H) - 20) issues.push("L'heure touche le dock");
      if (pills.some(function (p) { return p.y < SAFE_Y; })) issues.push("Les pilules dépassent la marge haute de 5 %");
    } else {
      issues.push("Dock sur une seule rangée : les autres applications sont coupées par le bord de l'écran");
    }
    return issues;
  }

  var api = {
    W: W, H: H, SAFE_X: SAFE_X, SAFE_Y: SAFE_Y, PILLS: PILLS, LAYOUTS: LAYOUTS, BASELINE: LAYOUTS.B.baseline, WATERLINE: 0.56,
    DOCK_TOP: DOCK_TOP, DOCK_BOTTOM: DOCK_BOTTOM, ROW_GAP_DP: ROW_GAP_DP, BELOW_ALPHA: BELOW_ALPHA, BAND_ALPHA: BAND_ALPHA,
    clockMetrics: clockMetrics, clockVisible: clockVisible, dateLines: dateLines, pill: pill, cardTop: cardTop,
    tileHeight: tileHeight, tileWidth: tileWidth, dockCards: dockCards, dockBand: dockBand, overlap: overlap, checks: checks
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.AlpineLayout = api;
})(typeof window !== 'undefined' ? window : this);
