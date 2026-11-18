export interface HolidaySynchronizationStatus {
  state: 'DISABLED' | 'NOT_CONFIGURED' | 'READY' | 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'ERROR';
  lastAttempt: string | null;
  lastSuccess: string | null;
  added: number;
  message: string;
}

export interface HolidayRecord {
  id: number;
  date: string;
  description: string;
  active: boolean;
  registeredById: number;
  registeredByName: string;
  registeredAt: string;
}

export interface CreateHolidayRequest {
  date: string;
  description: string;
}
