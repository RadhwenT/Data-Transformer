# Prompt Claude Code — Data Transformer : preview 4b, bouton « use », historique d'exécution

> **⚠️ Document historique, partiellement dépassé.** Ceci est le prompt effectivement soumis pour livrer preview/run/historique — conservé tel quel comme trace du travail demandé, pas mis à jour au fil de l'eau. Deux décisions qu'il documente ont depuis été révisées par des demandes ultérieures : le panneau de preview décrit ici a été retiré de l'écran `run` (voir `docs/scenario-nominal.md`, Scénario C, et `docs/api-list.md`), et le composant `topbar` qu'il fait livrer a depuis été remplacé par une sidebar (`features/shared/sidebar/`, `features/shared/app-shell/`). Pour l'état actuel de référence, se fier au code et aux autres fichiers de `docs/`, pas à ce prompt.
>
> **Mode d'emploi (d'origine).** Ouvre Claude Code à la racine `data-transformer/`, puis colle tout ce qui suit à partir de la ligne `## 0. Rôle et cadre`. Le document est écrit pour être auto-suffisant : il contient l'état réel du code, les décisions déjà tranchées (pour éviter les allers-retours) et un plan d'exécution étape par étape.

---

## 0. Rôle et cadre

Tu travailles sur **Data Transformer**, une application full-stack de transformation de fichiers de données. Le projet est fonctionnel : authentification par session, gestion des traitements et de leurs règles, moteur de transformation, exécution synchrone côté backend. Ce qui manque, c'est **la partie « utiliser » du produit** côté interface.

Avant d'écrire la moindre ligne de code, lis les fichiers listés en §2 pour valider que la description ci-dessous correspond au dépôt. Si un écart existe, signale-le et adapte-toi au code réel — ce document décrit l'intention, le dépôt fait foi.

Travaille en **étapes séquentielles** (§8), avec un commit par étape, en gardant le build vert (`mvn test` côté back, `npm test` + `npm run build` côté front) à la fin de chaque étape.

---

## 1. Stack et conventions à respecter

**Backend** — `data-transformer/`
- Java 21, Spring Boot, Maven, Lombok, JPA/Hibernate, PostgreSQL (docker-compose), Testcontainers pour les tests.
- Jackson **3** : les imports sont `tools.jackson.databind.ObjectMapper` et `tools.jackson.dataformat.xml.XmlMapper` — **pas** `com.fasterxml.jackson`.
- Sécurité : session HTTP classique (cookie), `session.getAttribute("userId")`. Pas de Spring Security, pas de JWT.
- Tables préfixées `DT_` (`DT_processes`, `DT_process_rules`, `DT_executions`, `DT_files`, `DT_rules`, `DT_users`).
- `spring.jpa.open-in-view=false` : tout accès lazy hors transaction plante — utiliser des `join fetch` explicites comme dans `ExecutionRepository`.
- Commentaires de code en français, sans accents (convention existante).

**Frontend** — `data-transformer-frontend/`
- Angular 22, composants **standalone**, **signals**, `provideZonelessChangeDetection()`, control flow `@if` / `@for` (jamais `*ngIf` / `*ngFor`).
- Tests : **vitest** (`*.spec.ts` à côté des composants).
- Tous les appels HTTP passent `{ withCredentials: true }` (session cookie).
- Un composant = 4 fichiers : `nom.ts`, `nom.html`, `nom.css`, `nom.spec.ts`.
- Libellés d'interface en **anglais**, en minuscules (`processes`, `+ new process`, `use`, `download output`) ; messages d'erreur utilisateur en français sans accents, comme l'existant.

**Interdictions strictes**
- ❌ Aucune nouvelle dépendance npm ou Maven. Le drag & drop, le formatage JSON/XML et le diff se font à la main.
- ❌ Aucune migration destructrice ni changement de schéma des tables existantes (le preview ne persiste rien).
- ❌ Pas de librairie de composants UI, pas de Tailwind, pas d'icônes externes (SVG inline uniquement, comme dans `processes.html`).
- ❌ Ne pas casser les tests existants ni les endpoints existants.

