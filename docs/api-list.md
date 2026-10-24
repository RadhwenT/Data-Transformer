# Liste des API REST

Mis à jour pour refléter l'implémentation réelle (backend `data-transformer`). Organisé par ressource.

## Authentification

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Authentifie un utilisateur (email + mot de passe), pose un cookie de session |
| `POST` | `/api/auth/logout` | Invalide la session |
| `POST` | `/api/auth/change-password` | Change son propre mot de passe (currentPassword, newPassword ; 8 caractères min., au moins un chiffre) — connecté uniquement |
| `PUT` | `/api/auth/me` | Modifie sa propre identité (firstName, lastName, email) — jamais le rôle, réservé à l'admin via `/api/admin/users`. 409 si l'email est déjà pris par un autre compte |

> **Décision tranchée** : session classique (cookie), pas de dépendance Spring Security complète, pas de JWT.
>
> **Il n'y a plus d'auto-inscription.** `POST /api/auth/register` a été retiré : seul un administrateur crée des comptes, via `/api/admin/users`. Le mot de passe est généré aléatoirement et communiqué une seule fois à la création.

## Administration (`role = ADMIN` requis)

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/api/admin/users` | Crée un compte (firstName, lastName, email, role) ; renvoie le mot de passe généré **une seule fois** |
| `GET` | `/api/admin/users` | Liste tous les comptes (actifs et désactivés) |
| `DELETE` | `/api/admin/users/{id}` | Désactive un compte (suppression logique) — un admin ne peut pas se désactiver lui-même |
| `PUT` | `/api/admin/users/{id}/activate` | Réactive un compte désactivé |
| `GET` | `/api/admin/processes` | Vue agrégée : tous les traitements actifs, toutes utilisateurs confondus (avec `ownerId`) — alimente le dashboard admin |
| `GET` | `/api/admin/executions` | Vue agrégée : toutes les exécutions, toutes utilisateurs confondues (avec `ownerId`) — alimente le dashboard admin |

## Traitements (`Process`)

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/processes` | Liste les traitements **actifs** de l'utilisateur connecté (avec `ruleCount`, `lastRunStatus`, `lastRunAt`, `lastRunExecutionId`, `ownerId`) |
| `GET` | `/api/processes/{id}` | Détail d'un traitement, avec ses règles déjà configurées (**pas** de `ruleCount` sur cette réponse : utiliser `rules.length` côté client) |
| `POST` | `/api/processes` | Crée un nouveau traitement (name, description, inputFormat, outputFormat) |
| `PUT` | `/api/processes/{id}` | Modifie un traitement existant |
| `DELETE` | `/api/processes/{id}` | Suppression **logique** (`active = false`) — l'historique des exécutions reste intact |
| `POST` | `/api/processes/{id}/preview` | **Dry-run** : rejoue la chaîne de règles sur un fichier déposé (multipart) sans rien persister — voir détail ci-dessous |

