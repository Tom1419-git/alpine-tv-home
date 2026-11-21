#!/usr/bin/env node
/*
 * Installe l'Accueil alpin sur une TV LG webOS, en une commande :  node webos/install.js
 *
 * Sans option, un menu demande ce qu'il faut faire (installer, changer la ville, lancer, desinstaller). Avec des options,
 * rien n'est demande : pratique pour un assistant IA (voir webos/PROMPT.md).
 *
 *   --action install|city|launch|remove   (defaut : menu)
 *   --ip 192.168.1.50                      adresse de la TV (mode developeur active)
 *   --name lgtv                            nom de la TV dans les outils LG (defaut : lgtv)
 *   --city "Lausanne"  ou  --lat 46.5 --lon 6.6   position pour la meteo et le soleil
 *   --fahrenheit, --12h                    affichage
 *   --photo riffelsee|braies|auto          photo de fond (auto : une photo differente chaque jour)
 *   --yes                                  ne pose aucune question de confirmation
 *   --dry-run                              affiche les commandes sans rien lancer
 *
 * Il faut Node 18 ou plus. Les outils LG (@webos-tools/cli, commandes ares-*) sont utilises s'ils sont installes ; sinon,
 * avec votre accord, ils sont telecharges par npx depuis npm (rien n'est installe sur le systeme).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const cp = require('child_process');
const readline = require('readline');

const APP_ID = 'com.tom1419.alpine';
const APP_DIR = path.join(__dirname, 'app');
const OUT_DIR = path.join(__dirname, '..', 'build', 'webos');
const CONFIG = path.join(APP_DIR, 'config.js');

function parseArgs(argv) {
  const a = { action: '', ip: '', name: 'lgtv', city: '', lat: null, lon: null, fahrenheit: false, h12: false, photo: '', yes: false, dry: false };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    const next = () => argv[++i];
    if (k === '--action') a.action = next();
    else if (k === '--ip') a.ip = next();
    else if (k === '--name') a.name = next();
    else if (k === '--city') a.city = next();
    else if (k === '--lat') a.lat = Number(next());
    else if (k === '--lon') a.lon = Number(next());
    else if (k === '--fahrenheit') a.fahrenheit = true;
    else if (k === '--12h') a.h12 = true;
    else if (k === '--photo') a.photo = next();
    else if (k === '--yes') a.yes = true;
    else if (k === '--dry-run') a.dry = true;
    else if (k === '-h' || k === '--help') a.action = 'help';
    else { console.error('Option inconnue : ' + k); process.exit(2); }
  }
  return a;
}

const args = parseArgs(process.argv.slice(2));
const PHOTO_IDS = { riffelsee: 'riffelsee-lever', 'riffelsee-lever': 'riffelsee-lever', braies: 'braies-matin', 'braies-matin': 'braies-matin', auto: 'auto' };
if (args.photo && !PHOTO_IDS[args.photo]) { console.error('--photo : riffelsee, braies ou auto'); process.exit(2); }
let rl = null;
function ask(question, def) {
  if (args.yes && def !== undefined) return Promise.resolve(def);
  if (!rl) rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((ok) => rl.question(question + (def ? ' [' + def + '] ' : ' '), (r) => ok(r.trim() || def || '')));
}

function validIp(s) {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(s);
  return !!m && m.slice(1).every((n) => Number(n) <= 255);
}

// ---- configuration de l'appli ---------------------------------------------------------------------------------------

function writeConfig(c) {
  const text = '/* Reglages de l\'appli. install.js les ecrit ; on peut aussi les modifier a la main avant d\'installer. */\n' +
    'window.ALPINE_CONFIG = ' + JSON.stringify(c, null, 2) + ';\n';
  if (args.dry) { console.log('[dry-run] config.js :\n' + text); return; }
  fs.writeFileSync(CONFIG, text);
}

function readConfig() {
  try {
    const src = fs.readFileSync(CONFIG, 'utf8');
    return JSON.parse(src.slice(src.indexOf('{'), src.lastIndexOf('}') + 1));
  } catch (e) {
    return { city: 'Lausanne', lat: 46.516, lon: 6.63282, fahrenheit: false, format24: true, photo: 'riffelsee-lever' };
  }
}

