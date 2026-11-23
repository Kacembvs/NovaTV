# NOVA IPTV — SPÉCIFICATIONS UX/UI, DESIGN ET RECOMMANDATIONS

**Projet :** NOVA IPTV  
**Version :** 1.0  
**Document complémentaire au cahier des charges principal**  
**Plateformes :** Android Mobile, Android TV et Google TV  
**Technologies :** Kotlin, Jetpack Compose, AndroidX Media3

---

# 1. DIRECTION ARTISTIQUE GÉNÉRALE

## 1.1 Vision visuelle

NOVA IPTV doit proposer une expérience visuelle premium, moderne et cinématique.

L'application doit combiner les meilleures idées d'interface de :

- Netflix : présentation des contenus et navigation.
- Apple TV : élégance, simplicité et animations.
- HONOR MagicOS : transparences, profondeur et interactions.
- TiviMate : ergonomie IPTV et navigation TV.
- OTT Navigator : richesse fonctionnelle et personnalisation.

Il ne faut pas reproduire directement ces applications, mais créer une identité visuelle originale.

**Concept : NOVA — Beyond Streaming.**

L'interface doit transmettre une impression de qualité dès le premier lancement.

## 1.2 Palette de couleurs principale

Thème par défaut : NOVA Blue.

- Fond principal : #080D18.
- Fond secondaire : #101829.
- Cartes : #172238.
- Bleu électrique : #258BFF.
- Cyan : #43D5FF.
- Violet : #8257FF.
- Texte principal : #FFFFFF.
- Texte secondaire : #A5B1C5.
- Bordures discrètes : #27344A.

Les dégradés bleu/violet peuvent être utilisés pour les éléments importants.

Les effets lumineux doivent rester discrets.

## 1.3 Thèmes personnalisables

Prévoir cinq thèmes :

1. NOVA Blue : bleu nuit, cyan et violet.
2. Cinema Gold : noir et or.
3. Stream Red : noir et rouge.
4. Pure Light : interface claire.
5. Custom : personnalisation des couleurs.

Le changement de thème doit être immédiat, sans redémarrage de l'application.

---

# 2. IDENTITÉ ET LOGO

Créer une identité graphique originale.

Le logo doit comporter :

- Le nom NOVA.
- Un symbole évoquant la lecture vidéo.
- Un effet visuel de nova ou d'explosion lumineuse.
- Une typographie moderne et géométrique.

Prévoir les déclinaisons suivantes :

- Logo horizontal.
- Logo carré.
- Icône Android.
- Écran de démarrage.
- Logo monochrome.
- Logo adapté aux fonds clairs et foncés.

Éviter les animations complexes au démarrage.

---

# 3. CONCEPTION DE L'INTERFACE MOBILE

## 3.1 Navigation principale

Prévoir une barre de navigation inférieure comportant :

- Accueil.
- TV.
- Films.
- Séries.
- Paramètres.

Les favoris et l'historique seront accessibles depuis l'accueil et les sections concernées.

La navigation doit rester simple et cohérente.

## 3.2 Écran d'accueil

L'accueil doit être inspiré des plateformes de streaming premium.

Structure recommandée :

1. En-tête avec logo NOVA.
2. Sélecteur de profil.
3. Notifications éventuelles.
4. Grande bannière de présentation.
5. Boutons Regarder et Détails.
6. Reprendre la lecture.
7. Continuer à regarder.
8. Mes favoris.
9. Séries en cours.
10. Films récemment consultés.
11. Dernières chaînes regardées.
12. Recommandations.
13. Nouveautés du catalogue.

Les rubriques doivent être affichées uniquement lorsqu'elles contiennent des données.

Ne jamais afficher de rangées vides.

## 3.3 Bannières

Les bannières doivent pouvoir présenter :

- Une affiche ou image de fond.
- Le titre du contenu.
- Une description courte.
- L'année.
- Le genre.
- Un bouton de lecture.
- Un bouton de détails.

Les images doivent être adaptées à l'écran et chargées progressivement.

Éviter les carrousels automatiques trop rapides.

## 3.4 Cartes de contenu

Prévoir deux formats principaux :

**Cartes verticales :**
- Films.
- Séries.
- Recommandations.

**Cartes horizontales :**
- Reprendre la lecture.
- Continuer à regarder.
- Épisodes.
- Contenus récemment consultés.

Chaque carte doit proposer, selon le contexte :

- Affiche.
- Titre.
- Progression.
- Badge éventuel.
- Ajout aux favoris.
- Accès aux détails.

Les cartes doivent avoir des coins arrondis et une animation légère au toucher.

