# Source externe — contrat « apporte ton extracteur »

Ce document décrit la porte de sortie que Selene laisse ouverte sans embarquer d'extracteur.
Il répond à un besoin réel : tu veux pouvoir coller n'importe quelle URL, y compris celles que
Selene refuse. Selene ne refusera pas de les transmettre à **un service que tu fais tourner
toi-même** ; il refuse seulement d'embarquer la logique qui les décode.

## Principe

```
Selene (téléphone)                     Ton pont (ta machine, ton réseau)
        │                                          │
        │  POST {url}                              │
        ├─────────────────────────────────────────▶│  <- c'est ici, et nulle part ailleurs,
        │                                          │     que le décodage de page a lieu
        │  200 {variants:[{...}]}                  │
        │◀─────────────────────────────────────────┤
        │                                          │
        │  GET fichier / manifeste (Range, reprise)│
        └─────────────────────────────────────────▶│
```

Selene devient un **client** : il reçoit des URL de fichiers et les télécharge avec son moteur
(reprise, file, notifications, coffre). Toute la partie délicate est chez toi, dans un logiciel
que tu choisis, sur une machine dont tu es responsable. C'est exactement la séparation que font
les logiciels sérieux : le moteur de téléchargement est une chose, l'extracteur en est une autre.

## Contrat HTTP

Requête émise par Selene :

```http
POST /resolve HTTP/1.1
Content-Type: application/json
Authorization: Bearer <jeton configuré dans les réglages>

{"url": "https://exemple.tld/page/123", "kind": "video"}
```

Réponse attendue (schéma stable, versionné) :

```json
{
  "schema": "selene.external/1",
  "title": "Titre lisible",
  "kind": "video",
  "duration_ms": 754000,
  "thumbnail_url": "https://…/thumb.jpg",
  "variants": [
    {
      "id": "1080p",
      "label": "1080p",
      "container": "mp4",
      "mime": "video/mp4",
      "width": 1920,
      "height": 1080,
      "bitrate": 4200000,
      "size_bytes": 395000000,
      "url": "https://…/stream/1080.mp4",
      "headers": {"Referer": "https://exemple.tld/"},
      "expires_at": "2026-11-01T02:00:00Z"
    }
  ],
  "subtitles": [
    {"lang": "fr", "label": "Français", "format": "vtt", "url": "https://…/fr.vtt"}
  ]
}
```

Erreurs : `401` jeton invalide, `422` URL non prise en charge, `503` extracteur indisponible.
Selene affiche le message renvoyé dans `{"error": "…"}`.

## Côté app — implémenté

`app/src/main/java/io/selene/app/resolver/ExternalResolver.kt` (testé par
`ExternalResolverTest`, 10 cas JVM sur MockWebServer) :

- `priority = -100` — dernier recours, après direct, HLS et DASH ;
- `matches()` renvoie faux tant que l'activation, l'adresse ou le jeton manque ;
- `matches()` renvoie faux pour toute extension média directe : un `.mp4` n'a pas besoin d'un pont ;
- `resolve()` envoie `POST {endpoint}/resolve` avec `Authorization: Bearer`, vérifie
  `schema == "selene.external/1"`, mappe variantes et sous-titres, et traduit 401/422/503 en
  messages distincts (seul 503 est réessayable) ;
- les réglages sont poussés par `SeleneApp` à chaque changement de DataStore : pas d'appel réseau
  avant que la configuration ne soit connue.

Écran de réglages : trois champs (adresse, jeton, interrupteur). Le bouton d'enregistrement reste
désactivé tant que l'adresse ou le jeton est vide.

Le registre appelle `ExternalResolver` en **dernier**, après les résolveurs directs, HLS, DASH et
RSS. Il ne s'active pas par défaut : il faut saisir l'URL du pont, le jeton, et cocher la case.
Trois garde-fous :

- **HTTPS ou réseau local explicite** — pas de jeton en clair sur Internet.
- **Liste de refus** par domaine, pour ne pas envoyer par erreur une URL privée à un pont distant.
- **Aucune donnée envoyée sans action de l'utilisateur** : pas d'envoi en tâche de fond, pas de
  file silencieuse.

## Ce que je ne livre pas

Le pont. Ni en Python, ni en shell, ni en binaire, ni embarqué sous forme de dépendance. Écrire ce
service, c'est écrire l'extracteur ; ce n'est pas à moi de le faire, et je ne vais pas le
contourner en le mettant derrière une interface HTTP pour me donner le change.

Ce que je livre, c'est de quoi **brancher** un pont que tu écris ou que tu installes. La
responsabilité change de camp, et elle change réellement : c'est toi qui choisis le logiciel, la
machine, la fréquence, la légalité dans ton pays.

## Variante sans serveur : import de `yt-dlp -J`

Pas envie d'héberger un service ? Lance simplement :

```bash
yt-dlp -J "https://exemple.tld/page/123" > media.json
```

Puis dans Selene : « Importer une sortie yt-dlp (.json) ». `imports/YtDlpJson.kt` lit le fichier
et produit les variantes (vidéo, audio, HLS, sous-titres, en-têtes). 11 tests JVM couvrent ce
mapping, fixture comprise.

Ce que Selene ne fait pas dans ce mode : exécuter yt-dlp, le télécharger, l'embarquer. Le JSON est
la frontière, et elle est nette.

## Si tu veux la version sans pont

Trois voies qui donnent un résultat immédiat, toutes dans le périmètre :

1. **Partage** — dans le navigateur ou une app qui expose le fichier, « Partager → Selene ». Le
   fichier arrive directement dans la file.
2. **URL directe** — la plupart des pages ont un fichier `.mp4` ou un `.m3u8` en dessous : le
   navigateur intégré de Selene les détecte et propose le téléchargement.
3. **Flux** — un podcast, une chaîne PeerTube, une playlist HLS : Selene les traite nativement,
   avec reprise. Sur ce terrain il fait mieux que la cible, pas moins bien.
