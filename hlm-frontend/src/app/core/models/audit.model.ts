export interface AuditEventResponse {
  id: string;
  eventType: string;
  actorUserId: string;
  correlationType: string;
  correlationId: string;
  occurredAt: string;
  payloadJson?: string;
}
