# Scénario nominal — Traitement « Nettoyage clients »

## Scénario A — Création et configuration du traitement

1. L'utilisateur se connecte à l'application.
2. Il arrive sur la liste de ses traitements.
3. Il clique sur **« Nouveau traitement »**.
4. Il renseigne : nom = *Nettoyage clients*, format d'entrée = **JSON**, format de sortie = **JSON**.
5. Il choisit entre deux options :
   - **5a. Configurer les règles maintenant** — chemin détaillé ci-dessous (scénario nominal).
   - **5b. Enregistrer sans règle et configurer plus tard** — le traitement est créé immédiatement en base (aucune ligne dans la table `DT_process_rules`), visible dans la liste avec le statut « Aucune règle configurée ». L'utilisateur pourra revenir dessus à tout moment pour ajouter les règles, en une ou plusieurs fois.

> Ce chemin 5b est permis par la cardinalité `0,N` retenue entre `DT_processes` et `DT_process_rules` dans le modèle de données.

6. Il ajoute la règle **« Concaténer deux valeurs »** → `source_field` = `prenom`, `source_field_2` = `nom`, `target_field` = `nomComplet` (`execution_order` = 1). Les champs sources sont automatiquement supprimés du résultat.
7. Il ajoute la règle **« Mettre en majuscules »** → `source_field` = `nomComplet` uniquement (`execution_order` = 2).
8. Il ajoute la règle **« Reformater une date »** → `source_field` = `date_naissance` uniquement (`execution_order` = 3). Format source et cible fixés dans le code pour le MVP (JJ/MM/AAAA → AAAA-MM-JJ).
9. Il clique sur **« Enregistrer le traitement »**. Le traitement est créé en base avec ses 3 règles paramétrées, dans l'ordre.
10. Une confirmation s'affiche, l'utilisateur est redirigé vers la liste des traitements.

> **Évolutions futures (hors MVP)** : rendre configurable la suppression des champs sources lors de la concaténation, et rendre configurables les formats de date source/cible.

---

## Scénario B — Exécution d'un traitement existant

