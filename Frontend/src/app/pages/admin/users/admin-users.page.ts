import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TimeoutError, finalize, timeout } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CreateManagedUserRequest,
  LaboratorySummary,
  ManagedRole,
  ManagedUser,
} from '../../../core/administration/user-administration.models';
import { UserAdministrationService } from '../../../core/administration/user-administration.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';
import { GeneralAdministratorsComponent } from './general-administrators.component';
import { USER_IDENTIFICATION, userIdentityValidators } from '../../../core/administration/managed-user-validation';

@Component({
  selector: 'app-admin-users-page',
  imports: [FormsModule, PaginationComponent, ReactiveFormsModule, GeneralAdministratorsComponent],
  templateUrl: './admin-users.page.html',
  styleUrl: './admin-users.page.scss',
})
export class AdminUsersPage implements OnInit {
  @ViewChild('userEditor') private userEditor?: ElementRef<HTMLElement>;
  @ViewChild(GeneralAdministratorsComponent) private generalAdministrators?: GeneralAdministratorsComponent;
  private readonly formBuilder = inject(FormBuilder);
  private readonly userService = inject(UserAdministrationService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly roles: { value: ManagedRole; label: string;}[] = [
    { value: 'PRACTICANTE', label: 'Practicante'},
    { value: 'TUTOR_ESPE', label: 'Tutor ESPE' },
    { value: 'ADMIN_LABORATORIO', label: 'Administrador de laboratorio' },
    { value: 'ADMIN_GENERAL', label: 'Administrador general' },
  ];
  readonly form = this.formBuilder.nonNullable.group({
    nationalId: ['', userIdentityValidators.nationalId],
    firstName: ['', userIdentityValidators.firstName],
    lastName: ['', userIdentityValidators.lastName],
    email: ['', userIdentityValidators.email],
  });

  laboratories: LaboratorySummary[] = [];
  users: ManagedUser[] = [];
  totalUsers = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  userSearch = '';
  selectedLaboratoryId: number | null = null;
  editingUserId: number | null = null;
  updatingStatusUserId: number | null = null;
  selectedRoles: ManagedRole[] = [];
  accountType: 'INSTITUTIONAL' | 'LOCAL' = 'INSTITUTIONAL';
  password = '';
  confirmPassword = '';
  loadingLaboratories = false;
  laboratoriesFailed = false;
  loadingUsers = false;
  usersFailed = false;
  saving = false;
  errorMessage = '';
  successMessage = '';
  passwordVisible = false;
  resettingUser: ManagedUser | null = null;
  resetPassword = '';
  confirmResetPassword = '';
  resettingPassword = false;
  private userListRequestId = 0;

  get isGlobalAdmin(): boolean {
    return this.auth.session()?.user.roles.includes('ADMIN_GENERAL') ?? false;
  }

  get availableRoles(): { value: ManagedRole; label: string }[] {
    return this.isGlobalAdmin ? this.roles : this.roles.filter((role) => role.value !== 'ADMIN_GENERAL');
  }

  get filteredUsers(): ManagedUser[] {
    const term = this.userSearch.trim().toLocaleLowerCase();
    if (!term) return this.users;
    return this.users.filter((user) => user.nationalId.toLocaleLowerCase().includes(term));
  }

  get visibleUsers(): ManagedUser[] { return this.filteredUsers; }

  ngOnInit(): void { this.loadLaboratories(); }

  dismissError(): void {
    this.errorMessage = '';
    this.changeDetector.markForCheck();
  }

  loadLaboratories(): void {
    this.loadingLaboratories = true;
    this.laboratoriesFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    const request = this.isGlobalAdmin
      ? this.userService.listLaboratories()
      : this.userService.listAccessibleLaboratories();
    request
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loadingLaboratories = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (laboratories) => {
          this.laboratories = laboratories;
          this.selectedLaboratoryId = !this.isGlobalAdmin && laboratories.length === 1
            ? laboratories[0].id
            : null;
          this.users = [];
          this.usersFailed = false;
          this.changeDetector.markForCheck();
          if (this.selectedLaboratoryId !== null) this.loadUsers();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.laboratoriesFailed = true;
          this.errorMessage = error instanceof TimeoutError
            ? 'La API se tardo en responder'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  onLaboratoryChange(id: number | null): void {
    this.userListRequestId++;
    this.selectedLaboratoryId = id;
    this.users = [];
    this.totalUsers = 0;
    this.userSearch = '';
    this.currentPage = 1;
    this.usersFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    if (id !== null) this.loadUsers();
  }

  loadUsers(page = 1): void {
    if (this.selectedLaboratoryId === null) return;
    if (this.userSearch.trim() && !USER_IDENTIFICATION.test(this.userSearch.trim())) {
      this.errorMessage = 'Busca con hasta 10 letras o números, sin espacios.';
      return;
    }
    const requestId = ++this.userListRequestId;
    this.currentPage = page;
    this.loadingUsers = true;
    this.usersFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    this.userService.listUsersPage(this.selectedLaboratoryId, page - 1, this.pageSize, this.userSearch.trim())
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestId === this.userListRequestId) this.loadingUsers = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          if (requestId !== this.userListRequestId) return;
          this.users = result.content;
          this.totalUsers = result.totalElements;
          this.currentPage = page;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestId !== this.userListRequestId) return;
          this.usersFailed = true;
          this.errorMessage = error instanceof TimeoutError
            ? 'La aplicacion no respondió, Vuelve a intentarlo.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  saveUser(): void {
    if (this.saving) return;
    if (!this.validateUserForm()) return;
    if (this.editingUserId === null) {
      this.createUser();
      return;
    }
    this.updateUser();
  }

  fieldError(field: keyof typeof this.form.controls): string {
    const errors = this.form.controls[field].errors;
    if (!errors) return '';
    switch (field) {
      case 'nationalId': return 'La identificación debe contener entre 1 y 10 letras o números, sin espacios.';
      case 'firstName': return 'Ingresa nombres de hasta 100 caracteres, con letras, espacios, puntos, guiones o apóstrofes.';
      case 'lastName': return 'Ingresa apellidos de hasta 100 caracteres, con letras, espacios, puntos, guiones o apóstrofes.';
      case 'email': return 'Ingresa un correo válido de hasta 150 caracteres, como usuario@dominio.com.';
    }
  }

  private validateUserForm(): boolean {
    this.form.markAllAsTouched();
    if (!Number.isSafeInteger(this.selectedLaboratoryId) || !this.laboratories.some(lab => lab.id === this.selectedLaboratoryId)) {
      this.errorMessage = 'Selecciona un laboratorio disponible.';
      return false;
    }
    const invalidField = (Object.keys(this.form.controls) as (keyof typeof this.form.controls)[])
      .find(field => this.form.controls[field].invalid);
    if (invalidField) {
      this.errorMessage = this.fieldError(invalidField);
      return false;
    }
    if (!this.selectedRoles.length || this.selectedRoles.some(role => !this.availableRoles.some(option => option.value === role))) {
      this.errorMessage = 'Selecciona al menos un rol permitido para la cuenta.';
      return false;
    }
    return true;
  }

  editUser(user: ManagedUser): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.editingUserId = user.id;
    this.form.reset({
      nationalId: user.nationalId,
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
    });
    this.selectedRoles = [...user.roles];
    this.password = '';
    this.confirmPassword = '';
    this.changeDetector.markForCheck();
    this.userEditor?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelEdit(): void {
    this.editingUserId = null;
    this.form.reset({ nationalId: '', firstName: '', lastName: '', email: '' });
    this.selectedRoles = [];
    this.changeDetector.markForCheck();
  }

  private updateUser(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.form.markAllAsTouched();
    const laboratoryId = this.selectedLaboratoryId;
    const userId = this.editingUserId;
    if (laboratoryId === null || userId === null || this.form.invalid || this.saving) return;
    if (this.selectedRoles.length === 0) {
      this.errorMessage = 'Selecciona al menos un rol para la cuenta.';
      return;
    }
    if (this.selectedRoles.includes('PRACTICANTE') && this.selectedRoles.length > 1) {
      this.errorMessage = 'Practicante no se puede combinar con otros roles.';
      return;
    }

    const value = this.form.getRawValue();
    this.saving = true;
    this.userService.updateUser(laboratoryId, userId, {
      nationalId: value.nationalId.trim(),
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      email: value.email.trim(),
      roles: [...this.selectedRoles],
    })
      .pipe(
        timeout({ first: 15000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.saving = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (updated) => {
          this.users = this.users.map((user) => user.id === updated.id ? updated : user);
          this.successMessage = `Se actualizaron los datos de ${updated.firstName} ${updated.lastName}.`;
          this.generalAdministrators?.loadUsers();
          this.editingUserId = null;
          this.selectedRoles = [];
          this.form.reset({ nationalId: '', firstName: '', lastName: '', email: '' });
          this.changeDetector.markForCheck();
          this.loadUsers();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La actualización tardó demasiado.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  toggleUserStatus(user: ManagedUser): void {
    const laboratoryId = this.selectedLaboratoryId;
    if (laboratoryId === null || this.updatingStatusUserId !== null) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.updatingStatusUserId = user.id;
    const active = user.active !== 1;
    this.userService.setUserActive(laboratoryId, user.id, active)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.updatingStatusUserId = null;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (updated) => {
          this.users = this.users.map((item) => item.id === updated.id ? updated : item);
          this.successMessage = `${updated.firstName} ${updated.lastName} quedó ${updated.active === 1 ? 'activo' : 'inactivo'}.`;
          this.generalAdministrators?.loadUsers();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza la lista para confirmar el estado de la cuenta.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  beginPasswordReset(user: ManagedUser): void {
    if (user.accountType !== 'LOCAL') return;
    this.errorMessage = '';
    this.resettingUser = user;
    this.resetPassword = '';
    this.confirmResetPassword = '';
  }

  cancelPasswordReset(): void {
    if (this.resettingPassword) return;
    this.resettingUser = null;
    this.resetPassword = '';
    this.confirmResetPassword = '';
  }

  confirmPasswordReset(): void {
    const user = this.resettingUser;
    const laboratoryId = this.selectedLaboratoryId;
    if (!user || laboratoryId === null || this.resettingPassword) return;
    const bytes = new TextEncoder().encode(this.resetPassword).length;
    if (!this.resetPassword.trim() || this.resetPassword.length < 12) {
      this.resettingUser = null;
      this.errorMessage = 'La contraseña temporal debe tener al menos 12 caracteres.';
      return;
    }
    if (bytes > 72) {
      this.resettingUser = null;
      this.errorMessage = 'La contraseña temporal es demasiado larga. Acórtala e inténtalo de nuevo.';
      return;
    }
    if (this.resetPassword !== this.confirmResetPassword) {
      this.resettingUser = null;
      this.errorMessage = 'La confirmación no coincide con la contraseña temporal.';
      return;
    }
    this.errorMessage = '';
    this.resettingPassword = true;
    this.userService.resetLocalPassword(laboratoryId, user.id, this.resetPassword)
      .pipe(timeout({ first: 15000 }), takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.resettingPassword = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: () => {
          this.successMessage = `Se restableció la contraseña de ${user.firstName} ${user.lastName}. Deberá cambiarla al iniciar sesión.`;
          this.resettingUser = null;
          this.resetPassword = '';
          this.confirmResetPassword = '';
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza la lista antes de volver a intentarlo.'
            : this.apiError(error);
          this.resettingUser = null;
          this.changeDetector.markForCheck();
        },
      });
  }

  hasRole(role: ManagedRole): boolean { return this.selectedRoles.includes(role); }

  isRoleDisabled(role: ManagedRole): boolean {
    if (this.hasRole(role)) return false;
    if (role === 'PRACTICANTE') return this.selectedRoles.some((selected) => selected !== 'PRACTICANTE');
    return this.hasRole('PRACTICANTE');
  }

  toggleRole(role: ManagedRole, checked: boolean): void {
    if (!checked) {
      this.selectedRoles = this.selectedRoles.filter((selected) => selected !== role);
      return;
    }
    if (role === 'PRACTICANTE') {
      this.selectedRoles = ['PRACTICANTE'];
      return;
    }
    this.selectedRoles = [...this.selectedRoles.filter((selected) => selected !== 'PRACTICANTE'), role];
  }

  setAccountType(type: 'INSTITUTIONAL' | 'LOCAL'): void {
    this.accountType = type;
    this.password = '';
    this.confirmPassword = '';
  }

  createUser(): void {
    if (this.saving) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.form.markAllAsTouched();
    const laboratoryId = this.selectedLaboratoryId;
    if (laboratoryId === null) {
      this.errorMessage = 'Selecciona el laboratorio al que se asignará el usuario.';
      return;
    }
    if (!this.validateUserForm()) return;
    if (!this.selectedRoles.length) {
      this.errorMessage = 'Selecciona al menos un rol para la cuenta.';
      return;
    }
    if (this.selectedRoles.includes('PRACTICANTE') && this.selectedRoles.length > 1) {
      this.errorMessage = 'Practicante no se puede combinar con otros roles.';
      return;
    }
    if (this.accountType !== 'LOCAL' && this.accountType !== 'INSTITUTIONAL') {
      this.errorMessage = 'Selecciona el tipo de cuenta.';
      return;
    }
    if (this.accountType === 'LOCAL') {
      const passwordBytes = new TextEncoder().encode(this.password).length;
      if (!this.password.trim() || this.password.length < 12) {
        this.errorMessage = 'La contraseña debe tener al menos 12 caracteres.';
        return;
      }
      if (passwordBytes > 72) {
        this.errorMessage = 'La contraseña es demasiado larga. Acórtala e inténtalo de nuevo.';
        return;
      }
      if (this.password !== this.confirmPassword) {
        this.errorMessage = 'Las contraseñas no coinciden.';
        return;
      }
    }

    const value = this.form.getRawValue();
    const request: CreateManagedUserRequest = {
      nationalId: value.nationalId.trim(),
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      email: value.email.trim(),
      roles: this.selectedRoles,
      localAccount: this.accountType === 'LOCAL',
      ...(this.accountType === 'LOCAL' ? { password: this.password } : {}),
    };
    this.saving = true;
    this.userService.createUser(laboratoryId, request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.successMessage = `Se registró a ${user.firstName} ${user.lastName} con los roles seleccionados.`;
          this.generalAdministrators?.loadUsers();
          this.form.reset({ nationalId: '', firstName: '', lastName: '', email: '' });
          this.selectedRoles = [];
          this.accountType = 'INSTITUTIONAL';
          this.password = '';
          this.confirmPassword = '';
          this.passwordVisible = false;
          this.saving = false;
          this.changeDetector.markForCheck();
          this.loadUsers();
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage = this.apiError(error);
          this.saving = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  roleLabel(role: ManagedRole): string {
    return this.roles.find((item) => item.value === role)?.label ?? role;
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene permisos para consultar usuarios en este laboratorio.';
    if (error.status === 0) return 'No se pudo conectar';
    return body?.detail || body?.message || 'No se pudo completar la operación. Intenta nuevamente.';
  }
}
