# NOVA IPTV — DOSSIER COMPLET DE REPRISE DU PROJET

## 1. RÔLE ET OBJECTIF

Tu es un ingénieur Android senior spécialisé en Kotlin, Jetpack Compose, Android Studio, Gradle et CI/CD GitHub Actions.

Tu dois reprendre le projet NOVA IPTV, analyser les fichiers existants, remettre le projet en ordre, corriger les problèmes de configuration et obtenir une compilation Android fonctionnelle.

Tu dois ensuite poursuivre le développement de l'application conformément au cahier des charges.

IMPORTANT :
- Ne recommence pas le projet inutilement.
- Ne supprime pas les éléments déjà développés.
- Ne considère pas une fonctionnalité comme terminée si elle n'est pas réellement codée.
- Ne prétends jamais qu'une compilation est réussie sans avoir exécuté et vérifié le build.
- Ne me redemande pas les informations déjà présentes dans ce document.
- Prends les décisions techniques raisonnables et explique-les.
- Travaille progressivement et livre des résultats concrets.

---

# 2. IDENTITÉ DU PROJET

Nom : NOVA IPTV  
Version cible : 1.0  
Plateformes : Android Mobile, Android TV et Google TV  
Langage : Kotlin  
Interface : Jetpack Compose et Material 3  
Lecteur vidéo : AndroidX Media3 / ExoPlayer  
Architecture : MVVM  
Base de données prévue : Room  
Préférences : DataStore  

Dépôt GitHub :

https://github.com/kacemox/nova-iptv

L'objectif est de créer une application IPTV moderne, premium, rapide et agréable à utiliser, aussi bien sur smartphone que sur téléviseur Android.

---

# 3. CONTENU DU ZIP EXISTANT

L'archive fournie s'appelle :

NOVA_IPTV.zip

Elle contient actuellement :

NOVA_IPTV/
├── NOVA_IPTV_CAHIER_DES_CHARGES.md
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            └── java/
                └── com/
                    └── nova/
                        └── iptv/
                            └── MainActivity.kt

Le ZIP contient également un dossier __MACOSX, qui correspond à des métadonnées macOS inutiles pour le projet Android.

## 3.1 Éléments déjà présents

Le projet possède déjà :

- Une activité Android MainActivity.
- Une interface Jetpack Compose.
- Un thème Material 3 sombre.
- Les couleurs de base de NOVA.
- Un écran d'accueil.
- Le titre NOVA IPTV.
- Une carte de bienvenue.
- Un bouton « Découvrir NOVA ».
- Trois tuiles visuelles :
  - TV en direct.
  - Films.
  - Séries.
- Un manifeste Android.
- Une configuration Gradle racine.
- Une configuration Gradle pour le module app.
- Un fichier README.
- Un cahier des charges fonctionnel.

## 3.2 Identité visuelle actuelle

Couleurs définies :

- Bleu nuit : #080D1B
- Cyan : #36D9FF
- Violet : #8564FF

Le design recherché est premium, moderne et cinématique.

Inspirations :
- Netflix.
- Apple TV.
- TiviMate.
- OTT Navigator.
- Molotov.
- HONOR MagicOS.

## 3.3 Configuration technique actuelle

Les versions déclarées dans le ZIP sont :

| Composant | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Compile SDK | 35 |
| Minimum SDK | 23 |
| Target SDK | 35 |
| Compose BOM | 2024.12.01 |
| Activity Compose | 1.10.0 |
| Lifecycle | 2.8.7 |

Dépendances Compose déjà présentes :

- Compose UI.
- Material 3.
- Material Icons.
- Activity Compose.
- Lifecycle Runtime Compose.
- Compose Tooling.

ATTENTION : le ZIP ne contient pas encore le lecteur Media3, Room, DataStore, Coil, le parsing M3U, Xtream Codes ou l'EPG.

---

# 4. PROBLÈMES DE COMPILATION À CORRIGER

Deux problèmes ont déjà été rencontrés sur GitHub Actions.

## 4.1 Première erreur : Gradle Wrapper absent

Erreur observée :

chmod: cannot access 'gradlew': No such file or directory

Le projet ne possède pas actuellement les fichiers du Gradle Wrapper.

Il faut ajouter :

gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties

Utiliser une version de Gradle compatible avec la version d'Android Gradle Plugin retenue.

La solution recommandée est d'utiliser le Gradle Wrapper avec :

./gradlew clean assembleDebug

