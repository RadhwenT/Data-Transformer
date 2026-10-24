import { Component, OnInit, ViewChild, computed, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { DataProcess, Execution } from '../../models/api.models';
import { ExecutionService } from '../../services/execution.service';
import { ProcessService } from '../../services/process.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { PaletteCommand } from '../shared/command-palette/command-palette';
import { USER_NAV_ITEMS } from '../shared/nav-items';

const STATUS_OPTIONS = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED'] as const;

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

@Component({
  selector: 'app-executions',
  imports: [RouterLink, AppShell],
  templateUrl: './executions.html',
  styleUrl: './executions.css',
})
export class Executions implements OnInit {

  @ViewChild(AppShell) shell!: AppShell;

  readonly navItems = USER_NAV_ITEMS;

  readonly executions = signal<Execution[]>([]);
  readonly processes = signal<DataProcess[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  processFilter = '';
  statusFilter = '';

  /** Filtres exposes dans la palette de commandes (bouton "filtres avances" du bandeau). */
  readonly filterCommands = computed<PaletteCommand[]>(() => [
    { label: 'tous les processus', hint: 'filtre', run: () => { this.processFilter = ''; } },
    ...this.processes().map((p) => ({
      label: `process: ${p.name}`,
      hint: 'filtre',
      run: () => { this.processFilter = p.name; },
    })),
    { label: 'tous les statuts', hint: 'filtre', run: () => { this.statusFilter = ''; } },
    ...STATUS_OPTIONS.map((s) => ({
      label: `statut: ${s.toLowerCase()}`,
      hint: 'filtre',
      run: () => { this.statusFilter = s; },
    })),
  ]);

  constructor(
    private executionService: ExecutionService,
    private processService: ProcessService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.processService.getAll().subscribe((data) => this.processes.set(data));

    this.executionService.getAll().subscribe({
      next: (data) => {
        this.executions.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else {
          this.errorMessage.set('impossible de charger les executions');
        }
      },
    });
  }

  filteredExecutions(): Execution[] {
    return this.executions().filter((e) =>
      (!this.processFilter || e.processName === this.processFilter) &&
      (!this.statusFilter || e.status === this.statusFilter)
    );
  }

  formatDate(iso: string): string {
    return DATE_FORMAT.format(new Date(iso));
  }

  formatDuration(execution: Execution): string {
    if (!execution.finishedAt) {
      return '—';
    }
    const seconds = (new Date(execution.finishedAt).getTime() - new Date(execution.startedAt).getTime()) / 1000;
    return `${seconds.toFixed(1)}s`;
  }

  open(execution: Execution): void {
    this.router.navigate(['/executions', execution.id]);
  }

  openFilters(): void {
    this.shell.openPalette();
  }
}
