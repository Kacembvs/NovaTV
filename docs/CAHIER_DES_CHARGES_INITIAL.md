# NOVA IPTV — Cahier des charges complet
## Projet Android Mobile & Android TV

**Nom du projet :** NOVA IPTV  
**Version cible :** 1.0  
**Type :** Application IPTV et lecteur multimédia premium  
**Plateformes :** Android Mobile, Android TV et Google TV  
**Langage :** Kotlin  
**Public cible :** Utilisateurs de playlists IPTV personnelles et de services auxquels ils ont légitimement accès.

---

# 1. VISION DU PROJET

Développer une application IPTV haut de gamme appelée NOVA IPTV, inspirée des meilleures expériences utilisateur de Netflix, OTT Navigator, TiviMate, Molotov, Apple TV et MagicOS de HONOR.

L'application doit être :
- Extrêmement fluide.
- Très légère en mémoire et en ressources processeur.
- Rapide au démarrage et au changement de chaîne.
- Moderne, élégante et personnalisable.
- Adaptée aussi bien aux smartphones qu'aux téléviseurs Android.
- Entièrement utilisable à la télécommande sur Android TV.
- Intuitive, sans menus compliqués ni temps de chargement excessifs.

L'objectif n'est pas de créer un simple lecteur IPTV, mais un véritable centre multimédia intelligent.

**Priorité absolue : fluidité, ergonomie, rapidité et qualité de lecture.**

# 2. IDENTITÉ VISUELLE

Nom : NOVA IPTV.

Identité :
- Fond noir et bleu nuit.
- Bleu électrique et violet.
- Logo NOVA avec symbole de lecture et effet nova.
- Design premium, minimaliste et cinématique.

Inspirations :
- Netflix pour la navigation et la découverte des contenus.
- Apple iOS pour les animations, transitions et fenêtres translucides.
- HONOR MagicOS pour les effets de profondeur, les transparences et les interactions.
- TiviMate et OTT Navigator pour les fonctions IPTV avancées.

Prévoir plusieurs thèmes :
1. NOVA Blue : bleu nuit et cyan.
2. Cinema Gold : noir et or.
3. Stream Red : noir et rouge.
4. Pure Light : thème clair.
5. Custom : personnalisation manuelle des couleurs.

Les effets visuels doivent rester légers. Les animations ne doivent jamais ralentir le lecteur vidéo.

# 3. PLATEFORMES ET TECHNOLOGIES

Développer une véritable application Android native.

Technologies recommandées :
- Kotlin.
- Jetpack Compose.
- Jetpack Compose for TV ou composants TV adaptés.
- Material 3.
- AndroidX Media3 / ExoPlayer.
- Room pour les données locales.
- DataStore pour les préférences.
- Kotlin Coroutines et Flow.
- Coil pour les images.
- WorkManager pour les tâches de synchronisation en arrière-plan.

Architecture recommandée :
- MVVM.
- Clean Architecture lorsque cela apporte une vraie valeur.
- Séparation UI, domaine, données et moteur de lecture.
- Injection de dépendances.
- Gestion centralisée des erreurs.

Éviter les bibliothèques inutiles et les dépendances trop lourdes.

Prévoir une base commune Android Mobile / Android TV avec des interfaces adaptatives.

Ne pas développer la version iOS pour le moment. Elle constituera un projet futur indépendant.

# 4. GESTION MULTI-PROFILS IPTV

Fonctionnalité fondamentale.

L'utilisateur doit pouvoir ajouter plusieurs profils IPTV.

Formats obligatoires :
- M3U.
- M3U8.
- Xtream Codes API.

Prévoir éventuellement Stalker Portal comme extension future.

Chaque profil doit posséder :
- Nom personnalisé.
- Type de connexion.
- URL ou paramètres de connexion.
- Identifiant et mot de passe si nécessaires.
- Logo ou couleur personnalisée.
- Date de dernière synchronisation.
- État de connexion.
- Préférences propres.

Fonctions :
- Ajouter.
- Modifier.
- Supprimer.
- Renommer.
- Actualiser.
- Activer ou désactiver.
- Changer de profil rapidement.
- Sélectionner le profil au démarrage.

Aucune limite artificielle de profils imposée par l'application.

Les favoris, historiques et progressions doivent être isolés par profil.

Prévoir une option future permettant de fusionner les contenus de plusieurs profils dans une bibliothèque globale, sans perdre leur origine.

Ne jamais afficher les identifiants sensibles dans les logs.

# 5. CATÉGORIES DE CONTENUS

Organiser les contenus en trois grandes sections :

