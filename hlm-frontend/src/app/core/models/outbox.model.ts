export type MessageChannel = 'EMAIL' | 'SMS';
export type MessageStatus  = 'PENDING' | 'SENT' | 'FAILED';

export interface OutboundMessage {
  id: string;
  channel: MessageChannel;
  status: MessageStatus;
  recipient: string;
  subject: string | null;
  body: string;
  createdAt: string;
  sentAt: string | null;
  retriesCount: number;
  lastError: string | null;
  correlationType: string | null;
  correlationId: string | null;
  createdByUserId: string;
}

export interface OutboundMessagePage {
  content: OutboundMessage[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface SendMessageRequest {
  channel: MessageChannel;
  contactId?: string | null;
  recipient?: string | null;
  subject?: string | null;
  body: string;
  correlationType?: string | null;
  correlationId?: string | null;
}

export interface SendMessageResponse {
  messageId: string;
}
