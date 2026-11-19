#!/usr/bin/env bash
# Prepares a release: bumps versionCode, sets versionName, builds and verifies
# the signed release APK, then prints the git commands to tag it.
#
# Usage: ./release.sh <version>      e.g. ./release.sh 0.4.0
set -euo pipefail

cd "$(dirname "$0")"

GRADLE_FILE=app/build.gradle.kts

die() { echo "❌ $*" >&2; exit 1; }

[[ $# -eq 1 ]] || die "Usage : ./release.sh <version>   (ex. ./release.sh 0.4.0)"
VERSION=$1
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Version invalide « $VERSION » : format attendu X.Y.Z (ex. 0.4.0)."

TAG="v$VERSION"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "Le tag $TAG existe déjà."
[[ -f keystore.properties ]] || die "keystore.properties absent : l'APK release ne serait pas signé (voir README, « Publier une version »)."

CURRENT_CODE=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE")
CURRENT_NAME=$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE")
[[ -n $CURRENT_CODE && -n $CURRENT_NAME ]] || die "versionCode / versionName introuvables dans $GRADLE_FILE."

# The new version must be strictly greater than the current one.
if [[ $(printf '%s\n%s\n' "$CURRENT_NAME" "$VERSION" | sort -V | tail -1) != "$VERSION" || $CURRENT_NAME == "$VERSION" ]]; then
    die "La version $VERSION doit être supérieure à la version actuelle $CURRENT_NAME."
fi

NEW_CODE=$((CURRENT_CODE + 1))
echo "▶ $CURRENT_NAME ($CURRENT_CODE) → $VERSION ($NEW_CODE)"

sed -i.bak -E \
    -e "s/^([[:space:]]*versionCode = )[0-9]+/\1$NEW_CODE/" \
    -e "s/^([[:space:]]*versionName = )\"[^\"]+\"/\1\"$VERSION\"/" \
    "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

# On failure, put the previous version back so the tree is left as it was.
restore() {
    sed -i.bak -E \
        -e "s/^([[:space:]]*versionCode = )[0-9]+/\1$CURRENT_CODE/" \
        -e "s/^([[:space:]]*versionName = )\"[^\"]+\"/\1\"$CURRENT_NAME\"/" \
        "$GRADLE_FILE"
    rm -f "$GRADLE_FILE.bak"
    echo "↩ Version restaurée à $CURRENT_NAME ($CURRENT_CODE)." >&2
}
trap 'restore' ERR

echo "▶ Tests, lint et build release (vérification du manifeste incluse)…"
./gradlew testDebugUnitTest lint assembleRelease

APK=app/build/outputs/apk/release/selene-$VERSION-release.apk
[[ -f $APK ]] || { echo "❌ APK signé introuvable : $APK" >&2; false; }

echo "▶ Vérification de la signature…"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z $SDK && -f local.properties ]]; then
    SDK=$(sed -nE 's/^sdk\.dir=(.*)/\1/p' local.properties)
fi
[[ -n $SDK && -d $SDK/build-tools ]] || { echo "❌ SDK Android introuvable (ANDROID_HOME ou local.properties)." >&2; false; }
APKSIGNER=$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)/apksigner
CERTS=$("$APKSIGNER" verify --print-certs "$APK")
echo "$CERTS" | grep -E "certificate DN|SHA-256" || true
if echo "$CERTS" | grep -q "CN=Android Debug"; then
    echo "❌ L'APK est signé avec la clé de debug." >&2
    false
fi

trap - ERR

cat <<EOF

✅ Version $VERSION ($NEW_CODE) prête : $APK

Pour commiter et créer le tag :

  git commit -am "chore: release $VERSION" && git tag $TAG && git push origin main $TAG

Puis sur GitHub → Releases → Draft a new release → tag $TAG, joindre l'APK.
EOF
