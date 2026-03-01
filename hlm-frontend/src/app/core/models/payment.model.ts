export type TrancheStatus = 'PLANNED' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE';
export type PaymentCallStatus = 'DRAFT' | 'ISSUED' | 'OVERDUE' | 'CLOSED';
export type PaymentMethod = 'BANK_TRANSFER' | 'CHECK' | 'CASH' | 'OTHER';

export interface TrancheResponse {
  id: string;
  trancheOrder: number;
  label: string;
  percentage: number;
  amount: number;
  dueDate: string | null;
  triggerCondition: string | null;
  status: TrancheStatus;
}

export interface PaymentScheduleResponse {
  id: string;
  contractId: string;
  notes: string | null;
  createdAt: string;
  tranches: TrancheResponse[];
}

export interface PaymentCallResponse {
  id: string;
  trancheId: string;
  callNumber: number;
  amountDue: number;
  issuedAt: string | null;
  dueDate: string | null;
  status: PaymentCallStatus;
}

export interface PaymentResponse {
  id: string;
  paymentCallId: string;
  amountReceived: number;
  receivedAt: string;
  method: PaymentMethod;
  reference: string | null;
  notes: string | null;
  createdAt: string;
}

export interface CreatePaymentScheduleRequest {
  tranches: TrancheRequest[];
  notes?: string;
}

export interface TrancheRequest {
  label: string;
  percentage: number;
  amount: number;
  dueDate?: string;
  triggerCondition?: string;
}

export interface RecordPaymentRequest {
  amountReceived: number;
  receivedAt: string;
  method: PaymentMethod;
  reference?: string;
  notes?: string;
}
