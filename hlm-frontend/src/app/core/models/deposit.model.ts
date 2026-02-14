export interface Deposit {
  id: string;
  contactId: string;
  propertyId: string;
  agentId: string;
  amount: number;
  currency: string;
  depositDate: string;
  reference: string;
  status: string;
  notes: string | null;
  dueDate: string | null;
  confirmedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DepositReportResponse {
  items: Deposit[];
  count: number;
  totalAmount: number;
  byAgent: unknown[];
}
