import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../../services/auth.service';
import { ThemeService } from '../../../services/theme.service';

export interface SidebarNavItem {
  key: string;
  label: string;
  path: string;
  /** Libelle utilise dans la palette de commandes, ex. "aller au dashboard". */
  paletteLabel: string;
}

@Component({
  selector: 'app-sidebar',
  imports: [],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css'
})
export class Sidebar {

  @Input({ required: true }) items!: SidebarNavItem[];
  @Input({ required: true }) active!: string;

  constructor(
    private authService: AuthService,
    private router: Router,
    private themeService: ThemeService
  ) {}

  theme() {
    return this.themeService.theme();
  }

  themeLabel(): string {
    return this.theme() === 'dark' ? 'terminal (sombre)' : 'terminal (clair)';
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  goTo(path: string): void {
    this.router.navigate([path]);
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => {
        this.authService.clearSession();
        this.router.navigate(['/login']);
      }
    });
  }
}
