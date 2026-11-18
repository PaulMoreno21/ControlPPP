import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { toDisplayDate, toIsoDate } from '../../../core/date-format';
import { PracticeReportService } from '../../../core/reports/practice-report.service';
import { PracticeReport, PracticeReportRow, ReportScope, ReportTraineeOption } from '../../../core/reports/practice-report.models';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-practice-report-page',
  imports: [FormsModule, PaginationComponent],
  templateUrl: './practice-report.page.html',
  styleUrl: './practice-report.page.scss',
})
export class PracticeReportPage implements OnInit {
  private readonly reportService = inject(PracticeReportService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  scope: ReportScope | null = null;
  laboratories: ReportScope['laboratories'] = [];
  trainees: ReportTraineeOption[] = [];
  rows: PracticeReportRow[] = [];
  totalRows = 0;
  currentPage = 1;
  readonly pageSize = TABLE_PAGE_SIZE;
  attendanceDays = 0;
  attendanceHours = 0;
  acceptedActivityHours = 0;
  from: string;
  to: string;
  selectedLaboratoryId: number | null = null;
  selectedTraineeId: number | null = null;
  loadingScope = false;
  loading = false;
  scopeFailed = false;
  reportFailed = false;
  errorMessage = '';
  hasQueried = false;

  constructor() {
    const today = new Date();
    const iso = this.localIsoDate(today);
    this.from = toDisplayDate(`${iso.slice(0, 7)}-01`);
    this.to = toDisplayDate(iso);
  }

  ngOnInit(): void { this.loadScope(null, true); }

  get roleLabel(): string {
    switch (this.scope?.role) {
      case 'ADMIN_GENERAL': return 'ADMINISTRACIÓN GENERAL';
      case 'ADMIN_LABORATORIO': return 'ADMINISTRACIÓN DE LABORATORIO';
      case 'TUTOR_ESPE': return 'TUTOR ESPE';
      default: return 'PRACTICANTE';
    }
  }

  loadScope(laboratoryId: number | null, initial = false): void {
    this.loadingScope = true;
    this.scopeFailed = false;
    this.reportService.getScope(laboratoryId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.loadingScope = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: (scope) => {
          this.scope = scope;
          this.laboratories = scope.laboratories;
          this.trainees = scope.trainees;
          if (initial && !scope.canChooseLaboratory && scope.laboratories.length === 1) {
            this.selectedLaboratoryId = scope.laboratories[0].id;
          }
          if (initial && !scope.canChooseTrainee && scope.trainees.length === 1) {
            this.selectedTraineeId = scope.trainees[0].id;
          }
          if (this.selectedTraineeId !== null && !this.trainees.some(item => item.id === this.selectedTraineeId)) {
            this.selectedTraineeId = !scope.canChooseTrainee && scope.trainees.length === 1
              ? scope.trainees[0].id : null;
          }
          if (initial && scope.role === 'PRACTICANTE') this.loadReport();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.scopeFailed = true;
          this.errorMessage = this.apiError(error, 'No se pudo cargar el alcance de los reportes.');
        },
      });
  }

  changeLaboratory(value: number | null): void {
    this.selectedLaboratoryId = value;
    this.selectedTraineeId = null;
    this.rows = [];
    this.totalRows = 0;
    this.hasQueried = false;
    this.loadScope(value);
  }

  pickerDate(value: string): string { return toIsoDate(value) ?? ''; }

  setDateFromPicker(event: Event, field: 'from' | 'to'): void {
    const iso = (event.target as HTMLInputElement).value;
    if (iso) this[field] = toDisplayDate(iso);
  }

  openDatePicker(input: HTMLInputElement): void { input.showPicker(); }

  loadReport(requestedPage = 1): void {
    this.errorMessage = '';
    this.reportFailed = false;
    const fromIso = toIsoDate(this.from);
    const toIso = toIsoDate(this.to);
    if (!fromIso || !toIso) {
      this.errorMessage = 'Ingresa fechas válidas con formato dd/mm/yyyy.';
      return;
    }
    if (fromIso > toIso) {
      this.errorMessage = 'La fecha inicial debe ser anterior o igual a la fecha final.';
      return;
    }
    if (Date.parse(`${toIso}T00:00:00Z`) - Date.parse(`${fromIso}T00:00:00Z`) > 366 * 86400000) {
      this.errorMessage = 'El rango de consulta no puede superar 366 días.';
      return;
    }
    this.loading = true;
    this.currentPage = requestedPage;
    this.reportService.getReport({
      from: fromIso,
      to: toIso,
      laboratoryId: this.selectedLaboratoryId,
      traineeId: this.selectedTraineeId,
      page: requestedPage - 1,
      size: this.pageSize,
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
      this.loading = false;
      this.changeDetector.markForCheck();
    })).subscribe({
      next: (result: PracticeReport) => {
        this.rows = result.rows.content;
        this.totalRows = result.rows.totalElements;
        this.attendanceDays = result.totalAttendanceDays;
        this.attendanceHours = result.attendanceHours;
        this.acceptedActivityHours = result.acceptedActivityHours;
        this.hasQueried = true;
        this.changeDetector.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.reportFailed = true;
        this.errorMessage = this.apiError(error, 'No se pudo consultar el reporte.');
      },
    });
  }

  activityStatusLabel(status: string): string {
    switch (status) {
      case 'ACEPTADA': return 'Aceptada';
      case 'ENVIADA': return 'Pendiente';
      case 'RECHAZADA': return 'Rechazada';
      default: return 'Borrador';
    }
  }

  formatTime(value: string | null): string {
    if (!value) return '—';
    const match = /T(\d{2}:\d{2})/.exec(value);
    return match?.[1] ?? value;
  }

  formatDate(value: string): string { return toDisplayDate(value); }

  formatHours(value: number): string { return `${Number(value ?? 0).toFixed(2)} h`; }

  private localIsoDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    if (typeof error.error === 'string') {
      try {
        const body = JSON.parse(error.error) as { detail?: string; message?: string };
        return body.detail || body.message || fallback;
      } catch { return fallback; }
    }
    return error.error?.detail || error.error?.message || fallback;
  }
}
