import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ExecutionContent, ExecutionDetail as ExecutionDetailModel, ProcessRule } from '../../models/api.models';
import { ExecutionService } from '../../services/execution.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { USER_NAV_ITEMS } from '../shared/nav-items';

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

@Component({
  selector: 'app-execution-detail',
  imports: [RouterLink, AppShell],
  templateUrl: './execution-detail.html',
  styleUrl: './execution-detail.css',
})
export class ExecutionDetail implements OnInit {

  readonly navItems = USER_NAV_ITEMS;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly execution = signal<ExecutionDetailModel | null>(null);

  readonly contentLoading = signal(true);
  readonly content = signal<ExecutionContent | null>(null);

  private executionId!: number;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private executionService: ExecutionService
  ) {}

  ngOnInit(): void {
    this.executionId = Number(this.route.snapshot.paramMap.get('id'));

    this.executionService.getById(this.executionId).subscribe({
      next: (execution) => {
        this.execution.set(execution);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else {
          this.errorMessage.set('execution introuvable');
        }
      },
    });

    this.executionService.getContent(this.executionId).subscribe({
      next: (content) => {
        this.content.set(content);
        this.contentLoading.set(false);
      },
      error: () => this.contentLoading.set(false),
    });
  }

  formatDate(iso: string | null): string {
    return iso ? DATE_FORMAT.format(new Date(iso)) : '—';
  }

  formatDuration(ms: number | null): string {
    return ms !== null ? `${(ms / 1000).toFixed(1)}s` : '—';
  }

  ruleLabel(rule: ProcessRule): string {
    switch (rule.ruleType) {
      case 'CONCATENATE':
        return `${rule.sourceField} + ${rule.sourceField2} → ${rule.targetField}`;
      case 'RENAME':
        return `${rule.sourceField} → ${rule.targetField}`;
      case 'FORMAT_DATE':
        return `${rule.sourceField} · dd/MM/yyyy → yyyy-MM-dd`;
      default:
        return rule.sourceField;
    }
  }

  /** Best-effort : le message d'erreur cite generalement le nom du champ en cause. */
  isLikelyFailedRule(rule: ProcessRule): boolean {
    const message = this.execution()?.errorMessage;
    if (!message) {
      return false;
    }
    return [rule.sourceField, rule.sourceField2, rule.targetField]
      .filter((field): field is string => !!field)
      .some((field) => message.includes(field));
  }

  downloadOutput(): void {
    this.executionService.downloadOutput(this.executionId).subscribe((blob) => {
      this.triggerDownload(blob, `execution-${this.executionId}-output`);
    });
  }

  downloadInput(): void {
    this.executionService.downloadInput(this.executionId).subscribe((blob) => {
      this.triggerDownload(blob, `execution-${this.executionId}-input`);
    });
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
  }
}
