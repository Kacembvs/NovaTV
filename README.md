# NOVA IPTV

Application IPTV et lecteur multimédia premium pour **Android Mobile, Android TV et Google TV**
(Kotlin, Jetpack Compose, Material 3).

- Cahier des charges : [NOVA_IPTV_CAHIER_DES_CHARGES.md](NOVA_IPTV_CAHIER_DES_CHARGES.md)
- Dossier de reprise : [docs/REPRISE_DU_PROJET.md](docs/REPRISE_DU_PROJET.md)
- Spécifications UX/UI : [docs/SPECIFICATIONS_UX_UI.md](docs/SPECIFICATIONS_UX_UI.md)
- Logo source : [docs/logo_nova_iptv.jpg](docs/logo_nova_iptv.jpg)

## Arborescence

```
.github/workflows/main.yml   CI GitHub Actions (build + APK en artefact)
app/                         Module Android
gradle/wrapper/              Gradle Wrapper (Gradle 8.9)
gradlew, gradlew.bat
build.gradle.kts, settings.gradle.kts, gradle.properties
docs/                        Cahiers des charges, logo
```

## Versions

| Composant | Version |
|---|---|
| JDK | 17 |
| Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 23 |
| Compose BOM | 2024.12.01 |

## Compiler

Prérequis : JDK 17 et Android SDK (platform 35). Android Studio fournit les deux.

```bash
./gradlew clean assembleDebug        # Linux / macOS
gradlew.bat clean assembleDebug      # Windows
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`
(identifiant `com.nova.iptv.debug`, installable à côté d'une future version release).

Installation sur un appareil : `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Hors Android Studio, créer un fichier `local.properties` (non versionné) contenant
`sdk.dir=<chemin du SDK Android>`.

## Télécharger l'APK (téléphone / TV)

Lien direct, mis à jour automatiquement à chaque push sur `main` :

https://github.com/Kacembvs/NovaTV/releases/download/latest-debug/nova-iptv-debug.apk

Toutes les versions de debug sont signées avec la même clé (`app/debug.keystore`, clé de
debug publique standard), ce qui permet d'installer une nouvelle version par-dessus l'ancienne.

## Intégration continue

Le workflow `.github/workflows/main.yml` s'exécute à chaque push sur `main`/`master`,
sur les pull requests et manuellement. Il installe le JDK 17, met Gradle en cache,
lance `./gradlew clean assembleDebug testDebugUnitTest` et publie l'APK dans l'artefact
`nova-iptv-debug-apk`.

## Architecture

```
app/src/main/java/com/nova/iptv/
  NovaApp, AppContainer      Application + injection de dépendances manuelle
  core/                      HTTP (OkHttp), chiffrement Keystore, utilitaires
  domain/                    Modèles métier (types de profil/contenu, PlayableItem)
  data/                      Profils, synchronisation progressive, préférences (DataStore)
  data/db/                   Room : profils, contenus, favoris, historique
  data/source/               Parseur M3U en streaming, client Xtream Codes
  ui/                        Compose : navigation adaptative, accueil, TV, catalogues, séries, profils
  player/                    Lecteur Media3 (ExoPlayer + HLS)
```

## État du projet

Compilation debug vérifiée, 13 tests unitaires OK (parseurs M3U et XMLTV, URLs Xtream, règles autoplay/sommeil, migration de base 1→2 sous Robolectric). Parseur validé
sur une playlist publique réelle (231 chaînes, 33 catégories).

| Fonction | État |
|---|---|
| Profils M3U/M3U8 et Xtream : ajouter, modifier, supprimer, actualiser, changer de profil | Fait |
| Mot de passe Xtream chiffré (Android Keystore), jamais stocké en clair ni dans les URLs en base | Fait |
| Chargement progressif par lots de 500 (navigation possible pendant la synchro) | Fait |
| TV en direct : catégories, recherche, favoris, logos | Fait |
| Films et séries : grilles d'affiches, catégories, recherche, favoris | Fait |
| Séries Xtream : saisons, épisodes, progression par épisode | Fait |
| Lecteur Media3 : HLS/TS/MP4/MKV, pistes audio, sous-titres, vitesse, plein écran | Fait |
| Zapping : boutons précédent/suivant, flèches haut/bas, touches CH+/CH- | Fait |
| Reprise de lecture (sauvegarde toutes les 30 s et à la fermeture) + « Recommencer » | Fait |
| Reconnexion automatique du direct (3 tentatives), lecteur externe (VLC…) | Fait |
| Accueil : reprendre, dernières chaînes, favoris (rangées masquées si vides) | Fait |
| Navigation : barre inférieure (mobile), barre latérale + focus visible (TV) | Fait |
| Épisode suivant automatique : compte à rebours (5/10/15/20 s, défaut 20), « Lire maintenant » / « Annuler » | Fait |
| Mode sommeil prioritaire : fin de l'épisode, fin du film, 15/30/45/60 min (arrêt + sauvegarde) | Fait |
| Réglages de lecture (onglet Réglages) | Fait |
| Saut automatique du générique | Non fait : aucune donnée fiable (chapitres) fournie par les flux IPTV |
| Guide TV XMLTV (.xml / .xml.gz) en streaming : url-tvg, xmltv.php Xtream ou adresse personnalisée | Fait |
| Association chaîne/EPG par tvg-id puis par nom (89/231 chaînes iptv-org FR retrouvées avec xmltvfr.fr) | Fait |
| Programme en cours/suivant (liste des chaînes et lecteur), grille Guide TV 12 h | Fait |
| Actualisation du guide après chaque synchro et au démarrage si > 12 h | Fait (sans WorkManager) |
| Gestes luminosité/volume, verrouillage écran, thèmes, recommandations | À faire |
| Tests instrumentés / sur Android TV réelle | À faire |