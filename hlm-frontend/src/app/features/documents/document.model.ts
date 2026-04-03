export type DocumentEntityType = 'CONTACT' | 'CONTRACT' | 'DEPOSIT' | 'PROPERTY' | 'RESERVATION' | 'PROJECT';

export interface DocumentResponse {
  id: string;
  societeId: string;
  entityType: DocumentEntityType;
  entityId: string;
  fileName: string;
  mimeType: string;
  fileSize?: number;
  description?: string;
  uploadedBy: string;
  createdAt: string;
}
