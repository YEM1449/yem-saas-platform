export type PaymentScheduleStatus = 'DRAFT' | 'ISSUED' | 'SENT' | 'OVERDUE' | 'PAID' | 'CANCELED';

export interface PaymentScheduleItem {
  id: string;
  contractId: string;
  projectId: string;
  propertyId: string;
  sequence: number;
  label: string;
  amount: number;
  amountPaid: number;
  amountRemaining: number;
  dueDate: string;
  status: PaymentScheduleStatus;
  issuedAt: string | null;
  sentAt: string | null;
  canceledAt: string | null;
  notes: string | null;
  createdAt: string;
}

export interface SchedulePayment {
  id: string;
  scheduleItemId: string;
  amountPaid: number;
  paidAt: string;
  channel: string | null;
  paymentReference: string | null;
  notes: string | null;
  createdAt: string;
}

export interface CreateScheduleItemRequest {
  label: string;
  amount: number;
  dueDate: string;
  notes?: string;
}

export interface UpdateScheduleItemRequest {
  label?: string;
  amount?: number;
  dueDate?: string;
  notes?: string;
}

export interface AddPaymentRequest {
  amount: number;
  paidAt: string;
  channel?: string;
  paymentReference?: string;
  notes?: string;
}

export interface SendScheduleItemRequest {
  contactId?: string;
  emailOverride?: string;
  smsOverride?: string;
  sendEmail: boolean;
  sendSms: boolean;
}

// Cash dashboard
export interface CashDashboardResponse {
  from: string;
  to: string;
  expectedInPeriod: number;
  issuedInPeriod: number;
  collectedInPeriod: number;
  overdueAmount: number;
  overdueCount: number;
  agingBuckets: AgingBucket[];
  nextDueItems: NextDueItem[];
}

export interface AgingBucket {
  label: string;
  totalAmount: number;
  itemCount: number;
}

export interface NextDueItem {
  itemId: string;
  contractId: string;
  itemLabel: string;
  dueDate: string;
  amountRemaining: number;
}
