import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TimeoutError, finalize, timeout } from 'rxjs';
import { AssignedTrainee } from '../../../core/tutor/tutor.models';
import { TutorService } from '../../../core/tutor/tutor.service';
import { TutorPendingService } from '../../../core/tutor/tutor-pending.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-tutor-trainees-page',
  imports: [FormsModule, PaginationComponent, RouterLink],
  templateUrl: './tutor-trainees.page.html',
  styleUrl: './tutor-trainees.page.scss',
})
export class TutorTraineesPage implements OnInit {
  private readonly tutorService = inject(TutorService);
  readonly tutorPending = inject(TutorPendingService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  trainees: AssignedTrainee[] = [];
  totalTrainees = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  search = '';
  loading = false;
  loadFailed = false;
  errorMessage = '';
  private requestVersion = 0;

  get filteredTrainees(): AssignedTrainee[] {
    return this.trainees;
  }

  get visibleTrainees(): AssignedTrainee[] { return this.filteredTrainees; }

  ngOnInit(): void {
    this.tutorPending.refresh();
    this.loadTrainees();
  }

  refresh(): void {
    this.tutorPending.refresh();
    this.loadTrainees();
  }

  loadTrainees(page = 1): void {
    const requestVersion = ++this.requestVersion;
    this.currentPage = page;
    this.loading = true;
    this.loadFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    this.tutorService.listAssignedTraineesPage(page - 1, this.pageSize, this.search)
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestVersion === this.requestVersion) this.loading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (trainees) => {
          if (requestVersion !== this.requestVersion) return;
          this.trainees = trainees.content;
          this.totalTrainees = trainees.totalElements;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestVersion !== this.requestVersion) return;
          this.loadFailed = true;
          this.errorMessage = error instanceof TimeoutError
            ? 'La consulta tardó demasiado. Inténtalo nuevamente.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene permisos para consultar practicantes asignados.';
    if (error.status === 0) return 'No se pudo conectar con el servidor.';
    return body?.detail || body?.message || 'No se pudo cargar la lista de practicantes.';
  }
}
