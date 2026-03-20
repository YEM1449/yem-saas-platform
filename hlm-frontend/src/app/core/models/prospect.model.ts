export interface Prospect {
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

export interface ProspectPage {
  content: Prospect[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const PROSPECT_STATUSES = [
  'PROSPECT',
  'QUALIFIED_PROSPECT',
  'LOST',
] as const;