---

# 4. INTERFACE TÉLÉVISION EN DIRECT

L'interface TV en direct doit être conçue pour une navigation rapide.

## 4.1 Organisation

Prévoir trois zones :

**Zone gauche :**
- Toutes les chaînes.
- Favoris.
- Catégories.
- Groupes personnalisés.

**Zone centrale :**
- Liste des chaînes.
- Logos.
- Noms.
- Programme actuel.
- Heure de fin.
- Indicateur de lecture.

**Zone droite :**
- Aperçu vidéo.
- Informations sur le programme.
- Programme suivant.

La disposition doit pouvoir s'adapter aux différentes résolutions.

## 4.2 Zapping

Le changement de chaîne doit être rapide.

Prévoir :

- Chaîne précédente.
- Chaîne suivante.
- Liste rapide des dernières chaînes.
- Favoris accessibles rapidement.
- Mini-guide des programmes.
- Retour instantané à la chaîne précédente.

Le chargement des métadonnées et des logos ne doit jamais bloquer le zapping.

---

# 5. CATALOGUE FILMS ET SÉRIES

## 5.1 Catalogue

Créer une interface de découverte avec :

- Grille d'affiches.
- Recherche.
- Filtres par genre.
- Filtres par année.
- Tri alphabétique.
- Tri par date d'ajout.
- Tri personnalisé.
- Favoris.

Prévoir une pagination ou un chargement progressif.

## 5.2 Fiche détaillée d'un film

La fiche doit afficher :

- Image de fond cinématique.
- Affiche.
- Titre.
- Année.
- Durée.
- Genre.
- Synopsis.
- Informations techniques disponibles.
- Bouton Regarder.
- Bouton Reprendre.
- Bouton Recommencer.
- Bouton Favoris.
- Boutons Like et Dislike.

Ajouter une section de recommandations similaires.

## 5.3 Fiche détaillée d'une série

Afficher :

- Image de présentation.
- Titre.
- Synopsis.
- Année.
- Nombre de saisons disponibles.
- Bouton Continuer.
- Liste des saisons.
- Liste des épisodes.

Chaque épisode doit comporter :

- Numéro.
- Titre.
- Miniature si disponible.
- Durée si disponible.
- Progression.
- État regardé ou non regardé.

---

# 6. INTERFACE ANDROID TV

## 6.1 Philosophie

L'interface Android TV ne doit jamais être une simple version agrandie de l'interface mobile.

Elle doit être pensée spécifiquement pour une utilisation à la télécommande.

Priorité aux éléments suivants :

- Navigation D-pad.
- Lisibilité à distance.
- Grandes affiches.
- Menus horizontaux.
- Navigation rapide.
- Focus clairement identifiable.
- Transitions discrètes.

## 6.2 Écran d'accueil TV

Structure recommandée :

- Barre latérale verticale.
- Logo NOVA.
- Grande bannière cinématique.
- Rangée Reprendre la lecture.
- Rangée Films populaires.
- Rangée Séries.
- Rangée Favoris.
- Rangée Recommandations.

La barre latérale doit pouvoir se réduire pour libérer de l'espace.

## 6.3 Système de focus

Lorsqu'un élément est sélectionné avec la télécommande :

- Bordure lumineuse.
- Léger agrandissement.
- Contraste renforcé.
- Animation fluide.
- Retour visuel immédiat.

Prévoir une navigation cohérente entre les menus et les rangées.

Ne jamais perdre le focus lors du chargement de nouvelles données.

## 6.4 Téléviseurs grand format

L'interface doit être testée sur plusieurs résolutions et densités.

Une attention particulière doit être accordée aux téléviseurs de grande diagonale, notamment 98 pouces.

Ne pas étirer artificiellement les composants.

Adapter les dimensions, les marges et la taille des affiches.

---

# 7. DESIGN DU LECTEUR VIDÉO

## 7.1 Philosophie

Le lecteur doit rester minimaliste.

La vidéo doit occuper la totalité de l'écran lorsque les commandes sont masquées.

Les contrôles doivent apparaître avec une animation légère.

## 7.2 Interface de lecture mobile

Prévoir :

- Bouton Retour.
- Titre du contenu.
- Lecture / Pause.
- Barre de progression.
- Temps écoulé.
- Durée totale.
- Avance et retour.
- Audio.
- Sous-titres.
- Vitesse.
- Paramètres.
- Plein écran.

## 7.3 Fenêtres contextuelles

Créer un système commun de fenêtres translucides.

Prévoir des panneaux pour :

