import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { DataProcess, Execution } from '../../models/api.models';
import { AdminService, AdminUser } from '../../services/admin.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { ADMIN_NAV_ITEMS } from '../shared/nav-items';

export interface UserStats {
  user: AdminUser;
  procCount: number;
  execCount: number;
  successRate: string;
}

const SPARKLINE_ACCENT = [4, 6, 5, 7];
const SPARKLINE_EXEC = [2, 3, 5, 4];
const SPARKLINE_SUCCESS = [8, 8, 7, 9];
const SPARKLINE_USERS = [3, 3, 3, 3];

@Component({
  selector: 'app-admin-dashboard',
  imports: [FormsModule, AppShell],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css'
})
export class AdminDashboard implements OnInit {

  readonly navItems = ADMIN_NAV_ITEMS;

  readonly users = signal<AdminUser[]>([]);
  readonly processes = signal<DataProcess[]>([]);
  readonly executions = signal<Execution[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly userFilter = signal('all');

  readonly sparklineProcesses = SPARKLINE_ACCENT;
  readonly sparklineExecutions = SPARKLINE_EXEC;
  readonly sparklineSuccess = SPARKLINE_SUCCESS;
  readonly sparklineUsers = SPARKLINE_USERS;

  readonly selectedUserId = computed<number | null>(() => {
    const value = this.userFilter();
    return value === 'all' ? null : Number(value);
  });

  readonly isAllUsers = computed(() => this.selectedUserId() === null);

  readonly scopedProcesses = computed<DataProcess[]>(() => {
    const id = this.selectedUserId();
    return id === null ? this.processes() : this.processes().filter((p) => p.ownerId === id);
  });

  readonly scopedExecutions = computed<Execution[]>(() => {
    const id = this.selectedUserId();
    return id === null ? this.executions() : this.executions().filter((e) => e.ownerId === id);
  });

  readonly kpiProcessCount = computed(() => this.scopedProcesses().length);
  readonly kpiExecutionCount = computed(() => this.scopedExecutions().length);

  readonly kpiSuccessRate = computed(() => {
    const execs = this.scopedExecutions();
    if (execs.length === 0) {
      return '—';
    }
    const successCount = execs.filter((e) => e.status === 'SUCCESS').length;
    return Math.round((successCount / execs.length) * 100) + '%';
  });

  readonly kpiActiveUserCount = computed(() => {
    const id = this.selectedUserId();
    return id !== null ? '1' : String(this.users().filter((u) => u.active).length);
  });

  readonly usersWithStats = computed<UserStats[]>(() =>
    this.users().map((user) => {
      const procCount = this.processes().filter((p) => p.ownerId === user.id).length;
      const userExecs = this.executions().filter((e) => e.ownerId === user.id);
      const successCount = userExecs.filter((e) => e.status === 'SUCCESS').length;
      return {
        user,
        procCount,
        execCount: userExecs.length,
        successRate: userExecs.length ? Math.round((successCount / userExecs.length) * 100) + '%' : '—',
      };
    })
  );

  constructor(
    private adminService: AdminService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      users: this.adminService.listUsers(),
      processes: this.adminService.listAllProcesses(),
      executions: this.adminService.listAllExecutions(),
    }).subscribe({
      next: ({ users, processes, executions }) => {
        this.users.set(users);
        this.processes.set(processes);
        this.executions.set(executions);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else if (err.status === 403) {
          this.errorMessage.set('acces reserve aux administrateurs');
        } else {
          this.errorMessage.set('impossible de charger le dashboard');
        }
      },
    });
  }

  onFilterChange(value: string): void {
    this.userFilter.set(value);
  }

  formatDate(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
      + ' ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

}
