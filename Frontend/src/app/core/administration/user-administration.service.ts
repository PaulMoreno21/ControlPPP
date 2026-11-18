import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResult } from '../../shared/pagination/page-result';
import {
  CreateManagedUserRequest,
  CreateLaboratoryRequest,
  LaboratorySummary,
  ManagedUser,
  UpdateManagedUserRequest,
} from './user-administration.models';

@Injectable({ providedIn: 'root' })
export class UserAdministrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/admin`;

  listGeneralAdministrators(page: number, size: number, search: string): Observable<PageResult<ManagedUser>> {
    return this.http.get<PageResult<ManagedUser>>(`${this.baseUrl}/general-administrators/page`, {
      params: { page, size, search },
    });
  }

  updateGeneralAdministrator(id: number, data: Pick<ManagedUser, 'nationalId' | 'firstName' | 'lastName' | 'email'>): Observable<ManagedUser> {
    return this.http.put<ManagedUser>(`${this.baseUrl}/general-administrators/${id}`, data);
  }

  setGeneralAdministratorActive(id: number, active: boolean): Observable<ManagedUser> {
    return this.http.patch<ManagedUser>(`${this.baseUrl}/general-administrators/${id}/status`, { active });
  }

  resetGeneralAdministratorPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/general-administrators/${id}/password`, { newPassword });
  }

  listLaboratories(): Observable<LaboratorySummary[]> {
    return this.http.get<LaboratorySummary[]>(`${this.baseUrl}/laboratories`);
  }

  listLaboratoriesPage(page: number, size: number): Observable<PageResult<LaboratorySummary>> {
    return this.http.get<PageResult<LaboratorySummary>>(`${this.baseUrl}/laboratories/page`, {
      params: { page, size },
    });
  }

  listAccessibleLaboratories(): Observable<LaboratorySummary[]> {
    return this.http.get<LaboratorySummary[]>(`${this.baseUrl}/laboratories/accessible`);
  }

  createLaboratory(request: CreateLaboratoryRequest): Observable<LaboratorySummary> {
    return this.http.post<LaboratorySummary>(`${this.baseUrl}/laboratories`, request);
  }

  updateLaboratory(laboratoryId: number, request: CreateLaboratoryRequest): Observable<LaboratorySummary> {
    return this.http.put<LaboratorySummary>(`${this.baseUrl}/laboratories/${laboratoryId}`, request);
  }

  deleteLaboratory(laboratoryId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/laboratories/${laboratoryId}`);
  }

  listUsers(laboratoryId: number): Observable<ManagedUser[]> {
    return this.http.get<ManagedUser[]>(`${this.baseUrl}/laboratories/${laboratoryId}/users`);
  }

  listUsersPage(laboratoryId: number, page: number, size: number, search: string): Observable<PageResult<ManagedUser>> {
    return this.http.get<PageResult<ManagedUser>>(`${this.baseUrl}/laboratories/${laboratoryId}/users/page`, {
      params: { page, size, search },
    });
  }

  createUser(laboratoryId: number, request: CreateManagedUserRequest): Observable<ManagedUser> {
    return this.http.post<ManagedUser>(`${this.baseUrl}/laboratories/${laboratoryId}/users`, request);
  }

  updateUser(laboratoryId: number, userId: number, request: UpdateManagedUserRequest): Observable<ManagedUser> {
    return this.http.put<ManagedUser>(`${this.baseUrl}/laboratories/${laboratoryId}/users/${userId}`, request);
  }

  setUserActive(laboratoryId: number, userId: number, active: boolean): Observable<ManagedUser> {
    return this.http.patch<ManagedUser>(
      `${this.baseUrl}/laboratories/${laboratoryId}/users/${userId}/status`,
      { active },
    );
  }

  resetLocalPassword(laboratoryId: number, userId: number, newPassword: string): Observable<void> {
    return this.http.put<void>(
      `${this.baseUrl}/laboratories/${laboratoryId}/users/${userId}/password`,
      { newPassword },
    );
  }
}
