import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AssignmentRecord, AssignmentRequest } from './assignment.models';
import { PageResult } from '../../shared/pagination/page-result';

@Injectable({ providedIn: 'root' })
export class AssignmentAdministrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/admin/laboratories`;

  list(laboratoryId: number): Observable<AssignmentRecord[]> {
    return this.http.get<AssignmentRecord[]>(`${this.baseUrl}/${laboratoryId}/assignments`);
  }

  listPage(laboratoryId: number, page: number, size: number, search: string): Observable<PageResult<AssignmentRecord>> {
    return this.http.get<PageResult<AssignmentRecord>>(`${this.baseUrl}/${laboratoryId}/assignments/page`, {
      params: { page, size, search },
    });
  }

  assignTutor(laboratoryId: number, request: AssignmentRequest): Observable<AssignmentRecord> {
    return this.http.post<AssignmentRecord>(`${this.baseUrl}/${laboratoryId}/assignments`, request);
  }

  unlinkTutor(laboratoryId: number, assignmentId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${laboratoryId}/assignments/${assignmentId}`);
  }
}
