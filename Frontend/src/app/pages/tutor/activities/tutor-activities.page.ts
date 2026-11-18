import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TimeoutError, finalize, timeout } from 'rxjs';
import { ActivityReviewDecision, TutorActivity, TutorActivityStatus, TutorTraineeActivityHistory } from '../../../core/tutor/tutor.models';
import { TutorService } from '../../../core/tutor/tutor.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-tutor-activities-page',
  imports: [DatePipe, DecimalPipe, FormsModule, PaginationComponent, RouterLink],
  templateUrl: './tutor-activities.page.html',
  styleUrl: './tutor-activities.page.scss',
})
export class TutorActivitiesPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly tutorService = inject(TutorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly traineeId = Number(this.route.snapshot.paramMap.get('traineeId'));
  history: TutorTraineeActivityHistory | null = null;
  loading = false;
  loadFailed = false;
  errorMessage = '';
  successMessage = '';
  selectedActivity: TutorActivity | null = null;
  selectedActivityNumber: number | null = null;
  selectedDecision: ActivityReviewDecision | null = null;
  observation = '';
  reviewError = '';
  reviewingId: number | null = null;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;

  get visibleActivities(): TutorActivity[] {
    return this.history?.activities.content ?? [];
  }

  get pendingCount(): number {
    return this.history?.pendingActivitiesCount ?? 0;
  }

  get acceptedCount(): number {
    return this.history?.acceptedActivitiesCount ?? 0;
  }

  ngOnInit(): void {
    this.loadActivities();
  }

  loadActivities(page = 1): void {
    if (!Number.isSafeInteger(this.traineeId) || this.traineeId <= 0) {
      this.loadFailed = true;
      this.errorMessage = 'No se encontró el practicante solicitado.';
      return;
    }

    this.loading = true;
    this.currentPage = page;
    this.loadFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    this.tutorService.getTraineeActivitiesPage(this.traineeId, page - 1, this.pageSize)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (history) => {
          this.history = history;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.loadFailed = true;
          this.errorMessage = error instanceof TimeoutError
            ? 'La consulta tardó demasiado. Inténtalo nuevamente.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  activityNumber(index: number): number {
    return (this.currentPage - 1) * this.pageSize + index + 1;
  }

  requestReview(activity: TutorActivity, decision: ActivityReviewDecision, index: number): void {
    if (activity.status !== 'PENDIENTE' || activity.submissionId === null) return;
    this.reviewError = '';
    this.observation = '';
    this.selectedActivity = activity;
    this.selectedActivityNumber = this.activityNumber(index);
    this.selectedDecision = decision;
  }

  cancelReview(): void {
    if (this.reviewingId !== null) return;
    this.selectedActivity = null;
    this.selectedActivityNumber = null;
    this.selectedDecision = null;
    this.observation = '';
    this.reviewError = '';
  }

  confirmReview(): void {
    const activity = this.selectedActivity;
    const decision = this.selectedDecision;
    if (activity === null || decision === null || activity.submissionId === null || this.reviewingId !== null) return;

    const normalizedObservation = this.observation.trim();
    if (decision === 'RECHAZADA' && !normalizedObservation) {
      this.reviewError = 'Escribe una observación para explicar el rechazo.';
      return;
    }

    this.reviewingId = activity.submissionId;
    this.reviewError = '';
    this.successMessage = '';
    this.tutorService.reviewSubmission(activity.submissionId, {
      decision,
      ...(decision === 'RECHAZADA' ? { observation: normalizedObservation } : {}),
    })
      .pipe(
        timeout({ first: 15000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.reviewingId = null;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.successMessage = decision === 'ACEPTADA'
            ? 'La actividad fue aceptada y sus horas se sumaron al total aprobado.'
            : 'La actividad fue rechazada. El practicante podrá corregirla y enviarla nuevamente.';
          this.reviewingId = null;
          this.cancelReview();
          this.loadActivities();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.reviewError = error instanceof TimeoutError
            ? 'La revisión tardó demasiado. Actualiza el historial para confirmar el resultado.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  formatDate(value: string): string {
    const [year, month, day] = value.split('-');
    return `${day}/${month}/${year}`;
  }

  statusLabel(status: TutorActivityStatus): string {
    const labels: Record<TutorActivityStatus, string> = {
      PENDIENTE: 'Pendiente',
      ACEPTADA: 'Aceptada',
      RECHAZADA: 'Rechazada',
    };
    return labels[status];
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Este practicante no está asignado a tu tutoría vigente.';
    if (error.status === 0) return 'No se pudo conectar con el servidor.';
    return body?.detail || body?.message || 'No se pudo completar la operación.';
  }
}
