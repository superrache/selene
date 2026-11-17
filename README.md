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
./gradlew assembleRelease      # APK release minifié → app/build/outputs/apk/release/selene-<version>-release.apk
                               # (signé si keystore.properties existe, sinon -unsigned)
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

Le build debug a pour identifiant `app.selene.debug` (nom affiché « Sélène (debug) ») : il s'installe **à côté** de la version publiée `app.selene`, avec ses propres données.

```sh
./gradlew installDebug                                        # compile et installe sur l'appareil connecté
adb install -r app/build/outputs/apk/debug/selene-*-debug.apk # installer un APK déjà compilé (-r : mise à jour)
adb shell am start -n app.selene.debug/app.selene.MainActivity # lancer l'application
adb logcat --pid=$(adb shell pidof app.selene.debug)          # logs de l'application seule
adb uninstall app.selene.debug                                # désinstaller (EFFACE les données)
```

> ⚠️ Un APK ne s'installe par-dessus un autre que s'il est signé avec la **même clé**. Changer de clé impose une désinstallation, qui efface la base chiffrée. **Faire un export JSON avant** (Réglages → Exporter).

## Publier une version (GitHub Releases + Obtainium)

### Une seule fois : la clé de signature

```sh
keytool -genkeypair -v -keystore release.jks -alias selene -keyalg RSA -keysize 4096 -validity 10000
```

Puis créer `keystore.properties` à la racine :

```properties
storeFile=release.jks
storePassword=…
keyAlias=selene
keyPassword=…
```

`release.jks` et `keystore.properties` sont ignorés par git. **Sauvegarder les deux** (gestionnaire de mots de passe, clé USB) : sans cette clé, plus aucune mise à jour ne pourra s'installer par-dessus l'application des utilisatrices.

### À chaque version

1. Incrémenter `versionCode` (+1) et `versionName` (ex. `0.2.0`) dans `app/build.gradle.kts`.
2. `./gradlew testDebugUnitTest lint assembleRelease`
3. Vérifier la signature (doit afficher votre certificat, pas `CN=Android Debug`) :
   ```sh
   BT=$ANDROID_HOME/build-tools/$(ls $ANDROID_HOME/build-tools | sort -V | tail -1)
   $BT/apksigner verify --print-certs app/build/outputs/apk/release/selene-0.2.0-release.apk
   ```
4. Commiter, créer le tag `v0.2.0` et le pousser : `git tag v0.2.0 && git push origin main v0.2.0`.
5. Sur GitHub → _Releases_ → _Draft a new release_ → choisir le tag `v0.2.0`, écrire les nouveautés, joindre l'APK, _Publish_.

Obtainium compare le tag de la release (`v0.2.0`) au `versionName` installé : garder les deux identiques.

### Côté utilisatrice

1. Installer [Obtainium](https://github.com/ImranR98/Obtainium/releases) (une seule autorisation « sources inconnues », pour Obtainium).
2. Obtainium → _Ajouter une application_ → `https://github.com/superrache/selene` → _Installer_.
3. Les mises à jour sont ensuite proposées par Obtainium.

Le dépôt doit être **public** pour qu'Obtainium le lise sans jeton.

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

## Licence

Sélène est un logiciel libre distribué sous la licence **GNU GPL v3 ou ultérieure** (`GPL-3.0-or-later`) : voir [`LICENSE`](LICENSE). Toute version modifiée et redistribuée doit rester libre, sous la même licence, avec son code source.

Composants tiers embarqués :

- Polices Space Grotesk et Inter (`app/src/main/res/font/`) : SIL Open Font License 1.1.
- Bibliothèques AndroidX, Kotlin et kotlinx : Apache License 2.0.
- SQLCipher Community Edition : licence de type BSD (Zetetic).
