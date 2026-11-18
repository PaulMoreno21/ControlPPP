import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, TimeoutError, finalize, of, switchMap, timeout } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ActivityService } from '../../../core/activities/activity.service';
import { Activity, ActivityBatchRequest, ActivityRequest, ActivityStatus } from '../../../core/activities/activity.models';
import { ddMmYyyyValidator, notAfterDateValidator, toDisplayDate, toIsoDate } from '../../../core/date-format';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-trainee-activities-page',
  imports: [DecimalPipe, FormsModule, PaginationComponent, ReactiveFormsModule, RouterLink],
  templateUrl: './trainee-activities.page.html',
  styleUrl: './trainee-activities.page.scss',
})
export class TraineeActivitiesPage implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly activityService = inject(ActivityService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly isCreateRoute = this.route.snapshot.data['createMode'] === true;
  readonly today = this.formatDate(new Date());
  private readonly creationDateValidator: ValidatorFn = control =>
    this.editingId == null ? notAfterDateValidator(this.today)(control) : null;

  readonly form = this.formBuilder.nonNullable.group({
    date: [toDisplayDate(this.today), [Validators.required, ddMmYyyyValidator, this.creationDateValidator]],
    description: ['', [Validators.required, Validators.maxLength(1000)]],
    durationHours: [1, [Validators.required, Validators.min(0.01), Validators.max(6)]],
  });
  activityEntries: { id: number; description: string; durationHours: number | null }[] = [];
  private nextActivityEntryId = 1;
  createdActivityCount = 0;
  createdHours = 0;
  readonly statusLabels: Record<ActivityStatus, string> = {
    BORRADOR: 'Borrador', ENVIADA: 'Pendiente', ACEPTADA: 'Aceptada', RECHAZADA: 'Rechazada',
  };
  private filteredActivities: Activity[] = [];
  totalActivities = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  pendingActivities: Activity[] = [];
  dailyActivities: Activity[] = [];
  private dailyUsageRequestId = 0;
  dailyUsageLoading = false;
  from = '';
  to = toDisplayDate(this.today);
  loading = false;
  private listRequestId = 0;
  pendingLoading = false;
  saving = false;
  submittingId: number | null = null;
  deletingActivityId: number | null = null;
  pendingDeleteActivity: Activity | null = null;
  pendingDeleteActivityNumber: number | null = null;
  formVisible = false;
  creationSaved = false;
  editingId: number | null = null;
  editingObservation = '';
  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    let useDefaultRange = true;
    if (!this.isCreateRoute) {
      const queryFrom = this.route.snapshot.queryParamMap.get('from');
      const queryTo = this.route.snapshot.queryParamMap.get('to');
      if (this.isIsoDate(queryFrom) && this.isIsoDate(queryTo)) {
        this.from = toDisplayDate(queryFrom);
        this.to = toDisplayDate(queryTo);
        useDefaultRange = false;
      }
    }
    this.form.controls.date.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((date) => {
        const isoDate = toIsoDate(date);
        if (isoDate) this.loadDailyUsage(isoDate);
        else {
          this.dailyUsageRequestId++;
          this.dailyActivities = [];
          this.dailyUsageLoading = false;
        }
        this.persistCreationDraft();
      });
    if (this.isCreateRoute) this.prepareCreate();
    else this.load(1, useDefaultRange);
  }

  dismissError(): void {
    this.errorMessage = '';
  }

  get dailyHours(): number {
    const hours = this.dailyActivities
      .filter((activity) => activity.id !== this.editingId)
      .reduce((total, activity) => total + Number(activity.durationHours), 0);
    return Math.round(hours * 100) / 100;
  }

  get activities(): Activity[] {
    const byId = new Map<number, Activity>();
    [...this.filteredActivities, ...this.pendingActivities].forEach((activity) => byId.set(activity.id, activity));
    return [...byId.values()].sort((left, right) =>
      left.date.localeCompare(right.date) || left.id - right.id,
    );
  }

  get visibleActivities(): Activity[] { return this.activities; }
  get pageOffset(): number { return (this.currentPage - 1) * this.pageSize; }

  get pendingHours(): number {
    const hours = this.activityEntries.reduce((total, entry) => {
      const value = Number(entry.durationHours ?? 0);
      return total + (Number.isFinite(value) ? value : 0);
    }, 0);
    return Math.round(hours * 100) / 100;
  }

  get totalHours(): number { return Math.round((this.dailyHours + this.pendingHours) * 100) / 100; }
  get availableHours(): number { return Math.max(0, 6 - this.totalHours); }

  private loadDailyUsage(date: string): void {
    if (!date) return;
    const requestId = ++this.dailyUsageRequestId;
    this.dailyActivities = [];
    this.dailyUsageLoading = true;
    this.activityService.list(date, date)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestId === this.dailyUsageRequestId) this.dailyUsageLoading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (activities) => {
          if (requestId === this.dailyUsageRequestId && toIsoDate(this.form.controls.date.value) === date) {
            this.dailyActivities = activities;
          }
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestId === this.dailyUsageRequestId) this.errorMessage = this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  load(page = 1, useDefaultRange = false): void {
    this.errorMessage = '';
    this.currentPage = 1;
    const requestId = ++this.listRequestId;
    const from = useDefaultRange ? null : this.normalizeFilterDate('from');
    const to = useDefaultRange ? null : this.normalizeFilterDate('to');
    if (!useDefaultRange && (!from || !to || from > to)) {
      this.errorMessage = 'Ingresa fechas válidas en formato dd/mm/yyyy.';
      this.loading = false;
      return;
    }
    this.loading = true;
    this.currentPage = page;
    this.changeDetector.markForCheck();
    const range = useDefaultRange ? this.activityService.dateRange() : of({ from: from!, to: to! });
    range
      .pipe(
        switchMap((dates) => {
          if (requestId !== this.listRequestId) return EMPTY;
          if (useDefaultRange) {
            this.from = toDisplayDate(dates.from);
            this.to = toDisplayDate(dates.to);
          }
          return this.activityService.listPage(dates.from, dates.to, page - 1, this.pageSize);
        }),
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestId === this.listRequestId) this.loading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          if (requestId === this.listRequestId) {
            this.filteredActivities = result.content;
            this.pendingActivities = [];
            this.totalActivities = result.totalElements;
          }
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestId === this.listRequestId) this.errorMessage = this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  private loadPendingActivities(): void {
    this.pendingLoading = true;
    this.activityService.listPending()
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.pendingLoading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
      next: (activities) => {
        this.pendingActivities = activities;
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse | TimeoutError) => {
        this.pendingActivities = [];
        this.errorMessage = this.apiError(error);
        this.changeDetector.markForCheck();
      },
    });
  }

  search(): void {
    const from = this.normalizeFilterDate('from');
    const to = this.normalizeFilterDate('to');
    if (!from || !to || from > to) {
      this.errorMessage = 'La fecha inicial debe ser anterior o igual a la fecha final.';
      return;
    }
    this.load();
  }

  openCreate(): void {
    void this.router.navigateByUrl('/practicante/actividades/nueva');
  }

  goToActivities(): void {
    if (this.saving) return;
    void this.router.navigateByUrl('/practicante/actividades');
  }

  private prepareCreate(): void {
    this.editingId = null;
    this.formVisible = false;
    this.form.reset({ date: toDisplayDate(this.today), description: '', durationHours: 1 });
    this.activityEntries = [];
    this.restoreCreationDraft();
    this.createdActivityCount = 0;
    this.createdHours = 0;
    this.formVisible = true;
    this.creationSaved = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.persistCreationDraft();
  }

  edit(activity: Activity): void {
    this.editingId = activity.id;
    this.editingObservation = activity.status === 'RECHAZADA' ? activity.observation ?? '' : '';
    this.form.reset({ date: toDisplayDate(activity.date), description: activity.description, durationHours: activity.durationHours });
    this.formVisible = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  cancelForm(): void {
    if (this.isCreateRoute) {
      if (this.saving) return;
      void this.router.navigateByUrl('/practicante/actividades');
      return;
    }
    this.formVisible = false;
    this.editingId = null;
    this.editingObservation = '';
    this.form.reset({ date: toDisplayDate(this.today), description: '', durationHours: 1 });
  }

  save(): void {
    if (this.isCreateRoute && this.editingId === null) {
      this.saveBatch();
      return;
    }
    this.errorMessage = '';
    this.successMessage = '';
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving) return;
    const value = this.form.getRawValue();
    const date = toIsoDate(value.date);
    if (!date) return;

    const request: ActivityRequest = {
      date,
      description: value.description.trim(),
      durationHours: Number(value.durationHours),
    };
    this.saving = true;
    const operation = this.editingId === null
      ? this.activityService.create(request)
      : this.activityService.update(this.editingId, request);
    operation.pipe(
      timeout({ first: 15000 }),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => {
        this.saving = false;
        this.changeDetector.markForCheck();
      }),
    ).subscribe({
      next: () => {
        this.formVisible = false;
        this.editingId = null;
        this.successMessage = 'Actividad guardada. Debes enviarla a revisión para que la vea tu tutor.';
        if (this.isCreateRoute) this.creationSaved = true;
        else this.load();
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse | TimeoutError) => {
        this.errorMessage = this.apiError(error);
        this.changeDetector.markForCheck();
      },
    });
  }

  addActivityEntry(): void {
    if (this.totalHours >= 6) return;
    this.activityEntries = [...this.activityEntries, this.newActivityEntry()];
    this.errorMessage = '';
    this.persistCreationDraft();
  }

  removeActivityEntry(entryId: number): void {
    this.activityEntries = this.activityEntries.filter((entry) => entry.id !== entryId);
    this.errorMessage = '';
    this.persistCreationDraft();
  }

  requestDelete(activity: Activity, activityNumber: number): void {
    if (activity.status !== 'BORRADOR' || this.deletingActivityId !== null) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.pendingDeleteActivity = activity;
    this.pendingDeleteActivityNumber = activityNumber;
  }

  cancelDelete(): void {
    if (this.deletingActivityId !== null) return;
    this.pendingDeleteActivity = null;
    this.pendingDeleteActivityNumber = null;
  }

  confirmDelete(): void {
    const activity = this.pendingDeleteActivity;
    if (activity === null || this.deletingActivityId !== null) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.deletingActivityId = activity.id;
    this.activityService.delete(activity.id)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingActivityId = null;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.filteredActivities = this.filteredActivities.filter((item) => item.id !== activity.id);
          this.totalActivities = Math.max(0, this.totalActivities - 1);
          this.dailyActivities = this.dailyActivities.filter((item) => item.id !== activity.id);
          this.pendingDeleteActivity = null;
          this.pendingDeleteActivityNumber = null;
          this.successMessage = 'Se eliminó la actividad en borrador.';
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.pendingDeleteActivity = null;
          this.pendingDeleteActivityNumber = null;
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza Mis actividades para confirmar si se eliminó el borrador.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  persistCreationDraft(): void {
    if (!this.isCreateRoute || !this.formVisible) return;
    const key = this.creationDraftKey();
    if (!key) return;
    try {
      sessionStorage.setItem(key, JSON.stringify({
        date: this.form.controls.date.value,
        activities: this.activityEntries.map(({ description, durationHours }) => ({ description, durationHours })),
      }));
    } catch {
      // The form remains usable if session storage is unavailable.
    }
  }

  private restoreCreationDraft(): void {
    const key = this.creationDraftKey();
    if (!key) return;
    try {
      const saved = sessionStorage.getItem(key);
      if (!saved) return;
      const draft = JSON.parse(saved) as {
        date?: unknown;
        activities?: { description?: unknown; durationHours?: unknown }[];
      };
      const displayDate = typeof draft.date === 'string' ? draft.date : '';
      const isoDate = toIsoDate(displayDate);
      if (!isoDate || isoDate > this.today || !Array.isArray(draft.activities)) return;
      this.form.controls.date.setValue(displayDate);
      this.activityEntries = draft.activities
        .filter((entry) => entry && typeof entry.description === 'string')
        .map((entry) => ({
          id: this.nextActivityEntryId++,
          description: entry.description as string,
          durationHours: typeof entry.durationHours === 'number' ? entry.durationHours : null,
        }));
    } catch {
      this.clearCreationDraft();
    }
  }

  private clearCreationDraft(): void {
    const key = this.creationDraftKey();
    if (!key) return;
    try { sessionStorage.removeItem(key); } catch { /* Ignore unavailable storage. */ }
  }

  private creationDraftKey(): string | null {
    const userId = this.auth.session()?.user.id;
    return userId ? `control-ppp-activity-draft-${userId}` : null;
  }

  private saveBatch(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.form.controls.date.markAsTouched();
    if (this.saving) return;
    if (this.dailyUsageLoading) {
      this.errorMessage = 'Espera a que termine la consulta de las actividades registradas para esa fecha.';
      return;
    }
    const date = toIsoDate(this.form.controls.date.value);
    if (!date) {
      this.errorMessage = 'Ingresa una fecha válida en formato dd/mm/yyyy.';
      return;
    }
    if (this.activityEntries.length === 0) {
      this.errorMessage = 'Agrega al menos una actividad.';
      return;
    }
    const invalidEntry = this.activityEntries.some((entry) =>
      !entry.description.trim()
      || entry.description.trim().length > 1000
      || entry.durationHours === null
      || !Number.isFinite(Number(entry.durationHours))
      || Number(entry.durationHours) < 0.01
      || Number(entry.durationHours) > 6,
    );
    if (invalidEntry) {
      this.errorMessage = 'Cada actividad requiere una descripción y una duración mayor que 0 y hasta 6 horas.';
      return;
    }
    if (this.totalHours > 6) {
      this.errorMessage = 'El total de actividades del día no puede superar seis horas.';
      return;
    }

    const request: ActivityBatchRequest = {
      date,
      activities: this.activityEntries.map((entry) => ({
        description: entry.description.trim(),
        durationHours: Number(entry.durationHours),
      })),
    };
    this.saving = true;
    this.activityService.createBatch(request)
      .pipe(
        timeout({ first: 15000 }),
        finalize(() => {
          this.saving = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (activities) => {
          this.dailyActivities = [...this.dailyActivities, ...activities];
          this.createdActivityCount = activities.length;
          this.createdHours = activities.reduce((total, activity) => total + Number(activity.durationHours), 0);
          this.formVisible = false;
          this.creationSaved = true;
          this.clearCreationDraft();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  private newActivityEntry(): { id: number; description: string; durationHours: number | null } {
    return { id: this.nextActivityEntryId++, description: '', durationHours: null };
  }

  submit(activity: Activity): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.submittingId = activity.id;
    this.changeDetector.markForCheck();
    this.activityService.submit(activity.id).subscribe({
      next: () => {
        this.submittingId = null;
        this.successMessage = 'Actividad enviada al tutor para revisión.';
        this.changeDetector.markForCheck();
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.apiError(error);
        this.submittingId = null;
        this.changeDetector.markForCheck();
      },
    });
  }

  canEdit(activity: Activity): boolean { return activity.status === 'BORRADOR' || activity.status === 'RECHAZADA'; }
  statusClass(status: ActivityStatus): string { return status.toLowerCase(); }
  trackActivity(_index: number, activity: Activity): number { return activity.id; }

  displayDate(date: string): string {
    return toDisplayDate(date);
  }

  pickerDate(displayDate: string): string {
    return toIsoDate(displayDate) ?? '';
  }

  openDatePicker(input: HTMLInputElement): void {
    input.showPicker();
  }

  setActivityDateFromPicker(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.form.controls.date.setValue(value ? toDisplayDate(value) : '');
  }

  setFilterDateFromPicker(event: Event, control: 'from' | 'to'): void {
    const value = (event.target as HTMLInputElement).value;
    if (control === 'from') this.from = value ? toDisplayDate(value) : '';
    else this.to = value ? toDisplayDate(value) : '';
  }

  private apiError(error: HttpErrorResponse | TimeoutError): string {
    if (error instanceof TimeoutError) {
      return 'La solicitud tardó demasiado. El formulario quedó guardado como borrador local; verifica la conexión  e inténtalo otra vez.';
    }
    const body = error.error as { detail?: string; message?: string } | null;
    return body?.detail || body?.message || 'No se pudo completar la operación. Intenta nuevamente.';
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private isIsoDate(value: string | null): value is string {
    if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    return toIsoDate(toDisplayDate(value)) === value;
  }

  private normalizeFilterDate(control: 'from' | 'to'): string | null {
    const value = this[control].trim();
    const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value);
    const normalized = match
      ? `${match[1].padStart(2, '0')}/${match[2].padStart(2, '0')}/${match[3]}`
      : value;
    const isoDate = toIsoDate(normalized);
    if (isoDate) this[control] = toDisplayDate(isoDate);
    return isoDate;
  }
}
