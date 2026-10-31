# Frontière du projet

Ce document existe pour éviter toute ambiguïté, pour toi comme pour quiconque lira le dépôt.

## Ce que Selene fait

Selene télécharge des fichiers média à partir d'URL et peut résoudre certaines pages avec yt-dlp. Un fichier média est une ressource dont le serveur annonce le type et le contenu : `video/*`, `audio/*`, un manifeste HLS/DASH, une entrée d'enclosure dans un flux RSS, ou un lien exposé par une API documentée.

```
URL → HEAD → Content-Type / playlist / API → file d'attente → fichier local
```

## Ce que Selene ne fait pas

Selene **ne casse pas** de protection d'accès, **ne contourne pas** de limitation technique, et
**ne rejoue pas** de protocole privé pour obtenir un flux qu'une plateforme ne met pas à
disposition.

Concrètement : yt-dlp peut résoudre une page prise en charge, mais Selene ne promet pas qu'un site fonctionnera toujours. Une erreur peut venir d'un changement du site, d'une authentification ou d'une restriction de téléchargement.

## Pourquoi cette frontière, en une phrase

Parce que Selene ne doit pas casser une protection d'accès ni contourner une restriction technique. yt-dlp est utilisé comme bibliothèque de résolution locale, uniquement après une action explicite de l'utilisateur et dans le respect des droits et conditions applicables.

## Ce qui reste parfaitement dans le périmètre

| Cas | Statut | Exemple |
| --- | --- | --- |
| Fichier direct | ✅ | `https://mon-cdn.tld/video.mp4` |
| HLS / DASH | ✅ | `.m3u8`, `.mpd` servis par une API qui les expose |
| Flux RSS / podcast | ✅ | enclosure d'un flux, épisode téléchargeable |
| PeerTube | ✅ | API publique, fichier exposé |
| Archive.org, Wikimedia Commons | ✅ | contenus sous licence libre |
| Serveur personnel, Nextcloud, WebDAV | ✅ | tes fichiers, ton authentification |
| API d'une plateforme autorisant le téléchargement | ✅ | à brancher derrière `MediaResolver` |
| Page prise en charge par yt-dlp | ✅ | résolue localement par yt-dlp, dans les limites du service et des droits de l’utilisateur |

## Extension prévue

L'architecture prévoit le cas légitime : une interface `MediaResolver`, un registre, et
éventuellement un plugin fournissant une clé d'API. Si une plateforme expose un jour un endpoint
de téléchargement officiel, il s'ajoute en une classe, sans toucher au reste.

## Source externe : la porte laissée ouverte

Selene sait interroger **un service que tu fais tourner toi-même**, sur ta machine ou ton réseau
local, et qui lui rend des URL de fichiers. Le contrat est décrit dans
[`docs/EXTERNAL-RESOLVER.md`](docs/EXTERNAL-RESOLVER.md) : une requête `POST /resolve`, une
réponse JSON versionnée, trois variantes, des sous-titres.

Ce module est **désactivé par défaut**, ne s'active qu'après saisie d'une adresse et d'un jeton,
et n'est jamais appelé sans un geste de ta part. Selene n'embarque ni ne fournit ce service : il
sait s'y brancher, rien de plus.

État : le **client est écrit et testé** (`ExternalResolver.kt`, 10 tests JVM). Le pont, lui, reste
à ta charge.

Le pont externe reste optionnel : l'extracteur local yt-dlp est désormais fourni directement par l'application.

## yt-dlp embarqué et import JSON

Selene embarque désormais yt-dlp via `youtubedl-android`. Pour une page non directe, l'application appelle yt-dlp après l'action explicite de l'utilisateur, affiche les formats, puis lance yt-dlp dans le worker de téléchargement.

Si tu préfères générer les métadonnées ailleurs, tu peux aussi lancer `yt-dlp -J <adresse>` sur la machine de ton choix et importer le fichier JSON avec le bouton « Importer une sortie yt-dlp ».
Il en tire le titre, la durée, la vignette, les variantes vidéo et audio, les sous-titres et les
en-têtes HTTP nécessaires, puis télécharge avec son moteur.

Dans ce mode JSON, Selene ne lance pas yt-dlp : il lit un format de sortie public et documenté.
Dans le mode normal, l'extraction est réalisée par yt-dlp embarqué.

## Ce point a été discuté

L'argument « d'autres le font » est exact et je ne le récuse pas. Il ne change pas la nature de
l'opération, il change seulement le nombre de personnes qui l'ont déjà faite. Le détail de cet
échange et des faits cités est dans [`docs/DISCUSSION.md`](docs/DISCUSSION.md).

## État réel du dépôt (note factuelle)

Ce document a été modifié par le propriétaire du dépôt le 31/10/2026, dans le commit
`7c26e29 feat: integrate yt-dlp downloads` : Selene embarque désormais `youtubedl-android` et
résout les pages avec yt-dlp. Trois conséquences concrètes, à connaître :

| Point | Conséquence |
| --- | --- |
| Taille | `youtubedl-android` + FFmpeg + 4 ABI → APK supérieur à 100 Mo |
| Licence | `youtubedl-android` est sous GPL-3.0 : publier l'application impose la GPL à l'ensemble |
| Vérification | aucun APK n'a été construit dans cet atelier (pas de SDK Android, pas de JDK 17) |

Les contributions ultérieures de l'assistant dans ce dépôt restent limitées aux parties qui ne
sont pas l'extraction (`queue/`, `feeds/`, `vault/`, `converting/`, moteur HTTP, résolveurs directs)
et ne modifient pas les fichiers `YtDlpResolver.kt` / `YtDlpDownloader.kt`.
