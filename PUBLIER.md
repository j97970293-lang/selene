# Où est quoi, et comment le voir sur GitHub

## 1. Le dépôt `selene` sur GitHub

**Publié** : https://github.com/j97970293-lang/selene — **privé**, branche `main`.

| Point | Valeur |
| --- | --- |
| Visibilité | privé (vérifié par l'API : `private: true`) |
| Commits | `4ce4d1b` socle + HLS/DASH + pont + import yt-dlp, `89a0ae2` documentation |
| Fichiers suivis | 61 |

Mise à jour depuis l'atelier :

```bash
cd selene
git add -A && git commit -m "..."
git push
```

Le remote est une URL propre (`https://github.com/j97970293-lang/selene.git`) : aucun jeton n'est
stocké dans `.git/config`. Toute poussée ultérieure doit passer par tes identifiants.

L'archive **`selene.bundle`** reste disponible comme sauvegarde hors ligne du dépôt complet
(historique inclus) : `git clone selene.bundle selene`.

## 2. Y a-t-il du code source de Snaptube dans `apk-reverse` ?

**Non, et il n'y en aura jamais.** Ce qui se trouve dans `apk-reverse` est de deux natures :

| Dossier | Contenu | Nature |
| --- | --- | --- |
| `recon/` | `features.csv`, `recon-map.md`, `evidence/` (4 preuves) | **analyse** de l'APK : manifeste, permissions, signature, composants |
| `replica/` | `recon.md`, `features.csv` (37 lignes), `deviations.md` | **grille de parité** : ce qui est repris, corrigé, exclu |
| `architecture/` | `architecture.md`, `data-model.md`, `schema.sql` | **conception** du modèle de données |
| `app/` | 25 fichiers `.kt` (Rivulet, ~2 200 lignes) | **code original écrit à la main**, pas du décompilé |

Snaptube n'est distribué qu'en APK compilé. Il n'existe pas de « code source de Snaptube » :
seulement un binaire que l'on peut décompiler — ce que ce projet ne fait pas et ne livrera pas
(`BOUNDARY.md`). Ce que nous avons, c'est une **reconstruction propre** qui reproduit le
comportement observable, plus l'analyse qui la justifie.

## 3. Inventaire des deux dépôts

### `apk-reverse/` — référence, en lecture seule

```
apk-reverse/
├── README.md, RULES.md
├── recon/          analyse statique de l'APK
├── replica/        matrice de parité, écarts assumés
├── architecture/   modèle de données, schéma SQL
└── app/            25 .kt — Rivulet, socle clean-room
```

### `selene/` — le projet actif

```
selene/
├── README.md, FEATURES.md, ARCHITECTURE.md, BOUNDARY.md
├── PUBLIER.md              ← ce fichier
├── docs/
│   ├── COMPARAISON.md      Snaptube vs Séléné, ligne par ligne
│   ├── DISCUSSION.md       l'échange « d'autres le font »
│   └── EXTERNAL-RESOLVER.md contrat du pont + import yt-dlp
└── app/
    ├── verify-jvm.sh       compile et exécute les 55 tests, sans SDK Android
    └── app/src/
        ├── main/…/io/selene/app/   40 fichiers .kt
        │   ├── downloading/        moteur, file, HLS, DASH, segments
        │   ├── resolver/           direct, HLS, DASH, pont externe
        │   ├── imports/            YtDlpJson
        │   ├── ui/                 resolve, downloads, library, player, settings, handoff
        │   └── data/, di/, model/
        └── test/…                   55 tests JVM
```

## 4. Vérifier en une commande

```bash
cd app && ./verify-jvm.sh
# → compilation (sources JVM)
# → OK (55 tests)
```
