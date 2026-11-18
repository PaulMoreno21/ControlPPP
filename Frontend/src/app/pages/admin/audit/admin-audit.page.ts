import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TimeoutError, finalize, timeout } from 'rxjs';
import { AuditAdministrationService } from '../../../core/administration/audit-administration.service';
import { AuditLogEntry } from '../../../core/administration/audit-log.models';
import { ddMmYyyyValidator, toDisplayDate, toIsoDate } from '../../../core/date-format';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-admin-audit-page',
  imports: [PaginationComponent, ReactiveFormsModule],
  templateUrl: './admin-audit.page.html',
  styleUrl: './admin-audit.page.scss',
})
export class AdminAuditPage implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auditService = inject(AuditAdministrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly today = new Date();
  private readonly rangeStart = new Date(
    this.today.getFullYear(), this.today.getMonth(), this.today.getDate() - 29,
  );

  private displayDate(date: Date): string {
    return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear()}`;
  }

  readonly filterForm = this.formBuilder.nonNullable.group({
    from: [this.displayDate(this.rangeStart), [Validators.required, ddMmYyyyValidator]],
    to: [this.displayDate(this.today), [Validators.required, ddMmYyyyValidator]],
  });

  entries: AuditLogEntry[] = [];
  totalEntries = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  loading = false;
  querying = false;
  loadFailed = false;
  errorMessage = '';

  get visibleEntries(): AuditLogEntry[] { return this.entries; }

  ngOnInit(): void { this.loadEntries(false); }

  loadEntries(fromQuery = true, requestedPage = 1): void {
    this.errorMessage = '';
    this.loadFailed = false;
    this.filterForm.markAllAsTouched();
    if (this.filterForm.invalid) {
      this.changeDetector.markForCheck();
      return;
    }

    const { from: displayFrom, to: displayTo } = this.filterForm.getRawValue();
    const from = toIsoDate(displayFrom);
    const to = toIsoDate(displayTo);
    if (!from || !to || from > to) {
      this.errorMessage = 'La fecha inicial debe ser anterior o igual a la fecha final.';
      this.changeDetector.markForCheck();
      return;
    }

    this.currentPage = requestedPage;
    this.loading = true;
    this.querying = fromQuery;
    this.changeDetector.markForCheck();
    this.auditService.listPage(from, to, requestedPage - 1, this.pageSize)
      .pipe(
        timeout({ first: 15000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.querying = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          this.entries = result.content;
          this.totalEntries = result.totalElements;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.loadFailed = true;
          this.errorMessage = error instanceof TimeoutError
            ? 'La aplicacion no respondio, inténtalo otra vez.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  formatDateTime(dateTime: string): string {
    return new Intl.DateTimeFormat('es-EC', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZone: 'America/Guayaquil',
    }).format(new Date(dateTime));
  }

  actionLabel(action: string): string {
    const labels: Record<string, string> = {
      LOGIN_LOCAL: 'Inicio de sesión',
      LOGIN_FALLIDO: 'Inicio de sesión fallido',
      CREAR_USUARIO: 'Registro de usuario',
      EDITAR_USUARIO: 'Edición de usuario',
      CAMBIAR_ROLES_USUARIO: 'Cambio de roles de usuario',
      CAMBIAR_CONTRASENA: 'Cambio de contraseña',
      RESTABLECER_CONTRASENA: 'Restablecimiento de contraseña',
      ACTIVAR_USUARIO: 'Activación de usuario',
      DESACTIVAR_USUARIO: 'Desactivación de usuario',
      CREAR_ACTIVIDAD: 'Registro de actividad',
      EDITAR_ACTIVIDAD: 'Edición de actividad',
      ELIMINAR_ACTIVIDAD: 'Eliminación de actividad',
      ENVIAR_ACTIVIDAD: 'Envío de actividad',
      ACEPTAR_ACTIVIDAD: 'Actividad aceptada',
      RECHAZAR_ACTIVIDAD: 'Actividad rechazada',
      ASIGNAR_TUTOR: 'Asignación de tutor',
      REASIGNAR_TUTOR: 'Reasignación de tutor',
      DESVINCULAR_TUTOR: 'Desvinculación de tutor',
      CREAR_FERIADO: 'Registro de feriado',
      EDITAR_FERIADO: 'Edición de feriado',
      IMPORTAR_FERIADOS: 'Carga automática de feriado',
      PRECARGA_ANUAL_FERIADOS: 'Precarga anual de feriados completada',
      ACTIVAR_FERIADO: 'Activación de feriado',
      DESACTIVAR_FERIADO: 'Desactivación de feriado',
      ELIMINAR_FERIADO: 'Eliminación de feriado',
      CREAR_LABORATORIO: 'Registro de laboratorio',
      EDITAR_LABORATORIO: 'Edición de laboratorio',
      ELIMINAR_LABORATORIO: 'Eliminación de laboratorio',
      REGISTRAR_ENTRADA: 'Registro de entrada',
      REGISTRAR_SALIDA: 'Registro de salida',
      CIERRE_AUTOMATICO_ASISTENCIA: 'Cierre automático de jornada',
    };
    if (labels[action]) return labels[action];
    if (action.startsWith('EXPORTAR_BITACORA_')) {
      return `Exportación de bitácora ${action.slice('EXPORTAR_BITACORA_'.length)}`;
    }
    return action.toLocaleLowerCase().replaceAll('_', ' ');
  }

  entityLabel(entityName: string): string {
    const labels: Record<string, string> = {
      TCWUSUARIOS: 'Cuenta de usuario',
      TCWACTIVIDADES: 'Actividad',
      TCWASISTENCIAS: 'Jornada',
      TCWASIGNACIONES: 'Asignación',
      TCWFERIADOS: 'Feriado',
      CALENDARIO_FERIADOS: 'Calendario de feriados',
      TCWLABORATORIOS: 'Laboratorio',
      BITACORA: 'Bitácora',
      AUTENTICACION: 'Acceso al sistema',
    };
    return labels[entityName] ?? entityName;
  }

  pickerDate(displayDate: string): string {
    return toIsoDate(displayDate) ?? '';
  }

  openDatePicker(input: HTMLInputElement): void {
    input.showPicker();
  }

  setFilterDateFromPicker(event: Event, control: 'from' | 'to'): void {
    const value = (event.target as HTMLInputElement).value;
    this.filterForm.controls[control].setValue(value ? toDisplayDate(value) : '');
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene los permisos necesarios para consultar auditorías.';
    if (error.status === 0) return 'No se pudo conectar';
    return body?.detail || body?.message || 'No se pudo consultar la auditoría. Intenta nuevamente.';
  }
}
