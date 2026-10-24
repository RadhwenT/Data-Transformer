import { Component, HostListener, computed, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../../services/auth.service';
import { ThemeService } from '../../../services/theme.service';
import { CommandPalette, PaletteCommand } from '../command-palette/command-palette';
import { Sidebar, SidebarNavItem } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  imports: [Sidebar, CommandPalette],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css'
})
export class AppShell {

  readonly navItems = input.required<SidebarNavItem[]>();
  readonly active = input.required<string>();
  readonly title = input.required<string>();
  /** Commandes propres a l'ecran (ex. filtres) : affichees en tete de la palette, avant nav/theme. */
  readonly extraCommands = input<PaletteCommand[]>([]);

  readonly paletteOpen = signal(false);
  readonly paletteQuery = signal('');

  readonly commands = computed<PaletteCommand[]>(() => [
    ...this.extraCommands(),
    ...this.navItems().map((item) => ({
      label: item.paletteLabel,
      hint: 'nav',
      run: () => this.router.navigate([item.path]),
    })),
    { label: 'activer le theme clair', hint: 'action', run: () => this.themeService.set('light') },
    { label: 'activer le theme sombre', hint: 'action', run: () => this.themeService.set('dark') },
  ]);

  constructor(
    private authService: AuthService,
    private router: Router,
    private themeService: ThemeService
  ) {}

  userEmail(): string {
    return this.authService.currentUser()?.email ?? '';
  }

  openPalette(): void {
    this.paletteQuery.set('');
    this.paletteOpen.set(true);
  }

  closePalette(): void {
    this.paletteOpen.set(false);
  }

  onPaletteQueryChange(value: string): void {
    this.paletteQuery.set(value);
  }

  runCommand(cmd: PaletteCommand): void {
    cmd.run();
    this.closePalette();
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      const willOpen = !this.paletteOpen();
      this.paletteOpen.set(willOpen);
      if (willOpen) {
        this.paletteQuery.set('');
      }
    } else if (e.key === 'Escape' && this.paletteOpen()) {
      this.closePalette();
    }
  }
}
