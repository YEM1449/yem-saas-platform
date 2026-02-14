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
}

export interface ContactPage {
  content: Contact[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
