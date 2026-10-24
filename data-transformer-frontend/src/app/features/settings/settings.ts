import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../services/auth.service';
import { AppShell } from '../shared/app-shell/app-shell';
import { SidebarNavItem } from '../shared/sidebar/sidebar';
import { ADMIN_NAV_ITEMS, USER_NAV_ITEMS } from '../shared/nav-items';

@Component({
  selector: 'app-settings',
  imports: [FormsModule, AppShell],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class Settings {

  readonly navItems: SidebarNavItem[];

  firstName = '';
  lastName = '';
  email = '';
  readonly savingProfile = signal(false);
  readonly profileError = signal<string | null>(null);
  readonly profileSuccess = signal(false);

  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  readonly changingPassword = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly passwordSuccess = signal(false);

  constructor(private authService: AuthService) {
    const user = this.authService.currentUser();
    this.navItems = user?.role === 'ADMIN' ? ADMIN_NAV_ITEMS : USER_NAV_ITEMS;

    this.firstName = user?.firstName ?? '';
    this.lastName = user?.lastName ?? '';
    this.email = user?.email ?? '';
  }

  saveProfile(): void {
    if (this.savingProfile() || !this.firstName.trim() || !this.lastName.trim() || !this.email.trim()) {
      return;
    }
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.profileSuccess.set(false);

    this.authService.updateProfile(this.firstName.trim(), this.lastName.trim(), this.email.trim()).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.profileSuccess.set(true);
      },
      error: (err) => {
        this.savingProfile.set(false);
        if (err.status === 409) {
          this.profileError.set('cet email est deja utilise');
        } else if (err.status === 400) {
          this.profileError.set('donnees invalides');
        } else {
          this.profileError.set("l'enregistrement a echoue");
        }
      },
    });
  }

  changePassword(): void {
    if (this.changingPassword()) {
      return;
    }
    this.passwordError.set(null);
    this.passwordSuccess.set(false);

    if (this.newPassword !== this.confirmPassword) {
      this.passwordError.set('les mots de passe ne correspondent pas');
      return;
    }

    this.changingPassword.set(true);

    this.authService.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.changingPassword.set(false);
        this.passwordSuccess.set(true);
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
      },
      error: (err) => {
        this.changingPassword.set(false);
        if (err.status === 401) {
          this.passwordError.set('mot de passe actuel incorrect');
        } else if (err.status === 400) {
          this.passwordError.set('8 caracteres minimum, au moins un chiffre');
        } else {
          this.passwordError.set('le changement de mot de passe a echoue');
        }
      },
    });
  }
}
