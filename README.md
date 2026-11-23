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

https://github.com/kacemox/NOVA-IPTV-A/releases/download/latest-debug/nova-iptv-debug.apk

Toutes les versions de debug sont signées avec la même clé (`app/debug.keystore`, clé de
debug publique standard), ce qui permet d'installer une nouvelle version par-dessus l'ancienne.

## Intégration continue

Le workflow `.github/workflows/main.yml` s'exécute à chaque push sur `main`/`master`,
sur les pull requests et manuellement. Il installe le JDK 17, met Gradle en cache,
lance `./gradlew clean assembleDebug testDebugUnitTest` et publie l'APK dans l'artefact
`nova-iptv-debug-apk`.

## État du projet

**Phase 1 — remise en ordre : terminée et vérifiée** (build debug et release réussis localement).

| Élément | État |
|---|---|
| Structure Gradle à la racine, wrapper, `.gitignore` | Fait |
| Icône adaptative + bannière Android TV (depuis le logo) | Fait |
| Lancement mobile (LAUNCHER) et TV (LEANBACK_LAUNCHER) | Fait |
| Écran d'accueil Compose (aperçu visuel, aucune action) | Fait |
| Workflow GitHub Actions | Écrit, à valider au premier push |
| Navigation, profils, M3U/Xtream, Room, lecteur Media3, EPG, films/séries, autoplay | À faire (phases suivantes) |
