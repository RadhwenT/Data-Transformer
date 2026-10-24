import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { concatMap } from 'rxjs/operators';
import { forkJoin, of } from 'rxjs';

import { Execution, ProcessDetail as ProcessDetailModel, ProcessRule, ProcessRuleFormData, Rule } from '../../models/api.models';
import { ExecutionService } from '../../services/execution.service';
import { ProcessService } from '../../services/process.service';
import { RuleService } from '../../services/rule.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { USER_NAV_ITEMS } from '../shared/nav-items';

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const RECENT_EXECUTIONS_LIMIT = 5;

/** Miroir de ProcessController.validateFieldsForRuleType (backend) : purement
 * pour piloter l'affichage des champs, le backend reste la seule source de
 * verite pour la validation reelle. */
const RULE_FIELD_REQUIREMENTS: Record<string, { source2: boolean; target: boolean }> = {
  CONCATENATE: { source2: true, target: true },
  RENAME: { source2: false, target: true },
  UPPERCASE: { source2: false, target: false },
  FORMAT_DATE: { source2: false, target: false },
};

/**
 * Place tres au-dela de tout ordre reel le temps de la reconciliation (voir
 * saveAll) : garantit qu'aucune regle existante ne collisionne avec une autre
 * pendant qu'on les deplace vers leurs positions finales.
 */
const TEMP_ORDER_OFFSET = 10000;

/** Une regle du pipeline tel qu'edite localement, avant sauvegarde. */
interface DraftRule {
  /** Cle stable pour le tracking @for : l'id reel si deja persistee, sinon un id negatif genere localement. */
  localId: number;
  /** null tant que la regle n'a jamais ete sauvegardee cote serveur. */
  id: number | null;
  ruleId: number;
  ruleType: string;
  sourceField: string;
  sourceField2: string | null;
  targetField: string | null;
}

@Component({
  selector: 'app-process-detail',
  imports: [FormsModule, RouterLink, AppShell],
  templateUrl: './process-detail.html',
  styleUrl: './process-detail.css',
})
export class ProcessDetail implements OnInit {

  readonly navItems = USER_NAV_ITEMS;

  readonly isNew = signal(true);
  readonly processId = signal<number | null>(null);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly savingAll = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly rulesCatalog = signal<Rule[]>([]);
  readonly savedRules = signal<ProcessRule[]>([]);
  readonly draftRules = signal<DraftRule[]>([]);
  readonly recentExecutions = signal<Execution[]>([]);

  // --- formulaire du traitement ---
  name = '';
  description = '';
  inputFormat = 'JSON';
  outputFormat = 'JSON';

  private savedName = '';
  private savedDescription = '';
  private savedInputFormat = 'JSON';
  private savedOutputFormat = 'JSON';

  // --- formulaire d'ajout/edition de regle (dans le brouillon) ---
  readonly editingLocalId = signal<number | null>(null);
  readonly ruleError = signal<string | null>(null);
  selectedRuleTypeId: number | null = null;
  ruleSourceField = '';
  ruleSourceField2 = '';
  ruleTargetField = '';

