import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TimeoutError, finalize, timeout } from 'rxjs';
import { ActivityReviewDecision, PendingActivitySubmission } from '../../../core/tutor/tutor.models';
import { TutorService } from '../../../core/tutor/tutor.service';

@Component({
  selector: 'app-tutor-reviews-page',
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './tutor-reviews.page.html',
  styleUrl: './tutor-reviews.page.scss',
})
export class TutorReviewsPage implements OnInit {
  private readonly tutorService = inject(TutorService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  submissions: PendingActivitySubmission[] = [];
  search = '';
  loading = false;
  loadFailed = false;
  reviewingId: number | null = null;
  errorMessage = '';
  successMessage = '';
  selectedSubmission: PendingActivitySubmission | null = null;
  selectedDecision: ActivityReviewDecision | null = null;
  observation = '';

  get filteredSubmissions(): PendingActivitySubmission[] {
    const term = this.search.trim().toLocaleLowerCase();
    if (!term) return this.submissions;
    return this.submissions.filter((submission) =>
      `${submission.traineeName} ${submission.description}`.toLocaleLowerCase().includes(term),
    );
  }

  ngOnInit(): void {
    this.loadPending();
  }

  loadPending(): void {
    this.loading = true;
    this.loadFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    this.tutorService.listPendingSubmissions()
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (submissions) => {
          this.submissions = submissions;
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

  requestReview(submission: PendingActivitySubmission, decision: ActivityReviewDecision): void {
    this.errorMessage = '';
    this.observation = '';
    this.selectedSubmission = submission;
    this.selectedDecision = decision;
  }

  cancelReview(): void {
    if (this.reviewingId !== null) return;
    this.selectedSubmission = null;
    this.selectedDecision = null;
    this.observation = '';
    this.errorMessage = '';
  }

  confirmReview(): void {
    const submission = this.selectedSubmission;
    const decision = this.selectedDecision;
    if (submission === null || decision === null || this.reviewingId !== null) return;
    const normalizedObservation = this.observation.trim();
    if (decision === 'RECHAZADA' && !normalizedObservation) {
      this.errorMessage = 'Escribe una observación para explicar el rechazo.';
      return;
    }

    this.reviewingId = submission.id;
    this.errorMessage = '';
    this.successMessage = '';
    this.tutorService.reviewSubmission(submission.id, {
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
          this.submissions = this.submissions.filter((item) => item.id !== submission.id);
          this.successMessage = decision === 'ACEPTADA'
            ? `Se aceptó la actividad enviada por ${submission.traineeName}.`
            : `Se rechazó la actividad enviada por ${submission.traineeName}.`;
          this.selectedSubmission = null;
          this.selectedDecision = null;
          this.observation = '';
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La revisión tardó demasiado. Actualiza la lista para confirmar el resultado.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  formatActivityDate(value: string): string {
    const [year, month, day] = value.split('-');
    return `${day}/${month}/${year}`;
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene permisos para revisar esta actividad.';
    if (error.status === 0) return 'No se pudo conectar con el servidor.';
    return body?.detail || body?.message || 'No se pudo guardar la revisión.';
  }
}
