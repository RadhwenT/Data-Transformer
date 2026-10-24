import { Component, OnInit, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { DataProcess, Execution } from '../../models/api.models';
import { ExecutionService } from '../../services/execution.service';
import { ProcessService } from '../../services/process.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { USER_NAV_ITEMS } from '../shared/nav-items';

const RECENT_EXECUTIONS_LIMIT = 5;

// Purement decoratif (comme les KPI du dashboard admin) : donnees mockees en
// attendant un vrai historique agrege cote backend.
const SPARKLINE_PROCESSES = [4, 6, 5, 7, 6, 8, 7, 9];
const SPARKLINE_EXECUTIONS = [2, 3, 5, 4, 6, 3, 4, 5];
const SPARKLINE_SUCCESS = [8, 8, 7, 9, 6, 8, 9, 9];
const SPARKLINE_DURATION = [5, 4, 6, 5, 4, 3, 4, 4];

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

@Component({
  selector: 'app-dashboard',
  imports: [AppShell],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {

  readonly navItems = USER_NAV_ITEMS;

  readonly processes = signal<DataProcess[]>([]);
  readonly executions = signal<Execution[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly sparklineProcesses = SPARKLINE_PROCESSES;
  readonly sparklineExecutions = SPARKLINE_EXECUTIONS;
  readonly sparklineSuccess = SPARKLINE_SUCCESS;
  readonly sparklineDuration = SPARKLINE_DURATION;

  readonly activeProcessCount = computed(() => this.processes().length);

  readonly executions24h = computed(() => {
    const cutoff = Date.now() - ONE_DAY_MS;
    return this.executions().filter((e) => new Date(e.startedAt).getTime() >= cutoff).length;
  });

  readonly successRate = computed(() => {
    const execs = this.executions();
    if (execs.length === 0) {
      return '—';
    }
    const successCount = execs.filter((e) => e.status === 'SUCCESS').length;
    return Math.round((successCount / execs.length) * 100) + '%';
  });

  readonly avgDuration = computed(() => {
    const durations = this.executions()
      .filter((e) => e.finishedAt)
      .map((e) => (new Date(e.finishedAt!).getTime() - new Date(e.startedAt).getTime()) / 1000);
    if (durations.length === 0) {
      return '—';
    }
    const avg = durations.reduce((a, b) => a + b, 0) / durations.length;
    return avg.toFixed(2) + 's';
  });

  readonly recentExecutions = computed(() => this.executions().slice(0, RECENT_EXECUTIONS_LIMIT));

  constructor(
    private processService: ProcessService,
    private executionService: ExecutionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      processes: this.processService.getAll(),
      executions: this.executionService.getAll(),
    }).subscribe({
      next: ({ processes, executions }) => {
        this.processes.set(processes);
        this.executions.set(executions);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else {
          this.errorMessage.set('impossible de charger le dashboard');
        }
      },
    });
  }

  formatDate(iso: string): string {
    return DATE_FORMAT.format(new Date(iso));
  }

  open(execution: Execution): void {
    this.router.navigate(['/executions', execution.id]);
  }

  goNewProcess(): void {
    this.router.navigate(['/processes', 'new']);
  }

  goRunProcess(): void {
    this.router.navigate(['/processes']);
  }

  goExecutions(): void {
    this.router.navigate(['/executions']);
  }
}
