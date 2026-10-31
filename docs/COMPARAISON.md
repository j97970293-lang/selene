# Snaptube vs Séléné — les différences

Comparaison établie à partir de `apk-reverse/recon/` et `apk-reverse/replica/`, c'est-à-dire de
l'analyse de `Snaptube_v7_68_1_76801201.apk` (`com.snaptube.premium`, 7.68.1.76801201).

## 1. La différence de fond

| | Snaptube | Séléné |
| --- | --- | --- |
| Nature | extracteur de plateformes + téléchargeur | téléchargeur de médias accessibles |
| Source d'une vidéo | décode la page YouTube/TikTok/Instagram côté client | URL directe, manifeste, flux, API documentée |
| Ce qui fait sa valeur | l'extraction | le moteur : reprise, file, bibliothèque |
| Statut juridique | rupture des CGU des plateformes ciblées | aucun contournement |

Selene ne sait pas faire la première ligne. Tout le reste du tableau ci-dessous reste vrai.

## 2. Fiche technique

| | Snaptube 7.68.1 | Séléné |
| --- | --- | --- |
| Package | `com.snaptube.premium` | `io.selene.app` |
| Taille | 62 989 999 o (63 Mo) | à mesurer (socle `apk-reverse/app` : 23 fichiers, 2 198 lignes) |
| Classes | 51 012 | — |
| minSdk / target | 21 / 36 | 21 / 36 (même plancher) |
| Signature | auto-signée `C=IN`, série `47723C30`, alias `LUNADEV_` | clé de release propre |
| Distribution | sideload depuis un site, hors Play Store | dépôt source, build reproductible |
| Repack | oui : `FancyApplication`, `NPProtect`, `libmatrix-hook*.so` | aucun |
| Compilé | oui | **pas encore** (pas de SDK/JDK 17+ dans l'atelier) |

## 3. Permissions et vie privée

| | Snaptube | Séléné |
| --- | --- | --- |
| Nombre de permissions | 17 | 6 |
| `MANAGE_EXTERNAL_STORAGE` | oui | non — écriture via MediaStore |
| `SYSTEM_ALERT_WINDOW` | oui | non |
| Trafic HTTP en clair | autorisé (`usesCleartextTraffic="true"`) | interdit (`network_security_config`) |
| Presse-papiers | surveillance permanente au démarrage | opt-in, désactivé par défaut |
| Publicités | interstitiels + splash | aucun SDK publicitaire |
| Télémétrie | analytique, par défaut | aucune |
| Comptes tiers | connexion à des sites tiers | non |

## 4. Comportement du téléchargement

| | Snaptube | Séléné |
| --- | --- | --- |
| File persistante | oui | oui (Room + WorkManager) |
| Reprise après coupure | **non fiable, non documentée** | testée : `Range`, serveur qui ignore `Range`, `416`, `429`, `408`, `5xx` |
| Reprise après redémarrage | partielle | oui, état en base + `KEEP` |
| Erreur affichée | barre figée, échec silencieux | état typé + message + « réessayer » |
| Progression | rafraîchie à haute fréquence | throttling 4 Hz |
| Concurrence | inconnue | 1 à 4, configurable |
| Priorités / réordonnancement | non | oui, glisser-déposer |
| Wi-Fi seulement | oui | oui |
| Actions depuis la notification | oui | oui |
| Nommage des fichiers | non déterministe | `FileNamer` : accents translittérés, 120 car., collisions ` (2)` |

## 5. Formats acceptés

| | Snaptube | Séléné |
| --- | --- | --- |
| Fichier direct MP4 / WebM / M4A / MP3 | oui | oui |
| HLS `.m3u8` | oui | oui, avec reprise **par segment** |
| DASH `.mpd` | oui | oui, si non chiffré (aucun déchiffrement de DRM) |
| RSS / podcast | partiel | oui, épisodes en un geste |
| PeerTube, Archive.org, Wikimedia | non | oui |
| WebDAV, Nextcloud, serveur perso | non | oui |
| Page YouTube / TikTok / Instagram | oui | **non** — décodage par pont externe optionnel |
| Sous-titres séparés `.vtt` | oui | oui |
| Pistes audio et langues | oui | oui |

## 6. Ce que la cible a en plus

| Fonction | Pourquoi Selene ne la reprend pas |
| --- | --- |
| Extraction de pages de plateformes | contournement de CGU — ligne tenue depuis le début |
| Nettoyeur de fichiers, boost mémoire | autre produit, permissions invasives |
| Sauvegarde des statuts WhatsApp | accès aux données d'un tiers |
| Publicités, télémétrie, support par tickets | modèle économique de la cible, pas une fonction |
| Thèmes pilotés par configuration distante | endpoints distants absents de l'APK, non observables |

## 7. Ce que Selene a en plus

| Fonction | Détail |
| --- | --- |
| Reprise de lecture à la seconde | la cible repart du début |
| HLS et DASH avec reprise par segment | la cible le fait sans garantie de reprise |
| Flux RSS et podcasts | absent chez la cible |
| Priorités et réordonnancement | absent chez la cible |
| Coffre chiffré AES-GCM par fichier | la cible a un « coffre » à mot de passe, périmètre différent |
| Aucun ADB, aucune permission système | le code tourne en espace applicatif standard |
| Sources clean-room | pas de décompilé redistribué |

## 8. Le résumé en une phrase

Snaptube est meilleur que Séléné sur exactement une chose — trouver une vidéo là où il n'y a pas
de fichier — et cette chose est celle que je refuse de reconstruire. Partout ailleurs : reprise,
diagnostic des erreurs, permissions, vie privée, formats de flux, nommage des fichiers, Séléné est
au-dessus, et sur la reprise réseau l'écart n'est pas cosmétique.
