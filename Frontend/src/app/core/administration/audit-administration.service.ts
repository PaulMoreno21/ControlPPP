import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditLogEntry } from './audit-log.models';
import { PageResult } from '../../shared/pagination/page-result';

@Injectable({ providedIn: 'root' })
export class AuditAdministrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/admin/audit-logs`;

  listPage(from: string, to: string, page: number, size: number): Observable<PageResult<AuditLogEntry>> {
    const params = new HttpParams().set('from', from).set('to', to).set('page', page).set('size', size);
    return this.http.get<PageResult<AuditLogEntry>>(`${this.baseUrl}/page`, { params });
  }
}
