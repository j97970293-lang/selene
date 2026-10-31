# Selene

Selene est une application Android Kotlin/Compose de résolution et de téléchargement de médias. Elle accepte les fichiers directs, les manifestes HLS/DASH et, désormais, les pages prises en charge par [`yt-dlp`](https://github.com/yt-dlp/yt-dlp).

L’application est conçue pour télécharger les contenus que l’utilisateur est autorisé à enregistrer. Les conditions d’utilisation et les droits d’auteur de chaque service restent applicables.

## Télécharger l'APK

Deux APK signés (clé de debug : installation directe acceptée par Android), compilés
automatiquement par GitHub Actions à chaque poussée :

| Architecture | Fichier | Taille |
| --- | --- | --- |
| `arm64-v8a` (64 bits, à prendre en priorité) | [selene-0.1.0-arm64-v8a.apk](https://github.com/j97970293-lang/selene/releases/download/v0.1.0/selene-0.1.0-arm64-v8a.apk) | 55 Mo |
| `armeabi-v7a` (32 bits, appareils anciens) | [selene-0.1.0-armeabi-v7a.apk](https://github.com/j97970293-lang/selene/releases/download/v0.1.0/selene-0.1.0-armeabi-v7a.apk) | 49 Mo |

Page de la release : <https://github.com/j97970293-lang/selene/releases/tag/v0.1.0>

L'essentiel du poids vient de `yt-dlp` : FFmpeg (34 Mo) et l'interpréteur Python
(13,6 Mo) embarqués par architecture. Les ABI `x86`/`x86_64` ne sont pas produites :
elles ne servaient qu'aux émulateurs et doublaient le téléchargement.

## Compilation vérifiée

| Job GitHub Actions | Ce qu'il prouve |
| --- | --- |
| `Tests JVM (cœur non-Android)` | résolveurs, file, coffre, parseurs, classement des médias : ~130 tests, ~15 s |
| `APK release (SDK Android)` | `assembleRelease` complet : R8, ressources réduites, deux APK par ABI |

Localement, sans SDK Android : `cd app && ./verify-jvm.sh`.

## Fonctionnalités

- Analyse d’une URL `http(s)` depuis l’application ou via le partage Android.
- Résolution native des fichiers directs, HLS `.m3u8` et DASH `.mpd`.
- Résolution des pages avec `yt-dlp`, qui prend en charge un grand nombre de sites et de formats.
- Choix de la qualité vidéo ou audio avant la mise en file.
- Fusion automatique des pistes vidéo et audio séparées grâce à FFmpeg.
- File persistante avec WorkManager, reprise après coupure, pause, annulation, notifications et option Wi-Fi uniquement.
- Bibliothèque locale et lecture des fichiers téléchargés.
- **Export vers la galerie** : copie dans `Movies/Selene`, `Music/Selene` ou `Download/Selene`
  (MediaStore sur Android 10+, écriture directe et permission à la demande sur Android 7-9).
- **Notifications demandées au premier lancement** (Android 13+) : progression et fin visibles.
- **Moteur yt-dlp réparable** : version affichée, mise à jour du binaire depuis les réglages,
  échec de démarrage expliqué au lieu d'être avalé.
- **Navigateur de détection** : observe les requêtes, les balises `<video>/<audio>` et les liens,
  classe ce qu'il voit (flux, vidéo, audio, sous-titres, fragment, protégé) et ne propose que ce
  qui est téléchargeable.
- **Presse-papiers opt-in** : un lien copié est proposé quand le réglage est activé.
- **Diagnostic du pont externe** : `GET /health` → joignable / jeton refusé / schéma incompatible.
- Import possible d’un JSON produit par `yt-dlp -J` lorsque l’utilisateur préfère générer les métadonnées ailleurs.

| Bloc | Contenu |
| --- | --- |
| `app/` | application Kotlin + Compose, package `io.selene.app` |
| `resolver/` | direct, HLS, DASH, RSS/podcast, pont externe, yt-dlp |
| `queue/` | file priorisée : bandes de priorité, réordonnancement, reprise, Wi-Fi seul |
| `feeds/` | flux RSS / Atom → épisodes téléchargeables |
| `vault/` | coffre chiffré AES-256-GCM par blocs, clé dans l'Android Keystore |
| `converting/` | export audio `.m4a` (AAC) via Media3 |
| `imports/` | import d'une sortie `yt-dlp -J` |
| `library/` | bibliothèque, recherche locale |
| `playback/` | lecteur plein écran, fenêtre flottante (PiP) |
| `browser/` | navigateur d'observation réseau : il retient les médias qu'une page charge réellement |

Pour une page qui n’est pas elle-même un fichier média, Selene appelle la bibliothèque Android `youtubedl-android`, qui embarque yt-dlp et Python. `yt-dlp` renvoie les métadonnées et les formats disponibles. Selene les transforme en variantes affichées dans l’interface.

Quand une variante yt-dlp est choisie, le worker lance yt-dlp avec l’URL de la page et le format sélectionné. Pour une vidéo séparée de sa piste audio, le sélecteur utilisé est `format+bestaudio/format`; FFmpeg fusionne ensuite les flux dans le conteneur demandé. Les fichiers directs, HLS et DASH continuent d’utiliser le moteur HTTP segmenté de Selene.

La version Android inclut les ABI `armeabi-v7a`, `arm64-v8a`, `x86` et `x86_64`. Le module FFmpeg est inclus pour permettre la fusion des pistes et les post-traitements pris en charge par yt-dlp. Cela augmente sensiblement la taille de l’APK.

## Construire le projet

Prérequis : JDK 17 ou 21, Android SDK avec la plateforme 36 et les build-tools correspondants.

```bash
cd app
gradle wrapper --gradle-version 8.11   # une seule fois si le wrapper n’existe pas
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
```

L’APK de debug est généré dans `app/app/build/outputs/apk/debug/app-debug.apk`.

Les tests JVM peuvent être lancés sans appareil :

```bash
cd app
./verify-jvm.sh
```

Pour une première compilation Android complète :

```bash
cd app
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Utilisation

1. Ouvrir Selene et coller l’adresse d’une page ou d’un fichier média.
2. Appuyer sur **Analyser**.
3. Pour une page prise en charge par yt-dlp, choisir la variante vidéo ou audio proposée.
4. Appuyer sur **Télécharger**. Le travail apparaît dans l’onglet des téléchargements.

Pour transmettre une adresse depuis une autre application, utiliser **Partager → Selene**. Pour importer une sortie déjà générée :

```bash
yt-dlp -J "https://exemple.org/video" > media.json
```

Puis choisir **Importer une sortie yt-dlp (.json)** dans Selene.

## Mise à jour de yt-dlp

La bibliothèque Android fournit la version yt-dlp embarquée dans sa dépendance. Pour actualiser yt-dlp, mettre à jour la version `ytdlpAndroid` dans `app/gradle/libs.versions.toml`, puis vérifier la compatibilité des modules `library` et `ffmpeg`.

## Limites et sécurité

Selene ne contourne pas les DRM, les contrôles d’accès, les comptes privés ou les protections techniques. Un échec de yt-dlp peut aussi venir d’une évolution du site, d’une authentification requise, d’un format non supporté par l’appareil ou de l’absence d’une autorisation de téléchargement.

Les pages sont analysées uniquement après une action explicite de l’utilisateur. Les URL de fichiers directs ne sont pas envoyées à yt-dlp : elles passent directement par le résolveur natif. Les fichiers temporaires et les téléchargements sont écrits dans l’espace privé de l’application.

## Structure du dépôt

- `app/app/src/main/java/io/selene/app/resolver/` : résolveurs directs, HLS, DASH, pont externe et yt-dlp.
- `app/app/src/main/java/io/selene/app/downloading/` : moteur HTTP, worker WorkManager et exécuteur yt-dlp.
- `app/app/src/main/java/io/selene/app/imports/YtDlpJson.kt` : import du JSON `yt-dlp -J`.
- `ARCHITECTURE.md` : décisions et organisation du code.
- `FEATURES.md` : matrice des fonctionnalités.
- `BOUNDARY.md` : périmètre technique et limites du projet.
- `app/BUILD.md` : instructions de compilation et de vérification détaillées.

## Licences

Selene doit conserver les obligations des dépendances utilisées. yt-dlp est distribué sous licence [Unlicense](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE), tandis que `youtubedl-android` et ses composants Android ont leurs propres licences, notamment GPL-3.0 pour la bibliothèque wrapper. Vérifier les fichiers de licence des artefacts avant toute distribution de l’APK.
