import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AdminService, AdminUser, CreatedUser } from '../../services/admin.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { ADMIN_NAV_ITEMS } from '../shared/nav-items';

@Component({
  selector: 'app-admin',
  imports: [FormsModule, AppShell],
  templateUrl: './admin.html',
  styleUrl: './admin.css'
})
export class Admin implements OnInit {

  readonly navItems = ADMIN_NAV_ITEMS;

  readonly users = signal<AdminUser[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly showForm = signal(false);
  readonly creating = signal(false);
  readonly createdUser = signal<CreatedUser | null>(null);
  readonly search = signal('');
  readonly copyFeedback = signal<'idle' | 'copied' | 'failed'>('idle');

  searchTerm = '';
  firstName = '';
  lastName = '';
  email = '';
  role = 'USER';

  constructor(
    private adminService: AdminService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.adminService.listUsers().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.router.navigate(['/login']);
        } else if (err.status === 403) {
          this.errorMessage.set('acces reserve aux administrateurs');
        } else {
          this.errorMessage.set('impossible de charger les utilisateurs');
        }
      }
    });
  }

  openForm(): void {
    this.firstName = '';
    this.lastName = '';
    this.email = '';
    this.role = 'USER';
    this.createdUser.set(null);
    this.errorMessage.set(null);
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.createdUser.set(null);
  }

  onSubmit(): void {
    if (this.creating()) {
      return;
    }

    this.creating.set(true);
    this.errorMessage.set(null);

    this.adminService.createUser(this.firstName, this.lastName, this.email, this.role).subscribe({
      next: (created) => {
        this.creating.set(false);
        this.createdUser.set(created);
        this.load();
      },
      error: (err) => {
        this.creating.set(false);
        if (err.status === 409) {
          this.errorMessage.set('cet email est deja utilise');
        } else if (err.status === 400) {
          this.errorMessage.set('donnees invalides');
        } else {
          this.errorMessage.set('erreur lors de la creation');
        }
      }
    });
  }

  deactivate(user: AdminUser): void {
    if (!confirm(`desactiver le compte de ${user.email} ?`)) {
      return;
    }

    this.adminService.deactivateUser(user.id).subscribe({
      next: () => this.load(),
      error: (err) => {
        if (err.status === 400) {
          this.errorMessage.set('vous ne pouvez pas desactiver votre propre compte');
        } else {
          this.errorMessage.set('impossible de desactiver ce compte');
        }
      }
    });
  }

  copyPassword(): void {
    const password = this.createdUser()?.generatedPassword;
    if (!password) {
      return;
    }

    navigator.clipboard.writeText(password).then(
      () => {
        this.copyFeedback.set('copied');
        setTimeout(() => this.copyFeedback.set('idle'), 2000);
      },
      () => this.copyFeedback.set('failed')
    );
  }

  filteredUsers(): AdminUser[] {
    const term = this.search().toLowerCase().trim();
    if (!term) {
      return this.users();
    }
    return this.users().filter(u =>
      u.email.toLowerCase().includes(term) ||
      u.firstName.toLowerCase().includes(term) ||
      u.lastName.toLowerCase().includes(term)
    );
  }

  onSearchChange(value: string): void {
    this.search.set(value);
  }

  activate(user: AdminUser): void {
    this.adminService.activateUser(user.id).subscribe({
      next: () => this.load(),
      error: () => this.errorMessage.set('impossible de reactiver ce compte')
    });
  }

}