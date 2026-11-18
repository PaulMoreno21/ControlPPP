import { PageResult } from '../../shared/pagination/page-result';

export interface TutorPendingCounts {
  total: number;
  trainees: { traineeId: number; pendingActivitiesCount: number }[];
}

export interface AssignedTrainee {
  id: number;
  fullName: string;
  email: string;
  laboratoryId: number;
  laboratoryName: string;
}

export interface PendingActivitySubmission {
  id: number;
  activityId: number;
  traineeId: number;
  traineeName: string;
  activityDate: string;
  description: string;
  durationHours: number;
  submissionNumber: number;
  submittedAt: string;
}

export type ActivityReviewDecision = 'ACEPTADA' | 'RECHAZADA';

export interface ActivityReviewRequest {
  decision: ActivityReviewDecision;
  observation?: string;
}

export type TutorActivityStatus = 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA';

export interface TutorActivity {
  activityId: number;
  date: string;
  description: string;
  durationHours: number;
  status: TutorActivityStatus;
  submissionId: number | null;
  submissionNumber: number | null;
  submittedAt: string | null;
  observation: string | null;
  reviewedAt: string | null;
}

export interface TutorTraineeActivityHistory {
  trainee: AssignedTrainee;
  acceptedHoursTotal: number;
  acceptedActivitiesCount: number;
  pendingActivitiesCount: number;
  activities: PageResult<TutorActivity>;
}