---

## 2. État actuel du code (à vérifier avant de coder)

### Backend — déjà en place

```
data-transformer/src/main/java/io/github/radhwent/datatransformer/
├── controller/  AuthController, AdminController, ProcessController, RuleController,
│                ExecutionController, GlobalExceptionHandler + DTOs (ProcessResponse,
│                ProcessDetailResponse, ProcessRuleResponse, ExecutionResponse, ...)
├── entity/      User, DataProcess, ProcessRule, Rule, Execution, DataFile,
│                Status(PENDING|RUNNING|SUCCESS|FAILED), RuleType(RENAME|UPPERCASE|
│                FORMAT_DATE|CONCATENATE), FileFormat(JSON|XML), FileType(INPUT|OUTPUT)
├── repository/  DataProcessRepository, ProcessRuleRepository, ExecutionRepository,
│                DataFileRepository, RuleRepository, UserRepository
└── service/     AuthService, AdminService, ExecutionService, TransformationEngine,
                 FileStorageService, PasswordGenerator + exceptions métier
```

Endpoints existants (voir `docs/api-list.md`) :

| Méthode | Endpoint | Statut |
|---|---|---|
| `POST` | `/api/auth/login` · `/logout` · `/change-password` | ✅ |
| `*` | `/api/admin/users*` | ✅ |
| `GET/POST/PUT/DELETE` | `/api/processes` · `/api/processes/{id}` | ✅ |
| `POST/PUT/DELETE` | `/api/processes/{id}/rules*` | ✅ |
| `GET` | `/api/rules` | ✅ |
| `POST` | `/api/executions` (multipart : `processId` + `file`) | ✅ |
| `GET` | `/api/executions` · `/api/executions/{id}` · `/{id}/download` | ✅ |

Points clés à réutiliser, **surtout ne pas réécrire** :
- `TransformationEngine.applyRule(data, type, sourceField, sourceField2, targetField)` : moteur des 4 règles.
- `ExecutionService.applyRulesToAll(records, rules)` (privé) : boucle « tout ou rien » qui préfixe l'erreur par `Enregistrement i/n : ...`.
- `FileStorageService.readRecords(MultipartFile, FileFormat)` : parse JSON **ou** XML vers `List<Map<String,Object>>`, objet unique comme tableau.
- `ProcessController.requireOwnedProcess(...)` : contrôle d'appartenance ; un traitement d'un autre utilisateur renvoie **« introuvable »**, jamais « interdit » (on ne confirme pas l'existence d'un id).
- `GlobalExceptionHandler` : mapping exceptions → codes HTTP.

### Frontend — déjà en place

```
data-transformer-frontend/src/app/
├── app.routes.ts        login, processes, processes/new, processes/:id, admin
├── models/api.models.ts User, DataProcess, ProcessRule, ProcessDetail, Rule, Execution
├── services/            auth.service, auth.guard, process.service, rule.service,
│                        admin.service, api.config
└── features/            login/, processes/, process-detail/, admin/
```

**Ce qui manque totalement côté front** : aucun écran d'exécution, aucun appel à `/api/executions`, aucun bouton « use ». La table des traitements n'offre que `edit` et `delete`.

**Dette à corriger au passage** : la topbar (marque + lien admin + email + logout) est dupliquée à l'identique dans `processes.html`, `process-detail.html` et `admin.html`. On va ajouter 3 écrans : extraire un composant partagé avant, pas après.

---

## 3. Objectif — les 3 fonctionnalités demandées

1. **Preview du résultat attendu** (inspiré de la maquette **4b**) : dès qu'un fichier JSON ou XML est déposé, l'utilisateur voit **avant / après** — le fichier tel quel, la chaîne de règles appliquée, et le résultat qui *sortirait* — **sans rien exécuter ni persister**. Objectif produit : détecter un nom de champ mal saisi avant de créer une exécution ratée.
2. **Bouton « use »** : lancer réellement le traitement sur le fichier, depuis la liste des traitements.
3. **Historique d'utilisation par utilisateur** : liste des exécutions de l'utilisateur connecté, écran de détail (input, règles appliquées, output, téléchargement, erreur), et rappel des dernières exécutions sur la fiche du traitement.