## A. TV en direct
- Toutes les chaînes.
- Catégories.
- Favoris.
- Dernières chaînes regardées.
- Recherche rapide.
- Logos des chaînes.
- Zapping rapide.
- Groupes personnalisés.
- Guide TV EPG.

## B. Films
- Affiches.
- Synopsis.
- Année.
- Genre.
- Durée.
- Informations disponibles dans les métadonnées.
- Favoris.
- Likes.
- Historique.
- Reprise de lecture.

## C. Séries
- Affiche et synopsis.
- Saisons.
- Épisodes.
- Numérotation.
- Progression individuelle.
- Reprise de lecture.
- Épisode suivant.
- Historique des épisodes regardés.

# 6. SYSTÈME DE CHARGEMENT INTELLIGENT

C'est l'une des priorités techniques les plus importantes.

L'application ne doit JAMAIS obliger l'utilisateur à attendre plusieurs minutes avant de pouvoir consulter ses contenus.

Mettre en place un chargement progressif et asynchrone.

Comportement attendu :

1. Connexion au profil.
2. Récupération des informations essentielles.
3. Affichage immédiat des catégories disponibles.
4. Affichage progressif des chaînes, films et séries.
5. Chargement des affiches en arrière-plan.
6. Chargement des logos à la demande.
7. Récupération EPG indépendante.
8. Actualisation intelligente sans bloquer l'interface.

Ne jamais attendre la totalité des métadonnées avant d'afficher les premiers contenus.

Optimisations :
- Parsing M3U progressif.
- Pagination et chargement par lots.
- Cache local.
- Cache mémoire et disque pour les images.
- Limitation des téléchargements simultanés.
- Annulation des requêtes inutiles.
- Déduplication des requêtes.
- Actualisation différentielle quand le fournisseur le permet.
- Gestion des erreurs réseau.
- Reprise des téléchargements interrompus.
- Chargement EPG par fenêtres temporelles.

Séparer complètement le chargement des métadonnées du moteur de lecture.

L'utilisateur doit pouvoir regarder une chaîne pendant que les affiches et informations continuent de se charger.

# 7. EXPÉRIENCE UTILISATEUR FAÇON NETFLIX

L'écran d'accueil doit être dynamique.

Prévoir les rubriques :

- Reprendre la lecture.
- Continuer à regarder.
- Mes favoris.
- Mes coups de cœur.
- Séries en cours.
- Films récemment consultés.
- Dernières chaînes regardées.
- Suggestions personnalisées.
- Nouveautés du catalogue.
- Recommandations par genre.

Chaque contenu doit proposer :
- Like / Dislike.
- Ajouter aux favoris.
- Retirer des favoris.
- Reprendre la lecture.
- Recommencer depuis le début.
- Afficher les détails.

Les recommandations doivent évoluer selon les habitudes de visionnage, les likes, les genres consultés et les contenus terminés.

Commencer par un moteur de recommandations local, sans dépendre d'un serveur externe.

# 8. LECTEUR VIDÉO INTERNE

Le lecteur interne doit être l'un des meilleurs éléments de NOVA.

Technologie principale : AndroidX Media3 / ExoPlayer.

Fonctionnalités indispensables :

## Commandes principales
- Lecture / Pause.
- Avance rapide.
- Retour rapide.
- Barre de progression.
- Affichage du temps écoulé.
- Durée totale.
- Reprise de lecture.
- Plein écran.
- Zapping pour les chaînes TV.
- Contrôle de la vitesse de lecture.

## Contrôle de luminosité
Sur mobile :
- Geste vertical à gauche pour modifier la luminosité.
- Indicateur visuel translucide.
- Réglage de luminosité de l'écran pendant la lecture.
- Possibilité de désactiver les gestes.

## Contrôle du volume
Sur mobile :
- Geste vertical à droite.
- Indicateur de volume.
- Contrôle du volume système.
- Possibilité de désactiver les gestes.

## Vitesse de lecture
Proposer :
- 0,5x.
- 0,75x.
- 1x.
- 1,25x.
- 1,5x.
- 1,75x.
- 2x.

## Langues et sous-titres
- Sélection de la langue audio.
- Changement de piste audio.
- Sélection des sous-titres.
- Activation et désactivation des sous-titres.
- Choix de la piste par défaut.
- Affichage des pistes disponibles uniquement.

## Autres fonctionnalités
- Verrouillage de l'écran.
- Rotation automatique configurable.
- Maintien de l'écran allumé pendant la lecture.
- Minuteur de sommeil.
- Sélection de la qualité lorsqu'elle est proposée.
- Gestion des erreurs de lecture.
- Tentatives de reconnexion raisonnables.
- Affichage des informations techniques du flux si disponibles.

