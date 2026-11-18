import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, TimeoutError, finalize, timeout } from 'rxjs';
import { CreateLaboratoryRequest, LaboratorySummary } from '../../../core/administration/user-administration.models';
import { UserAdministrationService } from '../../../core/administration/user-administration.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-admin-laboratories-page',
  imports: [PaginationComponent, ReactiveFormsModule],
  templateUrl: './admin-laboratories.page.html',
  styleUrl: './admin-laboratories.page.scss',
})
export class AdminLaboratoriesPage implements OnInit {
  @ViewChild('laboratoryForm') private laboratoryForm?: ElementRef<HTMLElement>;
  private catalogSubscription?: Subscription;
  private readonly formBuilder = inject(FormBuilder);
  private readonly administrationService = inject(UserAdministrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    location: ['', Validators.maxLength(150)],
  });
  laboratories: LaboratorySummary[] = [];
  totalLaboratories = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  loading = false;
  loadFailed = false;
  saving = false;
  editingLaboratoryId: number | null = null;
  deletingLaboratoryId: number | null = null;
  pendingDeleteLaboratory: LaboratorySummary | null = null;
  errorMessage = '';
  successMessage = '';

  get visibleLaboratories(): LaboratorySummary[] { return this.laboratories; }

  ngOnInit(): void { this.loadLaboratories(); }

  loadLaboratories(page = 1): void {
    this.currentPage = page;
    this.catalogSubscription?.unsubscribe();
    this.loading = true;
    this.loadFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    this.catalogSubscription = this.administrationService.listLaboratoriesPage(page - 1, this.pageSize)
      .pipe(
        timeout({ first: 6000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (result) => {
          this.laboratories = result.content;
          this.totalLaboratories = result.totalElements;
          this.currentPage = page;
          this.loadFailed = false;
          this.loading = false;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.loadFailed = true;
          this.loading = false;
          this.errorMessage = error instanceof TimeoutError
            ? 'La aplicacion tardo en responder, vuelve a intentar.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  saveLaboratory(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving) return;
    const value = this.form.getRawValue();
    const request: CreateLaboratoryRequest = {
      name: value.name.trim(),
      location: value.location.trim() || null,
    };
    if (!request.name) {
      this.form.controls.name.setErrors({ required: true });
      return;
    }
    const duplicate = this.laboratories.some(
      (laboratory) => laboratory.id !== this.editingLaboratoryId
        && laboratory.name.trim().toLocaleLowerCase() === request.name.toLocaleLowerCase(),
    );
    if (duplicate) {
      this.errorMessage = 'Ya existe otro laboratorio con ese nombre.';
      return;
    }
    if (this.editingLaboratoryId !== null) {
      this.updateLaboratory(this.editingLaboratoryId, request);
      return;
    }

    this.registerLaboratory(request);
  }

  editLaboratory(laboratory: LaboratorySummary): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.editingLaboratoryId = laboratory.id;
    this.form.reset({ name: laboratory.name, location: laboratory.location ?? '' });
    this.changeDetector.markForCheck();
    this.laboratoryForm?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelEdit(): void {
    this.editingLaboratoryId = null;
    this.form.reset({ name: '', location: '' });
    this.changeDetector.markForCheck();
  }

  requestDelete(laboratory: LaboratorySummary): void {
    this.pendingDeleteLaboratory = laboratory;
  }

  cancelDelete(): void {
    if (this.deletingLaboratoryId !== null) return;
    this.pendingDeleteLaboratory = null;
  }

  confirmDelete(): void {
    const laboratory = this.pendingDeleteLaboratory;
    if (laboratory === null || this.deletingLaboratoryId !== null || this.saving) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.deletingLaboratoryId = laboratory.id;
    this.administrationService.deleteLaboratory(laboratory.id)
      .pipe(
        timeout({ first: 15000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.deletingLaboratoryId = null;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.laboratories = this.laboratories.filter((item) => item.id !== laboratory.id);
          this.successMessage = `Se eliminó el laboratorio ${laboratory.name}.`;
          this.pendingDeleteLaboratory = null;
          if (this.editingLaboratoryId === laboratory.id) this.cancelEdit();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.pendingDeleteLaboratory = null;
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza el catálogo para confirmar si se eliminó.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  private registerLaboratory(request: CreateLaboratoryRequest): void {
    this.saving = true;
    this.changeDetector.markForCheck();
    this.administrationService.createLaboratory(request)
      .pipe(timeout({ first: 15000 }))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (laboratory) => {
          this.catalogSubscription?.unsubscribe();
          this.catalogSubscription = undefined;
          this.successMessage = `Se registró el laboratorio ${laboratory.name}.`;
          this.laboratories = [...this.laboratories.filter((item) => item.id !== laboratory.id), laboratory]
            .sort((first, second) => first.name.localeCompare(second.name));
          this.loadFailed = false;
          this.loading = false;
          this.form.reset({ name: '', location: '' });
          this.saving = false;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (error instanceof TimeoutError) {
            this.verifyAfterTimeout(request);
            return;
          }
          this.errorMessage = this.apiError(error);
          this.saving = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  private updateLaboratory(laboratoryId: number, request: CreateLaboratoryRequest): void {
    this.saving = true;
    this.changeDetector.markForCheck();
    this.administrationService.updateLaboratory(laboratoryId, request)
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
          this.laboratories = [...this.laboratories.filter((item) => item.id !== updated.id), updated]
            .sort((first, second) => first.name.localeCompare(second.name));
          this.successMessage = `Se actualizaron los datos del laboratorio ${updated.name}.`;
          this.resetEditor();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = error instanceof TimeoutError
            ? 'La solicitud tardó demasiado. Actualiza el catálogo para confirmar si se guardaron los cambios.'
            : this.apiError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  private resetEditor(): void {
    this.editingLaboratoryId = null;
    this.form.reset({ name: '', location: '' });
  }

  private verifyAfterTimeout(request: CreateLaboratoryRequest): void {
    this.administrationService.listLaboratories()
      .pipe(timeout({ first: 8000 }), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (laboratories) => {
          this.laboratories = laboratories;
          this.loadFailed = false;
          const created = laboratories.find(
            (laboratory) => laboratory.name.trim().toLocaleLowerCase() === request.name.toLocaleLowerCase(),
          );
          if (created) {
            this.successMessage = `El laboratorio ${created.name} aparece en el catálogo. La solicitud tardó en responder.`;
            this.resetEditor();
          } else {
            this.errorMessage = 'No se confirmó el registro y no aparece en el catálogo. Espera un momento y actualiza antes de intentar nuevamente.';
          }
          this.saving = false;
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.errorMessage = 'La solicitud tardó demasiado y no se pudo confirmar el resultado. Actualiza el catálogo antes de volver a registrar.';
          this.saving = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  private apiError(error: HttpErrorResponse): string {
    const body = error.error as { detail?: string; message?: string } | null;
    if (error.status === 401) return 'La sesión venció o no es válida. Cierra sesión e ingresa nuevamente.';
    if (error.status === 403) return 'Tu cuenta no tiene los permisos necesarios necesario para consultar laboratorios.';
    if (error.status === 0) return 'No se pudo conectar';
    return body?.detail || body?.message || 'No se pudo completar la operación. Intenta nuevamente.';
  }
}
