import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { DataProcess, Execution } from '../models/api.models';
import { API_URL } from './api.config';

export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  active: boolean;
}

export interface CreatedUser extends AdminUser {
  generatedPassword: string;
  avertissement: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {

  constructor(private http: HttpClient) {}

  listUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${API_URL}/admin/users`, { withCredentials: true });
  }

  createUser(firstName: string, lastName: string, email: string, role: string): Observable<CreatedUser> {
    return this.http.post<CreatedUser>(
      `${API_URL}/admin/users`,
      { firstName, lastName, email, role },
      { withCredentials: true }
    );
  }

  deactivateUser(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/admin/users/${id}`, { withCredentials: true });
  }

    activateUser(id: number): Observable<void> {
    return this.http.put<void>(
      `${API_URL}/admin/users/${id}/activate`,
      {},
      { withCredentials: true }
    );
  }

  /** Vue admin agregee : tous les traitements actifs, toutes utilisateurs confondus. */
  listAllProcesses(): Observable<DataProcess[]> {
    return this.http.get<DataProcess[]>(`${API_URL}/admin/processes`, { withCredentials: true });
  }

  /** Vue admin agregee : toutes les executions, toutes utilisateurs confondus. */
  listAllExecutions(): Observable<Execution[]> {
    return this.http.get<Execution[]>(`${API_URL}/admin/executions`, { withCredentials: true });
  }
}