## Interface du lecteur

Créer des petites fenêtres contextuelles translucides inspirées d'Apple :
- Sélection audio.
- Sous-titres.
- Vitesse.
- Qualité.
- Paramètres.
- Minuteur de sommeil.

Les fenêtres doivent être élégantes, lisibles et rapides à ouvrir.

Sur Android TV, adapter toutes les commandes à la télécommande.

# 9. LECTEURS EXTERNES

Si un flux ne peut pas être lu correctement par le lecteur interne, proposer l'ouverture dans un lecteur externe.

Lecteurs compatibles à détecter :
- VLC for Android.
- Autres lecteurs compatibles installés par l'utilisateur.

Ne pas imposer d'abonnement ni de licence payante pour utiliser cette fonction.

Détecter les applications compatibles via les mécanismes Android appropriés.

Si aucun lecteur externe n'est installé, afficher une proposition d'installation, sans téléchargement automatique ni obligation.

Le lecteur interne reste le lecteur par défaut.

# 10. GESTION INTELLIGENTE DES ÉPISODES

Comportement par défaut demandé :

- Lecture automatique : ACTIVÉE.
- Compte à rebours : 20 secondes.
- Mode sommeil : ARRÊTER APRÈS L'ÉPISODE.
- Passage automatique du générique : ACTIVÉ.

## Épisode suivant

Environ 20 secondes avant la fin d'un épisode :
- Afficher une fenêtre avec l'affiche du prochain épisode.
- Proposer « Lire maintenant ».
- Afficher un compte à rebours de 20 secondes.
- Proposer « Annuler ».
- Passer automatiquement à l'épisode suivant si le compte à rebours arrive à zéro et si la lecture automatique est autorisée.

## Priorité des réglages

Le mode sommeil doit toujours être prioritaire.

Lorsque le mode sommeil est réglé sur « Arrêter après l'épisode » :
- Terminer l'épisode actuel.
- Ne pas lancer le suivant.
- Arrêter la lecture.
- Ne pas laisser le lecteur tourner inutilement.
- Enregistrer la progression.

## Options configurables
- Lecture automatique activée/désactivée.
- Compte à rebours de 5, 10, 15 ou 20 secondes.
- Lecture manuelle.
- Arrêt après l'épisode.
- Arrêt après 15, 30, 45 ou 60 minutes.
- Arrêt à la fin du film.
- Désactivation du saut automatique du générique.

La détection des intros et génériques ne doit pas être inventée si les informations nécessaires ne sont pas disponibles. Utiliser des marqueurs de chapitres ou métadonnées lorsqu'ils existent, et permettre un réglage manuel.

# 11. REPRISE DE LECTURE ET HISTORIQUE

Chaque lecture doit enregistrer :
- Profil IPTV.
- Identifiant du contenu.
- Saison.
- Épisode.
- Position de lecture.
- Date de dernière consultation.
- État terminé / en cours.

Au prochain démarrage, proposer de reprendre à la position précédente.

La progression doit être sauvegardée régulièrement et lors de la fermeture du lecteur.

Éviter d'écrire en base de données à chaque seconde.

Prévoir une action « Recommencer depuis le début ».

# 12. GUIDE TV EPG

Support XMLTV.

Fonctions :
- Programme actuel.
- Programme suivant.
- Horaires.
- Description.
- Association chaîne / identifiant EPG.
- Guide des programmes.
- Actualisation en arrière-plan.
- Cache local.

Le guide ne doit jamais bloquer l'affichage des chaînes.

# 13. INTERFACE MOBILE

Sur smartphone Android, notamment HONOR :

- Navigation tactile.
- Gestes naturels.
- Barre de navigation inférieure.
- Affiches adaptées au format mobile.
- Transitions fluides.
- Fenêtres translucides.
- Effets de profondeur modérés.
- Mode portrait et paysage.
- Adaptation aux différentes tailles d'écran.

L'interface doit donner une impression de qualité comparable à une application premium Apple ou HONOR.

# 14. INTERFACE ANDROID TV

Priorité à l'utilisation sur téléviseur avec télécommande.

Prévoir :
- Navigation au D-pad.
- Focus clairement visible.
- Grandes affiches.
- Textes lisibles à distance.
- Menus horizontaux.
- Navigation rapide entre catégories.
- Grilles de contenus.
- Lecteur plein écran.
- Guide TV.
- Menus contextuels accessibles à la télécommande.

L'application doit être optimisée pour les grands téléviseurs, notamment un téléviseur TCL de 98 pouces.

Ne pas simplement agrandir l'interface mobile : concevoir une vraie expérience TV.

# 15. PERFORMANCE ET BATTERIE