## 4.2 Deuxième erreur : mauvaise structure du dépôt GitHub

Un journal GitHub Actions du 23 novembre 2026 montre que :

- Le checkout fonctionne.
- Java 17 est installé.
- Gradle 8.9 est configuré.
- Le workflow détecte la racine du dépôt.
- La vérification de app/build.gradle.kts échoue.

Erreur :

test -f "$PROJECT_DIR/app/build.gradle.kts"

Le dépôt contenait notamment :

.git/
.github/
.gitignore
NOVA_IPTV.zip
README.md
settings.gradle.kts
settings.gradle.kts.plugin-part.tmp

Le problème est que le ZIP est présent dans GitHub, mais son contenu n'a pas été extrait dans la structure du projet.

GitHub Actions ne compile pas automatiquement le contenu d'une archive ZIP.

Il faut extraire les fichiers du dossier NOVA_IPTV/ vers la racine du dépôt, ou adapter explicitement le workflow pour construire depuis ce sous-dossier.

Il faut également vérifier et nettoyer le fichier temporaire :

settings.gradle.kts.plugin-part.tmp

Ne pas supposer que le settings.gradle.kts isolé à la racine est correct.

## 4.3 Structure GitHub attendue

La structure finale doit être cohérente :

nova-iptv/
├── .github/
│   └── workflows/
│       └── main.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── java/com/nova/iptv/MainActivity.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── NOVA_IPTV_CAHIER_DES_CHARGES.md
└── README.md

Éviter absolument cette structure incorrecte :

NOVA_IPTV/NOVA_IPTV/app/...

---

# 5. FONCTIONNALITÉS ATTENDUES

## 5.1 Gestion des profils IPTV

Formats obligatoires :

- M3U.
- M3U8.
- Xtream Codes API.

Stalker Portal pourra être ajouté ultérieurement.

Fonctions :

- Ajouter un profil.
- Modifier un profil.
- Supprimer un profil.
- Renommer un profil.
- Actualiser un profil.
- Activer ou désactiver un profil.
- Changer rapidement de profil.
- Choisir le profil au démarrage.

Chaque profil doit conserver ses propres favoris, son historique et sa progression.

Les identifiants doivent être stockés de manière sécurisée.

Ne jamais inscrire les mots de passe dans les logs.

## 5.2 TV en direct

Fonctionnalités :

- Liste des chaînes.
- Catégories.
- Logos.
- Favoris.
- Dernières chaînes regardées.
- Recherche.
- Zapping.
- Groupes personnalisés.
- Guide TV EPG.
- Affichage du programme actuel et suivant.

## 5.3 Films

Fonctionnalités :

- Affiches.
- Synopsis.
- Année.
- Genre.
- Durée.
- Favoris.
- Likes.
- Historique.
- Reprise de lecture.
- Informations détaillées.

## 5.4 Séries

Fonctionnalités :

- Affiches.
- Synopsis.
- Saisons.
- Épisodes.
- Numérotation.
- Progression individuelle.
- Reprise de lecture.
- Lecture automatique de l'épisode suivant.

## 5.5 Chargement progressif

L'application doit rester utilisable pendant le chargement des playlists.

Ordre souhaité :

1. Connexion au profil.
2. Récupération des informations essentielles.
3. Affichage rapide des catégories.
4. Affichage progressif des contenus.
5. Chargement des affiches et logos.
6. Chargement indépendant de l'EPG.
7. Actualisation en arrière-plan.

Prévoir :

- Parsing M3U progressif.
- Cache local.
- Limitation des téléchargements simultanés.
- Annulation des requêtes inutiles.
- Gestion des erreurs réseau.
- Chargement des images à la demande.

La lecture vidéo doit être indépendante du parsing et du chargement des métadonnées.

---

# 6. LECTEUR VIDÉO

Le lecteur interne doit utiliser AndroidX Media3 / ExoPlayer.

Fonctionnalités attendues :

- Lecture.
- Pause.
- Avance rapide.
- Retour rapide.
- Barre de progression.
- Temps écoulé.
- Durée.
- Plein écran.
- Reprise de lecture.
- Zapping TV.
- Réglage de vitesse.
- Choix de la piste audio.
- Choix des sous-titres.
- Verrouillage de l'écran.
- Rotation configurable.
- Écran maintenu allumé.
- Minuteur sommeil.
- Gestion des erreurs et reconnexions.

Vitesses :

