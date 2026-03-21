export interface Contact {
  id: string;
  contactType: string;
  status: string;
  qualified: boolean;
  tempClientUntil: string | null;
  firstName: string;
  lastName: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  nationalId: string | null;
  address: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  // GDPR / Law 09-08 consent fields
  consentGiven: boolean;
  consentDate: string | null;
  consentMethod: string | null;
  processingBasis: string | null;
}

export interface PageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ContactPage {
  content: Contact[];
  page: PageMeta;
}

export interface TimelineEvent {
  timestamp: string;
  eventType: string;
  category: 'AUDIT' | 'MESSAGE' | 'NOTIFICATION' | 'STATUS_CHANGE';
  summary: string;
  correlationId: string;
}