async function geocode(name) {
  const url = 'https://geocoding-api.open-meteo.com/v1/search?count=5&language=fr&name=' + encodeURIComponent(name);
  const r = await fetch(url);
  if (!r.ok) throw new Error('geocodage : HTTP ' + r.status);
  return (await r.json()).results || [];
}

async function chooseLocation(cfg) {
  if (args.lat !== null && args.lon !== null) {
    if (!(args.lat >= -90 && args.lat <= 90 && args.lon >= -180 && args.lon <= 180)) throw new Error('--lat/--lon hors limites');
    return { city: args.city || 'Ma ville', lat: args.lat, lon: args.lon };
  }
  let wanted = args.city;
  for (;;) {
    if (!wanted) wanted = await ask('Ville pour la meteo et le soleil (ex. Lausanne) :', cfg.city);
    const found = await geocode(wanted);
    if (!found.length) { console.log('Aucune ville trouvee pour "' + wanted + '".'); if (args.yes) throw new Error('ville introuvable'); wanted = ''; continue; }
    if (found.length === 1 || args.yes || args.city) {
      const f = found[0];
      return { city: f.name, lat: f.latitude, lon: f.longitude };
    }
    found.forEach((f, i) => console.log('  ' + (i + 1) + ') ' + f.name + (f.admin1 ? ', ' + f.admin1 : '') + ', ' + (f.country || '')));
    const n = Number(await ask('Numero :', '1'));
    if (n >= 1 && n <= found.length) {
      const f = found[n - 1];
      return { city: f.name, lat: f.latitude, lon: f.longitude };
    }
    wanted = '';
  }
}

async function updateConfig() {
  const cfg = readConfig();
  const loc = await chooseLocation(cfg);
  cfg.city = loc.city; cfg.lat = Math.round(loc.lat * 1e4) / 1e4; cfg.lon = Math.round(loc.lon * 1e4) / 1e4;
  if (args.fahrenheit) cfg.fahrenheit = true;
  if (args.h12) cfg.format24 = false;
  if (args.photo) cfg.photo = PHOTO_IDS[args.photo];
  else if (!args.yes) {
    console.log('Photo de fond :\n  1) Riffelsee et le Cervin\n  2) Lago di Braies (Dolomites)\n  3) Une photo differente chaque jour');
    const n = await ask('Votre choix :', { 'riffelsee-lever': '1', 'braies-matin': '2', auto: '3' }[cfg.photo] || '1');
    cfg.photo = { 1: 'riffelsee-lever', 2: 'braies-matin', 3: 'auto' }[n] || cfg.photo || 'riffelsee-lever';
  }
  writeConfig(cfg);
  console.log('Position : ' + cfg.city + ' (' + cfg.lat + ', ' + cfg.lon + ')');
}

// ---- outils LG -----------------------------------------------------------------------------------------------------

let runner = null;   // 'global' ou 'npx'

function has(tool) {
  const r = cp.spawnSync(tool, ['--version'], { stdio: 'ignore', shell: process.platform === 'win32' });
  return r.status === 0;
}

async function findTools() {
  if (runner) return;
  if (args.dry) { runner = 'global'; return; }
  if (has('ares-package')) { runner = 'global'; return; }
  console.log('Les outils LG (ares-package, ares-install...) ne sont pas installes.');
  const ok = await ask('Les telecharger avec npx depuis npm (paquet @webos-tools/cli, rien n\'est installe sur le systeme) ? (o/n)', 'o');
  if (!/^o/i.test(ok)) {
    console.log('Alternative : npm install -g @webos-tools/cli, puis relancez ce script.');
    process.exit(1);
  }
  runner = 'npx';
}

function ares(tool, argv, opts) {
  const cmd = runner === 'npx' ? 'npx' : tool;
  const full = runner === 'npx' ? ['--yes', '-p', '@webos-tools/cli', tool].concat(argv) : argv;
  const shown = [cmd].concat(full).join(' ');
  if (args.dry) { console.log('[dry-run] ' + shown); return { status: 0, stdout: '' }; }
  const r = cp.spawnSync(cmd, full, Object.assign({ stdio: 'inherit', shell: process.platform === 'win32', encoding: 'utf8' }, opts || {}));
  if (r.status !== 0 && !(opts && opts.soft)) throw new Error('echec : ' + shown);
  return r;
}

