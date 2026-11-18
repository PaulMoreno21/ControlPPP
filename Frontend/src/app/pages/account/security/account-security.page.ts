import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { PasswordManagementService } from '../../../core/auth/password-management.service';

@Component({
  selector: 'app-account-security-page',
  imports: [FormsModule],
  templateUrl: './account-security.page.html',
  styleUrl: './account-security.page.scss',
})
export class AccountSecurityPage {
  private readonly passwords = inject(PasswordManagementService);
  readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  saving = false;
  errorMessage = '';
  successMessage = '';

  get isInstitutionalAccount(): boolean {
    return this.auth.session()?.user.accountType === 'INSTITUTIONAL';
  }

  changePassword(): void {
    if (this.saving) return;
    this.errorMessage = '';
    this.successMessage = '';
    if (!this.currentPassword) { this.errorMessage = 'Ingresa tu contraseña actual.'; return; }
    const bytes = new TextEncoder().encode(this.newPassword).length;
    if (this.newPassword.length < 12) {
      this.errorMessage = 'La nueva contraseña debe tener al menos 12 caracteres.';
      return;
    }
    if (bytes > 72) {
      this.errorMessage = 'La nueva contraseña es demasiado larga. Acórtala e inténtalo de nuevo.';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'La confirmación no coincide con la nueva contraseña.';
      return;
    }
    this.saving = true;
    this.passwords.changePassword(this.currentPassword, this.newPassword)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.saving = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: () => {
          this.auth.markPasswordChanged();
          this.currentPassword = '';
          this.newPassword = '';
          this.confirmPassword = '';
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage = error.error?.detail || error.error?.message
            || 'No se pudo cambiar la contraseña. Inténtalo nuevamente.';
          this.changeDetector.markForCheck();
        },
      });
  }

  dismissError(): void { this.errorMessage = ''; }
}