---

## 4. Décisions déjà tranchées (ne pas les remettre en question)

| # | Décision | Raison |
|---|---|---|
| D1 | Le preview est calculé **côté backend**, nouvel endpoint `POST /api/processes/{id}/preview`. | Réutilise `TransformationEngine` : le preview est *garanti* identique au vrai run. Le réimplémenter en TypeScript créerait deux moteurs qui divergent. |
| D2 | Le preview **ne persiste rien** : aucune ligne `DT_executions`, aucune ligne `DT_files`, aucun fichier écrit dans `app.storage.dir`. | C'est un dry-run. L'historique ne doit contenir que de vraies exécutions. |
| D3 | Le bouton « use » ouvre un **écran dédié `/processes/:id/run`**, pas une modale. | Le preview avant/après a besoin de place ; c'est l'option la plus « pro » et la plus démontrable en soutenance. |
| D4 | Une erreur de règle pendant le preview renvoie **HTTP 200** avec `status: "ERROR"` et l'ordre de la règle fautive. Seul un fichier illisible / mauvais format renvoie **400**. | Un champ manquant est une information à afficher dans le panneau, pas un plantage d'API. |
| D5 | L'historique reste **strictement par utilisateur** (`session.userId`), aucune vue admin globale dans ce lot. | Périmètre maîtrisé ; `ExecutionController` filtre déjà par `userId`. |
| D6 | Le thème reste le thème **sombre « terminal »** déjà en place (fond `#0b0d0c` avec grille, accent vert `#22c55e`, JetBrains Mono). Aucun redesign. | Les 3 nouveaux écrans doivent être indiscernables des écrans existants. |
| D7 | Aucune nouvelle dépendance, front ou back. | Contrainte de stage / revue de code. |

---

## 5. Spécification backend

### 5.1 Refactor préalable — un seul chemin d'exécution des règles

`ExecutionService.applyRulesToAll(...)` est privé et sera nécessaire au preview. **Extrais-le** dans un composant réutilisable, par exemple `service/RuleChainRunner.java` :

```java
@Service
public class RuleChainRunner {

    /** Applique la chaine de regles a tous les enregistrements (tout ou rien). */
    public List<Map<String, Object>> applyAll(List<Map<String, Object>> records, List<ProcessRule> rules);

    /** Applique la chaine a UN enregistrement en conservant l'etat apres chaque regle
     *  (utilise par le preview pour tracer la transformation etape par etape). */
    public List<Map<String, Object>> applyWithTrace(Map<String, Object> record, List<ProcessRule> rules);
}
```

