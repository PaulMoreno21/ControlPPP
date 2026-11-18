import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TimeoutError, finalize, timeout } from 'rxjs';
import { UserAdministrationService } from '../../../core/administration/user-administration.service';
import { ManagedRole, ManagedUser } from '../../../core/administration/user-administration.models';
import { USER_IDENTIFICATION, userIdentityValidators } from '../../../core/administration/managed-user-validation';
import { AuthService } from '../../../core/auth/auth.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-general-administrators',
  imports: [FormsModule, ReactiveFormsModule, PaginationComponent],
  templateUrl: './general-administrators.component.html',
  styleUrl: './admin-users.page.scss',
})
export class GeneralAdministratorsComponent implements OnInit {
  private readonly service = inject(UserAdministrationService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cd = inject(ChangeDetectorRef);
  readonly changed = output<void>();
  readonly pageSize = TABLE_PAGE_SIZE;
  readonly form = inject(FormBuilder).nonNullable.group({
    nationalId: ['', userIdentityValidators.nationalId], firstName: ['', userIdentityValidators.firstName],
    lastName: ['', userIdentityValidators.lastName], email: ['', userIdentityValidators.email],
  });
  users: ManagedUser[] = [];
  total = 0;
  page = 1;
  search = '';
  loading = false;
  failed = false;
  busy = false;
  errorMessage = '';
  successMessage = '';
  editing: ManagedUser | null = null;
  resetting: ManagedUser | null = null;
  statusTarget: ManagedUser | null = null;
  password = '';
  confirmation = '';
  private requestId = 0;

  ngOnInit(): void { this.loadUsers(); }
  isSelf(user: ManagedUser): boolean { return user.id === this.auth.session()?.user.id; }

  roleLabel(role: ManagedRole): string {
    return { ADMIN_GENERAL: 'Administrador general', ADMIN_LABORATORIO: 'Administrador de laboratorio',
      TUTOR_ESPE: 'Tutor ESPE', PRACTICANTE: 'Practicante' }[role];
  }

  loadUsers(page = 1): void {
    if (this.search.trim() && !USER_IDENTIFICATION.test(this.search.trim())) {
      this.errorMessage = 'Busca con hasta 10 letras o números, sin espacios.';
      return;
    }
    const id = ++this.requestId;
    this.loading = true;
    this.failed = false;
    this.cd.markForCheck();
    this.service.listGeneralAdministrators(page - 1, this.pageSize, this.search.trim())
      .pipe(timeout({ first: 12000 }), takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (id === this.requestId) this.loading = false;
        this.cd.markForCheck();
      })).subscribe({
        next: result => {
          if (id !== this.requestId) return;
          this.users = result.content;
          this.total = result.totalElements;
          this.page = page;
          if (result.totalPages > 0 && page > result.totalPages) this.loadUsers(result.totalPages);
          this.cd.markForCheck();
        },
        error: error => {
          if (id !== this.requestId) return;
          this.failed = true;
          this.errorMessage = this.friendlyError(error);
          this.cd.markForCheck();
        },
      });
  }

  edit(user: ManagedUser): void {
    if (this.busy) return;
    this.editing = user;
    this.form.reset({ nationalId: user.nationalId, firstName: user.firstName, lastName: user.lastName, email: user.email });
    this.successMessage = '';
  }

  save(): void {
    if (!this.editing || this.busy) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.errorMessage = 'Revisa la identificación, nombres, apellidos y correo. Todos son obligatorios y deben tener un formato válido.';
      return;
    }
    const value = this.form.getRawValue();
    this.busy = true;
    this.service.updateGeneralAdministrator(this.editing.id, {
      nationalId: value.nationalId.trim(), firstName: value.firstName.trim(), lastName: value.lastName.trim(), email: value.email.trim(),
    }).pipe(timeout({ first: 15000 }), takeUntilDestroyed(this.destroyRef), finalize(() => this.finish()))
      .subscribe({
        next: user => {
          this.editing = null;
          this.successMessage = `Se actualizaron los datos de ${user.firstName} ${user.lastName}.`;
          this.loadUsers(this.page);
          this.changed.emit();
        },
        error: error => { this.errorMessage = this.friendlyError(error); this.cd.markForCheck(); },
      });
  }

  beginReset(user: ManagedUser): void {
    if (this.busy || this.isSelf(user) || user.accountType !== 'LOCAL') return;
    this.resetting = user;
    this.password = '';
    this.confirmation = '';
    this.successMessage = '';
  }

  reset(): void {
    if (!this.resetting || this.busy) return;
    if (!this.password.trim() || this.password.length < 12) {
      this.errorMessage = 'La contraseña temporal debe tener al menos 12 caracteres.';
      return;
    }
    if (new TextEncoder().encode(this.password).length > 72) {
      this.errorMessage = 'La contraseña es demasiado larga. Acórtala e inténtalo de nuevo.';
      return;
    }
    if (this.password !== this.confirmation) {
      this.errorMessage = 'La confirmación no coincide con la contraseña temporal.';
      return;
    }
    this.busy = true;
    this.service.resetGeneralAdministratorPassword(this.resetting.id, this.password)
      .pipe(timeout({ first: 15000 }), takeUntilDestroyed(this.destroyRef), finalize(() => this.finish()))
      .subscribe({
        next: () => {
          this.resetting = null;
          this.password = '';
          this.confirmation = '';
          this.successMessage = 'Se restableció la contraseña y se cerraron las sesiones de esa cuenta. Deberá cambiar la temporal al ingresar.';
        },
        error: error => { this.errorMessage = this.friendlyError(error); this.cd.markForCheck(); },
      });
  }

  confirmStatus(): void {
    if (!this.statusTarget || this.busy || this.isSelf(this.statusTarget)) return;
    const active = this.statusTarget.active !== 1;
    this.busy = true;
    this.service.setGeneralAdministratorActive(this.statusTarget.id, active)
      .pipe(timeout({ first: 15000 }), takeUntilDestroyed(this.destroyRef), finalize(() => this.finish()))
      .subscribe({
        next: user => {
          this.statusTarget = null;
          this.successMessage = `${user.firstName} ${user.lastName}: acceso ${active ? 'activado' : 'desactivado'}.`;
          this.loadUsers(this.page);
          this.changed.emit();
        },
        error: error => { this.errorMessage = this.friendlyError(error); this.cd.markForCheck(); },
      });
  }

  closeDialogs(): void {
    if (this.busy) return;
    this.editing = null;
    this.resetting = null;
    this.statusTarget = null;
    this.password = '';
    this.confirmation = '';
  }

  private finish(): void { this.busy = false; this.cd.markForCheck(); }
  private friendlyError(error: HttpErrorResponse | TimeoutError): string {
    if (error instanceof TimeoutError) return 'La solicitud tardó demasiado. Actualiza la lista para confirmar el resultado.';
    if (error.status === 0) return 'No se pudo conectar con el Sistema. Inténtalo nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene permiso para administrar estas cuentas.';
    return error.error?.detail || 'No se pudo completar la acción. Inténtalo nuevamente.';
  }
}
