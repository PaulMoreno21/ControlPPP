import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Attendance } from './attendance.models';
import { PageResult } from '../../shared/pagination/page-result';

@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl + '/api/v1/attendance';

  getToday(): Observable<Attendance | null> {
    return this.http.get<Attendance | null>(this.baseUrl + '/today', { timeout: 10000 });
  }

  getHistory(): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(this.baseUrl + '/history', { timeout: 10000 });
  }

  getHistoryPage(page: number, size: number): Observable<PageResult<Attendance>> {
    return this.http.get<PageResult<Attendance>>(`${this.baseUrl}/history/page`, {
      params: { page, size }, timeout: 10000,
    });
  }

  checkIn(): Observable<Attendance> {
    return this.http.post<Attendance>(this.baseUrl + '/check-in', {}, { timeout: 10000 });
  }

  checkOut(): Observable<Attendance> {
    return this.http.post<Attendance>(this.baseUrl + '/check-out', {}, { timeout: 10000 });
  }
}