function listDevices() {
  if (args.dry) return [];
  const r = cp.spawnSync(runner === 'npx' ? 'npx' : 'ares-setup-device', (runner === 'npx' ? ['--yes', '-p', '@webos-tools/cli', 'ares-setup-device'] : []).concat(['--list']),
    { encoding: 'utf8', shell: process.platform === 'win32' });
  return (r.stdout || '').split('\n').map((l) => l.trim().split(/\s+/)[0]).filter((n) => n && n !== 'name' && !/^-+$/.test(n));
}

async function ensureDevice() {
  const names = listDevices();
  if (names.indexOf(args.name) >= 0 && !args.ip) return;
  let ip = args.ip;
  while (!validIp(ip)) {
    if (args.yes) throw new Error('--ip manquant ou invalide');
    ip = await ask('Adresse IP de la TV (Reglages, Reseau, Wi-Fi, votre reseau) :');
  }
  ares('ares-setup-device', ['--add', args.name, '-i', 'host=' + ip, '-i', 'port=9922', '-i', 'username=prisoner'], { soft: names.indexOf(args.name) >= 0 });
  if (names.indexOf(args.name) >= 0) ares('ares-setup-device', ['--modify', args.name, '-i', 'host=' + ip, '-i', 'port=9922', '-i', 'username=prisoner']);
  console.log('\nOuvrez l\'appli "Developer Mode" sur la TV : elle affiche un code (passphrase). Saisissez-le ci-dessous.');
  ares('ares-novacom', ['--device', args.name, '--getkey']);
}

async function install() {
  await findTools();
  await updateConfig();
  await ensureDevice();
  fs.mkdirSync(OUT_DIR, { recursive: true });
  ares('ares-package', [APP_DIR, '-o', OUT_DIR]);
  const ipk = path.join(OUT_DIR, APP_ID + '_1.0.0_all.ipk');
  ares('ares-install', ['--device', args.name, ipk]);
  ares('ares-launch', ['--device', args.name, APP_ID]);
  console.log('\nInstalle. L\'appli "Accueil alpin" est dans la liste des applications de la TV.');
}

async function launch() { await findTools(); ares('ares-launch', ['--device', args.name, APP_ID]); }
async function remove() { await findTools(); ares('ares-install', ['--device', args.name, '--remove', APP_ID]); console.log('Desinstalle.'); }

function help() {
  console.log(`Mode developpeur LG, pas a pas :
  1. Sur la TV, ouvrez le LG Content Store et installez l'appli "Developer Mode".
  2. Ouvrez-la, connectez-vous a un compte LG developpeur (gratuit : webostv.developer.lge.com), activez "Dev Mode Status".
  3. Activez aussi "Key Server". La TV redemarre. Rouvrez l'appli : elle affiche un code (passphrase) et la duree restante.
  4. Notez l'adresse IP de la TV, puis lancez : node webos/install.js
LG limite la duree de la session developpeur : il faut la prolonger depuis l'appli Developer Mode quand elle expire.`);
}

async function main() {
  if (Number(process.versions.node.split('.')[0]) < 18) { console.error('Node 18 ou plus est necessaire.'); process.exit(1); }
  let action = args.action;
  if (!action) {
    console.log('Accueil alpin pour LG webOS\n  1) Installer ou mettre a jour sur ma TV\n  2) Changer la ville\n  3) Lancer l\'appli\n  4) Desinstaller\n  5) Aide : activer le mode developpeur');
    action = { 1: 'install', 2: 'city', 3: 'launch', 4: 'remove', 5: 'help' }[await ask('Votre choix :', '1')] || 'help';
  }
  if (action === 'install') await install();
  else if (action === 'city') { await updateConfig(); console.log('Relancez l\'installation (option 1) pour envoyer ce changement a la TV.'); }
  else if (action === 'launch') await launch();
  else if (action === 'remove') await remove();
  else help();
}

main().then(() => { if (rl) rl.close(); }, (e) => { console.error('Erreur : ' + e.message); if (rl) rl.close(); process.exit(1); });
