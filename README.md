# Sélène

Application Android de suivi de cycle menstruel, 100 % hors-ligne. Les règles du projet (architecture, modèle de données, design) sont décrites dans [`CLAUDE.md`](CLAUDE.md).

## Environnement de développement

L'image Docker contient le JDK 21, le SDK Android et Claude Code.

```sh
docker compose run --rm selene-dev
```

Toutes les commandes ci-dessous se lancent depuis la racine du projet (`/workspace` dans le conteneur).

## Compiler

```sh
./gradlew assembleDebug        # APK de debug → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # APK release minifié, NON signé → app/build/outputs/apk/release/
./gradlew clean                # repartir de zéro
```

`assemble<Variante>` lance d'abord `verify<Variante>ManifestPermissions`, qui fait échouer la compilation si le manifeste fusionné demande `INTERNET` ou une permission non autorisée. Le rapport est écrit dans `app/build/reports/manifest-permissions/`.

## Tester

```sh
./gradlew testDebugUnitTest                        # tous les tests JVM : domaine, JSON, DAO, dépôt, UI Compose (Robolectric)
./gradlew testDebugUnitTest --tests '*PredictorTest'          # une seule classe
./gradlew testDebugUnitTest --tests '*PredictorTest.someTest' # un seul test
./gradlew lint                                     # lint Android → app/build/reports/lint-results-debug.html
./gradlew check                                    # tests + lint + vérification du manifeste
```

Rapport des tests : `app/build/reports/tests/testDebugUnitTest/index.html`.

**À lancer avant de considérer une tâche terminée :** `./gradlew testDebugUnitTest lint`.

### Tests instrumentés (appareil requis)

Les tests SQLCipher et Keystore (`app/src/androidTest/…/EncryptedDatabaseTest`) demandent un vrai appareil ou un émulateur. Il n'y a pas d'émulateur dans le conteneur (pas de KVM). Depuis le conteneur, on peut se connecter à un appareil ou à un émulateur de l'hôte :

```sh
adb connect host.docker.internal:5555   # appareil/émulateur de l'hôte exposé en TCP
adb devices                             # vérifier la connexion
./gradlew connectedDebugAndroidTest
```

### Captures d'écran de contrôle

Rendu Robolectric natif de tous les écrans, sans appareil :

```sh
./gradlew testDebugUnitTest --tests '*ScreenshotTour' -Pselene.screenshots=build/screenshots
```

## Installer et déboguer

```sh
./gradlew installDebug                                  # compile et installe sur l'appareil connecté
adb install -r app/build/outputs/apk/debug/app-debug.apk # installer un APK déjà compilé (-r : mise à jour)
adb shell am start -n app.selene/.MainActivity           # lancer l'application
adb logcat --pid=$(adb shell pidof app.selene)          # logs de l'application seule
adb uninstall app.selene                                # désinstaller (EFFACE les données)
```

Sans câble ni adb : copier l'APK sur le téléphone et l'ouvrir, après avoir autorisé l'installation depuis des sources inconnues pour le gestionnaire de fichiers.

> ⚠️ Un APK ne s'installe par-dessus un autre que s'il est signé avec la **même clé**. Changer de clé (debug d'une autre machine, passage debug → release) impose une désinstallation, qui efface la base chiffrée. **Faire un export JSON avant** (Réglages → Exporter).

## Version release signée

`assembleRelease` produit un APK non signé (aucune configuration de signature dans le projet). Pour le signer :

```sh
# 1. Une seule fois : créer la clé. À conserver précieusement et hors du dépôt :
#    sans elle, aucune mise à jour ne pourra être installée par-dessus.
keytool -genkeypair -v -keystore ~/selene-release.jks -alias selene \
        -keyalg RSA -keysize 4096 -validity 10000

# 2. Compiler, aligner, signer, vérifier
./gradlew assembleRelease
BT=$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort -V | tail -1)
$BT/zipalign -p -f 4 app/build/outputs/apk/release/app-release-unsigned.apk app/build/selene-aligned.apk
$BT/apksigner sign --ks ~/selene-release.jks --ks-key-alias selene \
        --out app/build/selene-release.apk app/build/selene-aligned.apk
$BT/apksigner verify --print-certs app/build/selene-release.apk
```

Avant chaque nouvelle version, incrémenter `versionCode` (et `versionName`) dans `app/build.gradle.kts`.

## Inspecter un APK

```sh
BT=$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort -V | tail -1)
$BT/aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk  # permissions déclarées
$BT/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -3  # paquet, versions, SDK
```

## Gradle : astuces

```sh
./gradlew <tâche> --dry-run       # voir ce qu'une tâche exécuterait, sans la lancer
./gradlew :app:dependencies --configuration releaseRuntimeClasspath  # arbre des dépendances
./gradlew --stop                  # arrêter le démon (en cas de comportement étrange)
./gradlew assembleDebug --offline # compiler sans réseau (dépendances en cache)
```