- Audio.
- Sous-titres.
- Vitesse.
- Qualité.
- Minuteur.
- Paramètres du lecteur.

Ces fenêtres doivent être cohérentes visuellement.

Elles doivent s'ouvrir rapidement et ne pas interrompre inutilement la lecture.

## 7.4 Gestes tactiles

Sur mobile :

- Partie gauche : luminosité.
- Partie droite : volume.
- Double toucher gauche : retour.
- Double toucher droit : avance.
- Appui central : afficher ou masquer les commandes.

Les gestes doivent être configurables et désactivables.

Sur Android TV, toutes les commandes doivent être accessibles à la télécommande.

---

# 8. ANIMATIONS ET EFFETS VISUELS

Prévoir une bibliothèque d'animations réutilisables.

Animations souhaitées :

- Apparition progressive des cartes.
- Transitions entre les écrans.
- Agrandissement léger des affiches sélectionnées.
- Fondu des fenêtres.
- Animation du focus TV.
- Transitions entre les catégories.
- Effets de profondeur modérés.

Durées indicatives :

- Interaction simple : 100 à 150 ms.
- Ouverture d'une fenêtre : 150 à 220 ms.
- Transition d'écran : 180 à 250 ms.
- Animation de sélection TV : 120 à 180 ms.

Ces valeurs sont des objectifs de conception à ajuster après tests.

Règle absolue : les animations ne doivent jamais dégrader les performances de lecture vidéo.

Respecter également les paramètres système de réduction des animations lorsque cela est possible.

---

# 9. FONCTIONNALITÉS PREMIUM COMPLÉMENTAIRES

Ajouter les fonctionnalités suivantes à la feuille de route.

## Priorité élevée

### 9.1 Recherche globale

Une seule recherche permettant de retrouver :

- Chaînes TV.
- Films.
- Séries.
- Épisodes.
- Catégories.

Les résultats doivent être regroupés par type.

La recherche doit fonctionner sur les données locales déjà disponibles.

### 9.2 Picture-in-Picture

Permettre de continuer à regarder une vidéo dans une petite fenêtre pendant l'utilisation d'autres applications sur Android mobile.

Respecter les limitations Android et le cycle de vie du lecteur.

### 9.3 Google Cast

Prévoir la possibilité d'envoyer un flux compatible vers un appareil Chromecast ou Google Cast.

Cette fonctionnalité doit être conditionnée à la compatibilité du flux et du récepteur.

### 9.4 Sauvegarde et restauration

Permettre d'exporter et de restaurer :

- Profils.
- Favoris.
- Préférences.
- Historique.
- Paramètres.
- Progressions.

Les identifiants doivent être protégés. Ne pas exporter les mots de passe en clair.

### 9.5 Informations techniques du lecteur

Ajouter un panneau technique affichant, lorsque disponible :

- Codec vidéo.
- Codec audio.
- Résolution.
- Fréquence d'image.
- Débit.
- Type de connexion.
- État du tampon.
- Temps de chargement.
- Erreurs de lecture.

Ce panneau doit être accessible depuis le lecteur.

## Priorité moyenne

### 9.6 Contrôle parental

Prévoir :

- Code PIN.
- Protection des catégories.
- Protection de certains profils.
- Verrouillage des paramètres sensibles.

### 9.7 Personnalisation

Permettre de :

- Réorganiser les catégories.
- Masquer certaines rubriques.
- Choisir l'écran de démarrage.
- Modifier la présentation des catalogues.
- Personnaliser les raccourcis.

### 9.8 Préférences audio

Permettre de mémoriser les langues audio et sous-titres préférées.

Appliquer les préférences lorsque les pistes correspondantes sont disponibles.

---

# 10. ARCHITECTURE TECHNIQUE COMPLÉMENTAIRE

## 10.1 Séparation Mobile / TV

Conserver un socle métier commun.

Séparer les interfaces :

- Mobile UI.
- TV UI.
- Composants communs.
- Moteur de lecture.
- Gestion des profils.
- Gestion des sources.
- Base de données.

Éviter de dupliquer la logique métier entre les deux plateformes.

## 10.2 Système de design

Créer un ensemble centralisé de composants Compose :

- NovaButton.
- NovaIconButton.
- NovaCard.
- NovaPosterCard.
- NovaEpisodeCard.
- NovaBanner.
- NovaDialog.
- NovaBottomSheet.
- NovaNavigationBar.
- NovaSidebar.
- NovaFocusIndicator.
- NovaProgressBar.

Les couleurs, dimensions, espacements, formes et animations doivent être centralisés.

Éviter les valeurs visuelles codées en dur dans chaque écran.