## Règles (`Rule` / `ProcessRule`)

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/rules` | Catalogue des règles de transformation disponibles (public une fois connecté) |
| `POST` | `/api/processes/{id}/rules` | Ajoute une règle paramétrée à un traitement (ruleId, executionOrder, sourceField, sourceField2, targetField) |
| `PUT` | `/api/processes/{processId}/rules/{ruleId}` | Modifie une règle paramétrée existante |
| `DELETE` | `/api/processes/{processId}/rules/{ruleId}` | Suppression **physique** — une règle paramétrée n'a pas d'historique propre à préserver |

## Preview (dry-run)

> **Note d'implémentation** : cet endpoint reste fonctionnel et testé côté backend, mais n'est plus appelé depuis l'interface — l'écran `/processes/:id/run` affiche désormais le pipeline de règles en lecture seule plutôt qu'un aperçu avant/après calculé sur le fichier déposé. Reste exploitable via l'API pour un usage futur (ou externe).

`POST /api/processes/{id}/preview` — même contrôle d'appartenance que les autres endpoints de traitement, `file` en multipart. **Ne persiste rien** : aucune ligne `DT_executions`/`DT_files`, aucune écriture sur `app.storage.dir` (garanti par un test dédié, `PreviewServiceTest.previewNePersisteRienEtNecritAucunFichier`). Réutilise `RuleChainRunner` — le composant partagé avec `ExecutionService` — pour que le résultat soit garanti identique à un vrai run (`PreviewServiceTest.equivalencePreviewEtExecute`).

Réponse : `status` (`OK`/`ERROR`/`NO_RULES`), `before`/`after` (jusqu'à 3 enregistrements), `steps[]` (trace détaillée du premier enregistrement, régle par règle), `failedRuleOrder`/`errorMessage` si `ERROR`. Une erreur de règle renvoie **200** (le plantage est une information à afficher, pas un échec HTTP) ; seuls un fichier illisible/vide ou trop volumineux renvoient 400. Garde-fous configurables : `app.preview.max-file-size` (1 Mo par défaut, au-delà → 400) et `app.preview.max-records` (200 par défaut, au-delà → troncature silencieuse, `truncated: true`).

## Exécutions (`Execution` / `File`)

| Méthode | Endpoint | Description |
|---|---|---|
| `POST` | `/api/executions` | Lance une exécution (processId + fichier en multipart) — synchrone, renvoie le résultat final |
| `GET` | `/api/executions` | Historique des exécutions de l'utilisateur connecté (avec `processName`, `ownerId`). Filtre optionnel `?processId={id}` (vérifie l'appartenance du traitement avant de scoper la requête) |
| `GET` | `/api/executions/{id}` | Détail enrichi d'une exécution : `processId`, formats, `durationMs`, `inputFileName`, `hasOutput`, `rules[]` (snapshot des règles du traitement) |
| `GET` | `/api/executions/{id}/content` | Contenu texte input/output pour affichage à l'écran (`input`, `output`, `inputTruncated`, `outputTruncated`) — plafonné à 100 Ko par fichier, `output` vaut `null` si l'exécution a échoué |
| `GET` | `/api/executions/{id}/download` | Télécharge le fichier de sortie généré — exige `status = SUCCESS` (409 sinon) |
| `GET` | `/api/executions/{id}/download-input` | Télécharge le fichier d'entrée envoyé — disponible quel que soit le statut de l'exécution, y compris `FAILED` (le fichier est écrit avant l'application des règles) |

## Formats de fichier

Le format d'un traitement (`inputFormat`/`outputFormat`) est `JSON` ou `XML`, indépendamment l'un de l'autre — un traitement peut convertir JSON → XML ou XML → JSON. Le fichier déposé peut contenir :
- un **objet unique** (un enregistrement),
- ou un **tableau d'objets** (plusieurs enregistrements, traités un par un, dans l'ordre).

Les règles s'appliquent à chaque enregistrement individuellement. Si un enregistrement échoue, l'exécution entière passe en `FAILED` et le message d'erreur précise sa position (« Enregistrement 2/5 : ... ») — stratégie tout-ou-rien, documentée dans `ExecutionService`.

## Correspondance avec l'architecture

Chaque endpoint est porté par un `@RestController`, qui délègue à la couche service correspondante :

- `/api/auth/*` → `AuthService`
- `/api/admin/users*` → `AdminService` ; `/api/admin/processes` et `/api/admin/executions` lisent directement les repositories (`DataProcessRepository`, `ProcessRuleRepository`, `ExecutionRepository`), même logique que `ProcessController`, sans filtre par utilisateur
- `/api/processes/*` → repositories directement (`DataProcessRepository`, `ProcessRuleRepository`) plus quelques vérifications inline dans `ProcessController`
- `/api/executions/*` → `ExecutionService`
- `/api/rules` → lecture directe du catalogue (pas de logique métier complexe, repository suffit)

Toutes les routes protégées par une session vérifient l'appartenance de la ressource à l'utilisateur connecté avant toute lecture ou écriture (voir `requireOwnedProcess`/`requireRuleOfProcess` dans `ProcessController`, logique équivalente inline dans `ExecutionController`).

## Notes

- `POST /api/executions` est **synchrone** : la réponse contient déjà le statut final (`SUCCESS` ou `FAILED`), pas de polling ni de second appel nécessaire.
- Le fichier d'entrée n'est jamais persisté via un endpoint séparé — il est envoyé directement dans le corps de `POST /api/executions`.
- Un traitement, une fois désactivé (`DELETE /api/processes/{id}`), n'apparaît plus dans les listes ni n'est exécutable, mais ses exécutions passées restent consultables dans l'historique.
