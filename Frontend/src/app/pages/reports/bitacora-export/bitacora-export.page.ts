import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, timeout } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ActivityService } from '../../../core/activities/activity.service';
import { toDisplayDate, toIsoDate } from '../../../core/date-format';
import { ActivityReportService } from '../../../core/reports/activity-report.service';
import { BitacoraFormat } from '../../../core/reports/report.models';

@Component({
  selector: 'app-bitacora-export-page',
  imports: [FormsModule],
  templateUrl: './bitacora-export.page.html',
  styleUrl: './bitacora-export.page.scss',
})
export class BitacoraExportPage implements OnInit {
  private readonly reportService = inject(ActivityReportService);
  private readonly activityService = inject(ActivityService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly formats: { value: BitacoraFormat; label: string }[] = [
    { value: 'pdf', label: 'PDF' }, { value: 'xlsx', label: 'Excel (.xlsx)' },
    { value: 'docx', label: 'Word (.docx)' }, { value: 'csv', label: 'CSV' },
  ];
  traineeId: number | null = null;
  traineeName = '';
  traineeEmail = '';
  from = '';
  to = '';
  format: BitacoraFormat = 'pdf';
  exporting = false;
  loadingRange = false;
  rangeFailed = false;
  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    const user = this.auth.session()?.user;
    if (user?.roles.includes('PRACTICANTE')) {
      this.traineeId = user.id;
      this.traineeName = user.name;
      this.traineeEmail = user.email;
      this.loadDefaultRange();
    }
  }

  loadDefaultRange(): void {
    if (this.traineeId === null || this.loadingRange || this.exporting) return;
    this.loadingRange = true;
    this.rangeFailed = false;
    this.errorMessage = '';
    this.activityService.dateRange()
      .pipe(timeout({ first: 12000 }), takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.loadingRange = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: (range) => {
          this.from = toDisplayDate(range.from);
          this.to = toDisplayDate(range.to);
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.rangeFailed = true;
          this.errorMessage = 'No se pudo consultar el periodo de tu bitácora. Reintenta o selecciona las fechas manualmente.';
          this.changeDetector.markForCheck();
        },
      });
  }

  pickerDate(value: string): string { return toIsoDate(value) ?? ''; }

  setDateFromPicker(event: Event, field: 'from' | 'to'): void {
    const iso = (event.target as HTMLInputElement).value;
    if (iso) this[field] = toDisplayDate(iso);
  }

  openDatePicker(input: HTMLInputElement): void {
    input.showPicker();
  }

  export(): void {
    if (this.loadingRange || this.exporting) return;
    this.errorMessage = '';
    this.successMessage = '';
    const fromIso = toIsoDate(this.from);
    const toIso = toIsoDate(this.to);
    if (this.traineeId === null) { this.errorMessage = 'No se pudo identificar la cuenta del practicante.'; return; }
    if (!fromIso || !toIso) { this.errorMessage = 'Ingresa fechas válidas con formato dd/mm/yyyy.'; return; }
    if (fromIso > toIso) { this.errorMessage = 'La fecha inicial debe ser anterior o igual a la fecha final.'; return; }

    this.exporting = true;
    this.reportService.exportBitacora(this.traineeId, fromIso, toIso, this.format)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.exporting = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: (response) => {
          const blob = response.body;
          if (!blob) { this.errorMessage = 'No se pudo exportar la bitácora. Inténtalo nuevamente.'; return; }
          const disposition = response.headers.get('content-disposition') ?? '';
          const filename = /filename="?([^";]+)"?/i.exec(disposition)?.[1]
            ?? `bitacora_${this.traineeId}_${fromIso}_${toIso}.${this.format}`;
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = filename;
          link.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
          this.successMessage = `Bitácora exportada en ${this.format.toUpperCase()}.`;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage = this.exportError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  dismissError(): void { this.errorMessage = ''; }

  private exportError(error: HttpErrorResponse): string {
    if (error.status === 401) return 'Tu sesión venció. Inicia sesión e inténtalo nuevamente.';
    if (error.status === 403) return 'No tienes permiso para exportar esta bitácora.';
    if (error.status === 0) return 'No se pudo conectar con el servidor. Inténtalo nuevamente.';
    return 'No se pudo exportar la bitácora. Inténtalo nuevamente.';
  }
}
