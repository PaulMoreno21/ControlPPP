import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { AuthenticatedLayout } from './layout/authenticated-layout/authenticated-layout';
import { HomePage } from './pages/home/home.page';
import { LoginPage } from './pages/login/login.page';
import { AdminUsersPage } from './pages/admin/users/admin-users.page';
import { AdminLaboratoriesPage } from './pages/admin/laboratories/admin-laboratories.page';
import { TraineeActivitiesPage } from './pages/trainee/activities/trainee-activities.page';
import { AdminHolidaysPage } from './pages/admin/holidays/admin-holidays.page';
import { AdminAuditPage } from './pages/admin/audit/admin-audit.page';
import { TraineeAttendancePage } from './pages/trainee/attendance/trainee-attendance.page';
import { AdminAssignmentsPage } from './pages/admin/assignments/admin-assignments.page';
import { TutorTraineesPage } from './pages/tutor/trainees/tutor-trainees.page';
import { TutorActivitiesPage } from './pages/tutor/activities/tutor-activities.page';
import { BitacoraExportPage } from './pages/reports/bitacora-export/bitacora-export.page';
import { PracticeReportPage } from './pages/reports/practice-report/practice-report.page';
import { AccountSecurityPage } from './pages/account/security/account-security.page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: 'login', component: LoginPage },
  {
    path: '',
    component: AuthenticatedLayout,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: 'inicio', component: HomePage },
      { path: 'cuenta/seguridad', component: AccountSecurityPage },
      {
        path: 'reportes',
        component: PracticeReportPage,
        canActivate: [roleGuard(['ADMIN_GENERAL', 'ADMIN_LABORATORIO', 'TUTOR_ESPE', 'PRACTICANTE'])],
      },
      {
        path: 'bitacora/exportar',
        component: BitacoraExportPage,
        canActivate: [roleGuard('PRACTICANTE')],
      },
      {
        path: 'tutor/practicantes/:traineeId/actividades',
        component: TutorActivitiesPage,
        canActivate: [roleGuard('TUTOR_ESPE')],
      },
      {
        path: 'tutor/practicantes',
        component: TutorTraineesPage,
        canActivate: [roleGuard('TUTOR_ESPE')],
      },
      {
        path: 'admin/usuarios',
        component: AdminUsersPage,
        canActivate: [roleGuard(['ADMIN_GENERAL', 'ADMIN_LABORATORIO'])],
      },
      {
        path: 'admin/asignaciones',
        component: AdminAssignmentsPage,
        canActivate: [roleGuard(['ADMIN_GENERAL', 'ADMIN_LABORATORIO'])],
      },
      {
        path: 'admin/laboratorios',
        component: AdminLaboratoriesPage,
        canActivate: [roleGuard('ADMIN_GENERAL')],
      },
      {
        path: 'admin/feriados',
        component: AdminHolidaysPage,
        canActivate: [roleGuard('ADMIN_GENERAL')],
      },
      {
        path: 'admin/auditoria',
        component: AdminAuditPage,
        canActivate: [roleGuard('ADMIN_GENERAL')],
      },
      {
        path: 'practicante/horario',
        component: TraineeAttendancePage,
        canActivate: [roleGuard('PRACTICANTE')],
      },
      {
        path: 'practicante/actividades/nueva',
        component: TraineeActivitiesPage,
        canActivate: [roleGuard('PRACTICANTE')],
        data: { createMode: true },
      },
      {
        path: 'practicante/actividades',
        component: TraineeActivitiesPage,
        canActivate: [roleGuard('PRACTICANTE')],
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