- 0,5x
- 0,75x
- 1x
- 1,25x
- 1,5x
- 1,75x
- 2x

## Gestes mobiles

- Geste vertical à gauche : luminosité.
- Geste vertical à droite : volume.
- Affichage d'indicateurs translucides.
- Possibilité de désactiver les gestes.

Le lecteur doit également être entièrement utilisable avec une télécommande Android TV.

## Lecteurs externes

Le lecteur interne est prioritaire.

En cas de problème de compatibilité, proposer les lecteurs externes installés sur l'appareil, par exemple VLC for Android.

---

# 7. AUTOPLAY ET MODE SOMMEIL

Les paramètres suivants sont obligatoires :

| Paramètre | Valeur par défaut |
|---|---|
| Autoplay | true |
| Compte à rebours | 20 secondes |
| Annulation | Oui |
| Mode sommeil | Arrêt après épisode |
| Lecteur | Interne |

Avant la fin d'un épisode :

- Afficher le prochain épisode.
- Afficher un bouton « Lire maintenant ».
- Afficher un compte à rebours de 20 secondes.
- Afficher un bouton « Annuler ».

À la fin du compte à rebours, démarrer automatiquement l'épisode suivant si l'autoplay est activé.

Le mode sommeil est prioritaire.

Si le mode sommeil est activé :

- Terminer l'épisode en cours.
- Ne pas lancer le suivant.
- Arrêter le lecteur.
- Sauvegarder la progression.

Options souhaitées :

- Autoplay activé/désactivé.
- Délai 5, 10, 15 ou 20 secondes.
- Lecture manuelle.
- Arrêt après épisode.
- Arrêt après 15, 30, 45 ou 60 minutes.
- Arrêt à la fin du film.

Ne pas prétendre détecter automatiquement les génériques sans données fiables.

---

# 8. HISTORIQUE, REPRISE ET EPG

## Historique

Conserver :

- Profil.
- Contenu.
- Saison.
- Épisode.
- Position de lecture.
- Date.
- État terminé ou en cours.

Sauvegarder périodiquement la progression et à la fermeture du lecteur.

Éviter une écriture en base de données à chaque seconde.

Proposer :

- Reprendre.
- Recommencer.

## EPG

Prendre en charge XMLTV.

Fonctions :

- Programme actuel.
- Programme suivant.
- Horaires.
- Description.
- Association chaîne / EPG ID.
- Guide TV.
- Cache local.
- Actualisation en arrière-plan.

L'EPG doit être indépendant du chargement des chaînes.

---

# 9. INTERFACE ANDROID TV

L'application doit disposer d'une interface adaptée à Android TV et Google TV.

Fonctionnalités :

- Navigation D-pad.
- Focus visible.
- Grandes affiches.
- Textes lisibles à distance.
- Menus horizontaux.
- Grilles de contenus.
- Navigation rapide.
- Plein écran.
- Guide TV.
- Commandes accessibles à la télécommande.

Ne pas simplement étirer l'interface mobile.

---

# 10. ARCHITECTURE TECHNIQUE RECOMMANDÉE

Conserver Kotlin et Jetpack Compose.

Architecture souhaitée :

- UI : écrans et composants Compose.
- Domain : modèles et cas d'utilisation.
- Data : repositories, API et stockage.
- Player : moteur Media3.
- Core : utilitaires, gestion des erreurs et configuration.

Technologies prévues :

- Kotlin.
- Jetpack Compose.
- Material 3.
- AndroidX Media3.
- Room.
- DataStore.
- Coroutines.
- Flow.
- Coil.
- WorkManager.

Utiliser MVVM.

Éviter de placer toute la logique métier dans MainActivity.kt.

Séparer les responsabilités et prévoir des tests.

---

# 11. PLAN DE TRAVAIL OBLIGATOIRE

## PHASE 1 — AUDIT ET REMISE EN ORDRE

Priorité absolue.

1. Examiner le ZIP.
2. Examiner les fichiers existants.
3. Examiner l'arborescence GitHub.
4. Identifier les fichiers manquants.
5. Corriger la structure.
6. Nettoyer les fichiers temporaires.
7. Ajouter le Gradle Wrapper.
8. Vérifier les versions Gradle, AGP, Kotlin et JDK.
9. Vérifier que le projet s'ouvre dans Android Studio.

Ne pas commencer par ajouter des fonctionnalités tant que le projet n'est pas correctement configuré.

## PHASE 2 — COMPILATION