  private nextLocalId = -1;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private processService: ProcessService,
    private ruleService: RuleService,
    private executionService: ExecutionService
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isNew.set(false);
      this.processId.set(Number(idParam));
      this.loadAll(Number(idParam));
    } else {
      this.ruleService.getAll().subscribe((catalog) => this.rulesCatalog.set(catalog));
      this.loading.set(false);
    }
  }

  private loadAll(id: number): void {
    this.loading.set(true);
    forkJoin({
      catalog: this.ruleService.getAll(),
      process: this.processService.getById(id),
    }).subscribe({
      next: ({ catalog, process }) => {
        this.rulesCatalog.set(catalog);
        this.applyProcessSnapshot(process);
        this.loading.set(false);
        this.loadRecentExecutions(id);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else if (err.status === 404) {
          this.errorMessage.set('traitement introuvable');
        } else {
          this.errorMessage.set('impossible de charger le traitement');
        }
      },
    });
  }

  private applyProcessSnapshot(process: ProcessDetailModel): void {
    this.name = this.savedName = process.name;
    this.description = this.savedDescription = process.description ?? '';
    this.inputFormat = this.savedInputFormat = process.inputFormat;
    this.outputFormat = this.savedOutputFormat = process.outputFormat;
    this.savedRules.set(process.rules);
    this.draftRules.set(process.rules.map((r) => this.toDraftRule(r)));
    this.resetRuleForm();
  }

  private toDraftRule(rule: ProcessRule): DraftRule {
    const catalogEntry = this.rulesCatalog().find((r) => r.type === rule.ruleType);
    return {
      localId: rule.id,
      id: rule.id,
      ruleId: catalogEntry?.id ?? 0,
      ruleType: rule.ruleType,
      sourceField: rule.sourceField,
      sourceField2: rule.sourceField2,
      targetField: rule.targetField,
    };
  }

  private loadRecentExecutions(processId: number): void {
    this.executionService.getAll(processId).subscribe((executions) => {
      this.recentExecutions.set(executions.slice(0, RECENT_EXECUTIONS_LIMIT));
    });
  }

  formatDate(iso: string): string {
    return DATE_FORMAT.format(new Date(iso));
  }

  goToRun(): void {
    const id = this.processId();
    if (id) {
      this.router.navigate(['/processes', id, 'run']);
    }
  }

  goToExecution(execution: Execution): void {
    this.router.navigate(['/executions', execution.id]);
  }

  // --- creation d'un nouveau traitement (formulaire simple, inchange) ---

  saveProcess(): void {
    if (this.saving() || !this.name.trim()) {
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);

    const data = {
      name: this.name.trim(),
      description: this.description.trim(),
      inputFormat: this.inputFormat,
      outputFormat: this.outputFormat,
    };

    this.processService.create(data).subscribe({
      next: (process) => {
        this.saving.set(false);
        // le traitement existe desormais : on va sur son URL definitive
        // pour debloquer la section regles (elle a besoin d'un processId).
        this.router.navigate(['/processes', process.id], { replaceUrl: true });
      },
      error: (err) => {
        this.saving.set(false);
        if (err.status === 409) {
          this.errorMessage.set('un traitement porte deja ce nom');
        } else if (err.status === 400) {
          this.errorMessage.set('donnees invalides');
        } else {
          this.errorMessage.set("l'enregistrement a echoue");
        }
      },
    });
  }

  deleteProcess(): void {
    const id = this.processId();
    if (!id || !confirm(`supprimer le traitement "${this.name}" ?`)) {
      return;
    }
    this.processService.delete(id).subscribe({
      next: () => this.router.navigate(['/processes']),
      error: () => this.errorMessage.set('impossible de supprimer ce traitement'),
    });
  }

  // --- brouillon de regles (ecran d'edition) ---

  ruleTypeOf(ruleId: number | null): string | null {
    return this.rulesCatalog().find((r) => r.id === ruleId)?.type ?? null;
  }

  needsSourceField2(): boolean {
    const type = this.ruleTypeOf(this.selectedRuleTypeId);
    return type ? RULE_FIELD_REQUIREMENTS[type].source2 : false;
  }

  needsTargetField(): boolean {
    const type = this.ruleTypeOf(this.selectedRuleTypeId);
    return type ? RULE_FIELD_REQUIREMENTS[type].target : false;
  }

  selectRuleType(ruleId: number): void {
    this.selectedRuleTypeId = ruleId;
    if (!this.needsSourceField2()) {
      this.ruleSourceField2 = '';
    }
    if (!this.needsTargetField()) {
      this.ruleTargetField = '';
    }
  }

  private resetRuleForm(): void {
    this.editingLocalId.set(null);
    this.selectedRuleTypeId = this.rulesCatalog()[0]?.id ?? null;
    this.ruleSourceField = '';
    this.ruleSourceField2 = '';
    this.ruleTargetField = '';
    this.ruleError.set(null);
  }

  submitRuleForm(): void {
    if (this.selectedRuleTypeId == null || !this.ruleSourceField.trim()) {
      this.ruleError.set('le champ source est obligatoire');
      return;
    }

    const type = this.ruleTypeOf(this.selectedRuleTypeId)!;
    const editingId = this.editingLocalId();
    const existing = editingId !== null ? this.draftRules().find((d) => d.localId === editingId) : undefined;

    const entry: DraftRule = {
      localId: existing ? existing.localId : this.nextLocalId--,
      id: existing ? existing.id : null,
      ruleId: this.selectedRuleTypeId,
      ruleType: type,
      sourceField: this.ruleSourceField.trim(),
      sourceField2: this.needsSourceField2() ? (this.ruleSourceField2.trim() || null) : null,
      targetField: this.needsTargetField() ? (this.ruleTargetField.trim() || null) : null,
    };

    if (existing) {
      this.draftRules.update((list) => list.map((d) => (d.localId === existing.localId ? entry : d)));
    } else {
      this.draftRules.update((list) => [...list, entry]);
    }
    this.resetRuleForm();
  }

  editDraftRule(rule: DraftRule): void {
    this.editingLocalId.set(rule.localId);
    this.selectedRuleTypeId = rule.ruleId;
    this.ruleSourceField = rule.sourceField;
    this.ruleSourceField2 = rule.sourceField2 ?? '';
    this.ruleTargetField = rule.targetField ?? '';
    this.ruleError.set(null);
  }

  removeDraftRule(rule: DraftRule): void {
    this.draftRules.update((list) => list.filter((d) => d.localId !== rule.localId));
    if (this.editingLocalId() === rule.localId) {
      this.resetRuleForm();
    }
  }

  moveRuleUp(index: number): void {
    if (index <= 0) {
      return;
    }
    this.draftRules.update((list) => {
      const copy = [...list];
      [copy[index - 1], copy[index]] = [copy[index], copy[index - 1]];
      return copy;
    });
  }

  moveRuleDown(index: number): void {
    this.draftRules.update((list) => {
      if (index >= list.length - 1) {
        return list;
      }
      const copy = [...list];
      [copy[index], copy[index + 1]] = [copy[index + 1], copy[index]];
      return copy;
    });
  }

  /** Rejette toutes les modifications non sauvegardees (champs du traitement + brouillon de regles). */
  cancelAll(): void {
    this.name = this.savedName;
    this.description = this.savedDescription;
    this.inputFormat = this.savedInputFormat;
    this.outputFormat = this.savedOutputFormat;
    this.draftRules.set(this.savedRules().map((r) => this.toDraftRule(r)));
    this.resetRuleForm();
    this.errorMessage.set(null);
  }

  /**
   * Persiste le traitement + reconcilie le brouillon de regles avec le
   * backend en 3 temps, pour ne jamais violer la contrainte d'unicite
   * (processId, executionOrder) le temps de la reorganisation :
   *   1. supprime les regles retirees du brouillon (libere leurs positions)
   *   2. deplace les regles existantes vers des positions temporaires tres
   *      hautes (libere entierement la plage 1..N)
   *   3. cree/renumerote toutes les regles du brouillon dans l'ordre final
   */
  saveAll(): void {
    if (this.savingAll() || !this.name.trim()) {
      return;
    }
    const processId = this.processId();
    if (!processId) {
      return;
    }

    this.savingAll.set(true);
    this.errorMessage.set(null);

    const processData = {
      name: this.name.trim(),
      description: this.description.trim(),
      inputFormat: this.inputFormat,
      outputFormat: this.outputFormat,
    };

    const draft = this.draftRules();
    const removedIds = this.savedRules()
      .filter((saved) => !draft.some((d) => d.id === saved.id))
      .map((saved) => saved.id);
    const survivors = draft.filter((d): d is DraftRule & { id: number } => d.id !== null);

    this.processService.update(processId, processData).pipe(
      concatMap(() => removedIds.length
        ? forkJoin(removedIds.map((id) => this.processService.deleteRule(processId, id)))
        : of(null)),
      concatMap(() => survivors.length
        ? forkJoin(survivors.map((d, i) => this.processService.updateRule(
            processId, d.id, this.toPayload(d, TEMP_ORDER_OFFSET + i))))
        : of(null)),
      concatMap(() => {
        const calls = draft.map((d, index) => d.id !== null
          ? this.processService.updateRule(processId, d.id, this.toPayload(d, index + 1))
          : this.processService.addRule(processId, this.toPayload(d, index + 1)));
        return calls.length ? forkJoin(calls) : of(null);
      })
    ).subscribe({
      next: () => {
        this.savingAll.set(false);
        this.loadAll(processId);
      },
      error: (err) => {
        this.savingAll.set(false);
        if (err.status === 409) {
          this.errorMessage.set('un traitement porte deja ce nom');
        } else {
          this.errorMessage.set("l'enregistrement a echoue — rechargez la page pour verifier l'etat des regles");
        }
      },
    });
  }

  private toPayload(d: DraftRule, executionOrder: number): ProcessRuleFormData {
    return {
      ruleId: d.ruleId,
      executionOrder,
      sourceField: d.sourceField,
      sourceField2: d.sourceField2,
      targetField: d.targetField,
    };
  }
}
