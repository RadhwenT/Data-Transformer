import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { DataProcess, PreviewResponse, ProcessDetail, ProcessFormData, ProcessRule, ProcessRuleFormData } from '../models/api.models';
import { API_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ProcessService {

  constructor(private http: HttpClient) {}

  getAll(): Observable<DataProcess[]> {
    return this.http.get<DataProcess[]>(`${API_URL}/processes`, { withCredentials: true });
  }

  getById(id: number): Observable<ProcessDetail> {
    return this.http.get<ProcessDetail>(`${API_URL}/processes/${id}`, { withCredentials: true });
  }

  create(data: ProcessFormData): Observable<DataProcess> {
    return this.http.post<DataProcess>(`${API_URL}/processes`, data, { withCredentials: true });
  }

  update(id: number, data: ProcessFormData): Observable<DataProcess> {
    return this.http.put<DataProcess>(`${API_URL}/processes/${id}`, data, { withCredentials: true });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/processes/${id}`, { withCredentials: true });
  }

  addRule(processId: number, data: ProcessRuleFormData): Observable<ProcessRule> {
    return this.http.post<ProcessRule>(`${API_URL}/processes/${processId}/rules`, data, { withCredentials: true });
  }

  updateRule(processId: number, ruleId: number, data: ProcessRuleFormData): Observable<ProcessRule> {
    return this.http.put<ProcessRule>(
      `${API_URL}/processes/${processId}/rules/${ruleId}`,
      data,
      { withCredentials: true }
    );
  }

  deleteRule(processId: number, ruleId: number): Observable<void> {
    return this.http.delete<void>(`${API_URL}/processes/${processId}/rules/${ruleId}`, { withCredentials: true });
  }

  preview(id: number, file: File): Observable<PreviewResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PreviewResponse>(`${API_URL}/processes/${id}/preview`, formData, { withCredentials: true });
  }
}