`ExecutionService` délègue désormais à ce composant : **son comportement observable ne change pas** (mêmes messages d'erreur, même préfixe `Enregistrement i/n : `). Les tests existants `ExecutionServiceTest` et `TransformationEngineTest` doivent passer sans modification.

### 5.2 Nouvel endpoint — preview (dry-run)

```
POST /api/processes/{id}/preview
Content-Type: multipart/form-data
Params: file (obligatoire)
```

Règles de gestion :
- Session obligatoire → sinon `401` (`UnauthorizedException`, même helper que `ExecutionController.currentUserId`).
- Appartenance du traitement vérifiée via la logique de `requireOwnedProcess` → sinon **« introuvable »**.
- Traitement sans règle → `200` avec `status: "NO_RULES"` et `after == before` (l'utilisateur doit voir *pourquoi* rien ne change ; pas d'erreur 500 comme dans `execute`).
- Fichier vide / format invalide → `400` via `InvalidFileFormatException` (déjà mappée).
- Garde-fous : **taille max 1 Mo** et **200 enregistrements max lus** ; au-delà on tronque et on renseigne `truncated: true`. Rendre ces valeurs configurables dans `application.properties` (`app.preview.max-file-size`, `app.preview.max-records`).
- Prévisualiser **les 3 premiers enregistrements** au maximum (`before` / `after`), et la **trace détaillée sur le premier seulement**.

Réponse `PreviewResponse` :

```json
{
  "status": "OK",
  "processName": "Client cleanup",
  "inputFormat": "JSON",
  "outputFormat": "JSON",
  "recordCount": 12,
  "previewedCount": 3,
  "truncated": true,
  "before": [ { "prenom": "sarah", "nom": "martin", "date_naissance": "14/03/1995" } ],
  "after":  [ { "nomComplet": "SARAH MARTIN", "date_naissance": "1995-03-14" } ],
  "steps": [
    { "order": 1, "ruleType": "CONCATENATE", "label": "prenom + nom → nomComplet",
      "record": { "nomComplet": "sarah martin", "date_naissance": "14/03/1995" } },
    { "order": 2, "ruleType": "UPPERCASE", "label": "nomComplet",
      "record": { "nomComplet": "SARAH MARTIN", "date_naissance": "14/03/1995" } },
    { "order": 3, "ruleType": "FORMAT_DATE", "label": "date_naissance · dd/MM/yyyy → yyyy-MM-dd",
      "record": { "nomComplet": "SARAH MARTIN", "date_naissance": "1995-03-14" } }
  ],
  "failedRuleOrder": null,
  "errorMessage": null
}
```

- `status` ∈ `OK` | `ERROR` | `NO_RULES`.
- En cas d'`ERROR` : `after` est `null`, `steps` contient les étapes réussies jusqu'à la règle fautive incluse (celle-ci sans `record`), `failedRuleOrder` et `errorMessage` sont renseignés (message identique à celui du vrai run).
- `label` est une description lisible construite depuis `ProcessRule` (même format que la colonne « fields » de `process-detail.html`).

Implémentation attendue : un `PreviewService` dédié + un endpoint dans `ProcessController` (ou un `PreviewController` si `ProcessController`, déjà à ~14 ko, devient illisible — à toi de juger, mais justifie le choix dans le message de commit).

### 5.3 Enrichissement des endpoints d'exécution

1. **`GET /api/executions?processId={id}`** — filtre optionnel, pour l'historique par traitement. Vérifier l'appartenance du traitement. Sans paramètre, comportement actuel inchangé.
2. **`GET /api/executions/{id}`** — enrichir `ExecutionResponse` (ou créer `ExecutionDetailResponse` pour ne pas alourdir la liste) avec :
   - `processId`, `processName`, `inputFormat`, `outputFormat`
   - `durationMs` (calculé depuis `startedAt`/`finishedAt`, `null` si non terminée)
   - `inputFileName`, `hasOutput`
   - `rules[]` : snapshot des règles appliquées (`executionOrder`, `ruleName`, `ruleType`, `sourceField`, `sourceField2`, `targetField`)
   - `errorMessage`
3. **`GET /api/executions/{id}/content`** — nouveau. Renvoie le contenu **texte** des fichiers pour l'affichage à l'écran :
   ```json
   { "input": "...", "output": "...", "inputTruncated": false, "outputTruncated": false }
   ```
   Plafonner à **100 Ko par fichier** (au-delà : tronquer et lever le flag). `output` vaut `null` si l'exécution a échoué. Contrôle d'appartenance identique au `/download`.

`GET /api/executions/{id}/download` reste inchangé.

### 5.4 Tests backend attendus

- `PreviewServiceTest` / `PreviewControllerTest` : JSON mono-enregistrement, JSON tableau, XML, champ manquant → `status ERROR` + `failedRuleOrder`, traitement sans règle → `NO_RULES`, fichier corrompu → `400`, fichier > 1 Mo → `400`, traitement d'un autre utilisateur → `404`, sans session → `401`.
- **Test anti-régression capital** : après un preview, `executionRepository.count()` et `dataFileRepository.count()` sont **inchangés**, et aucun fichier n'a été créé dans `app.storage.dir`.
- **Test d'équivalence** : pour un même fichier et un même traitement, le `after` du preview est **égal** à la sortie du vrai `execute` (c'est la garantie qui justifie D1).
- `ExecutionControllerTest` : filtre `processId`, endpoint `/content` (troncature, output `null` si `FAILED`, `404` sur exécution d'autrui).

---

## 6. Spécification frontend

### 6.1 Socle

**`models/api.models.ts`** — ajouter :

```ts
export interface PreviewStep { order: number; ruleType: string; label: string; record: Record<string, unknown> | null; }

export interface PreviewResponse {
  status: 'OK' | 'ERROR' | 'NO_RULES';
  processName: string; inputFormat: string; outputFormat: string;
  recordCount: number; previewedCount: number; truncated: boolean;
  before: Record<string, unknown>[];
  after: Record<string, unknown>[] | null;
  steps: PreviewStep[];
  failedRuleOrder: number | null;
  errorMessage: string | null;
}

export interface ExecutionDetail extends Execution {
  processId: number; inputFormat: string; outputFormat: string;
  durationMs: number | null; inputFileName: string | null; hasOutput: boolean;
  rules: ProcessRule[];
}

export interface ExecutionContent {
  input: string | null; output: string | null;
  inputTruncated: boolean; outputTruncated: boolean;
}
```

**`services/execution.service.ts`** (nouveau) :

```ts
run(processId: number, file: File): Observable<Execution>            // POST /api/executions (FormData)
getAll(processId?: number): Observable<Execution[]>                  // GET  /api/executions
getById(id: number): Observable<ExecutionDetail>                     // GET  /api/executions/{id}
getContent(id: number): Observable<ExecutionContent>                 // GET  /api/executions/{id}/content
downloadOutput(id: number): Observable<Blob>                         // GET  /api/executions/{id}/download  (responseType: 'blob')
```

**`services/process.service.ts`** — ajouter `preview(id: number, file: File): Observable<PreviewResponse>`.

Le téléchargement se fait via `Blob` + `URL.createObjectURL` + `<a download>` créé à la volée, puis `URL.revokeObjectURL` (une navigation directe vers l'URL perdrait le cookie de session dans certains cas et ne gère pas les erreurs).

**Composant partagé `features/shared/topbar/`** : marque + SVG, lien `processes`, lien `executions`, lien `admin` (si `role === 'ADMIN'`), email, `logout`. Il prend un `input` pour l'onglet actif. Remplacer les 3 copies existantes (`processes.html`, `process-detail.html`, `admin.html`) — vérifier que les specs existantes passent toujours.

**`app.routes.ts`** — ajouter, tous derrière `authGuard` :

```ts
{ path: 'processes/:id/run', component: Run,           canActivate: [authGuard] },
{ path: 'executions',        component: Executions,    canActivate: [authGuard] },
{ path: 'executions/:id',    component: ExecutionDetail, canActivate: [authGuard] },
```

⚠️ Ordre des routes : `processes/new` et `processes/:id/run` doivent être déclarées **avant** `processes/:id`.

### 6.2 Écran `/processes/:id/run` — le cœur de la demande

Deux colonnes (`grid-template-columns: 380px 1fr`, empilées sous 1000 px).

**Colonne gauche — le fichier**
- En-tête : `run · {process.name}`, sous-titre `{ruleCount} rules · {inputFormat} → {outputFormat}`.
- Zone de dépôt : `drop your {inputFormat} file here / or browse device`. Drag & drop natif (`dragover` / `dragleave` / `drop`, `preventDefault`) + `<input type="file" hidden>` déclenché par un bouton. `accept=".json"` ou `.xml` selon `inputFormat`.
- Validations côté client **avant** tout appel : extension cohérente avec `inputFormat`, taille ≤ 1 Mo, fichier non vide. Message d'erreur inline, pas d'`alert()`.
- Fichier chargé : nom, taille lisible (`1.4 KB`), bouton `remove`.
- Actions : `cancel` (retour à `/processes`) et **`run`**. `run` est désactivé tant qu'aucun fichier n'est chargé, ou si le traitement n'a aucune règle.
- Pendant l'exécution : bouton en `running…`, formulaire figé, mention `synchronous — cannot be cancelled`.
- Succès → `router.navigate(['/executions', execution.id])`. Échec applicatif (`status FAILED` renvoyé en 200) → même redirection : l'écran de détail sait afficher une erreur. Erreur HTTP → message inline, on reste sur la page.

**Colonne droite — le preview (maquette 4b)**
- État vide : `drop a file to preview the result — nothing is executed`.
- Dès qu'un fichier valide est sélectionné : appel automatique de `preview()`, état `checking…`.
- `status: OK` : deux blocs **`before`** / **`after`** en JSON indenté (`JSON.stringify(x, null, 2)` — même en sortie XML on affiche la structure en JSON, avec une note `structure preview — output will be written as XML`), séparés par un marqueur `↓ {n} rules`. Sous les blocs, la trace : une ligne par règle (`1 concat · prenom + nom → nomComplet`) avec pastille verte. Si `recordCount > previewedCount` : `showing 1 of 12 records`.
- `status: ERROR` : bandeau rouge `rule {failedRuleOrder} failed — {errorMessage}`, les règles précédentes affichées en vert, la fautive en rouge, les suivantes en gris. Le bouton `run` **reste actif** (l'utilisateur garde le droit de tenter et de tracer l'échec dans l'historique) mais est marqué `run anyway`.
- `status: NO_RULES` : `this process has no rules yet` + lien `add rules` vers `/processes/:id`.
- Les clés des enregistrements qui ont **changé** entre `before` et `after` (ajoutées, supprimées, valeur modifiée) sont mises en évidence : vert pour ajouté/modifié, gris barré pour supprimé. Comparaison faite en TypeScript sur les objets renvoyés, sans librairie de diff.

### 6.3 Écran `/executions` — historique

Table, mêmes styles que `processes.html` : `id`, `process`, `started` (format `18 Mar, 14:02`), `duration` (`1.2s`, `—` si nulle), `status` (réutiliser la classe `.run-status`), actions `view` / `download` (`download` seulement si `SUCCESS`).
- Filtre par traitement (`<select>` alimenté par `ProcessService.getAll()`) et filtre par statut, tous deux appliqués côté client sur des `signal`s.
- État vide : `no executions yet` + lien vers `/processes`.
- Clic sur une ligne → `/executions/:id`.

### 6.4 Écran `/executions/:id` — détail

- En-tête : `execution #412`, pastille de statut, `{processName} · {startedAt} → {finishedAt} · {durationMs}`, bouton `download output` (si `hasOutput`), lien `run again` vers `/processes/{processId}/run`.
- Si `FAILED` : bloc rouge avec `errorMessage`, et la règle fautive surlignée dans la liste des règles quand le message permet de l'identifier.
- Deux panneaux `input · {inputFileName}` et `output`, alimentés par `getContent()`, en `<pre>` monospace avec `overflow: auto` et hauteur max. Bandeau `truncated — download the file for the full content` si tronqué.
- Bloc `rules applied` : liste ordonnée depuis `detail.rules`, même formatage que le preview.

### 6.5 Retouches sur les écrans existants

**`processes.html`** — dans la colonne actions, ajouter **avant** `edit` :
```
use   → routerLink /processes/{id}/run
```
Si `ruleCount === 0`, le bouton devient `+ rules` et pointe vers `/processes/{id}` (comme dans la maquette). La colonne `last run` devient cliquable et mène à la dernière exécution du traitement.

**`process-detail.html`** — sous le panneau des règles, un panneau `recent executions` : les 5 dernières exécutions du traitement (`getAll(processId)`), avec statut, date, lien `view`, plus un bouton `use this process` en haut de page. Masqué tant que le traitement n'est pas créé.

### 6.6 Tests frontend attendus (vitest)

- `run.spec.ts` : rejet d'un `.txt` quand `inputFormat = JSON` ; `preview()` appelé une fois au dépôt d'un fichier valide ; bouton `run` désactivé sans fichier ; redirection vers `/executions/:id` après succès ; affichage de la règle fautive sur `status: ERROR`.
- `executions.spec.ts` : rendu de la liste, filtre par traitement, `download` absent sur une exécution `FAILED`.
- `execution-detail.spec.ts` : rendu `SUCCESS` (input/output/règles) et rendu `FAILED` (message d'erreur, pas de bouton download).
- `topbar.spec.ts` : lien `admin` visible uniquement pour un `ADMIN`.
- Vérifier que `processes.spec.ts`, `process-detail.spec.ts`, `admin.spec.ts` passent toujours après l'extraction de la topbar.

---

## 7. Thème et design system

Le thème sombre existant est la référence. **Première action côté CSS** : centraliser les tokens dans `src/styles.css` sous forme de variables, puis remplacer les valeurs en dur dans les CSS existants.

```css
:root {
  --bg:            #0b0d0c;   /* fond de page (avec la grille 28px) */
  --bg-grid:       #101413;
  --surface:       #131313;   /* panneaux */
  --surface-alt:   #1a1a1a;   /* champs, badges neutres */
  --border:        #262626;
  --text:          #e5e5e5;
  --text-dim:      #a3a3a3;
  --muted:         #737373;
  --accent:        #22c55e;   /* vert phosphore, un seul accent */
  --accent-bg:     #14532d;
  --danger:        #ef4444;
  --danger-bg:     #450a0a;
  --font-mono:     'JetBrains Mono', 'Courier New', monospace;
}
```

Règles visuelles :
- Fond de page avec la grille : reprendre le bloc `background-image: linear-gradient(...)` de `processes.css` à l'identique sur les nouveaux écrans.
- Boutons : `.primary` = fond `--accent`, texte `#0d0d0d` ; `.ghost` = transparent, bordure `--border`, texte `--text-dim` ; `.ghost.danger` = bordure `--danger-bg`, texte `--danger`.
- Statuts : `.run-status.success` (`--accent-bg` / `--accent`), `.failed` (`--danger-bg` / `--danger`), `.pending` / `.running` (`--surface-alt` / `--text-dim`).
- Tout en minuscules, monospace partout, **aucune animation** sauf un curseur clignotant discret pendant `running…`, **aucun emoji**, aucune ombre portée.
- Blocs JSON : `<pre>` sur `--surface`, bordure `--border`, `border-radius: 3px`, `font-size: 13px`, `line-height: 1.5`.
- Accessibilité minimale : zone de dépôt focusable au clavier (`tabindex="0"`, `Enter`/`Space` ouvre le sélecteur), `aria-live="polite"` sur le panneau de preview, contraste ≥ 4.5:1 pour le texte courant.

---

## 8. Plan d'exécution

Une étape = un commit. Ne passe à l'étape suivante que si l'étape courante compile et que les tests passent.

| # | Étape | Livrable | Fichiers principaux |
|---|---|---|---|
| 0 | **Reconnaissance** — lire le code cité en §2, confirmer ou corriger l'état décrit. Ne rien coder. | Un résumé des écarts constatés | — |
| 1 | **Refactor `RuleChainRunner`** — extraire l'application de la chaîne de règles, brancher `ExecutionService` dessus. | Tests existants verts, comportement identique | `service/RuleChainRunner.java`, `ExecutionService.java` |
| 2 | **Endpoint preview** + DTO + tests (dont le test « rien n'est persisté » et le test d'équivalence preview/run). | `POST /api/processes/{id}/preview` | `PreviewService`, `PreviewResponse`, `PreviewStep`, `ProcessController` |
| 3 | **Endpoints d'exécution enrichis** — filtre `processId`, `ExecutionDetailResponse`, `/content` + tests. | 3 endpoints prêts pour le front | `ExecutionController`, DTOs, `ExecutionService` |
| 4 | **Socle front** — modèles, `execution.service.ts`, `preview()`, composant `topbar` partagé, routes. | Le front compile, la topbar est unique | `models/`, `services/`, `features/shared/topbar/`, `app.routes.ts` |
| 5 | **Écran `/processes/:id/run`** — dépôt de fichier, preview 4b, bouton `run`. | La fonctionnalité centrale | `features/run/` |
| 6 | **Écrans `/executions` et `/executions/:id`.** | L'historique | `features/executions/`, `features/execution-detail/` |
| 7 | **Intégration** — bouton `use` dans la liste, `recent executions` dans la fiche traitement, `last run` cliquable. | Les trois fonctionnalités se rejoignent | `processes.*`, `process-detail.*` |
| 8 | *(bonus, seulement si 1→7 sont livrées et vertes)* — réutiliser le panneau de preview dans `process-detail` : le fichier échantillon reste en mémoire et le preview se recalcule à chaque modification de règle (le vrai 4b). | Boucle de configuration instantanée | `features/shared/preview-panel/` |
| 9 | **Documentation** — mettre à jour `docs/api-list.md` (3 nouveaux endpoints + le filtre), ajouter le parcours « preview → use → historique » dans `docs/scenario-nominal.md`, mentionner les nouveaux écrans dans le `README.md`. | Doc alignée sur le code | `docs/`, `README.md` |

Si l'étape 8 doit être sacrifiée par manque de temps, ce n'est pas grave : le panneau de preview doit néanmoins être écrit **comme un composant réutilisable** dès l'étape 5, avec ses entrées en `input()`, pour que l'étape 8 se réduise à le poser ailleurs.

---

## 9. Definition of Done

Coche chaque point avant de me rendre la main :

**Fonctionnel**
- [ ] Depuis `/processes`, `use` mène à un écran de run pour le bon traitement.
- [ ] Déposer un `.json` valide affiche le avant/après **sans** créer d'exécution (vérifié en base : `SELECT count(*) FROM DT_executions` inchangé).
- [ ] Un `.xml` valide fonctionne aussi, y compris pour un traitement `XML → JSON`.
- [ ] Un fichier avec un champ manquant affiche la règle fautive et son message, sans erreur 500.
- [ ] Un tableau de plusieurs enregistrements affiche `showing 1 of n records`.
- [ ] `run` crée bien une exécution et redirige vers son détail.
- [ ] Une exécution en échec est visible dans l'historique avec son message d'erreur et sans bouton de téléchargement.
- [ ] `/executions` ne montre **que** les exécutions de l'utilisateur connecté (testé avec deux comptes).
- [ ] `download output` renvoie bien le fichier généré, nom de fichier compris.

**Technique**
- [ ] `mvn test` vert, `npm test` vert, `npm run build` sans erreur.
- [ ] Aucune nouvelle dépendance dans `pom.xml` ni `package.json` (diff à vérifier explicitement).
- [ ] Le preview et le run partagent le même code de transformation (`RuleChainRunner`), prouvé par le test d'équivalence.
- [ ] Aucun `console.log`, aucun `alert()`, aucun `any` non justifié, aucun `*ngIf`.
- [ ] La topbar n'existe qu'en un seul exemplaire.
- [ ] Tous les nouveaux appels HTTP passent `withCredentials: true`.
- [ ] Les 3 nouveaux écrans sont visuellement indiscernables des écrans existants (même fond, même grille, même accent).

**Rendu**
- [ ] Un récapitulatif des choix d'implémentation non triviaux et de ce qui reste ouvert.
- [ ] `docs/api-list.md` à jour.

---

## 10. Ce que j'attends de toi maintenant

1. Fais l'étape 0 et dis-moi ce que tu as trouvé (écarts éventuels avec ce document).
2. Propose ton découpage de commits si tu veux dévier du plan §8, en justifiant.
3. Puis enchaîne les étapes 1 → 9 en t'arrêtant à la fin de chaque étape pour un point court (2 lignes : ce qui est fait, ce qui est vert).
4. Ne me pose une question que si elle est **bloquante** et non tranchée en §4. Sinon, prends la décision la plus proche du code existant et signale-la dans ton récapitulatif.
