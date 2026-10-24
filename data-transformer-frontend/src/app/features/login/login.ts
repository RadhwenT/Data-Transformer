import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {

  email = '';
  password = '';

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.email, this.password).subscribe({
           next: (user) => {
        this.loading.set(false);
        this.router.navigate([user.role === 'ADMIN' ? '/admin/dashboard' : '/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 401) {
          this.errorMessage.set('email ou mot de passe incorrect');
        } else if (err.status === 400) {
          this.errorMessage.set('email invalide');
        } else if (err.status === 0) {
          this.errorMessage.set('serveur injoignable');
        } else {
          this.errorMessage.set('erreur inattendue');
        }
      }
    });
  }
}