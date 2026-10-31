# Architecture de Selene

Selene reprend le socle de `apk-reverse/app` (moteur, base, résolveur, DI, écrans) et ajoute les
modules qui manquent pour atteindre la parité. `apk-reverse` n'est pas modifié : Selene en est la
continuation.

## Modules Gradle cibles

```
:app                    navigation, thème, composition
:core:model             MediaItem, Variant, SubtitleTrack, TaskState…
:core:database          Room, migrations versionnées, schéma exporté
:core:network           OkHttp, retry, téléchargement segmenté
:core:download          moteur + worker + notifications
:core:designsystem      tokens, composants Compose
:feature:resolve        URL → variantes → format → destination
:feature:downloads      file, priorités, actions
:feature:library        grille, recherche, sélection multiple, playlists
:feature:player         plein écran, PiP, MediaSession
:feature:browser        onglets, détection de médias dans la page
:feature:vault          coffre chiffré
:feature:settings       préférences, thème, dossier, limites
:resolver:api           interface MediaResolver
:resolver:direct        URL directe (fichier, HEAD)
:resolver:hls           manifeste .m3u8 → segments
:resolver:dash          manifeste .mpd (si non chiffré)
:resolver:rss           flux RSS / podcast → épisodes
:resolver:archive       Archive.org, Wikimedia Commons
```

## Ce que chaque module ajoute par rapport à `apk-reverse/app`

| Module | Ajout clé |
| --- | --- |
| `:resolver:hls` | parse le manifeste, choisit la variante, télécharge les segments, concatène en `.ts` puis remuxe en MP4 (ou conserve TS). Reprise par segment — c'est ce qui rend une coupure réseau indolore. |
| `:resolver:dash` | même principe pour `.mpd`, uniquement si le contenu n'est pas chiffré (pas de déchiffrement de DRM : hors périmètre, et c'est la limite légale). |
| `:resolver:rss` | un flux devient une liste d'épisodes : titre, date, durée, `enclosure.url`. Zéro contournement, très utile. |
| `:feature:browser` | WebView + `WebViewClient` qui inspecte les `Content-Type` des requêtes et propose les ressources média détectées. Ne rejoue pas de protocole privé. |
| `:feature:player` | PiP (`enterPictureInPictureMode`, `PictureInPictureParams` avec ratio), `MediaSessionService` pour l'audio en arrière-plan, sélection de piste. |
| `:feature:vault` | chiffrement AES-GCM par fichier, clé maîtresse dans Android Keystore, déverrouillé par code/biométrie ; les fichiers sortent du dossier public. |

## Modèle de données — évolutions

`schema.sql` (migration 1) est repris tel quel. Migration 2 ajoute :

```
playlist(id, name, created_at)
playlist_item(playlist_id, media_id, position)
bookmark(id, url, title, favicon_path, position)
history_entry(id, media_id, last_played_at, position_ms)
source(id, label, kind, config_json, position, enabled)
vault_entry(id, media_id, encrypted_path, created_at)
```

Migration 3 : `download_task.priority`, `download_task.segment_index`, `download_task.manifest_url`.

## Décisions techniques

| Sujet | Décision | Motif |
| --- | --- | --- |
| Concurrence | 3 téléchargements, 2 connexions/fichier, sémaphore | au-delà, on sature le réseau mobile sans gagner de débit |
| Reprise HLS | par segment, pas par octet | un segment perdu ne coûte que ce segment |
| Écriture disque | dossier privé puis export MediaStore | aucune permission de stockage à l'exécution |
| PiP | seuil de rapport 16:9, `autoEnterEnabled` API 31+ | comportement natif attendu |
| Coffre | AES-GCM, clé Keystore, pas de mot de passe stocké | un code oublié = fichiers irrécupérables, c'est le contrat |
| Télémétrie | aucune | |
| Cleartext | interdit | |

## Séquence de construction

1. **Socle** — copier `apk-reverse/app`, renommer le package `io.selene.app`, vérifier que ça compile.
2. **HLS + RSS** — les deux résolveurs qui débloquent le plus de contenus légitimes.
3. **File avancée** — priorités, concurrence, réordonnancement.
4. **Bibliothèque** — playlists, historique, recherche insensible aux accents.
5. **Lecteur** — PiP, MediaSession, sélection de pistes.
6. **Navigateur** — détection de médias.
7. **Coffre** — chiffrement + déverrouillage.
8. **Finitions** — thèmes, traductions, accessibilité, tests d'écran.

## État

| Étape | État |
| --- | --- |
| 1. Socle importé, package `io.selene.app` | fait — 27 fichiers `.kt` côté `main` |
| 2. HLS + DASH | fait — `PlaylistParser`, `HlsResolver`, `DashManifestParser`, `DashResolver` |
| 2 bis. RSS / podcast | à faire |
| 3. File avancée (priorités, concurrence) | à faire |
| 4. Bibliothèque (playlists, historique) | à faire |
| 5. Lecteur (PiP, MediaSession) | à faire |
| 6. Navigateur à détection de médias | à faire |
| 7. Coffre chiffré | à faire |
| 8. Finitions | à faire |

Moteur segmenté commun : `SegmentDownloader` sert HLS et DASH (reprise par segment, état `.seg`).
Rien n'est compilé dans cet atelier : ni JDK 17+ ni SDK 36. La logique des deux parseurs a été
validée hors Kotlin, sur fixtures, avant livraison.
