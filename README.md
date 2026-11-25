# NOVA IPTV

Application IPTV et lecteur multimédia premium pour **Android Mobile, Android TV et Google TV**
(Kotlin, Jetpack Compose, Material 3).

- Cahier des charges : [NOVA_IPTV_CAHIER_DES_CHARGES.md](NOVA_IPTV_CAHIER_DES_CHARGES.md)
- Dossier de reprise : [docs/REPRISE_DU_PROJET.md](docs/REPRISE_DU_PROJET.md)
- Spécifications UX/UI : [docs/SPECIFICATIONS_UX_UI.md](docs/SPECIFICATIONS_UX_UI.md)
- Marque vectorielle NOVA : [nova_mark.xml](app/src/main/res/drawable/nova_mark.xml) (symbole lecture/nova minimaliste, sans effet 3D)

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

Sur TV, le menu garde une géométrie stable pendant la navigation : barre latérale
avec libellés (Classique/Guide), rail compact (Cinéma/Horizon) ou navigation haute
(Univers/Antenne).
Le bandeau d'accueil peut se parcourir par balayage horizontal au doigt ou avec
ses boutons précédent/suivant. Sur TV, placez le focus sur ces boutons :
Gauche/Droite change de suggestion et OK active le bouton. Depuis la première
suggestion, Gauche permet de ressortir vers le menu. Haut retrouve les actions
Regarder/Détails. La rotation automatique (12 s) se met en pause pendant le
focus ou un balayage, puis reprend avec un nouveau délai après l'interaction.
Sur TV, une seule suggestion est affichée à la fois (fondu enchaîné) : aucun bouton
d'une suggestion voisine n'existe hors écran, donc Droite depuis « Détails » reste
sur ce bouton au lieu de faire défiler le bandeau en boucle. Les flèches du
carrousel restent disponibles pour changer de suggestion.
Les réglages disposent de sections dédiées : en disposition latérale, Haut/Bas
choisit la section, Droite ou OK entre dans ses actions, Gauche retourne aux
sections. En disposition haute, Gauche/Droite choisit la section, Bas ou OK entre
dans les actions et Haut depuis la première action retrouve la section.
Les boutons ont un fond et une bordure de focus distincte de l'option active.
Les catalogues films/séries adaptent les catégories et les cartes au style choisi ;
Gauche depuis la première colonne retrouve les catégories verticales, Haut depuis
la première rangée retrouve les catégories horizontales.
La recherche s'ouvre uniquement après OK sur son bouton : le passage du focus
ne déclenche plus le clavier.
Dans le direct classique/Guide, choisissez une catégorie puis appuyez à droite
pour accéder aux chaînes ; la recherche et le guide restent accessibles en
remontant dans la colonne centrale. Dans les modes mosaïque/programmes, Bas depuis
les catégories entre dans les chaînes ; Haut depuis la première chaîne les retrouve.
Dans le formulaire de profil, l'adresse du guide TV est facultative : la flèche
bas ou l'action « Terminé » du clavier mène au bouton d'enregistrement. Le bouton
en forme d'œil permet d'afficher ou masquer le mot de passe Xtream saisi.
Sur TV, les champs du formulaire sont d'abord des boutons de navigation :
les flèches ne lancent aucun clavier. OK active la saisie du champ sélectionné ;
« Terminé » rend la navigation aux flèches, et Retour quitte la saisie sans
effacer sa valeur. Le formulaire mobile conserve ses champs de saisie habituels.
Dans le lecteur TV, OK et les flèches gauche/droite réaffichent les commandes ;
les touches multimédia restent utilisables. La flèche haut ouvre la barre NOVA,
qui donne notamment accès à toutes les options de lecture.
Sur TV, les boutons et les commandes du lecteur affichent un repère de focus
renforcé pour suivre la sélection à la télécommande.
Dans le lecteur, les icônes de la barre restent compactes (48 dp), sans les
boutons remplis utilisés dans les réglages. Les choix audio/sous-titres gardent
un fond sombre et un texte blanc, même en thème clair ; une coche indique
l'option active et un contour blanc indique le focus. Le repère des commandes
Media3 est un contour transparent au centre : il ne recouvre plus leurs icônes
ni la barre de progression d'un remplissage coloré.
Les surfaces, angles, accents et sélections du lecteur suivent l'interface et sa
palette : NOVA+ reste noir/blanc, Cinéma reprend ses accents rouges. La progression
native Media3 reprend aussi l'accent. Une palette claire conserve un lecteur
sombre neutre et lisible ; l'indicateur de focus reste blanc, sans remplissage.
Après la création du premier code PIN, la sélection des catégories s'ouvre
directement si un profil est actif. Dans cette liste, OK coche/décoche une
catégorie et la flèche droite mène au bouton « Terminé ».
Le contrôle parental masque les catégories dans les catalogues, mais pas encore
dans tous les parcours (notamment accueil, guide TV et certaines reprises) : il
ne constitue donc pas une protection complète.

