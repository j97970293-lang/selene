# Où est quoi, et comment le voir sur GitHub

## 1. Le dépôt `selene` existe-t-il sur GitHub ?

**Non, pas encore.** Il existe sur le disque de l'atelier (`/home/user/selene`), committé en git
(`1 commit`, 61 fichiers), mais il n'a jamais été poussé. Je ne peux pas créer un dépôt sur **ton**
compte GitHub : cela demande ton authentification, et je n'ai ni identifiant ni jeton ici.

Deux façons de le publier, à toi de choisir.

### A. Tu crées le dépôt, je pousse

1. Sur GitHub : **New repository** → nom `selene` → **Private** → ne rien initialiser.
2. Donne-moi un jeton **fine-grained**, porté sur ce seul dépôt, droit **Contents: Read and write**,
   expiration 7 jours. Pas un jeton global `repo`.

```bash
git remote add origin https://<jeton>@github.com/j97970293-lang/selene.git
git push -u origin main
```

### B. Tu récupères l'archive et tu pousses toi-même (aucun jeton à transmettre)

Le dépôt complet est dans **`selene.bundle`** (82 Ko, historique inclus). Sur ta machine :

```bash
git clone selene.bundle selene
cd selene
git remote set-url origin git@github.com:j97970293-lang/selene.git
git push -u origin main
```

C'est la voie recommandée : rien de sensible ne circule.

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
