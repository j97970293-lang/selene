# Matrice de parité — cible analysée → Selene

40 fonctionnalités recensées dans la cible, plus une ligne 15b hors application. `Statut` : repris / corrigé / exclu.
Aucune ligne de code, aucune ressource, aucun texte de la cible n'est utilisée.

## Entrée d'une URL

| # | Fonctionnalité | Statut | Note |
| --- | --- | --- | --- |
| 1 | Coller une URL | repris | champ + bouton coller |
| 2 | Partage depuis une autre app | repris | `ACTION_SEND text/plain`, extraction de la 1re URL |
| 3 | Lien profond `selene://resolve` | repris | `ACTION_VIEW` + schéma dédié |
| 4 | Détection d'URL dans le presse-papiers | corrigé | opt-in explicite, désactivé par défaut (la cible scrute en permanence) |
| 5 | Mini-navigateur intégré | repris | `browser/`, onglets, retour, historique |

## Résolution et formats

| # | Fonctionnalité | Statut | Note |
| --- | --- | --- | --- |
| 6 | Analyse de la source | repris | `MediaResolver` + registre |
| 7 | Choix vidéo / audio | repris | `MediaKind`, variantes typées |
| 8 | Choix de la qualité | repris | libellé, dimensions, débit |
| 9 | Pistes audio multiples | repris | `Variant.audioLanguage` |
| 10 | Choix de la langue d'interface | repris | ressources `values-*` |
| 11 | Sous-titres séparés | repris | `SubtitleTrack`, `.vtt`/`.srt` téléchargé à part |
| 12 | Aperçu avant téléchargement | repris | vignette + durée + taille estimée |
| 12b | Conversion audio d'un média téléchargé | repris | `.m4a` (AAC) via Media3 — MP3 non supporté, pas de faux fichier renommé |
| 13 | Choix du dossier de destination | repris | SAF (`ACTION_OPEN_DOCUMENT_TREE`) |
| 14 | Téléchargement d'un manifeste HLS/DASH | repris | segmentation, concaténation, reprise par segment (`.ts` / piste MP4) |
| 15 | Extraction d'une page non média via yt-dlp | repris | yt-dlp embarqué, formats affichés puis téléchargement local — voir `BOUNDARY.md` |
| 15b | Passerelle vers un extracteur auto-hébergé | **hors app, client livré** | `ExternalResolver` implémenté + testé ; le pont est fourni par l'utilisateur — `docs/EXTERNAL-RESOLVER.md` |

## File de téléchargements

| # | Fonctionnalité | Statut | Note |
| --- | --- | --- | --- |
| 16 | File persistante | repris | Room + WorkManager |
| 17 | Pause / reprise / annulation | repris | états typés |
| 18 | Reprise après coupure réseau | repris | `Range` + `.part`, testé |
| 19 | Reprise après redémarrage | repris | WorkManager `KEEP` + état en base |
| 20 | Concurrence configurable | repris | 1 à 4 téléchargements simultanés |
| 21 | Priorités et réordonnancement | repris | bandes de priorité + ▲▼★ dans la file (`QueueOrder`) |
| 22 | Wi-Fi uniquement | repris | contrainte `UNMETERED` |
| 23 | Notifications avec actions | repris | pause/annuler depuis la notification |
| 24 | Notification auto-effacée à la fin | repris | `setAutoCancel` |
| 25 | Erreurs explicites + réessayer | corrigé | la cible reste bloquée sans message |
| 26 | Progression lissée | corrigé | throttling 4 Hz (batterie/CPU) |

## Bibliothèque et lecture

| # | Fonctionnalité | Statut | Note |
| --- | --- | --- | --- |
| 27 | Bibliothèque locale | repris | grille + tri |
| 28 | Recherche locale | repris | Room `LIKE`, insensible aux accents |
| 29 | Sélection multiple | repris | suppression, partage, mise en file |
| 30 | Favoris / accès rapide | repris | `bookmark` |
| 31 | Playlists | repris | `playlist_item` |
| 32 | Historique et reprise de lecture | repris | position en ms (la cible repart de zéro) |
| 33 | Lecteur plein écran | repris | Media3 |
| 34 | Fenêtre flottante (PiP) | repris | `supportsPictureInPicture` |
| 35 | Lecture en arrière-plan | repris | `MediaSessionService` |
| 36 | Lecture d'une page web | corrigé | ouvre le navigateur système, pas un WebView interne qui lit des flux |

## Confidentialité, stockage, extras

| # | Fonctionnalité | Statut | Note |
| --- | --- | --- | --- |
| 37 | Coffre local protégé par code | repris | fichiers chiffrés (AES-GCM, clé Keystore) |
| 38 | Nettoyeur / boost mémoire | **exclu** | autre produit ; permissions système invasives |
| 39 | Sauvegarde des statuts WhatsApp | **exclu** | accès aux données d'un tiers |
| 40 | Publicités + télémétrie | **exclu** | aucun SDK publicitaire, aucun traceur |

## Total

| Statut | Nombre |
| --- | --- |
| Repris | 31 |
| Corrigé volontairement | 5 |
| Exclu | 4 (dont 1 fonctionnel, 3 hors sujet) |

Une ligne supplémentaire (15b) n'est ni reprise ni exclue : elle sort de l'application. Le pont
d'extraction est fourni par l'utilisateur, Selene ne fait que consommer son API.

Les 4 exclusions sont documentées une par une dans `BOUNDARY.md` et `ARCHITECTURE.md`, avec la
raison. Aucune n'est un oubli.
