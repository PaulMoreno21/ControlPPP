import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PracticeReport, ReportScope } from './practice-report.models';

@Injectable({ providedIn: 'root' })
export class PracticeReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/reports/practice`;

  getScope(laboratoryId: number | null): Observable<ReportScope> {
    let params = new HttpParams();
    if (laboratoryId !== null) params = params.set('laboratoryId', laboratoryId);
    return this.http.get<ReportScope>(`${this.baseUrl}/scope`, { params, timeout: 15000 });
  }

  getReport(filters: {
    from: string; to: string; laboratoryId: number | null; traineeId: number | null; page: number; size: number;
  }): Observable<PracticeReport> {
    let params = new HttpParams()
      .set('from', filters.from)
      .set('to', filters.to)
      .set('page', filters.page)
      .set('size', filters.size);
    if (filters.laboratoryId !== null) params = params.set('laboratoryId', filters.laboratoryId);
    if (filters.traineeId !== null) params = params.set('traineeId', filters.traineeId);
    return this.http.get<PracticeReport>(this.baseUrl, { params, timeout: 65000 });
  }
}
