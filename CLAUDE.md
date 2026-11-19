# Sélène — application Android de suivi de cycle menstruel

> « Sélène » est un nom de travail. Le nom du paquet est `app.selene` ; les deux se changent à un seul endroit (`applicationId`, `app_name`).

## Le projet en une phrase

Une application Android très simple, **100 % hors-ligne**, pour noter ses règles, ses symptômes du jour (dont l'humeur) et des événements remarquables, visualiser le cycle en cours, et obtenir des prévisions (prochaines règles, prochaine ovulation) calculées localement à partir de l'historique.

L'interface et tous les textes sont en **français**. Le code (identifiants, commentaires, messages de commit) est en **anglais**.

## Règles non négociables

Ces règles priment sur toute autre considération. Si une tâche semble les contredire, s'arrêter et demander.

1. **Aucun réseau.** Le manifeste ne déclare **pas** `android.permission.INTERNET`. Aucune dépendance qui l'ajoute par fusion de manifeste (Firebase, Crashlytics, analytics, publicité, bibliothèques de polices téléchargeables, etc.). Un test vérifie que le manifeste fusionné ne contient pas cette permission (voir « Tests »).
2. **Aucun serveur, aucun compte, aucune télémétrie.** Pas de rapport de plantage distant.
3. **Stockage chiffré.** Toutes les données utilisateur vivent dans une base SQLCipher. Rien de sensible dans des `SharedPreferences` ou fichiers en clair, ni dans les logs (`Log.*` ne reçoit jamais une date de règles, un symptôme, une humeur ou un titre d'événement).
4. **Pas de sauvegarde implicite.** `android:allowBackup="false"` et règles `dataExtractionRules` qui excluent tout (cloud et transfert d'appareil). La seule sortie des données est l'export JSON déclenché par l'utilisatrice.
5. **Permissions minimales.** Uniquement `READ_CALENDAR` et `WRITE_CALENDAR`, demandées à l'exécution, seulement quand l'utilisatrice active la synchronisation du calendrier. Exception : `USE_BIOMETRIC` et `USE_FINGERPRINT`, permissions « normales » (sans dialogue, sans accès aux données) ajoutées par `androidx.biometric` pour le verrouillage facultatif. La tâche `verify<Variant>ManifestPermissions` n'autorise que ces quatre permissions. Import/export via le Storage Access Framework (aucune permission de stockage).
6. **Ce n'est pas un dispositif médical.** Les prévisions sont présentées comme des estimations, avec leur marge d'incertitude ; l'écran Analyse rappelle que ce n'est ni un avis médical ni une méthode de contraception.

## Pile technique

- Kotlin, Jetpack Compose, Material 3 (thème personnalisé, voir « Design »), module Gradle unique `:app`.
- `minSdk 26`, `targetSdk`/`compileSdk` : dernier SDK stable au moment de la création du projet.
- Dates : `java.time.LocalDate` partout (pas de `Date`/`Calendar` hors de l'intégration `CalendarContract`).
- Persistance : Room + SQLCipher (`net.zetetic:sqlcipher-android`, via `SupportOpenHelperFactory`).
- Clé de base : 32 octets aléatoires générés au premier lancement, chiffrés par une clé AES-GCM non exportable de l'Android Keystore, stockés chiffrés dans le stockage privé de l'application.
- Sérialisation : `kotlinx.serialization` (JSON).
- Injection : manuelle (un `AppContainer` créé dans `Application`). Pas de Hilt : le projet est petit.
- Navigation : Navigation Compose.
- Tests : JUnit + `kotlin.test` pour le domaine, Room en mémoire pour les DAO, Compose UI tests pour les parcours clés.
- Versions : utiliser les dernières versions stables, déclarées dans `gradle/libs.versions.toml`. **Vérifier les coordonnées et versions actuelles dans la documentation officielle avant de les écrire** plutôt que de les citer de mémoire. Avant d'ajouter une dépendance, vérifier qu'elle n'introduit pas la permission INTERNET.

## Architecture

```
app/src/main/java/app/selene/
  domain/            Kotlin pur, aucune importation android.*
    model/           DayLog, PeriodLog, SymptomLog, Mood, Symptom, NotableEvent, Cycle, Prediction
    CycleCalculator  découpe l'historique en cycles
    Predictor        statistiques + prévisions
    FullMoons        pleines lunes (Meeus, ch. 49), sans position : seul le fuseau horaire compte
  data/
    db/              entités Room, DAO, base chiffrée, KeyStore wrapper
    LogRepository    seule porte d'entrée vers la base (Flow)
    json/            export / import (schéma versionné)
    calendar/        PeriodCalendarSync (CalendarContract)
  ui/
    theme/           couleurs, typographie, formes
    cycle/           écran Cycle (anneau)
    calendar/        écran Calendrier + détail d'un jour
    entry/           feuille d'ajout / édition
    analysis/        écran Analyse
    settings/        écran Réglages
    components/      MoodFace, SymptomChip, LevelPicker, FlowPicker, BottomBar…
```

- Flux de données unidirectionnel : `Repository (Flow) → ViewModel (StateFlow<UiState>) → Composable`.
- `domain/` ne dépend de rien d'Android : tout l'algorithme de prévision est testable en JVM pur.
- Les prévisions ne sont **jamais stockées** : elles sont recalculées à partir des données à chaque changement.

## Modèle de données

Un enregistrement par jour au plus pour les règles et pour les symptômes ; plusieurs événements remarquables possibles par jour.

| Table           | Colonnes                                                                                     | Remarques                                                                                                                           |
| --------------- | -------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `period_log`    | `date` (PK, ISO `yyyy-MM-dd`), `flow` 1–4, `pain` 0–4                                        | flux : 1 léger, 2 moyen, 3 abondant, 4 très abondant                                                                                |
| `symptom_log`   | `date` (PK), `moods`, `symptoms` (codes séparés par des virgules), `irritability` 0–4 ou NULL, `fatigue` 0–4 ou NULL, `note` (≤ 200 caractères) ou NULL | « Symptômes du jour » : plusieurs humeurs et symptômes par jour ; une ligne vide est supprimée                                      |
| `notable_event` | `id` (UUID, PK), `date`, `title` (1–80 caractères)                                           | affiché avec l'icône éclair                                                                                                         |
| `settings`      | clé / valeur                                                                                 | synchronisation calendrier activée, id du calendrier choisi, id de l'événement créé, titre de l'événement, verrouillage biométrique |

**Symptômes du jour** (codes stables, à ne jamais renommer : ils sont stockés et exportés ; voir `Mood` et `Symptom` dans `domain/model/DayLog.kt`) :

- Humeur (plusieurs choix) : 😁 joyeuse `joyful`, 🙂 bien `good`, 😐 neutre `neutral`, 😒 agacée `annoyed`, 😔 triste `sad`, 😫 à bout `drained`, 🥺 fragile `fragile`, 😭 en larmes `crying`, 😰 anxieuse `anxious`. « Humeur basse » (signal SPM) : triste, à bout, fragile, en larmes, anxieuse, ou irritabilité ≥ 2 (modérée).
- Niveaux 0–4 facultatifs : irritabilité, fatigue.
- Douleurs : crampes, maux de tête, migraine, mal de dos, seins sensibles.
- Sexe : perte de libido, libido élevée, rapport sexuel.
- Peau : peau grasse, boutons, aphtes, peau sèche.
- Appétit et digestion : fringale, perte d'appétit, nausées 🤢, vomissements 🤮, ballonnements, constipation, diarrhée.
- Pertes et saignements : spotting (sang hors règles), pertes sèches, crémeuses, blanc d'œuf, inhabituelles.
- Sommeil : insomnie, sommeil agité, somnolence.
- Corps : rétention d'eau, bouffées de chaleur, vertiges.
- Autre : champ texte libre.

Un jour de règles n'exige plus d'humeur (l'ancien invariant humeur/règles est supprimé depuis la version 2 de la base et du format JSON). Règles et symptômes se suppriment indépendamment, avec annulation.

Base Room version 2 : la migration 1 → 2 convertit l'ancienne humeur 1–5 (1 en larmes, 2 triste, 3 neutre, 4 bien, 5 joyeuse) et déplace la fatigue des règles vers les symptômes (si > 0).

Tout événement est modifiable et supprimable après coup (depuis le détail d'un jour). Suppression avec annulation possible (snackbar « Annuler »).

## Format JSON d'export / import

```json
{
  "format": "selene-export",
  "version": 2,
  "exportedAt": "2026-11-15T13:47:00+01:00",
  "days": [
    {
      "date": "2026-11-09",
      "period": { "flow": 2, "pain": 2 },
      "symptoms": {
        "moods": ["sad"],
        "items": ["cramps", "nausea"],
        "irritability": 2,
        "fatigue": 3,
        "note": "Mal au genou"
      },
      "events": [{ "id": "6f1c…", "title": "Nuit blanche" }]
    }
  ]
}
```

- Un objet par jour ayant au moins une donnée ; `period`, `symptoms`, `events` (et chaque champ de `symptoms`) sont facultatifs ; jours triés par date.
- Version 1 (lecture seule) : `mood` 1–5 et `period.fatigue` sont convertis comme dans la migration de base.
- Les prévisions et les réglages ne sont pas exportés.
- **Export** : demander un mot de passe (au moins 8 caractères, saisi deux fois ; jamais conservé), puis `ACTION_CREATE_DOCUMENT`, nom proposé `selene-AAAA-MM-JJ.json`. Le fichier écrit est **toujours chiffré** (voir « Enveloppe chiffrée »).
- **Import** : `ACTION_OPEN_DOCUMENT`. Un fichier chiffré demande son mot de passe (mot de passe faux ou fichier altéré : message, nouvel essai possible) ; un export en clair (ancien) est encore accepté sans mot de passe. Valider entièrement avant d'écrire quoi que ce soit (format, version, dates, bornes, codes d'humeur et de symptôme inconnus, longueur de la note) ; en cas d'erreur, ne rien modifier et afficher un message précis. Proposer « Fusionner » (le fichier l'emporte jour par jour) ou « Remplacer tout ». Écriture en une transaction.
- Une version de schéma inconnue (supérieure) est refusée proprement. Toute évolution du schéma incrémente `version` et ajoute une migration de lecture.

### Enveloppe chiffrée

```json
{
  "format": "selene-encrypted",
  "version": 1,
  "kdf": { "name": "PBKDF2-HMAC-SHA256", "iterations": 600000, "salt": "<16 octets, base64>" },
  "cipher": { "name": "AES-256-GCM", "iv": "<12 octets, base64>" },
  "data": "<chiffré + tag GCM de 16 octets, base64>"
}
```

- Clé = PBKDF2-HMAC-SHA256(mot de passe normalisé NFC puis UTF-8, sel), 32 octets. `data` = AES-256-GCM du JSON `selene-export` ci-dessus (UTF-8), tag de 128 bits ajouté à la fin. Sel et IV aléatoires à chaque export.
- Code : `data/json/ExportCrypto`. Import : itérations acceptées de 10 000 à 10 000 000 (fichier piégé) ; `name` inconnu refusé.
- Exemple : `docs/exemples/exemple-export-chiffre.json`, mot de passe `lune rousse 2026` (aussi en ressource de test).

## Calcul des cycles et prévisions (`domain/`)

Kotlin pur, déterministe, sans effet de bord : `Predictor.predict(logs, today): Prediction`.

**Découpage en cycles**

- Un _épisode de règles_ = jours de règles consécutifs, en tolérant un trou d'un jour.
- Un _cycle_ va du premier jour d'un épisode à la veille du premier jour du suivant. Sa durée = écart entre deux débuts.
- Les cycles de durée < 15 j ou > 60 j sont exclus des statistiques (oubli de saisie probable) mais restent affichés.

**Statistiques**

- Sur les 12 derniers cycles valides au plus : durée moyenne pondérée (les cycles récents pèsent davantage), écart-type, durée moyenne des règles (moyenne simple, non pondérée : des règles mal notées un mois ne doivent pas peser davantage).
- Moins de 2 cycles valides : valeurs par défaut (cycle 28 j, règles 5 j) et fiabilité « faible ».
- Pondération : poids linéaires (le plus ancien des cycles retenus pèse 1, le plus récent n) ; l'écart-type utilise les mêmes poids.
- Fiabilité : faible < 2 cycles valides ; très bonne ≥ 6 cycles et écart-type ≤ 2 j ; bonne ≥ 4 cycles et écart-type ≤ 4 j ; moyenne sinon. Marge ± 3 j quand on utilise les valeurs par défaut.

**Prévision**

- Prochaines règles = début du cycle en cours + durée moyenne. Marge = ± max(1, écart-type arrondi) jours.
- Ovulation estimée = début des prochaines règles − 14 jours (phase lutéale, constante `LUTEAL_DAYS`). Fenêtre fertile = de 5 jours avant l'ovulation jusqu'au jour d'ovulation.
- Si la date prévue est dépassée sans règles saisies, la prévision glisse à « aujourd'hui » et l'état indique un retard.
- Fiabilité (faible / moyenne / bonne / très bonne) selon le nombre de cycles et l'écart-type.

**Affinage par l'humeur (syndrome prémenstruel)**

- Pour chaque cycle passé, chercher le premier jour d'« humeur basse » (au moins une humeur basse parmi celles du jour, voir `Mood.isLow`, ou une irritabilité ≥ 2 : `SymptomLog.isPmsSignalDay`) dans les 7 jours précédant les règles, et retenir son écart en jours avec le début des règles.
- Le signal est considéré fiable s'il est présent dans au moins 3 cycles et au moins la moitié des cycles analysés, avec un écart-type ≤ 1,5 j. Un cycle est « analysé » s'il a au moins une humeur ou un niveau d'irritabilité (même 0) saisi dans les 7 jours précédant les règles.
- Arc « SPM probable » de l'anneau : si le signal est fiable, de (prochaines règles − écart médian) à la veille des règles ; sinon les 5 derniers jours du cycle (affichés plus discrètement).
- Quand le signal est fiable et qu'une humeur basse est saisie dans la fin du cycle en cours (à partir de « date prévue − 8 jours »), calculer une seconde estimation = date de cette humeur + écart médian, puis la combiner à l'estimation statistique par moyenne pondérée par l'inverse des variances. La marge se resserre en conséquence.
- Cet affinage déplace les prochaines règles, donc aussi l'ovulation du cycle suivant.
- L'écran Analyse dit en clair si le signal est utilisé et sur combien de cycles il repose.

## Synchronisation avec le calendrier Android

- Désactivée par défaut. À l'activation dans Réglages : demande de `READ_CALENDAR` + `WRITE_CALENDAR`, puis choix du calendrier.
- **Confidentialité** : un calendrier lié à un compte (Google, Exchange…) est synchronisé vers un serveur. Proposer en premier un calendrier local (le créer au besoin avec `ACCOUNT_TYPE_LOCAL`) et afficher un avertissement clair si l'utilisatrice choisit un calendrier de compte.
- **Un seul événement**, sur la journée entière, couvrant les prochaines règles prévues (date estimée → + durée moyenne des règles − 1 ; en cas de retard, à partir d'aujourd'hui). Titre personnalisable, par défaut « Période de règles », sans description.
- L'identifiant de l'événement est conservé dans `settings`. À chaque changement de données, la prévision est recalculée et l'événement est **mis à jour** (jamais dupliqué). S'il a été supprimé depuis l'agenda, il est recréé. Quand les règles sont saisies, l'événement est déplacé sur les règles prévues suivantes.
- Désactiver la synchronisation supprime l'événement. Permission retirée : désactivation silencieuse du réglage, sans plantage.
- Ne lire aucun autre événement du calendrier que celui créé par l'application.

## Écrans

Barre du bas à 4 onglets (Cycle, Calendrier, Analyse, Réglages) avec un **gros bouton + central**, présent sur les quatre écrans.

1. **Cycle** — anneau représentant le cycle en cours : arc des règles (à partir du haut), arc de la fenêtre fertile, arc « SPM probable » en fin de cycle, curseur blanc sur le jour courant, « Jour N sur M » et la phase au centre. Dessiné avec `Canvas` Compose. Dessous : cartes « Ovulation estimée » et « Prochaines règles » (date, « dans N j », marge).
2. **Calendrier** — liste verticale de mois défilante (`LazyColumn`), trois mois visibles d'un coup d'œil, semaine commençant le lundi. Jour de règles saisi : pastille pleine corail ; règles prévues : contour pointillé corail ; fenêtre fertile : fond turquoise léger ; ovulation : pastille pleine turquoise ; aujourd'hui : contour blanc ; événement remarquable : point jaune (rayon 4,5 dp) sous le jour ; pleine lune : point blanc (rayon 3,5 dp) sous le jour, à gauche du point jaune s'il y en a un. Toucher un jour ouvre son détail.
3. **Détail d'un jour** — liste des éléments du jour, chacun avec « Modifier » et « Supprimer », et un bouton « Ajouter à ce jour ». Les jours de pleine lune affichent une carte « 🌕 Pleine lune » avec l'heure locale (information, ni modifiable ni supprimable).
4. **Ajout / édition** (bouton +) — sélecteur de date (aujourd'hui par défaut, pas de date future), puis trois types :
   - **Règles** : flux (4 niveaux), douleur (0–4).
   - **Symptômes** : page « Symptômes du jour » par cartes de catégorie (humeur en visages, irritabilité et fatigue, puces à cocher avec emoji, note « Autre »).
   - **Événement** : icône éclair + titre.
     Le même écran sert à l'édition (champs préremplis).
5. **Analyse** — cycle moyen, variabilité, durée des règles ; barres des durées des derniers cycles avec la moyenne ; prévision (dates + intervalles + fiabilité) ; explication du signal d'humeur ; rappel « ni avis médical ni contraception ».
6. **Réglages** — exporter / importer en JSON ; synchronisation du calendrier (interrupteur, calendrier, titre) ; état du chiffrement ; verrouillage biométrique facultatif (`BiometricPrompt`) ; « Effacer toutes les données » avec double confirmation.

État vide (premier lancement) : l'anneau est neutre et invite à noter le premier jour de règles ; Analyse explique qu'il faut au moins deux cycles.

## Design

Thème sombre unique, validé sur maquettes. Ne pas utiliser les couleurs dynamiques Material You.

| Rôle                                | Couleur                            |
| ----------------------------------- | ---------------------------------- |
| Fond                                | `#15121C`                          |
| Surface (cartes)                    | `#201B2B`                          |
| Surface élevée / sélection inactive | `#2B2439`                          |
| Barre du bas                        | `#1B1725`                          |
| Séparateurs                         | `#2E2740`, bordures `#3A3150`      |
| Texte principal                     | `#F4F0FA`                          |
| Texte secondaire                    | `#B0A6C2`                          |
| Règles (et bouton +)                | `#FF7A6B` — texte dessus `#15121C` |
| Ovulation / fenêtre fertile         | `#8CE6D8`                          |
| SPM probable                        | `#7C68C4`                          |
| Événement remarquable               | `#FFD666`                          |

- Typographie : Space Grotesk (titres, chiffres) et Inter (texte), **embarquées dans `res/font`** — jamais de polices téléchargeables (réseau).
- Rayons : cartes 18 dp, boutons de choix 14 dp, bouton principal en pilule de 56 dp de haut, bouton + rond de 68 dp.
- Humeurs : 9 visages vectoriels dessinés (composant `MoodFace`) inspirés des emoji 😁 🙂 😐 😒 😔 😫 🥺 😭 😰, pas des emoji système (🥺 n'existe pas sur Android 8). Puces de symptômes : emoji système limités à Emoji 5.0 (présents dès Android 8) ; le spotting utilise la goutte vectorielle corail. Événement remarquable : icône éclair vectorielle jaune.
- Accessibilité : cibles tactiles ≥ 48 dp, contraste texte ≥ 4,5:1, `contentDescription` sur l'anneau, les visages et les jours du calendrier (ex. « 9 novembre, règles, événement remarquable »). L'information ne repose jamais sur la couleur seule (plein / pointillé / contour).
- Si des maquettes sont présentes dans `docs/maquettes/`, elles font référence pour la mise en page.

## Tests

- `domain/` : couverture complète du découpage en cycles et du `Predictor` — historique vide, un seul cycle, cycles réguliers, irréguliers, valeurs aberrantes, trou d'un jour dans les règles, retard, signal d'humeur fiable / non fiable.
- `data/` : symptômes (enregistrement, remplacement, suppression, restauration) au niveau du dépôt ; migration de base 1 → 2 ; lecture des exports v1 ; aller-retour export → import identique ; chiffrement de l'export (vecteurs PBKDF2, mauvais mot de passe, fichier altéré, exemple documenté) ; import invalide ne modifie rien ; la base ne s'ouvre pas sans la clé.
- Manifeste : un test (ou une tâche Gradle de vérification) échoue si le manifeste fusionné contient `android.permission.INTERNET`.
- UI : ajouter un jour de règles, ajouter plusieurs symptômes, modifier, supprimer.
- Pas d'émulateur dans le conteneur : les tests DAO, dépôt et UI Compose tournent sous Robolectric dans `testDebugUnitTest` (SQLite non chiffré, base en mémoire via `TestContainer`). Les tests propres à SQLCipher et au Keystore (`androidTest/…/EncryptedDatabaseTest`) demandent un appareil.
- Captures d'écran de contrôle (rendu Robolectric natif) : `./gradlew testDebugUnitTest --tests '*ScreenshotTour' -Pselene.screenshots=<dossier>`.

## Commandes

```bash
./gradlew assembleDebug          # compiler
./gradlew testDebugUnitTest      # tests unitaires (domaine, JSON)
./gradlew connectedDebugAndroidTest   # tests instrumentés (base, UI)
./gradlew lint                   # lint Android
```

Lancer les tests unitaires et le lint avant de considérer une tâche terminée.

## Ordre de réalisation

1. Squelette du projet, thème, navigation, barre du bas avec bouton +, test « pas d'INTERNET ».
2. Modèle du domaine, `CycleCalculator`, `Predictor` et leurs tests (sans interface).
3. Base chiffrée (Room + SQLCipher + Keystore), `LogRepository`, invariant.
4. Feuille d'ajout / édition, détail d'un jour, suppression.
5. Écran Cycle (anneau).
6. Écran Calendrier.
7. Écran Analyse.
8. Export / import JSON.
9. Synchronisation du calendrier Android.
10. Verrouillage biométrique, états vides, passe d'accessibilité.

Une étape = un ou plusieurs commits petits et compilables. Ne pas commencer une étape tant que les tests de la précédente ne passent pas.

## Conventions

- Composables sans état quand c'est possible ; l'état vit dans les ViewModels.
- Tous les textes dans `res/values/strings.xml` (français). Pluriels via `plurals`.
- Dates affichées avec `DateTimeFormatter` en `Locale.FRENCH`.
- Pas de nouvelle dépendance sans nécessité claire ; toute dépendance ajoutée est justifiée dans le message de commit.
- En cas de doute sur une règle métier (prévision, invariant, format JSON), ce fichier fait foi ; s'il est muet, demander plutôt que d'inventer.
