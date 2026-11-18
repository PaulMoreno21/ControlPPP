export interface AssignmentRecord {
  id: number;
  traineeId: number;
  traineeName: string;
  tutorId: number;
  tutorName: string;
  laboratoryId: number;
  laboratoryName: string;
  assignedById: number;
  assignedAt: string;
  current: boolean;
}

export interface AssignmentRequest {
  traineeId: number;
  tutorId: number;
}
