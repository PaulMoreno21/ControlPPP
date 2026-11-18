export interface AuditLogEntry {
  id: number;
  actorId: number | null;
  actorName: string;
  action: string;
  entityName: string;
  entityId: number | null;
  occurredAt: string;
  details: string | null;
}