Objectif : application légère, rapide et stable.

Exigences :
- Ne pas charger toutes les images simultanément.
- Ne pas télécharger toutes les métadonnées avant affichage.
- Ne pas monopoliser le processeur.
- Limiter les tâches en arrière-plan.
- Respecter les restrictions Android.
- Ne pas laisser tourner le lecteur après un arrêt demandé.
- Ne pas lancer des épisodes automatiquement en mode sommeil.
- Optimiser la consommation mémoire.
- Éviter les fuites mémoire.
- Gérer correctement le cycle de vie Android.
- Utiliser l'accélération matérielle lorsque compatible.

La lecture vidéo doit rester indépendante des tâches de synchronisation et de métadonnées.

# 16. SÉCURITÉ ET VIE PRIVÉE

- Stockage sécurisé des identifiants.
- Aucun mot de passe dans les logs.
- Aucune collecte inutile de données personnelles.
- Historique conservé localement par défaut.
- Synchronisation facultative si elle est ajoutée ultérieurement.
- Pas de serveur propriétaire obligatoire.
- Pas de dépendance à un abonnement NOVA.

L'application doit uniquement servir à lire des contenus auxquels l'utilisateur est autorisé à accéder.

# 17. LIVRABLES ATTENDUS

Le développeur ou l'IA doit fournir un véritable projet Android Studio complet.

Livrables :

1. Code source Kotlin.
2. Structure Android Studio complète.
3. Fichiers Gradle.
4. Architecture MVVM.
5. Interface Android Mobile.
6. Interface Android TV.
7. Lecteur Media3 fonctionnel.
8. Import M3U/M3U8.
9. Connexion Xtream Codes.
10. Base de données Room.
11. Gestion des profils.
12. EPG XMLTV.
13. Système de favoris.
14. Historique et reprise.
15. Système de lecture automatique.
16. Minuteur de sommeil.
17. Logo NOVA.
18. Icônes Android.
19. README détaillé.
20. Workflow GitHub Actions.
21. APK de débogage.
22. Instructions de compilation et d'installation.

Prévoir des tests unitaires, tests du parsing M3U, tests de lecture et tests sur appareils Android.

# 18. MÉTHODE DE DÉVELOPPEMENT

Ne pas essayer de construire toutes les fonctionnalités en une seule fois sans tests.

Développer par étapes :

**Phase 1 — Fondation**
- Projet Android Studio.
- Architecture.
- Identité visuelle.
- Navigation mobile et TV.
- Écran d'accueil.

**Phase 2 — Sources IPTV**
- Import M3U.
- Xtream Codes.
- Multi-profils.
- Parsing progressif.
- Base locale.

**Phase 3 — Lecteur**
- Media3.
- Commandes vidéo.
- Luminosité.
- Volume.
- Vitesse.
- Audio et sous-titres.
- Lecteurs externes.

**Phase 4 — Expérience multimédia**
- Films et séries.
- Historique.
- Favoris et likes.
- Reprise de lecture.
- EPG.

**Phase 5 — Intelligence**
- Épisode suivant.
- Compte à rebours.
- Mode sommeil.
- Recommandations.
- Détection des intros lorsque possible.

**Phase 6 — Finition**
- Animations.
- Thèmes.
- Optimisation mémoire et batterie.
- Tests Android Mobile.
- Tests Android TV.
- Compilation et publication des APK.

À chaque phase, fournir un projet compilable et tester les fonctionnalités avant de passer à la suivante.

# 19. CONSIGNES IMPORTANTES À L'IA DÉVELOPPEUSE

Tu dois agir comme un ingénieur Android senior, un architecte logiciel et un designer UX/UI spécialisé dans les applications de streaming.

Ne te contente pas de produire des maquettes ou du code fictif.

Je veux du code réel, organisé, documenté et compilable.

Ne prétends jamais qu'une fonctionnalité est terminée si elle n'est pas réellement implémentée et testée.

Ne laisse pas de boutons décoratifs sans action.

Privilégie les solutions natives Android et les bibliothèques officielles.

Vérifie les versions des dépendances et leur compatibilité avant de les intégrer.

Ne bloque pas le projet en posant des questions sur des décisions techniques que tu peux raisonnablement prendre toi-même.

Lorsque plusieurs solutions existent, choisis celle qui offre le meilleur compromis entre performances, simplicité de maintenance et expérience utilisateur.

Commence immédiatement par la Phase 1, crée les vrais fichiers du projet, vérifie la compilation, puis continue progressivement.

**Résultat final attendu : NOVA IPTV, une application Android premium, légère, rapide, intelligente et entièrement fonctionnelle sur smartphone et Android TV.**