import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Execution, ExecutionContent, ExecutionDetail } from '../models/api.models';
import { API_URL } from './api.config';

@Injectable({ providedIn: 'root' })
export class ExecutionService {

  constructor(private http: HttpClient) {}

  run(processId: number, file: File): Observable<Execution> {
    const formData = new FormData();
    formData.append('processId', processId.toString());
    formData.append('file', file);
    return this.http.post<Execution>(`${API_URL}/executions`, formData, { withCredentials: true });
  }

  getAll(processId?: number): Observable<Execution[]> {
    const params: Record<string, string> = {};
    if (processId != null) {
      params['processId'] = processId.toString();
    }
    return this.http.get<Execution[]>(`${API_URL}/executions`, { params, withCredentials: true });
  }

  getById(id: number): Observable<ExecutionDetail> {
    return this.http.get<ExecutionDetail>(`${API_URL}/executions/${id}`, { withCredentials: true });
  }

  getContent(id: number): Observable<ExecutionContent> {
    return this.http.get<ExecutionContent>(`${API_URL}/executions/${id}/content`, { withCredentials: true });
  }

  downloadOutput(id: number): Observable<Blob> {
    return this.http.get(`${API_URL}/executions/${id}/download`, { withCredentials: true, responseType: 'blob' });
  }

  downloadInput(id: number): Observable<Blob> {
    return this.http.get(`${API_URL}/executions/${id}/download-input`, { withCredentials: true, responseType: 'blob' });
  }
}
