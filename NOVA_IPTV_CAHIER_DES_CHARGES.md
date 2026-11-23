# NOVA IPTV — Cahier des charges complet

## Projet Android Mobile & Android TV

- **Nom du projet :** NOVA IPTV
- **Version cible :** 1.0
- **Type :** Application IPTV et lecteur multimédia premium
- **Plateformes :** Android Mobile, Android TV et Google TV
- **Langage :** Kotlin
- **Public cible :** Utilisateurs de playlists IPTV personnelles et de services auxquels ils ont légitimement accès.

## 1. Vision du projet

Développer une application IPTV haut de gamme appelée NOVA IPTV, inspirée des meilleures expériences utilisateur de Netflix, OTT Navigator, TiviMate, Molotov, Apple TV et MagicOS de HONOR.

L’application doit être extrêmement fluide, légère en mémoire et en ressources processeur, rapide au démarrage et au changement de chaîne, moderne, élégante, personnalisable, adaptée aux smartphones et téléviseurs Android, entièrement utilisable à la télécommande et intuitive.

L’objectif est de créer un véritable centre multimédia intelligent. Priorités absolues : fluidité, ergonomie, rapidité et qualité de lecture.

## 2. Identité visuelle

- Fond noir et bleu nuit, bleu électrique et violet.
- Logo NOVA avec symbole de lecture et effet nova.
- Design premium, minimaliste et cinématique.
- Inspirations : Netflix (navigation et découverte), Apple (animations et fenêtres translucides), HONOR MagicOS (profondeur et transparences), TiviMate/OTT Navigator (fonctions IPTV).

Thèmes prévus :
1. NOVA Blue : bleu nuit et cyan.
2. Cinema Gold : noir et or.
3. Stream Red : noir et rouge.
4. Pure Light : thème clair.
5. Custom : personnalisation manuelle.

Les effets visuels doivent rester légers et ne jamais ralentir la lecture.

## 3. Plateformes et technologies

Application Android native : Kotlin, Jetpack Compose, composants adaptés Android TV, Material 3, AndroidX Media3/ExoPlayer, Room, DataStore, Coroutines/Flow, Coil et WorkManager.

Architecture MVVM, séparation UI/domaine/données/moteur de lecture, injection de dépendances et gestion centralisée des erreurs. Limiter les dépendances inutiles. Base Android commune avec interfaces adaptatives Mobile/TV. Pas de version iOS dans cette version.

## 4. Gestion multi-profils IPTV

Formats obligatoires : M3U, M3U8 et Xtream Codes API. Stalker Portal pourra être étudié ultérieurement.

Chaque profil comprend nom, type de connexion, URL/paramètres, identifiants si nécessaires, logo ou couleur, date de synchronisation, état de connexion et préférences propres.

Fonctions : ajouter, modifier, supprimer, renommer, actualiser, activer/désactiver, changer rapidement de profil et choisir le profil au démarrage. Pas de limite artificielle de profils. Favoris, historiques et progressions isolés par profil. Une bibliothèque globale fusionnée pourra être envisagée ultérieurement en conservant la provenance. Aucun identifiant sensible dans les logs.

## 5. Catégories de contenus

### TV en direct
Toutes les chaînes, catégories, favoris, dernières chaînes regardées, recherche, logos, zapping, groupes personnalisés et guide EPG.

### Films
Affiches, synopsis, année, genre, durée et métadonnées disponibles, favoris, likes, historique et reprise.

### Séries
Affiche, synopsis, saisons, épisodes, numérotation, progression individuelle, reprise, épisode suivant et historique.

## 6. Chargement intelligent

Ne jamais imposer d’attendre plusieurs minutes avant de consulter les contenus. Le chargement doit être progressif et asynchrone :

1. Connexion au profil.
2. Récupération des informations essentielles.
3. Affichage immédiat des catégories.
4. Affichage progressif des contenus.
5. Affiches et logos chargés en arrière-plan ou à la demande.
6. EPG indépendant.
7. Actualisation sans blocage de l’interface.

Prévoir parsing M3U progressif, pagination/lots, cache local et images, limitation des téléchargements simultanés, annulation des requêtes inutiles, déduplication, actualisation différentielle lorsque possible, gestion des erreurs réseau et reprise des téléchargements interrompus. La lecture doit rester indépendante du parsing et des métadonnées.

## 7. Accueil et découverte

Accueil dynamique : reprendre la lecture, continuer à regarder, favoris, coups de cœur, séries en cours, films récemment consultés, dernières chaînes, suggestions, nouveautés et recommandations par genre.

Chaque contenu propose like/dislike, favoris, reprise, recommencer et détails. Recommandations locales basées sur habitudes, likes, genres et contenus terminés, sans serveur externe obligatoire.

## 8. Lecteur vidéo interne

Lecteur principal AndroidX Media3/ExoPlayer.

Commandes : lecture/pause, avance/retour, barre de progression, temps écoulé/durée, reprise, plein écran, zapping TV et vitesse.

Sur mobile : geste vertical gauche pour luminosité et droite pour volume, indicateurs translucides et option de désactivation. Contrôle du volume système.

Vitesses : 0,5x, 0,75x, 1x, 1,25x, 1,5x, 1,75x et 2x.

