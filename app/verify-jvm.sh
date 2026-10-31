#!/usr/bin/env bash
# Vérification JVM de Selene — sans Android SDK, sans émulateur, sans appareil.
#
# Compile et exécute la partie du code qui ne dépend pas d'Android : moteur de téléchargement,
# parseurs HLS et DASH, résolveurs, pont externe. C'est le seul moyen de vérifier réellement ce
# code sur une machine sans SDK Android ; un APK, lui, exige le SDK et un JDK 17+.
#
# Usage : ./verify-jvm.sh
# Prérequis : curl, unzip, java (11+). Le premier lancement télécharge ~90 Mo (kotlinc + jars).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${TMPDIR:-/tmp}/selene-verify"
LIB="$WORK/lib"
KOTLINC="$WORK/kotlinc/bin/kotlinc"
MVN="https://repo1.maven.org/maven2"
KOTLIN_VERSION="2.0.21"

mkdir -p "$LIB"

jar() { # jar <chemin maven> <fichier>
  [ -f "$LIB/$2" ] || curl -sSfL -o "$LIB/$2" "$MVN/$1"
}

if [ ! -x "$KOTLINC" ]; then
  echo "→ kotlinc $KOTLIN_VERSION"
  curl -sSfL -o "$WORK/kotlinc.zip" \
    "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip"
  unzip -q -o "$WORK/kotlinc.zip" -d "$WORK"
fi

echo "→ dépendances"
jar "org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar" "kotlin-stdlib.jar"
jar "org/jetbrains/kotlin/kotlin-reflect/$KOTLIN_VERSION/kotlin-reflect-$KOTLIN_VERSION.jar" "kotlin-reflect.jar"
jar "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar" "coroutines.jar"
jar "com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar" "okhttp.jar"
jar "com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.jar" "okio.jar"
jar "com/squareup/okhttp3/mockwebserver/4.12.0/mockwebserver-4.12.0.jar" "mockwebserver.jar"
jar "com/squareup/moshi/moshi/1.15.1/moshi-1.15.1.jar" "moshi.jar"
jar "com/squareup/moshi/moshi-kotlin/1.15.1/moshi-kotlin-1.15.1.jar" "moshi-kotlin.jar"
jar "javax/inject/javax.inject/1/javax.inject-1.jar" "javax.inject.jar"
jar "junit/junit/4.13.2/junit-4.13.2.jar" "junit.jar"
jar "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar" "hamcrest.jar"
jar "com/google/truth/truth/1.4.4/truth-1.4.4.jar" "truth.jar"
jar "com/google/guava/guava/33.2.1-jre/guava-33.2.1-jre.jar" "guava.jar"
jar "com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar" "failureaccess.jar"

CP="$(ls "$LIB"/*.jar | tr '\n' ':')"
SRC="$ROOT/app/src"
OUT="$WORK/classes"
rm -rf "$OUT"; mkdir -p "$OUT"

echo "→ compilation (sources JVM)"
"$KOTLINC" -J-Xmx750m -cp "$CP" -jvm-target 11 -d "$OUT" \
  "$SRC/main/java/io/selene/app/model/Models.kt" \
  "$SRC/main/java/io/selene/app/data/UserSettings.kt" \
  "$SRC/main/java/io/selene/app/resolver/DashManifestParser.kt" \
  "$SRC/main/java/io/selene/app/resolver/DashResolver.kt" \
  "$SRC/main/java/io/selene/app/resolver/DirectUrlResolver.kt" \
  "$SRC/main/java/io/selene/app/resolver/ExternalResolver.kt" \
  "$SRC/main/java/io/selene/app/resolver/HlsResolver.kt" \
  "$SRC/main/java/io/selene/app/resolver/MediaResolver.kt" \
  "$SRC/main/java/io/selene/app/resolver/PlaylistParser.kt" \
  "$SRC"/main/java/io/selene/app/imports/*.kt \
  "$SRC"/main/java/io/selene/app/feeds/FeedParser.kt \
  "$SRC"/main/java/io/selene/app/vault/VaultCipher.kt \
  "$SRC"/main/java/io/selene/app/queue/QueueOrder.kt \
  "$SRC"/main/java/io/selene/app/queue/DownloadSlots.kt \
  "$SRC/main/java/io/selene/app/downloading/DownloadEngine.kt" \
  "$SRC/main/java/io/selene/app/downloading/FileNamer.kt" \
  "$SRC/main/java/io/selene/app/downloading/SegmentDownloader.kt" \
  "$SRC/main/java/io/selene/app/downloading/HlsDownloader.kt" \
  "$SRC/main/java/io/selene/app/downloading/DashDownloader.kt" \
  "$SRC"/test/java/io/selene/app/downloading/*.kt \
  "$SRC"/test/java/io/selene/app/resolver/*.kt \
  "$SRC"/test/java/io/selene/app/imports/*.kt \
  "$SRC"/test/java/io/selene/app/feeds/*.kt \
  "$SRC"/test/java/io/selene/app/vault/*.kt \
  "$SRC"/test/java/io/selene/app/queue/*.kt

echo "→ tests"
# Découverte automatique : une classe de test oubliée dans cette liste ne serait jamais exécutée
# (c'est arrivé), donc on liste les classes compilées plutôt que de les écrire à la main.
CLASSES=$(cd "$OUT" && find . -name '*Test.class' | sed 's|^\./||; s|/|.|g; s|\.class$||' | sort)
echo "  $(echo "$CLASSES" | wc -l) classes de test"
java -Xmx600m -cp "$OUT:$CP" org.junit.runner.JUnitCore $CLASSES
