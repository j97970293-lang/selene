# Selene

Application Android de téléchargement et de lecture multimédia — **parité fonctionnelle** avec la
cible analysée, construite proprement. Dépôt privé, frère de `apk-reverse` (qui reste le dépôt de
référence du code source clean-room, non modifié).

> Nom : **Séléné** (Σελήνη), déesse grecque de la Lune. Alternatives disponibles si tu préfères :
> **Nyx**, **Aether**, **Vega**, **Orion**, **Lyra**, **Atlas**.

---

## Ce que ce dépôt contient

| Bloc | Contenu |
| --- | --- |
| `app/` | application Kotlin + Compose, package `io.selene.app`, socle importé de `apk-reverse/app` |
| `browser/` | navigateur à onglets interne avec détection de médias dans la page |
| `queue/` | file de téléchargements : parallélisme, priorités, reprise, Wi-Fi seul |
| `library/` | bibliothèque, recherche locale, sélection multiple, playlists, historique |
| `vault/` | coffre local protégé par code (fichiers chiffrés dans le dossier privé) |
| `playback/` | lecteur plein écran, fenêtre flottante (PiP), pistes audio et sous-titres |
| `sources/` | sources déclaratives : favoris, speed dial, flux RSS/podcast, URL directe |

## Ce que ce dépôt ne contient pas

**Aucun moteur d'extraction pour les plateformes qui n'exposent pas de fichier média**
(YouTube, TikTok, Instagram, Facebook). Cette partie n'est pas reconstruite : elle contourne les
conditions d'utilisation de ces services, et c'est la seule limite du projet.

Trois façons d'obtenir un média dont la page n'est pas un fichier : un pont HTTP auto-hébergé
(`docs/EXTERNAL-RESOLVER.md`), l'import d'une sortie `yt-dlp -J`, ou le passage à une application
installée (Seal, NewPipe) en un tap. Aucune ne contient de décodeur de page dans ce dépôt.

Conséquence assumée : Selene télécharge **tout ce qui est un fichier média accessible par URL** —
MP4, WebM, MKV, M4A, MP3, FLAC, OGG, HLS `.m3u8`, DASH `.mpd`, flux RSS/podcast, PeerTube,
Archive.org, serveur personnel, ton propre CDN. Il ne télécharge pas une vidéo YouTube à partir de
sa page.

## Périmètre de parité

Voir `FEATURES.md` : 40 fonctionnalités recensées dans la cible, dont **31 reprises**,
**5 corrigées** (défauts volontairement non reproduits) et **4 exclues** (dont l'extraction).

## Démarrage

```bash
git init
git add -A
git commit -m "feat: Selene — parité fonctionnelle, sans extraction tierce"
git remote add origin git@github.com:j97970293-lang/selene.git
git push -u origin main
```

Le dépôt doit être créé **privé** sur GitHub (Settings → General → Danger zone → Change
visibility, ou à la création).

## Documents

- `FEATURES.md` — matrice de parité complète, fonctionnalité par fonctionnalité
- `ARCHITECTURE.md` — modules, modèle de données, décisions techniques
- `BOUNDARY.md` — la frontière exacte, écrite noir sur blanc, et pourquoi
- `docs/EXTERNAL-RESOLVER.md` — contrat d'API pour brancher un extracteur que tu héberges toi-même
- `docs/DISCUSSION.md` — « d'autres le font », les arguments traités un par un
