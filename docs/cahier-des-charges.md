# Cahier des charges

**Stage — Prototype de traitement de données**
2 stagiaires (binôme) — Durée : 1 mois

---

## 1. Contexte et objectif

L'objectif est de réaliser, en 1 mois et en binôme, un **prototype simple** permettant de :

- lire un fichier de données (JSON ou XML)
- appliquer quelques transformations basiques
- générer un fichier de résultat
- consulter ce résultat depuis une interface web minimale

Le stage vise autant la réalisation du prototype que l'apprentissage d'une méthode de travail projet complète (documentation, sprints, tests, restitution).

Le binôme travaille ensemble sur l'ensemble des étapes afin que chaque stagiaire comprenne le projet de bout en bout, backend comme frontend.

## 2. Fonctionnement du binôme

- Les deux stagiaires interviennent sur toutes les grandes étapes (documentation, backend, frontend, tests).
- **Pair-programming** recommandé sur les parties structurantes (modélisation des données, logique de transformation, architecture générale).
- **Répartition en parallèle** possible sur les tâches simples et indépendantes.

## 3. Périmètre du prototype

### Inclus

- Un seul scénario de traitement, pas de configuration dynamique avancée
- Lecture d'un fichier JSON ou XML (un seul format)
- 2 à 3 règles de transformation simples (ex : renommer un champ, mettre en majuscules, reformater une date)
- Génération d'un fichier de sortie (fichiers d'entrée et de sortie tracés en base)
- Interface web basique : liste des traitements, bouton "Lancer", affichage du statut (en attente / en cours / succès / erreur)
- Base de données PostgreSQL pour l'historique des exécutions
- Gestion des utilisateurs (créateur d'un traitement, exécutant d'un lancement)
- Catalogue de règles de transformation réutilisables, appliquées à un traitement avec des paramètres propres (champ source, champ cible, ordre d'exécution)

### Secondaire (à ajouter si le temps le permet)

- Docker / docker-compose
- Design graphique soigné
- Tests exhaustifs (au-delà des tests unitaires clés)
- Plusieurs types de traitements configurables

*Ces points ne sont pas prioritaires sur 1 mois, mais restent ouverts si le binôme avance plus vite que prévu.*

## 4. Stack technique

- **Backend** : Java + Spring Boot + API REST
- **Frontend** : React ou Angular (au choix du binôme)
- **Base de données** : PostgreSQL
- **Tests** : JUnit (tests clés, pas de couverture complète)
- **Gestion de code** : Git
- **Modélisation** : UML (diagramme de classes, diagramme de séquence) et modèle entité-association (MCD)

## 5. Planning sur 4 semaines

### Semaine 1 — Documentation et prise en main
- Jours 1-3 : rédaction du cahier des charges détaillé, diagrammes UML clés (classes, séquence, cas d'utilisation...)
- Jours 4-5 : mise en place des environnements de développement, premiers tutoriels Spring Boot et React, installation de PostgreSQL

### Semaine 2 — Sprint 1 : Backend et traitement de données
- Formation ciblée en début de semaine (Spring Boot, API REST) en binôme
- Développement du backend : modèle de données, lecture du fichier, logique de transformation, génération du fichier de sortie
- Objectif de fin de sprint : un scénario de traitement complet fonctionnel en ligne de commande ou via API, sans interface

### Semaine 3 — Sprint 2 : Interface web et intégration
- Formation ciblée en début de semaine sur le framework frontend choisi
- Développement de l'interface (liste des traitements, lancement, statut)
- Connexion de l'interface à l'API backend
- Démarrage de la rédaction du rapport de stage (au fil de l'eau)

### Semaine 4 — Finalisation
- Jours 1-2 : tests, correction des bugs identifiés
- Jours 3-4 : finalisation du README, du rapport, préparation du support de démonstration
- Jour 5 : démonstration finale

## 6. Livrables attendus (fin de stage)

- Cahier des charges, diagrammes UML et modèle entité-association (MCD)
- Backend fonctionnel avec au moins 1 scénario complet de traitement
- Interface web simple connectée au backend
- Base de données PostgreSQL avec historique des exécutions
- Quelques tests unitaires (JUnit)
- Code source sur Git
- Fichier README (installation + lancement)
- Rapport de stage
- Démonstration finale (15-20 min)

## 7. Compétences visées

- Rédaction de documentation technique (cahier des charges, UML)
- Développement backend avec Spring Boot et API REST
- Développement frontend simple (React ou Angular)
- Manipulation de fichiers JSON/XML
- Utilisation de PostgreSQL
- Travail en binôme, méthode agile légère (sprints) et utilisation de Git en équipe