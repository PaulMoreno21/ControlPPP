import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TimeoutError, catchError, filter, finalize, of, switchMap, timeout, timer } from 'rxjs';
import { ddMmYyyyValidator, toDisplayDate, toIsoDate } from '../../../core/date-format';
import { HolidayAdministrationService } from '../../../core/administration/holiday-administration.service';
import { CreateHolidayRequest, HolidayRecord, HolidaySynchronizationStatus } from '../../../core/administration/holiday.models';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-admin-holidays-page',
  imports: [PaginationComponent, ReactiveFormsModule],
  templateUrl: './admin-holidays.page.html',
  styleUrl: './admin-holidays.page.scss',
})
export class AdminHolidaysPage implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly holidayService = inject(HolidayAdministrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly currentYear = new Date().getFullYear();

  readonly createForm = this.formBuilder.nonNullable.group({
    date: ['', [Validators.required, ddMmYyyyValidator]],
    description: ['', [Validators.required, Validators.maxLength(150)]],
  });
  readonly filterForm = this.formBuilder.nonNullable.group({
    from: [`01/01/${this.currentYear}`, [Validators.required, ddMmYyyyValidator]],
    to: [`31/12/${this.currentYear}`, [Validators.required, ddMmYyyyValidator]],
  });
  readonly editForm = this.formBuilder.nonNullable.group({
    date: ['', [Validators.required, ddMmYyyyValidator]],
    description: ['', [Validators.required, Validators.maxLength(150)]],
  });

  holidays: HolidayRecord[] = [];
  totalHolidays = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  loading = false;
  querying = false;
  loadFailed = false;
  saving = false;
  editingHoliday: HolidayRecord | null = null;
  savingEdit = false;
  editErrorMessage = '';
  updatingHolidayId: number | null = null;
  deletingHolidayId: number | null = null;
  pendingDeleteHoliday: HolidayRecord | null = null;
  errorMessage = '';
  successMessage = '';
  private synchronization: HolidaySynchronizationStatus | null = null;

  get visibleHolidays(): HolidayRecord[] { return this.holidays; }

  ngOnInit(): void {
    this.loadHolidays(false);
    // This reads local status only; it never calls the provider or renews the login session.
    timer(0, 30000).pipe(
      filter(() => !document.hidden),
      switchMap(() => this.holidayService.synchronizationStatus().pipe(
        timeout({ first: 8000 }), catchError(() => of(null)),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(status => {
      if (status) {
        const previous = this.synchronization;
        this.synchronization = status;
        if (status.state !== 'RUNNING' && status.added > 0
            && (previous?.lastAttempt !== status.lastAttempt || previous?.state === 'RUNNING')
            && !this.saving && !this.editingHoliday && this.updatingHolidayId === null && this.deletingHolidayId === null) {
          this.loadHolidays(false, this.currentPage);
        }
      }
      this.changeDetector.markForCheck();
    });
  }

  loadHolidays(fromQuery = true, requestedPage = 1): void {
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
    this.holidayService.listPage(from, to, requestedPage - 1, this.pageSize)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.querying = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          this.holidays = result.content;
          this.totalHolidays = result.totalElements;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.loadFailed = true;
          this.errorMessage = error instanceof TimeoutError
            ? 'La API no respondió en el tiempo definido.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  createHoliday(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.createForm.markAllAsTouched();
    if (this.createForm.invalid || this.saving) return;

    const value = this.createForm.getRawValue();
    const date = toIsoDate(value.date);
    if (!date) return;
    const request: CreateHolidayRequest = {
      date,
      description: value.description.trim(),
    };
    if (!request.description) {
      this.createForm.controls.description.setErrors({ required: true });
      return;
    }

    this.saving = true;
    this.holidayService.create(request)
      .pipe(timeout({ first: 15000 }), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (holiday) => {
          this.successMessage = `Se registró el feriado «${holiday.description}» para el ${this.formatDate(holiday.date)}.`;
          this.createForm.reset({ date: '', description: '' });
          const { from: displayFrom, to: displayTo } = this.filterForm.getRawValue();
          const from = toIsoDate(displayFrom);
          const to = toIsoDate(displayTo);
          if (!from || !to || holiday.date < from || holiday.date > to) {
            const year = holiday.date.slice(0, 4);
            this.filterForm.setValue({ from: `01/01/${year}`, to: `31/12/${year}` });
          }
          this.saving = false;
          this.changeDetector.markForCheck();
          this.loadHolidays(false);
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza el listado antes de volver a registrar para evitar duplicados.'
            : this.apiError(error);
          this.saving = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  requestEdit(holiday: HolidayRecord): void {
    if (this.savingEdit || this.pendingDeleteHoliday || this.updatingHolidayId !== null || this.deletingHolidayId !== null) return;
    this.editingHoliday = { ...holiday };
    this.editErrorMessage = '';
    this.editForm.reset({ date: toDisplayDate(holiday.date), description: holiday.description });
  }

  cancelEdit(): void {
    if (this.savingEdit) return;
    this.editingHoliday = null;
    this.editErrorMessage = '';
  }

  setEditDateFromPicker(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.editForm.controls.date.setValue(value ? toDisplayDate(value) : '');
  }

  saveEdit(): void {
    const holiday = this.editingHoliday;
    if (!holiday || this.savingEdit) return;
    this.editErrorMessage = '';
    this.editForm.markAllAsTouched();
    if (this.editForm.invalid) return;
    const values = this.editForm.getRawValue();
    const date = toIsoDate(values.date);
    const description = values.description.trim();
    if (!date) return;
    if (!description) { this.editForm.controls.description.setErrors({ required: true }); return; }
    this.savingEdit = true;
    this.holidayService.update(holiday.id, { date, description }).pipe(
      timeout({ first: 15000 }), takeUntilDestroyed(this.destroyRef),
      finalize(() => { this.savingEdit = false; this.changeDetector.markForCheck(); }),
    ).subscribe({
      next: updated => {
        this.editingHoliday = null;
        this.successMessage = `Se actualizó el feriado «${updated.description}» del ${this.formatDate(updated.date)}.`;
        const from = toIsoDate(this.filterForm.controls.from.value);
        const to = toIsoDate(this.filterForm.controls.to.value);
        let page = this.currentPage;
        if (!from || !to || updated.date < from || updated.date > to) {
          const year = updated.date.slice(0, 4);
          this.filterForm.setValue({ from: `01/01/${year}`, to: `31/12/${year}` });
          page = 1;
        }
        this.loadHolidays(false, page);
      },
      error: (error: HttpErrorResponse | TimeoutError) => {
        this.editErrorMessage = error instanceof TimeoutError
          ? 'La solicitud tardó demasiado. Consulta el listado antes de volver a guardar.' : this.apiError(error);
      },
    });
  }

  toggleStatus(holiday: HolidayRecord): void {
    if (this.editingHoliday) return;
    if (this.updatingHolidayId !== null || this.deletingHolidayId !== null) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.updatingHolidayId = holiday.id;
    const active = !holiday.active;
    this.holidayService.setActive(holiday.id, active)
      .pipe(timeout({ first: 12000 }), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.holidays = this.holidays.map((item) => item.id === updated.id ? updated : item);
          this.successMessage = `El feriado del ${this.formatDate(updated.date)} quedó ${updated.active ? 'activo' : 'inactivo'}.`;
          this.updatingHolidayId = null;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza el listado para confirmar el estado del feriado.'
            : this.apiError(error);
          this.updatingHolidayId = null;
          this.changeDetector.markForCheck();
        },
      });
  }

  requestDelete(holiday: HolidayRecord): void {
    if (this.editingHoliday || this.updatingHolidayId !== null || this.deletingHolidayId !== null) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.pendingDeleteHoliday = holiday;
  }

  cancelDelete(): void {
    if (this.deletingHolidayId !== null) return;
    this.pendingDeleteHoliday = null;
  }

  confirmDelete(): void {
    const holiday = this.pendingDeleteHoliday;
    if (holiday === null || this.deletingHolidayId !== null || this.updatingHolidayId !== null) return;

    this.errorMessage = '';
    this.successMessage = '';
    this.deletingHolidayId = holiday.id;
    this.holidayService.delete(holiday.id)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingHolidayId = null;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.pendingDeleteHoliday = null;
          this.successMessage = `Se eliminó el feriado «${holiday.description}» del ${this.formatDate(holiday.date)}.`;
          this.loadHolidays(false, this.currentPage);
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.pendingDeleteHoliday = null;
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza el listado para confirmar si se eliminó el feriado.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  formatDate(date: string): string {
    return toDisplayDate(date);
  }

  pickerDate(displayDate: string): string {
    return toIsoDate(displayDate) ?? '';
  }

  openDatePicker(input: HTMLInputElement): void {
    input.showPicker();
  }

  setDateFromPicker(event: Event, control: 'date' | 'from' | 'to'): void {
    const value = (event.target as HTMLInputElement).value;
    const displayDate = value ? toDisplayDate(value) : '';
    if (control === 'date') this.createForm.controls.date.setValue(displayDate);
    else this.filterForm.controls[control].setValue(displayDate);
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene los permisos necesarios.';
    if (error.status === 0) return 'No se pudo conectar ';
    return body?.detail || body?.message || 'No se pudo completar la operación. Intenta nuevamente.';
  }
}
