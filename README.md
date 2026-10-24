## Documentation

- [Cahier des charges](docs/cahier-des-charges.md)
- [Modèle entité-association (MCD)](docs/modele-donnees.md)
- [Scénario nominal](docs/scenario-nominal.md)
- [Diagramme d'état_transition — Exécution](docs/etat_transition.md)
- [Diagramme de sequence — A](docs/sequence-scenario-a.md)
- [Diagramme de sequence — B](docs/sequence-scenario-b.md)
- [Diagramme de classes](docs/classes.md)
- [Liste des API REST](docs/api-list.md)

## Écrans (frontend)

Navigation en sidebar, dont le contenu dépend du rôle de l'utilisateur connecté (`role = USER` ou `ADMIN`).

**Rôle `USER`**
- `/login` — connexion
- `/dashboard` — vue d'ensemble personnelle (KPIs, exécutions récentes)
- `/processes` — liste des traitements (recherche, création, suppression, bouton use)
- `/processes/new`, `/processes/:id` — création/édition d'un traitement et de son pipeline de règles (brouillon local, sauvegardé en un clic via « save changes »), historique récent
- `/processes/:id/run` — dépôt de fichier, rappel des règles du pipeline, lancement d'une exécution
- `/executions`, `/executions/:id` — historique des exécutions (filtres via la palette de commandes ⌘K) et détail (input/output, règles appliquées, téléchargement de l'entrée et de la sortie)
- `/settings` — modification du profil (prénom/nom/email) et changement de mot de passe

**Rôle `ADMIN`** (ne voit ni `/dashboard`, ni `/processes*`, ni `/executions*`)
- `/admin/dashboard` — vue agrégée tous utilisateurs (KPIs, répartition par utilisateur, filtre par utilisateur)
- `/admin` — gestion des comptes utilisateurs (création, désactivation/réactivation)
- `/settings` — profil et mot de passe, comme pour un `USER`

> L'aperçu avant exécution (dry-run) reste disponible côté API (`POST /api/processes/{id}/preview`, voir `docs/api-list.md`) mais n'est plus exposé dans l'interface — l'écran `run` affiche directement le pipeline de règles en lecture seule à la place.

## Thème et palette de commandes

- **Thème clair/sombre** : bouton dans la sidebar (`ThemeService`), préférence mémorisée dans `localStorage`. Sombre par défaut.
- **Palette de commandes** (`⌘K` / `Ctrl+K`) : navigation rapide entre écrans et bascule de thème, accessible depuis n'importe quel écran connecté (`AppShell`).

> **Données décoratives** : les mini-graphiques (sparklines) des écrans `/dashboard` et `/admin/dashboard` sont des données **mockées en dur** dans le composant (`dashboard.ts`, `admin-dashboard.ts`) — aucun historique agrégé n'est calculé côté backend pour l'instant. Les KPIs chiffrés (nombre de traitements, taux de succès, durée moyenne) à côté sont en revanche calculés à partir des vraies données (`GET /api/processes`, `/api/executions`).