import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Renvoie vers /login si personne n'est connecte (indice local ; le backend reste la vraie barriere). */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.isLoggedIn() ? true : router.parseUrl('/login');
};

/** A utiliser apres authGuard : renvoie un utilisateur non-admin vers /processes plutot que /login. */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.currentUser()?.role === 'ADMIN' ? true : router.parseUrl('/processes');
};

/**
 * A utiliser apres authGuard sur les ecrans processes/executions/run : un admin
 * n'a plus vocation a executer de traitements, on le renvoie vers son dashboard.
 */
export const userOnlyGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.currentUser()?.role !== 'ADMIN' ? true : router.parseUrl('/admin/dashboard');
};
