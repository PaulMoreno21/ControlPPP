import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateHolidayRequest, HolidayRecord, HolidaySynchronizationStatus } from './holiday.models';
import { PageResult } from '../../shared/pagination/page-result';

@Injectable({ providedIn: 'root' })
export class HolidayAdministrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/admin/holidays`;

  synchronizationStatus(): Observable<HolidaySynchronizationStatus> {
    return this.http.get<HolidaySynchronizationStatus>(`${this.baseUrl}/synchronization`);
  }

  list(from: string, to: string): Observable<HolidayRecord[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<HolidayRecord[]>(this.baseUrl, { params });
  }

  listPage(from: string, to: string, page: number, size: number): Observable<PageResult<HolidayRecord>> {
    const params = new HttpParams().set('from', from).set('to', to).set('page', page).set('size', size);
    return this.http.get<PageResult<HolidayRecord>>(`${this.baseUrl}/page`, { params });
  }

  create(request: CreateHolidayRequest): Observable<HolidayRecord> {
    return this.http.post<HolidayRecord>(this.baseUrl, request);
  }

  setActive(id: number, active: boolean): Observable<HolidayRecord> {
    return this.http.patch<HolidayRecord>(`${this.baseUrl}/${id}/status`, { active });
  }

  update(id: number, request: CreateHolidayRequest): Observable<HolidayRecord> {
    return this.http.put<HolidayRecord>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
