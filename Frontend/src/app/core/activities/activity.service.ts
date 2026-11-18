import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Activity, ActivityBatchRequest, ActivityDateRange, ActivityRequest } from './activity.models';
import { PageResult } from '../../shared/pagination/page-result';

@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/activities`;

  list(from: string, to: string): Observable<Activity[]> {
    return this.http.get<Activity[]>(this.baseUrl, { params: { from, to } });
  }

  dateRange(): Observable<ActivityDateRange> {
    return this.http.get<ActivityDateRange>(`${this.baseUrl}/date-range`);
  }

  listPage(from: string, to: string, page: number, size: number): Observable<PageResult<Activity>> {
    return this.http.get<PageResult<Activity>>(`${this.baseUrl}/page`, {
      params: { from, to, page, size },
    });
  }

  listPending(): Observable<Activity[]> {
    return this.http.get<Activity[]>(`${this.baseUrl}/pending`);
  }

  create(request: ActivityRequest): Observable<Activity> {
    return this.http.post<Activity>(this.baseUrl, request);
  }

  createBatch(request: ActivityBatchRequest): Observable<Activity[]> {
    return this.http.post<Activity[]>(`${this.baseUrl}/batch`, request);
  }

  update(id: number, request: ActivityRequest): Observable<Activity> {
    return this.http.put<Activity>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  submit(id: number): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${id}/submissions`, {});
  }
}
