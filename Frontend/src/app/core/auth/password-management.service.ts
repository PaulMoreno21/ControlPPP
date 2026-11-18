import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PasswordManagementService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/auth/password`;

  changePassword(currentPassword: string, newPassword: string): Observable<{ passwordChangeRequired: boolean }> {
    return this.http.post<{ passwordChangeRequired: boolean }>(`${this.baseUrl}/change`, {
      currentPassword,
      newPassword,
    });
  }
}
