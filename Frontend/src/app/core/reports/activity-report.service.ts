import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BitacoraFormat } from './report.models';

@Injectable({ providedIn: 'root' })
export class ActivityReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/reports`;

  exportBitacora(traineeId: number, from: string, to: string, format: BitacoraFormat): Observable<HttpResponse<Blob>> {
    const params = new HttpParams().set('from', from).set('to', to).set('format', format);
    return this.http.get(`${this.baseUrl}/trainees/${traineeId}/bitacora`, {
      params,
      observe: 'response',
      responseType: 'blob',
      timeout: 65000,
    });
  }
}
