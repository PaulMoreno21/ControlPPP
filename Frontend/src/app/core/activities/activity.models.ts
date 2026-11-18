export type ActivityStatus = 'BORRADOR' | 'ENVIADA' | 'ACEPTADA' | 'RECHAZADA';

export interface Activity {
  id: number;
  date: string;
  description: string;
  durationHours: number;
  status: ActivityStatus;
  lastSubmittedAt: string | null;
  observation: string | null;
  reviewedAt: string | null;
}

export interface ActivityDateRange {
  from: string;
  to: string;
}

export interface ActivityRequest {
  date: string;
  description: string;
  durationHours: number;
}

export interface ActivityDraftRequest {
  description: string;
  durationHours: number;
}

export interface ActivityBatchRequest {
  date: string;
  activities: ActivityDraftRequest[];
}
