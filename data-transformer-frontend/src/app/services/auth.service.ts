import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { User } from '../models/api.models';
import { API_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly _currentUser = signal<User | null>(null);
  readonly currentUser = this._currentUser.asReadonly();

  constructor(private http: HttpClient) {
    const stored = sessionStorage.getItem('currentUser');
    if (stored) {
      this._currentUser.set(JSON.parse(stored));
    }
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<User>(`${API_URL}/auth/login`, { email, password }, { withCredentials: true })
      .pipe(tap(user => {
        this._currentUser.set(user);
        sessionStorage.setItem('currentUser', JSON.stringify(user));
      }));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${API_URL}/auth/logout`, {}, { withCredentials: true })
      .pipe(tap(() => this.clearSession()));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(
      `${API_URL}/auth/change-password`,
      { currentPassword, newPassword },
      { withCredentials: true }
    );
  }

  updateProfile(firstName: string, lastName: string, email: string): Observable<User> {
    return this.http
      .put<User>(`${API_URL}/auth/me`, { firstName, lastName, email }, { withCredentials: true })
      .pipe(tap(user => {
        this._currentUser.set(user);
        sessionStorage.setItem('currentUser', JSON.stringify(user));
      }));
  }

  isLoggedIn(): boolean {
    return this._currentUser() !== null;
  }

  clearSession(): void {
    this._currentUser.set(null);
    sessionStorage.removeItem('currentUser');
  }
}