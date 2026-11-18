import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TimeoutError, finalize, forkJoin, timeout } from 'rxjs';
import { AssignmentAdministrationService } from '../../../core/administration/assignment-administration.service';
import { AssignmentRecord } from '../../../core/administration/assignment.models';
import { UserAdministrationService } from '../../../core/administration/user-administration.service';
import { LaboratorySummary, ManagedRole, ManagedUser } from '../../../core/administration/user-administration.models';
import { AuthService } from '../../../core/auth/auth.service';
import { PaginationComponent } from '../../../shared/pagination/pagination.component';
import { TABLE_PAGE_SIZE } from '../../../shared/pagination/pagination';

@Component({
  selector: 'app-admin-assignments-page',
  imports: [DatePipe, FormsModule, PaginationComponent],
  templateUrl: './admin-assignments.page.html',
  styleUrl: './admin-assignments.page.scss',
})
export class AdminAssignmentsPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly userService = inject(UserAdministrationService);
  private readonly assignmentService = inject(AssignmentAdministrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly roleNames: Record<ManagedRole, string> = {
    PRACTICANTE: 'Practicante',
    TUTOR_ESPE: 'Tutor ESPE',
    ADMIN_LABORATORIO: 'Administrador de laboratorio',
    ADMIN_GENERAL: 'Administrador general',
  };
  laboratories: LaboratorySummary[] = [];
  selectedLaboratoryId: number | null = null;
  users: ManagedUser[] = [];
  assignments: AssignmentRecord[] = [];
  totalAssignments = 0;
  readonly pageSize = TABLE_PAGE_SIZE;
  currentPage = 1;
  tutorSearch = '';
  selectedTraineeId: number | null = null;
  selectedTutorId: number | null = null;
  traineeSearch = '';
  tutorOptionSearch = '';
  traineeOptionsOpen = false;
  tutorOptionsOpen = false;
  pendingReassignment: { traineeId: number; tutorId: number } | null = null;
  pendingUnlink: AssignmentRecord | null = null;
  unlinkingAssignmentId: number | null = null;
  loadingLaboratories = false;
  loadingData = false;
  loadingFailed = false;
  assigning = false;
  errorMessage = '';
  successMessage = '';
  private assignmentRequestId = 0;

  get isGlobalAdmin(): boolean {
    return this.auth.session()?.user.roles.includes('ADMIN_GENERAL') ?? false;
  }

  get trainees(): ManagedUser[] {
    return this.users.filter((user) => user.active === 1 && user.roles.includes('PRACTICANTE'));
  }

  get tutors(): ManagedUser[] {
    return this.users.filter((user) => user.active === 1 && user.roles.includes('TUTOR_ESPE'));
  }

  get filteredTrainees(): ManagedUser[] {
    return this.filterUsers(this.trainees, this.traineeSearch);
  }

  get filteredTutors(): ManagedUser[] {
    return this.filterUsers(this.tutors, this.tutorOptionSearch);
  }

  get activeAssignments(): AssignmentRecord[] {
    return this.assignments;
  }

  get filteredAssignments(): AssignmentRecord[] {
    return this.activeAssignments;
  }

  get visibleAssignments(): AssignmentRecord[] {
    return this.filteredAssignments;
  }

  get selectedTraineeHasAssignment(): boolean {
    return this.selectedTraineeId !== null
      && this.assignments.some((assignment) => assignment.current && assignment.traineeId === this.selectedTraineeId);
  }

  get selectedTutorAlreadyAssigned(): boolean {
    return this.selectedTraineeId !== null && this.selectedTutorId !== null
      && this.assignments.some((assignment) => assignment.current
        && assignment.traineeId === this.selectedTraineeId
        && assignment.tutorId === this.selectedTutorId);
  }

  selectedTraineeName(): string {
    const trainee = this.trainees.find((user) => user.id === this.pendingReassignment?.traineeId);
    return trainee ? `${trainee.firstName} ${trainee.lastName}` : 'seleccionado';
  }

  selectedTutorName(): string {
    const tutor = this.tutors.find((user) => user.id === this.pendingReassignment?.tutorId);
    return tutor ? `${tutor.firstName} ${tutor.lastName}` : 'el tutor seleccionado';
  }

  ngOnInit(): void {
    this.loadLaboratories();
  }

  loadLaboratories(): void {
    this.errorMessage = '';
    this.loadingLaboratories = true;
    this.loadingFailed = false;
    this.changeDetector.markForCheck();
    const request = this.isGlobalAdmin
      ? this.userService.listLaboratories()
      : this.userService.listAccessibleLaboratories();
    request
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loadingLaboratories = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: (laboratories) => {
          this.laboratories = laboratories;
          this.selectedLaboratoryId = !this.isGlobalAdmin && laboratories.length === 1
            ? laboratories[0].id
            : null;
          if (this.selectedLaboratoryId !== null) this.loadLaboratoryData();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.loadingFailed = true;
          this.errorMessage = this.apiError(error, 'No se pudo cargar el laboratorio asignado.');
          this.changeDetector.markForCheck();
        },
      });
  }

  changeLaboratory(id: number | null): void {
    this.selectedLaboratoryId = id;
    this.users = [];
    this.assignments = [];
    this.selectedTraineeId = null;
    this.selectedTutorId = null;
    this.traineeSearch = '';
    this.tutorOptionSearch = '';
    this.traineeOptionsOpen = false;
    this.tutorOptionsOpen = false;
    this.tutorSearch = '';
    this.errorMessage = '';
    this.successMessage = '';
    this.changeDetector.markForCheck();
    if (id !== null) this.loadLaboratoryData();
  }

  loadLaboratoryData(page = 1): void {
    const laboratoryId = this.selectedLaboratoryId;
    if (laboratoryId === null) return;
    const requestId = ++this.assignmentRequestId;
    this.currentPage = page;
    this.loadingData = true;
    this.loadingFailed = false;
    this.errorMessage = '';
    this.changeDetector.markForCheck();
    forkJoin({
      users: this.userService.listUsers(laboratoryId),
      assignments: this.assignmentService.listPage(laboratoryId, page - 1, this.pageSize, this.tutorSearch),
    })
      .pipe(
        timeout({ first: 12000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestId === this.assignmentRequestId) this.loadingData = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: ({ users, assignments }) => {
          if (requestId !== this.assignmentRequestId) return;
          this.users = users;
          this.assignments = assignments.content;
          this.totalAssignments = assignments.totalElements;
          this.currentPage = page;
          this.currentPage = 1;
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          if (requestId !== this.assignmentRequestId) return;
          this.loadingFailed = true;
          this.errorMessage = this.apiError(error, 'No se pudieron cargar los usuarios y las asignaciones.');
          this.changeDetector.markForCheck();
        },
      });
  }

  requestAssignment(): void {
    this.errorMessage = '';
    this.successMessage = '';
    if (this.selectedLaboratoryId === null || this.selectedTraineeId === null || this.selectedTutorId === null) {
      this.errorMessage = 'Selecciona un practicante y un tutor.';
      return;
    }
    if (this.selectedTutorAlreadyAssigned) {
      this.successMessage = 'El practicante ya tiene este tutor asignado.';
      return;
    }
    const selection = { traineeId: this.selectedTraineeId, tutorId: this.selectedTutorId };
    if (this.selectedTraineeHasAssignment) {
      this.pendingReassignment = selection;
      return;
    }
    this.saveAssignment(selection);
  }

  updateTraineeSearch(value: string): void {
    this.traineeSearch = value;
    this.selectedTraineeId = null;
    this.traineeOptionsOpen = true;
  }

  updateTutorOptionSearch(value: string): void {
    this.tutorOptionSearch = value;
    this.selectedTutorId = null;
    this.tutorOptionsOpen = true;
  }

  selectTrainee(trainee: ManagedUser): void {
    this.selectedTraineeId = trainee.id;
    this.traineeSearch = `${trainee.firstName} ${trainee.lastName} · ${trainee.email}`;
    this.traineeOptionsOpen = false;
  }

  selectTutor(tutor: ManagedUser): void {
    this.selectedTutorId = tutor.id;
    this.tutorOptionSearch = `${tutor.firstName} ${tutor.lastName} · ${tutor.email}`;
    this.tutorOptionsOpen = false;
  }

  confirmReassignment(): void {
    if (this.pendingReassignment === null) return;
    const selection = this.pendingReassignment;
    this.pendingReassignment = null;
    this.saveAssignment(selection);
  }

  cancelReassignment(): void {
    if (this.assigning) return;
    this.pendingReassignment = null;
  }

  requestUnlink(assignment: AssignmentRecord): void {
    if (!assignment.current || this.assigning || this.unlinkingAssignmentId !== null) return;
    this.errorMessage = '';
    this.successMessage = '';
    this.pendingUnlink = assignment;
  }

  cancelUnlink(): void {
    if (this.unlinkingAssignmentId !== null) return;
    this.pendingUnlink = null;
  }

  confirmUnlink(): void {
    const assignment = this.pendingUnlink;
    const laboratoryId = this.selectedLaboratoryId;
    if (assignment === null || laboratoryId === null || this.unlinkingAssignmentId !== null) return;
    this.unlinkingAssignmentId = assignment.id;
    this.errorMessage = '';
    this.successMessage = '';
    this.assignmentService.unlinkTutor(laboratoryId, assignment.id)
      .pipe(
        timeout({ first: 15000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.unlinkingAssignmentId = null;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.pendingUnlink = null;
          this.successMessage = 'Se desvinculó el tutor. La asignación se conservó en el historial.';
          this.loadLaboratoryData();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.pendingUnlink = null;
          this.errorMessage = this.apiError(error, 'No se pudo desvincular al tutor.');
          this.changeDetector.markForCheck();
        },
      });
  }

  private saveAssignment(selection: { traineeId: number; tutorId: number }): void {
    const laboratoryId = this.selectedLaboratoryId;
    if (laboratoryId === null || this.assigning) return;
    const wasReassignment = this.assignments.some(
      (assignment) => assignment.current && assignment.traineeId === selection.traineeId,
    );
    this.assigning = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.changeDetector.markForCheck();
    this.assignmentService.assignTutor(laboratoryId, selection)
      .pipe(
        timeout({ first: 15000 }),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.assigning = false;
          this.changeDetector.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.successMessage = wasReassignment
            ? 'Se actualizó el tutor asignado al practicante.'
            : 'Se asignó el tutor al practicante.';
          this.selectedTraineeId = null;
          this.selectedTutorId = null;
          this.traineeSearch = '';
          this.tutorOptionSearch = '';
          this.loadLaboratoryData();
          this.changeDetector.markForCheck();
        },
        error: (error: HttpErrorResponse | TimeoutError) => {
          this.errorMessage = this.apiError(error, 'No se pudo guardar la asignación.');
          this.changeDetector.markForCheck();
        },
      });
  }

  roleLabel(role: ManagedRole): string {
    return this.roleNames[role];
  }

  private filterUsers(users: ManagedUser[], search: string): ManagedUser[] {
    const term = search.trim().toLocaleLowerCase();
    if (!term) return users;
    return users.filter((user) =>
      `${user.firstName} ${user.lastName} ${user.email}`.toLocaleLowerCase().includes(term),
    );
  }

  private apiError(error: HttpErrorResponse | TimeoutError, fallback: string): string {
    if (error instanceof TimeoutError) return 'La solicitud tardó demasiado. Actualiza la vista e inténtalo otra vez.';
    const body = error.error as { detail?: string; message?: string } | null;
    return body?.detail || body?.message || fallback;
  }
}
