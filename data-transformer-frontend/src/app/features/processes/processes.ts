import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { DataProcess } from '../../models/api.models';
import { ProcessService } from '../../services/process.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { USER_NAV_ITEMS } from '../shared/nav-items';

@Component({
  selector: 'app-processes',
  imports: [FormsModule, RouterLink, AppShell],
  templateUrl: './processes.html',
  styleUrl: './processes.css'
})
export class Processes implements OnInit {

  readonly navItems = USER_NAV_ITEMS;

  readonly processes = signal<DataProcess[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly search = signal('');

  constructor(
    private processService: ProcessService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.processService.getAll().subscribe({
      next: (data) => {
        this.processes.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else {
          this.errorMessage.set('impossible de charger les traitements');
        }
      }
    });
  }

  open(process: DataProcess): void {
    this.router.navigate(['/processes', process.id]);
  }

  createNew(): void {
    this.router.navigate(['/processes', 'new']);
  }

  onSearchChange(value: string): void {
    this.search.set(value);
  }

  filteredProcesses(): DataProcess[] {
    const term = this.search().toLowerCase().trim();
    if (!term) {
      return this.processes();
    }
    return this.processes().filter((p) =>
      p.name.toLowerCase().includes(term) || (p.description ?? '').toLowerCase().includes(term)
    );
  }

  delete(process: DataProcess, event: Event): void {
    event.stopPropagation();
    if (!confirm(`supprimer le traitement "${process.name}" ?`)) {
      return;
    }
    this.processService.delete(process.id).subscribe({
      next: () => this.load(),
      error: () => this.errorMessage.set('impossible de supprimer ce traitement'),
    });
  }

}