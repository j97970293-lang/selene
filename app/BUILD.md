# Construire et lancer Selene

## Prérequis

| Outil | Version | Note |
| --- | --- | --- |
| JDK | 17 ou 21 | Android Gradle Plugin 8.7 exige le JDK 17 |
| Android SDK | platform 36, build-tools 36 | via Android Studio ou `sdkmanager` |
| Gradle | wrapper à générer | voir ci-dessous |

## Première construction

```bash
cd app
gradle wrapper --gradle-version 8.11        # une seule fois, génère ./gradlew
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
```

L'APK apparaît dans `app/build/outputs/apk/debug/app-debug.apk`.

## Tests (sans appareil, sans réseau externe)

```bash
cd app
./gradlew :app:testDebugUnitTest
```

`DownloadEngineTest` tourne sur JVM avec MockWebServer et couvre les cas qui cassent
en premier sur réseau mobile :

- téléchargement complet ;
- reprise sur fichier `.part` avec `Range` ;
- serveur qui **ignore** `Range` (200) : redémarrage propre, pas de fichier corrompu ;
- `416` traité comme « déjà complet » ;
- `503` puis succès (retry avec backoff) ;
- `404` non réessayé.

`FileNamerTest` couvre la translittération, la troncature et les collisions de noms.

`DashManifestParserTest` couvre le manifeste DASH sans réseau : durée ISO 8601, `SegmentTemplate`
à durée fixe, `SegmentTimeline` avec `r=2`, gabarit `%05d`, héritage depuis `AdaptationSet`,
`BaseURL` en fichier unique, refus d'une `ContentProtection`, XML invalide, racine inattendue,
manifeste vide, plusieurs pistes.

`YtDlpJsonTest` couvre l'import d'une sortie `yt-dlp -J` : métadonnées, distinction vidéo/audio
par codec, tri par résolution, `filesize_approx` en secours, en-têtes HTTP, manifeste HLS,
sous-titres, JSON dégénéré.

`ExternalResolverTest` couvre le pont externe sur MockWebServer : désactivé par défaut, fichier
direct jamais délégué, jeton obligatoire, schéma vérifié, 401 non réessayé, 503 réessayé, JSON
illisible, ordre de priorité du registre.

`PlaylistParserTest` couvre le manifeste HLS sans réseau : variantes d'un manifeste maître,
résolution des URLs relatives (dont `../`), cumul des durées `#EXTINF`, attributs entre guillemets
contenant des virgules, piste audio `DEFAULT=YES`, refus explicite d'un flux `AES-128`.

## État vérifiable — mesuré, pas supposé

| Point | État |
| --- | --- |
| Package | `io.selene.app` — 37 fichiers `.kt`, arborescences cohérentes |
| Cœur non-Android | **compilé** avec kotlinc 2.0.21, 0 erreur, 0 avertissement bloquant |
| Tests JVM | **55 tests, 55 verts** (`./verify-jvm.sh`) |
| APK | **non produit** — voir ci-dessous |
| APK, pourquoi | cet atelier a un JDK 11 (AGP 8.7 exige 17+), aucun SDK Android, 1 Go de RAM |

```bash
./verify-jvm.sh      # ~15 s à froid, télécharge kotlinc + jars dans /tmp
```

Le script compile et exécute exactement la partie du code qui ne touche pas à Android :
moteur de téléchargement, parseurs HLS et DASH, résolveurs, pont externe. Le reste (Compose,
Hilt, Room, WorkManager, Media3) exige le SDK Android et ne se vérifie que par un vrai build :

```bash
cd app
gradle wrapper --gradle-version 8.11
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
```

## Trois bugs trouvés en exécutant les tests

Écrire du code sans le compiler ne suffit pas, et l'exécution l'a prouvé — trois défauts réels,
invisibles à la relecture :

| Bug | Symptôme | Correction |
| --- | --- | --- |
| Groupe audio multiple | le français devenait anglais : le dernier `#EXT-X-MEDIA` écrasait le premier | `DEFAULT=YES` prioritaire, sinon première piste |
| Gabarit `$Number%05d$` | segments demandés littéralement, 4 × 404 | motif construit dans le bon ordre (`%0` + largeur + `d`) |
| Regex de largeur | `$` interprété comme ancre de fin, largeur toujours 1 | échappement `\$`, validé par le test |

## Vérification sur appareil

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.SEND -t text/plain \
  -e android.intent.extra.TEXT "https://exemple.org/video.mp4" \
  -n io.selene.app.debug/io.selene.app.MainActivity
```

## État de vérification — à lire

**Ce projet n'a pas été compilé dans l'environnement où il a été écrit** : pas de SDK
Android, pas de JDK 17+, 1,9 Go de RAM. Le code a été relu et vérifié par des contrôles
structurels (packages conformes aux chemins, aucune référence `R.string` manquante, aucun
doublon de classe par paquet), **pas** par une compilation.

Conséquence honnête : attends-toi à quelques corrections à la première compilation
(imports, signatures Compose, versions de bibliothèques). Les points les plus susceptibles
de demander un ajustement :

1. `libs.versions.toml` — les versions sont cohérentes entre elles mais à aligner sur ce que
   ton SDK accepte.
2. `LinearProgressIndicator(progress = { … })` — signature Material3 récente ; sur une BOM
   plus ancienne, utiliser la surcharge `progress: Float`.
3. `Configuration.Provider.workManagerConfiguration` — `val` depuis WorkManager 2.9.
4. Room + KSP : la première génération peut réclamer un `@TypeConverters` si tu ajoutes des
   types non primitifs aux entités.

## Ce qui n'est pas encore là

- Export MediaStore vers `Downloads/` (v1 écrit dans le dossier privé de l'app).
- Fenêtre flottante / PiP (le manifeste ne déclare pas `supportsPictureInPicture`).
- Sous-titres et pistes audio multiples (le modèle les porte, l'UI ne les expose pas).
- Favoris et accès rapide (entités prévues, écran absent).