## Téléchargements hors ligne

Films et épisodes peuvent être téléchargés pour être regardés sans connexion
(moteur de téléchargement Media3, comme les applications de streaming Android).

- **Où** : bouton « Télécharger » sur la fiche d'un film ; icône à droite de chaque
  épisode et bouton « Télécharger la saison » sur la fiche d'une série.
- **Suivi** : onglet « Hors ligne » sur téléphone ; bouton « Téléchargements » de
  l'accueil et section « Téléchargements » des réglages. Les séries sont regroupées
  en dossiers, les épisodes triés par saison et numéro. Chaque élément peut être
  lu, mis en pause, repris, annulé ou supprimé ; l'espace utilisé est affiché.
- **Arrière-plan** : notification de progression puis « Prêt à regarder hors ligne » ;
  2 téléchargements simultanés ; reprise automatique après une coupure réseau ou un
  redémarrage du téléphone.
- **Réglages** : Wi-Fi uniquement (activé par défaut) ; qualité Standard (720p) ou
  Haute pour les flux adaptatifs HLS (les films en un seul fichier sont enregistrés
  tels quels) ; téléchargements intelligents (épisode regardé jusqu'au bout supprimé,
  épisode suivant téléchargé).
- **Hors connexion** : le lecteur lit le fichier local, même en mode avion ; l'accueil
  affiche « Vous êtes hors ligne » avec un accès direct aux téléchargements.
  La fiche d'une série Xtream nécessite le réseau : hors ligne, passez par l'écran
  Téléchargements.

Les fichiers sont stockés dans l'espace privé de l'application (supprimés à la
désinstallation) ; supprimer un profil supprime ses téléchargements. Les fournisseurs
IPTV limitent souvent le nombre de connexions simultanées : un téléchargement en cours
peut en occuper une. Le contrôle parental ne s'applique pas encore à l'écran des
téléchargements.

## Interfaces TV

### Mode paysage sur téléphone et tablette

