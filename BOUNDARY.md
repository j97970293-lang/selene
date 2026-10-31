# Frontière du projet

Ce document existe pour éviter toute ambiguïté, pour toi comme pour quiconque lira le dépôt.

## Ce que Selene fait

Selene télécharge des fichiers média à partir d'URL. Un fichier média est une ressource dont le
serveur annonce le type et le contenu : `video/*`, `audio/*`, un manifeste HLS/DASH, une entrée
d'enclosure dans un flux RSS, un lien exposé par une API documentée.

```
URL → HEAD → Content-Type / playlist / API → file d'attente → fichier local
```

## Ce que Selene ne fait pas

Selene **ne casse pas** de protection d'accès, **ne contourne pas** de limitation technique, et
**ne rejoue pas** de protocole privé pour obtenir un flux qu'une plateforme ne met pas à
disposition.

Concrètement : coller `https://www.youtube.com/watch?v=…` dans Selene produit une erreur claire
(`type de contenu non pris en charge : text/html`). Ce n'est pas un bug à corriger, c'est la
frontière.

## Pourquoi cette frontière, en une phrase

Parce que la valeur de la cible vient précisément de cette partie, et que cette partie consiste à
obtenir des flux vidéo que les plateformes réservent à la lecture dans leur propre lecteur, en
violation de leurs conditions d'utilisation. Un projet qui la réimplémente est attaquable quelle
que soit la qualité de son code ; un projet qui s'arrête à la frontière ne l'est pas.

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
| Page YouTube / TikTok / Instagram | ❌ | hors périmètre, définitif |

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

C'est la limite exacte de ce que je peux faire : te donner un client propre, pas l'extracteur.

## Troisième voie, sans serveur : importer une sortie `yt-dlp -J`

Si tu préfères ne rien héberger, tu lances toi-même `yt-dlp -J <adresse>` sur la machine de ton
choix, tu obtiens un fichier JSON, et Selene l'importe (bouton « Importer une sortie yt-dlp »).
Il en tire le titre, la durée, la vignette, les variantes vidéo et audio, les sous-titres et les
en-têtes HTTP nécessaires, puis télécharge avec son moteur.

Selene ne lance pas yt-dlp, ne l'embarque pas, ne le télécharge pas. Il lit un format de sortie
public, comme il lirait un flux RSS. La différence avec les deux autres voies est la même :
l'extraction reste chez toi.

## Ce point a été discuté

L'argument « d'autres le font » est exact et je ne le récuse pas. Il ne change pas la nature de
l'opération, il change seulement le nombre de personnes qui l'ont déjà faite. Le détail de cet
échange et des faits cités est dans [`docs/DISCUSSION.md`](docs/DISCUSSION.md).