1. L'utilisateur arrive sur la page d'accueil : liste de ses traitements, avec une barre de recherche. Il trouve *Nettoyage clients* (recherche ou parcours de la liste).
2. Il clique sur **« Utiliser »**.
3. Une zone s'affiche pour fournir le fichier : glisser-déposer, ou parcourir l'appareil.
4. Il fournit le fichier `clients.json`. À ce stade, il peut encore cliquer **« Annuler »** — l'interface efface simplement la sélection, aucun appel n'a été fait au serveur, aucune trace en base.
5. Il clique sur **« Lancer »** — le bouton se désactive, l'option « Annuler » disparaît (l'interface se bloque jusqu'à la réponse, cohérent avec l'architecture synchrone : au-delà de ce point, l'exécution ne peut plus être interrompue depuis l'interface).
6. **Vérification du format** (avant toute création en base) : le fichier doit être au format JSON attendu. Si le format est invalide, un message d'erreur s'affiche immédiatement (« Format invalide, JSON attendu »), aucune trace n'est enregistrée en base, l'interface se réactive et l'utilisateur peut réessayer avec un autre fichier.
7. Si le format est valide, une ligne est créée dans la table `DT_executions` avec `status` = **PENDING** puis **RUNNING**. Côté interface, le bouton « Lancer » passe en état `running...` (désactivé) — pas de barre de progression à pourcentage, cohérent avec l'architecture synchrone : l'utilisateur attend simplement la réponse.
8. Le traitement s'exécute : lecture du fichier, application des 3 règles dans l'ordre (concaténation → majuscule → reformatage de date).
9. Une fois terminé, le `status` passe à **SUCCESS** (affiché « succès » à l'utilisateur), l'interface se réactive.
10. L'utilisateur peut **apercevoir** le résultat directement dans l'interface, ou le **télécharger**.

---

## Scénario C — Lancement depuis l'écran `run` et consultation de l'historique

Ce scénario détaille ce que l'écran de run fait réellement entre les étapes 4 et 5 du scénario B, plus la consultation après-coup.

> **Évolution par rapport à la version initiale.** L'écran `run` a d'abord inclus un panneau d'aperçu (dry-run avant/après, `POST /api/processes/{id}/preview`) : le fichier déposé était rejoué côté backend sans rien persister, pour détecter un champ mal saisi avant de lancer une vraie exécution. Ce panneau a depuis été retiré de l'interface au profit d'un simple rappel du pipeline de règles en lecture seule (voir ci-dessous) — plus direct, avec un aller-retour de moins avant de voir un vrai résultat. **L'endpoint `/preview` existe toujours côté backend, testé, mais n'est plus appelé depuis l'UI.**

1. Depuis la liste des traitements, l'utilisateur clique sur **« use »** pour *Nettoyage clients* (règles déjà configurées) — il arrive sur `/processes/{id}/run`.
2. L'écran affiche trois étapes numérotées :
   - **01 — fichier d'entrée** : zone de dépôt (glisser-déposer ou bouton « browse device »). Avant tout appel réseau, le fichier est validé côté client : extension cohérente avec le format d'entrée du traitement (`.json` ou `.xml`), taille ≤ 1 Mo, fichier non vide. Un fichier invalide affiche un message inline et n'est pas chargé.
   - **02 — règles appliquées** : rappel en lecture seule du pipeline configuré (ordre, type de règle, champs), sous forme de cartes — permet de vérifier d'un coup d'œil ce qui va s'exécuter avant de lancer.
   - **03 — exécution** : bouton **« run »**, désactivé tant qu'aucun fichier n'est chargé ou que le traitement n'a aucune règle.
3. L'utilisateur peut retirer le fichier chargé (bouton « remove ») avant de lancer — aucun appel réseau n'a encore eu lieu, aucune trace en base.
4. Il clique sur **« run »** : `POST /api/executions` est appelé, une ligne `DT_executions` est créée (PENDING → RUNNING → SUCCESS/FAILED), et l'utilisateur est redirigé vers `/executions/{id}` — que l'exécution ait réussi ou échoué (l'écran de détail sait afficher les deux cas).
5. Sur `/executions/{id}` : statut, durée, règles appliquées avec le nom de la règle et les champs concernés (la règle fautive surlignée en cas d'échec), contenu input/output affiché à l'écran (`GET /api/executions/{id}/content`), boutons de téléchargement de l'entrée (`download input`, disponible même en cas d'échec) et de la sortie (`download output`, uniquement si l'exécution a réussi).
6. Depuis `/executions`, l'utilisateur retrouve l'historique complet de ses exécutions (tous traitements confondus). Le filtrage par traitement et par statut se fait via la palette de commandes (bouton « filtres avancés » ou raccourci ⌘K), plutôt que par des menus déroulants dédiés. Depuis la fiche d'un traitement (`/processes/{id}`), un panneau **« recent executions »** montre directement les 5 dernières exécutions de ce traitement précis, sans changer d'écran.

---

## Exemple JSON associé

**Fichier d'entrée (`clients.json`)**
```json
{
  "prenom": "sarah",
  "nom": "martin",
  "date_naissance": "14/03/1995"
}
```

**Après la règle « Concaténer » (prenom + nom → nomComplet)**
```json
{
  "nomComplet": "sarah martin",
  "date_naissance": "14/03/1995"
}
```

**Après la règle « Mettre en majuscules » (sur nomComplet)**
```json
{
  "nomComplet": "SARAH MARTIN",
  "date_naissance": "14/03/1995"
}
```

**Après la règle « Reformater une date » (transformation sur place, source_field = date_naissance)**
```json
{
  "nomComplet": "SARAH MARTIN",
  "date_naissance": "1995-03-14"
}
```