1. Configurer GitHub Actions.
2. Installer Java 17.
3. Configurer le cache Gradle.
4. Utiliser le Gradle Wrapper.
5. Lancer clean assembleDebug.
6. Corriger les erreurs.
7. Recommencer jusqu'à obtenir un build réussi.
8. Générer l'APK debug.
9. Publier l'APK dans les artefacts GitHub Actions.

Commande cible :

./gradlew clean assembleDebug

Le résultat attendu est un APK dans :

app/build/outputs/apk/debug/

Ne pas déclarer le build réussi sans preuve.

## PHASE 3 — INTERFACE ET NAVIGATION

- Accueil.
- Navigation mobile.
- Navigation Android TV.
- Écrans TV, Films et Séries.
- Gestion des profils.
- Design NOVA complet.

## PHASE 4 — SOURCES IPTV

- Import M3U.
- Import M3U8.
- Connexion Xtream Codes.
- Parsing progressif.
- Catégories.
- Logos.
- Profils.
- Persistance locale.

## PHASE 5 — LECTEUR

- Intégration Media3.
- Commandes.
- Gestes.
- Audio.
- Sous-titres.
- Vitesse.
- Plein écran.
- Lecteurs externes.

## PHASE 6 — CONTENUS ET HISTORIQUE

- Films.
- Séries.
- Saisons.
- Épisodes.
- Favoris.
- Likes.
- Historique.
- Reprise.
- EPG.

## PHASE 7 — FONCTIONS AVANCÉES

- Autoplay.
- Compte à rebours 20 secondes.
- Mode sommeil.
- Recommandations.
- Optimisations.
- Thèmes.

## PHASE 8 — TESTS ET LIVRAISON

- Tests unitaires.
- Tests instrumentés.
- Test Android Mobile.
- Test Android TV.
- Vérification de la navigation.
- Vérification des profils.
- Vérification du lecteur.
- Vérification de la mémoire.
- Compilation finale.
- APK debug.
- README final.

---

# 12. LIVRABLES ATTENDUS

Je souhaite recevoir :

1. Un projet Android Studio complet.
2. Une arborescence propre.
3. Un Gradle Wrapper opérationnel.
4. Un workflow GitHub Actions fonctionnel.
5. Un APK debug réellement compilé.
6. Un README avec les instructions.
7. Une liste des fonctions terminées.
8. Une liste des fonctions partielles.
9. Une liste des fonctions restantes.
10. Les résultats des tests.
11. Un plan des prochaines étapes.

---

# 13. ÉTAT INITIAL HONNÊTE

Voici l'état réel du projet au moment de sa reprise :

| Élément | État |
|---|---|
| Cahier des charges | Présent |
| Projet Kotlin | Squelette présent |
| Écran d'accueil Compose | Présent |
| Thème NOVA | Présent |
| Manifeste Android | Présent |
| Configuration Gradle | Présente |
| Gradle Wrapper | Absent du ZIP |
| GitHub Actions | Non validé |
| Compilation réussie | Non démontrée |
| APK debug | Non démontré |
| Profils IPTV | Non implémentés |
| M3U/M3U8/Xtream | Non implémentés |
| Lecteur Media3 | Non implémenté |
| Films et séries | Non implémentés |
| Favoris et historique | Non implémentés |
| EPG XMLTV | Non implémenté |
| Autoplay 20 secondes | Exigence définie, pas codée |
| Interface Android TV complète | Non implémentée |
| Tests | Absents du ZIP |

---

# 14. INSTRUCTION FINALE

Commence immédiatement par la PHASE 1 : audit et remise en ordre du projet.

Je ne souhaite pas recevoir uniquement des explications théoriques.

Je veux que tu travailles directement sur les fichiers, que tu corriges les configurations et que tu produises les fichiers nécessaires.

La priorité est d'obtenir un projet Android Studio propre et compilable.

Une fois le build validé, poursuis progressivement le développement de NOVA IPTV.

Respecte impérativement les choix déjà définis, notamment :

- Nom NOVA IPTV.
- Design sombre premium.
- Kotlin.
- Jetpack Compose.
- Media3 / ExoPlayer.
- M3U / M3U8.
- Xtream Codes.
- Android Mobile et Android TV.
- Autoplay activé par défaut.
- Compte à rebours de 20 secondes.

Ne confonds jamais une maquette graphique avec une fonctionnalité opérationnelle.

Chaque fonctionnalité annoncée comme terminée doit être réellement implémentée et testée.