# Installer l'Accueil alpin sur une TV LG avec un assistant IA

Colle ce texte dans ton assistant (Claude Code, Codex, Gemini CLI ou autre) avec accès à ton terminal. Il peut cloner le dépôt lui-même.

---

Tu es mon assistant d'installation. Je veux installer l'Accueil alpin (une appli qui affiche une photo du Cervin dont la
lumière suit le vrai soleil, avec la météo, la date et l'heure) sur ma TV LG. Fais tout toi-même, étape par étape, en me
posant les questions ci-dessous. Ma TV est une LG sous webOS, PAS sous Android : seule l'appli du dossier `webos/` du dépôt
https://github.com/Tom1419-git/alpine-tv-home est concernée (le reste du dépôt est pour Android TV).

Règles : ne modifie aucun fichier du dépôt sauf `webos/app/config.js` (écrit par `install.js`). N'utilise que
`node webos/install.js` pour installer. Pas de root, pas de jailbreak, pas de Homebrew Channel, pas de menu d'usine de la TV.
Ne lance rien sur la TV avant d'avoir toutes mes réponses. Si une commande échoue, montre-moi l'erreur exacte et propose une
correction, sans boucler.

1. Si le dépôt n'est pas déjà cloné ici, clone-le : `git clone https://github.com/Tom1419-git/alpine-tv-home` et place-toi dedans.
2. Vérifie que Node 18 ou plus est installé (`node --version`). Sinon, dis-moi comment l'installer et attends.
3. Demande-moi le modèle exact de ma TV et son année (Réglages, Assistance, Informations sur la TV). Compatible : TV de 2018 ou
   après (webOS 4 et plus). Plus ancienne ou doute : prévens-moi avant d'installer, ce n'est pas garanti.
4. Demande-moi si le mode développeur est activé. Sinon, lis-moi les étapes avec `node webos/install.js --action help`.
5. Demande-moi l'adresse IP de la TV (Réglages, Réseau, Wi-Fi, mon réseau), ma ville (météo et soleil), Celsius ou Fahrenheit,
   heure sur 12 h ou 24 h.
6. Lance : `node webos/install.js --action install --ip <IP> --city "<ville>" --yes` (ajoute `--fahrenheit` et/ou `--12h` si je
   les ai demandés). À l'étape du code (passphrase), l'appli Developer Mode de la TV affiche un code : demande-le-moi, il se
   saisit dans le terminal.
7. À la fin, confirme que l'appli "Accueil alpin" est dans la liste des applications de la TV, et rappelle-moi que la session
   développeur de LG est limitée dans le temps et se prolonge depuis l'appli Developer Mode.