En paysage, téléphones (6–7") et tablettes affichent l'interface TV choisie
(menu, accueil, catalogues, direct, réglages) au lieu de l'interface mobile.
Elle est conçue pour un écran de 480 dp de haut : sur un écran plus bas, tout le
cadre est réduit proportionnellement (jusqu'à 60 %) pour garder la même composition,
dans la zone sûre (encoche, barres système). Les tablettes assez hautes l'affichent
sans réduction. Tout reste utilisable au doigt ; les champs de saisie gardent le
clavier tactile habituel. En portrait, l'interface mobile est inchangée.

Les réglages sont organisés de la même manière partout : sections Profils, Lecture,
Langues, Apparence, Contrôle parental et Sauvegarde. Sur téléphone en portrait,
elles deviennent des onglets à faire défiler en haut de l'écran ; la section choisie
s'affiche dessous avec de grands boutons tactiles.

Dans **Réglages → Apparence → Interface TV**, six dispositions NOVA originales
s'ajoutent à **NOVA Classique**, conservée par défaut pour permettre un retour sûr :

| Interface | Navigation | Accueil et contenus | Direct |
|---|---|---|---|
| NOVA Cinéma | Rail compact | Grande scène immersive, affiches verticales, catégories horizontales | Mosaïque + aperçu |
| NOVA Univers | Menu haut | Collections réelles du catalogue, grandes affiches, catégories horizontales | Mosaïque + aperçu |
| NOVA Antenne | Menu haut | Direct prioritaire, cartes paysage, catégories latérales | Liste de programmes maintenant/ensuite + aperçu |
| NOVA Horizon | Rail compact | Reprises en tête, cartes larges, catégories horizontales | Mosaïque + aperçu |
| NOVA Guide | Barre latérale | Raccourcis TV, catalogue en listes détaillées, guide plus dense | Trois colonnes catégories/chaînes/aperçu |
| NOVA+ | Menu haut | Scène de présentation, accès direct puis collections à explorer, affiches | Liste de programmes + aperçu |

Les fiches, cartes de recherche, sections de réglages et panneaux du lecteur
s'adaptent également au style. Les collections d'Univers ouvrent le bon catalogue,
filtré sur leur catégorie. Choisir une interface applique aussi sa palette par
défaut : noir/rouge pour Cinéma, noir/or pour Antenne, noir/blanc pour NOVA+ et
NOVA Blue pour les autres.
La palette peut ensuite être personnalisée sans changer la disposition ;
resélectionner une interface réapplique ses couleurs par défaut. Le choix est
persistant et inclus dans les sauvegardes, qui conservent les couleurs personnalisées.
Une ancienne sauvegarde sans interface TV conserve le choix installé.

Ces dispositions s'appliquent aux TV et aux téléphones/tablettes en paysage ;
le mobile en portrait conserve son interface. NOVA+ propose une direction noir et blanc
inspirée des portails TV/cinéma, sans copie des écrans Canal+. Ces dispositions
ne reproduisent pas les logos ou écrans
propriétaires d'autres services.

Les tests Robolectric couvrent le changement de navigation sans perte d'état,
la sélection d'interface à la télécommande, les collections filtrées, les cartes
affiche/paysage/liste, la navigation dans le direct et la sauvegarde. Des rendus
960 × 540 sont produits dans `app/build/reports/tv-designs/`.
Ces tests ne remplacent pas un essai sur Fire TV/TCL réels.

### Logo dans le lanceur TV

L'icône par défaut de l'APK est un PNG à toutes les densités (`mipmap-*dpi`),
avec une variante 512 × 512 pour la TV (`mipmap-television-nodpi`). Le lanceur
Fire TV lit l'icône déclarée par l'APK sans tenir compte du mode TV : il recevait
auparavant une icône adaptative XML qu'il n'affichait pas. L'icône adaptative est
désormais réservée aux écrans tactiles (`mipmap-anydpi-finger-v26`), donc aux
téléphones et tablettes. La bannière NOVA est un PNG 320 × 180.
Vérification : `aapt2 dump badging app-debug.apk` doit lister uniquement des PNG.

L'affichage dans le lanceur Fire TV doit encore être confirmé sur appareil.
Si le lanceur conserve l'ancienne vignette après la mise à jour, redémarrer
le Fire TV avant de conclure à un défaut ; ne pas effacer les données de NOVA.

## Télécharger l'APK (téléphone / TV)

Lien direct, mis à jour automatiquement à chaque push sur `main` :

https://github.com/Kacembvs/NovaTV/releases/download/latest-debug/nova-iptv-debug.apk

Toutes les versions de debug sont signées avec la même clé (`app/debug.keystore`, clé de
debug publique standard), ce qui permet d'installer une nouvelle version par-dessus l'ancienne.

## Application iPhone / iPad (iOS 17+)

Le dossier `ios/` contient une application native Swift/SwiftUI avec les mêmes
fonctions : profils M3U/Xtream (mot de passe dans le trousseau iOS), TV en direct
avec programme en cours/suivant, films, séries et saisons, recherche, Ma liste,
reprise de lecture, guide TV, téléchargements hors ligne, thèmes NOVA et contrôle
parental.

- **Lecteur** : lecteur Apple (Picture-in-Picture, AirPlay, pistes audio/sous-titres,
  vitesse, écran de verrouillage) et **moteur VLC intégré** (MobileVLCKit 3.6) pour
  MKV, AVI, TS, WMV… avec commandes NOVA (±10 s, double appui, pistes, vitesse).
  Bascule automatique vers VLC si le lecteur Apple échoue ; bascule manuelle dans « … ».
  Reprise, épisode suivant automatique, zapping, minuteur de sommeil, langues préférées.
  « Ouvrir avec » VLC, Outplayer ou Infuse, et lecteur par défaut au choix.
- **Réglages** : mêmes sections qu'Android (Profils, Lecture, Téléchargements, Langues,
  Apparence, Contrôle parental, Sauvegarde) ; barre latérale en paysage et sur iPad.
  Les sept interfaces NOVA (NOVA+, Cinéma…) appliquent couleurs et angles (droits sur
  NOVA+) ; textes et interrupteurs restent lisibles sur les accents blancs.
- **Sauvegarde** : même fichier que sur Android (code facultatif, PBKDF2 + AES-GCM) :
  une sauvegarde Android se restaure sur iPhone et inversement.
- **Compilation** : le workflow `.github/workflows/ios.yml` (runner macOS) génère le
  projet avec XcodeGen, lance les tests unitaires puis publie une IPA **non signée** :
  https://github.com/Kacembvs/NovaTV/releases/download/latest-ios/nova-ios-unsigned.ipa
- **Installation sans compte développeur payant** : Sideloadly (PC/Mac) ou AltStore
  signent l'IPA avec votre identifiant Apple gratuit ; l'app doit être re-signée tous
  les 7 jours (AltStore le fait automatiquement en Wi-Fi). Activez ensuite le mode
  développeur de l'iPhone (Réglages → Confidentialité et sécurité).

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

Tests unitaires : groupes de chaînes, URLs Cast, contrôle parental, worker d'actualisation, sauvegarde/restauration chiffrée, parseurs M3U et XMLTV, URLs Xtream, règles autoplay/sommeil, libellés pistes/vitesses, recommandations, migrations de base et navigation TV sous Robolectric. Parseur validé
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
| Panneaux NOVA translucides : audio, sous-titres, vitesse (0,5x→2x), qualité, minuteur, options | Fait |
| Gestes mobiles : luminosité (gauche), volume (droite), double appui ±10 s, désactivables | Fait |
| Verrouillage de l'écran, rotation configurable, informations techniques du flux | Fait |
| Télécommande : touche Menu = options ; haut depuis la barre de progression = barre d'actions | Fait |
| Fiche film : synopsis, durée, genre, réalisation, casting (Xtream get_vod_info), Regarder/Reprendre/Recommencer | Fait |
| J'aime / Je n'aime pas (films et séries), titres similaires | Fait |
| Recommandations locales (j'aime, favoris, visionnages, je n'aime pas), sans serveur | Fait |
| Accueil : séries en cours, coups de cœur, recommandés, nouveaux films/séries (Xtream) | Fait |
| Recherche globale chaînes / films / séries | Fait |
| Interfaces TV Classique, Cinéma, Univers, Antenne, Horizon, Guide et NOVA+ : dispositions et navigation distinctes | Implémenté, tests locaux ; validation TV réelle à faire |
| Bannière d'accueil (recommandés / nouveautés / sélection aléatoire), rotation lente | Fait |
| TV en direct en 3 zones (catégories, chaînes, aperçu vidéo + programme) sur TV et grand écran | Fait |
| Thèmes NOVA Blue, Cinema Gold, Stream Red, Pure Light, Personnalisé (accent) — changement immédiat | Fait |
| Picture-in-Picture mobile (auto Android 12+, bouton lecture/pause), désactivable | Fait |
| Sauvegarde/restauration JSON : profils, favoris, j'aime, reprises, réglages ; mots de passe chiffrés par code (PBKDF2 + AES-GCM) | Fait |
| Contrôle parental : code PIN haché (PBKDF2), catégories des catalogues masquées, réglages protégés | Partiel : accueil, guide et certaines reprises non couverts |
| Langues audio / sous-titres préférées (réglages + mémorisation du choix fait dans le lecteur) | Fait |
| Actualisation automatique WorkManager toutes les 12 h (playlists + guide), désactivable | Fait |
| Groupes de chaînes personnalisés (créer, renommer, supprimer, zapping dans le groupe, sauvegardés) | Fait |
| Google Cast (Default Media Receiver) : diffusion, pause, zapping, reprise locale à l'arrêt | Fait (dépend de la compatibilité du flux) |
| Tests sur appareils réels, version release signée (à la demande) | À faire |
| Tests instrumentés / sur Android TV réelle | À faire |

Les tests locaux vérifient le dessin du focus, les lignes de réglage à cible unique
et l'accès aux actions parentales au D-pad. Ils ne remplacent pas les essais TCL /
Fire TV. Le parcours des dialogues PIN et des catégories est couvert par des
tests instrumentés à exécuter sur appareil : le dialogue PIN n'a pas pu être
validé sous Robolectric (fenêtre non affichée / attente de rendu).