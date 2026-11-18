import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TimeoutError, finalize, timeout, timer } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Attendance } from '../../../core/attendance/attendance.models';
import { AttendanceService } from '../../../core/attendance/attendance.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-trainee-attendance-page',
  imports: [PaginationComponent],
  templateUrl: './trainee-attendance.page.html',
  styleUrl: './trainee-attendance.page.scss',
})
export class TraineeAttendancePage implements OnInit {
  private readonly attendanceService = inject(AttendanceService);
  private readonly auth = inject(AuthService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private attendanceMutationVersion = 0;
  private historyRequestId = 0;

  attendance: Attendance | null = null;
  attendanceHistory: Attendance[] = [];
  totalAttendanceHistory = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  loading = true;
  historyLoading = true;
  saving = false;
  errorMessage = '';
  historyError = '';
  successMessage = '';

  get visibleAttendanceHistory(): Attendance[] { return this.attendanceHistory; }

  ngOnInit(): void {
    this.attendance = this.readCachedAttendance();
    this.loadToday();
    this.loadHistory();
    timer(30000, 30000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.loading && !this.saving) this.loadToday(false);
      });
  }

  loadToday(showLoading = true): void {
    const requestVersion = this.attendanceMutationVersion;
    if (showLoading) this.loading = true;
    if (showLoading) this.errorMessage = '';
    this.attendanceService.getToday()
      .pipe(
        timeout({ first: 10000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.changeDetectorRef.markForCheck();
        }),
      )
      .subscribe({
        next: (attendance) => {
          if (requestVersion !== this.attendanceMutationVersion) return;
          if (attendance) {
            this.attendance = attendance;
            this.cacheAttendance(attendance);
            this.upsertHistory(attendance);
          }
          this.errorMessage = '';
          this.changeDetectorRef.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestVersion !== this.attendanceMutationVersion) return;
          this.errorMessage = this.apiError(error);
          this.changeDetectorRef.markForCheck();
        },
      });
  }

  loadHistory(page = 1): void {
    const requestId = ++this.historyRequestId;
    this.currentPage = page;
    this.historyLoading = true;
    this.attendanceService.getHistoryPage(page - 1, this.pageSize)
      .pipe(
        timeout({ first: 10000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestId === this.historyRequestId) this.historyLoading = false;
          this.changeDetectorRef.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          if (requestId !== this.historyRequestId) return;
          this.attendanceHistory = result.content;
          this.totalAttendanceHistory = result.totalElements;
          this.currentPage = page;
          const todayAttendance = result.content.find((item) => item.date === this.todayKey());
          if (todayAttendance) {
            this.attendance = todayAttendance;
            this.cacheAttendance(todayAttendance);
          }
          if (this.attendance) this.upsertHistory(this.attendance);
          this.historyError = '';
          this.changeDetectorRef.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestId !== this.historyRequestId) return;
          this.historyError = this.apiError(error);
          this.changeDetectorRef.markForCheck();
        },
      });
  }

  registerCheckIn(): void { this.registerAction('check-in'); }
  registerCheckOut(): void { this.registerAction('check-out'); }

  get status(): 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' {
    if (!this.attendance) return 'NOT_STARTED';
    return this.attendance.checkOut ? 'COMPLETED' : 'IN_PROGRESS';
  }

  get statusLabel(): string {
    return {
      NOT_STARTED: 'Sin entrada registrada',
      IN_PROGRESS: 'Jornada en curso',
      COMPLETED: 'Jornada completada',
    }[this.status];
  }

  get currentDate(): string {
    return new Intl.DateTimeFormat('es-EC', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      timeZone: 'America/Guayaquil',
    }).format(new Date());
  }

  formatTime(dateTime: string | null): string {
    if (!dateTime) return '—';
    const time = dateTime.split('T')[1];
    return time ? time.slice(0, 5) : '—';
  }

  workedDuration(attendance: Attendance): string {
    if (!attendance.checkIn || !attendance.checkOut) return 'Pendiente';
    const start = new Date(attendance.checkIn).getTime();
    const end = new Date(attendance.checkOut).getTime();
    if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '—';

    const totalMinutes = Math.floor((end - start) / 60000);
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours} h ${minutes.toString().padStart(2, '0')} min`;
  }

  private registerAction(action: 'check-in' | 'check-out'): void {
    if (this.saving) return;
    this.attendanceMutationVersion++;
    this.errorMessage = '';
    this.successMessage = '';
    this.saving = true;
    const request = action === 'check-in'
      ? this.attendanceService.checkIn()
      : this.attendanceService.checkOut();
    request
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.saving = false;
          this.changeDetectorRef.markForCheck();
        }),
      )
      .subscribe({
        next: (attendance) => {
          this.attendance = attendance;
          this.cacheAttendance(attendance);
          this.upsertHistory(attendance);
          this.loadHistory(1);
          this.loading = false;
          this.successMessage = action === 'check-in'
            ? 'Entrada registrada a las ' + this.formatTime(attendance.checkIn) + '.'
            : this.durationReachedLimit(attendance)
              ? 'La jornada se cerró automáticamente a las ' + this.formatTime(attendance.checkOut) + ' al completar seis horas.'
              : 'Salida registrada a las ' + this.formatTime(attendance.checkOut) + '.';
          this.changeDetectorRef.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = this.apiError(error, action);
          this.changeDetectorRef.markForCheck();
          this.loadToday(false);
        },
      });
  }

  private durationReachedLimit(attendance: Attendance): boolean {
    if (!attendance.checkOut || !attendance.checkIn) return false;
    const start = new Date(attendance.checkIn).getTime();
    const end = new Date(attendance.checkOut).getTime();
    return Number.isFinite(start) && Number.isFinite(end) && end - start >= 6 * 60 * 60 * 1000;
  }

  private upsertHistory(attendance: Attendance): void {
    const existingIndex = this.attendanceHistory.findIndex((item) => item.id === attendance.id);
    if (existingIndex >= 0) {
      this.attendanceHistory = this.attendanceHistory.map((item) =>
        item.id === attendance.id ? attendance : item,
      );
    } else if (this.currentPage === 1) {
      this.attendanceHistory = [...this.attendanceHistory, attendance];
      this.totalAttendanceHistory++;
    }
    this.attendanceHistory = [...this.attendanceHistory].sort((left, right) =>
      right.date.localeCompare(left.date),
    );
  }

  displayDate(date: string): string {
    const [year, month, day] = date.split('-');
    return year && month && day ? `${day}/${month}/${year}` : date;
  }

  private readCachedAttendance(): Attendance | null {
    const key = this.attendanceCacheKey();
    if (!key) return null;
    try {
      const cached = sessionStorage.getItem(key);
      if (!cached) return null;
      const attendance = JSON.parse(cached) as Attendance;
      return attendance?.date === this.todayKey() && typeof attendance.checkIn === 'string'
        ? attendance
        : null;
    } catch {
      return null;
    }
  }

  private cacheAttendance(attendance: Attendance): void {
    const key = this.attendanceCacheKey();
    if (!key || attendance.date !== this.todayKey()) return;
    try {
      sessionStorage.setItem(key, JSON.stringify(attendance));
    } catch {
      // The API response remains usable when browser storage is unavailable.
    }
  }

  private attendanceCacheKey(): string | null {
    const userId = this.auth.session()?.user.id;
    return userId ? `control-ppp-attendance-${userId}-${this.todayKey()}` : null;
  }

  private todayKey(): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      timeZone: 'America/Guayaquil',
    }).formatToParts(new Date());
    const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
    return `${get('year')}-${get('month')}-${get('day')}`;
  }

  private apiError(error: HttpErrorResponse | TimeoutError, action?: 'check-in' | 'check-out'): string {
    if (error instanceof TimeoutError) return 'La solicitud tardó demasiado. Actualiza la jornada e inténtalo otra vez.';
    const body = error.error as { detail?: string; message?: string; name?: string } | null;
    if (error.status === 0 && body?.name === 'TimeoutError') {
      const actionLabel = action === 'check-out' ? 'la salida' : 'la entrada';
      return `El servidor tardó demasiado en registrar ${actionLabel}. Actualiza la jornada para comprobar si se guardó.`;
    }
    if (error.status === 0) return 'No se pudo conectar con el Sistema. Verifica que esté activo e inténtalo otra vez.';
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene el rol de practicante necesario para registrar asistencia.';
    return body?.detail || body?.message || 'No se pudo completar la operación. Intenta nuevamente.';
  }
}