Audio et sous-titres : choix des pistes disponibles, activation/désactivation et préférence par défaut.

Autres fonctions : verrouillage écran, rotation configurable, écran allumé pendant lecture, minuteur sommeil, qualité si proposée, gestion des erreurs et reconnexions raisonnables, informations techniques disponibles.

Fenêtres translucides pour audio, sous-titres, vitesse, qualité, paramètres et minuteur. Commandes adaptées à la télécommande sur Android TV.

## 9. Lecteurs externes

Si le flux n’est pas lisible correctement en interne, proposer les lecteurs compatibles installés (VLC for Android ou autre) via les mécanismes Android appropriés. Aucun abonnement imposé, aucun téléchargement automatique. Le lecteur interne reste le lecteur par défaut.

## 10. Gestion intelligente des épisodes

Réglages par défaut :
- Lecture automatique : **activée**.
- Compte à rebours : **20 secondes**.
- Mode sommeil : **arrêter après l’épisode**.
- Passage automatique du générique : **activé**, uniquement si les informations nécessaires existent.

Environ 20 secondes avant la fin, afficher le prochain épisode, « Lire maintenant », un compte à rebours de 20 secondes et « Annuler ». À zéro, enchaîner si l’autoplay est autorisé.

Le mode sommeil est prioritaire : terminer l’épisode courant, ne pas lancer le suivant, arrêter le lecteur et sauvegarder la progression.

Options : autoplay on/off, délai 5/10/15/20 s, lecture manuelle, arrêt après épisode, arrêt après 15/30/45/60 minutes, arrêt à la fin du film et désactivation du saut automatique. Ne pas inventer une détection d’intro/générique : utiliser chapitres/métadonnées ou réglage manuel.

## 11. Reprise et historique

Enregistrer profil, contenu, saison, épisode, position, date et état terminé/en cours. Proposer la reprise au prochain démarrage. Sauvegarder périodiquement et à la fermeture, sans écriture chaque seconde. Action « Recommencer depuis le début ».

## 12. Guide TV EPG

Support XMLTV : programme actuel/suivant, horaires, description, association chaîne/EPG ID, guide, actualisation en arrière-plan et cache local. L’EPG ne doit pas bloquer les chaînes.

## 13. Interface mobile

Navigation tactile, gestes naturels, barre inférieure, affiches adaptées, transitions fluides, transparences modérées, portrait/paysage et adaptation aux tailles d’écran, notamment HONOR.

## 14. Interface Android TV

Expérience TV conçue spécifiquement : D-pad, focus visible, grandes affiches, textes lisibles à distance, menus horizontaux, navigation rapide, grilles, plein écran, guide TV et menus accessibles à la télécommande. Optimisation grands téléviseurs, notamment 98 pouces. Ne pas étirer l’interface mobile.

## 15. Performance et batterie

Ne pas charger toutes les images/métadonnées simultanément, ne pas monopoliser CPU/RAM, limiter les tâches arrière-plan, respecter le cycle de vie Android, arrêter le lecteur à la demande, ne pas enchaîner en mode sommeil, éviter les fuites mémoire et utiliser l’accélération matérielle compatible. Lecture indépendante des synchronisations.

## 16. Sécurité et vie privée

Identifiants stockés de manière sécurisée, aucun mot de passe dans les logs, collecte minimale, historique local par défaut, synchronisation éventuellement facultative, aucun serveur propriétaire obligatoire et aucun abonnement NOVA obligatoire. Utilisation limitée aux contenus auxquels l’utilisateur est autorisé à accéder.

## 17. Livrables attendus

Projet Android Studio complet : code Kotlin, structure Gradle, architecture MVVM, UI Mobile et TV, lecteur Media3, import M3U/M3U8, Xtream, Room, profils, EPG XMLTV, favoris, historique/reprise, autoplay, minuteur, branding, icônes, README, GitHub Actions, APK debug, guide de compilation/installation et tests unitaires/appareils.

## 18. Phases de développement

1. **Fondation :** projet Android Studio, architecture, identité visuelle, navigation mobile/TV et accueil.
2. **Sources IPTV :** M3U, Xtream, profils, parsing progressif et base locale.
3. **Lecteur :** Media3, commandes, luminosité, volume, vitesse, audio/sous-titres et lecteurs externes.
4. **Expérience multimédia :** films/séries, historique, favoris/likes, reprise et EPG.
5. **Intelligence :** épisode suivant, compte à rebours, sommeil, recommandations et détection d’intros lorsque possible.
6. **Finition :** animations, thèmes, optimisation, tests Mobile/TV et APK.

Chaque phase doit rester compilable et être testée avant la suivante.

## 19. Consignes à l’IA développeuse

Agir comme ingénieur Android senior, architecte logiciel et designer UX/UI streaming. Produire du code réel, organisé, documenté et compilable, pas des maquettes ni du code fictif. Ne jamais déclarer une fonction terminée sans implémentation et test réels. Aucun bouton décoratif. Privilégier les solutions Android natives et bibliothèques officielles. Vérifier les versions et compatibilités. Prendre les décisions techniques raisonnables sans bloquer sur des questions évitables.

**Commencer immédiatement par la Phase 1 : créer les fichiers réels du projet, vérifier la compilation, puis avancer progressivement.**
