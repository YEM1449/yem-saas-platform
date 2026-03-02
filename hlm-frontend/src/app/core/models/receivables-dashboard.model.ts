export interface AgingBucket {
  count: number;
  amount: number;
}

export interface OverdueByProjectRow {
  projectId: string;
  projectName: string;
  overdueAmount: number;
}

export interface RecentPaymentRow {
  paymentId: string;
  amountReceived: number;
  receivedAt: string;
  method: string;
  projectName: string;
  propertyRef: string;
  agentEmail: string;
}

export interface ReceivablesDashboard {
  asOf: string;
  totalOutstanding: number;
  totalOverdue: number;
  collectionRate: number | null;
  avgDaysToPayment: number | null;
  current: AgingBucket;
  days30: AgingBucket;
  days60: AgingBucket;
  days90: AgingBucket;
  days90plus: AgingBucket;
  overdueByProject: OverdueByProjectRow[];
  recentPayments: RecentPaymentRow[];
}
