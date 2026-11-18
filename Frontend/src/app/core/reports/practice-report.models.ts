import { PageResult } from '../../shared/pagination/page-result';
import { LaboratorySummary } from '../administration/user-administration.models';

export interface ReportTraineeOption {
  id: number;
  name: string;
  email: string;
  laboratoryId: number | null;
  laboratoryName: string;
}

export interface ReportScope {
  role: 'ADMIN_GENERAL' | 'ADMIN_LABORATORIO' | 'TUTOR_ESPE' | 'PRACTICANTE';
  laboratories: LaboratorySummary[];
  trainees: ReportTraineeOption[];
  canChooseLaboratory: boolean;
  canChooseTrainee: boolean;
}

export type ReportActivityStatus = 'BORRADOR' | 'ENVIADA' | 'ACEPTADA' | 'RECHAZADA';

export interface ReportActivity {
  id: number;
  date: string;
  description: string;
  hours: number;
  status: ReportActivityStatus;
}

export interface PracticeReportRow {
  traineeId: number;
  traineeName: string;
  traineeEmail: string;
  laboratoryName: string;
  date: string;
  checkIn: string | null;
  checkOut: string | null;
  attendanceHours: number;
  acceptedActivityHours: number;
  activities: ReportActivity[];
}

export interface PracticeReport {
  rows: PageResult<PracticeReportRow>;
  totalAttendanceDays: number;
  attendanceHours: number;
  acceptedActivityHours: number;
}
