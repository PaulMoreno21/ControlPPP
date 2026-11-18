import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subject, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResult } from '../../shared/pagination/page-result';
import {
  ActivityReviewRequest,
  AssignedTrainee,
  PendingActivitySubmission,
  TutorTraineeActivityHistory,
  TutorPendingCounts,
} from './tutor.models';

@Injectable({ providedIn: 'root' })
export class TutorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/tutor`;
  private readonly reviewed = new Subject<void>();
  readonly reviewCompleted = this.reviewed.asObservable();

  pendingCounts(): Observable<TutorPendingCounts> {
    return this.http.get<TutorPendingCounts>(`${this.baseUrl}/pending-counts`);
  }

  listAssignedTrainees(): Observable<AssignedTrainee[]> {
    return this.http.get<AssignedTrainee[]>(`${this.baseUrl}/trainees`);
  }

  listAssignedTraineesPage(page: number, size: number, search: string): Observable<PageResult<AssignedTrainee>> {
    return this.http.get<PageResult<AssignedTrainee>>(`${this.baseUrl}/trainees/page`, {
      params: { page, size, search },
    });
  }

  listPendingSubmissions(): Observable<PendingActivitySubmission[]> {
    return this.http.get<PendingActivitySubmission[]>(`${this.baseUrl}/submissions/pending`);
  }

  getTraineeActivities(traineeId: number): Observable<TutorTraineeActivityHistory> {
    return this.http.get<TutorTraineeActivityHistory>(`${this.baseUrl}/trainees/${traineeId}/activities`);
  }

  getTraineeActivitiesPage(traineeId: number, page: number, size: number): Observable<TutorTraineeActivityHistory> {
    return this.http.get<TutorTraineeActivityHistory>(`${this.baseUrl}/trainees/${traineeId}/activities/page`, {
      params: { page, size },
    });
  }

  reviewSubmission(submissionId: number, request: ActivityReviewRequest): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/submissions/${submissionId}/review`, request)
      .pipe(tap(() => this.reviewed.next()));
  }
}