## 10.3 Architecture des fournisseurs IPTV

Créer une abstraction commune pour les différentes sources.

Prévoir notamment :

- M3UProvider.
- XtreamProvider.
- XMLTVProvider.

Chaque fournisseur doit implémenter les opérations nécessaires à son format.

L'interface utilisateur ne doit pas dépendre directement du fonctionnement interne d'un fournisseur.

Cette architecture facilitera les extensions futures, notamment Stalker Portal.

## 10.4 Gestion des performances

Exigences supplémentaires :

- Utiliser des listes paresseuses Compose.
- Utiliser des clés stables pour les contenus.
- Éviter les recompositions inutiles.
- Charger les images à la demande.
- Réutiliser les images en cache.
- Limiter les tâches simultanées.
- Séparer le thread principal des opérations réseau.
- Mesurer les performances avec Android Studio Profiler.
- Vérifier les fuites mémoire.
- Surveiller les temps de démarrage et de changement de chaîne.

Ne pas optimiser prématurément sans mesure.

---

# 11. ACCESSIBILITÉ ET ERGONOMIE

Prévoir :

- Contrastes suffisants.
- Textes lisibles.
- Taille de police adaptable.
- Zones tactiles suffisamment grandes.
- Libellés accessibles.
- Compatibilité avec TalkBack lorsque possible.
- Navigation TV entièrement réalisable à la télécommande.
- Respect des préférences système de réduction des animations.

L'expérience doit rester confortable pendant de longues sessions.

---

# 12. TESTS VISUELS ET TECHNIQUES

Avant validation, effectuer des tests sur :

## Mobile

- Smartphone Android compact.
- Smartphone grand écran.
- Différentes densités d'écran.
- Portrait et paysage.
- Mode économie d'énergie.

## Android TV

- Résolution 1080p.
- Résolution 4K.
- Télécommande physique.
- Navigation D-pad.
- Grand écran 98 pouces.

## Performances

Mesurer :

- Temps de démarrage.
- Temps d'ouverture des catégories.
- Temps de chargement des affiches.
- Temps de démarrage vidéo.
- Temps de changement de chaîne.
- Utilisation mémoire.
- Consommation processeur.
- Stabilité après plusieurs heures de lecture.

Les mesures doivent être réalisées sur des appareils réels lorsque possible.

---

# 13. MÉTHODE DE DÉVELOPPEMENT UX/UI

Ne pas développer immédiatement toutes les interfaces.

Procéder dans cet ordre :

**Étape 1 — Identité visuelle**
- Logo NOVA.
- Palette de couleurs.
- Typographie.
- Icônes.
- Composants de base.

**Étape 2 — Prototype mobile**
- Accueil.
- TV.
- Films.
- Séries.
- Fiche détaillée.
- Lecteur.

**Étape 3 — Prototype TV**
- Accueil.
- Navigation latérale.
- Catalogue.
- Guide TV.
- Lecteur.
- Focus D-pad.

**Étape 4 — Intégration réelle**
- Jetpack Compose.
- ViewModels.
- Navigation.
- Base locale.
- Connexion aux sources.

**Étape 5 — Optimisation**
- Fluidité.
- Animations.
- Cache.
- Mémoire.
- Batterie.

Chaque écran doit être réellement fonctionnel avant sa validation.

---

# 14. CONSIGNES FINALES À L'IA DÉVELOPPEUSE

Tu dois considérer ce document comme un complément obligatoire au cahier des charges principal de NOVA IPTV.

Les deux documents doivent être lus conjointement.

Les priorités sont :

1. Fluidité.
2. Rapidité.
3. Qualité de lecture.
4. Ergonomie.
5. Identité visuelle.
6. Personnalisation.
7. Richesse fonctionnelle.

Ne jamais sacrifier la stabilité pour un effet graphique.

Ne jamais produire de boutons décoratifs sans fonctionnalité.

Ne jamais prétendre qu'un écran ou une fonctionnalité est terminé sans implémentation réelle et tests.

Ne pas remplacer les fonctionnalités demandées par des maquettes statiques.

Privilégier les composants natifs Android et une architecture maintenable.

L'application doit rester légère, même avec plusieurs milliers de chaînes et de contenus.

**OBJECTIF FINAL**

Créer NOVA IPTV : une application Android native premium, élégante, cinématique et extrêmement fluide, offrant une expérience homogène sur smartphone, Android TV et Google TV.

NOVA ne doit pas ressembler à un simple lecteur IPTV.

Elle doit devenir un véritable environnement multimédia personnel.

**NOVA IPTV — BEYOND STREAMING.**