import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Rule } from '../models/api.models';
import { API_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class RuleService {

  constructor(private http: HttpClient) {}

  getAll(): Observable<Rule[]> {
    return this.http.get<Rule[]>(`${API_URL}/rules`, { withCredentials: true });
  }
}
