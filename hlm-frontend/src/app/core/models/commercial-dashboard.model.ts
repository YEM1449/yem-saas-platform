export interface SalesByProjectRow {
  projectId: string;
  projectName: string;
  salesCount: number;
  salesAmount: number;
}

export interface SalesByAgentRow {
  agentId: string;
  agentEmail: string;
  salesCount: number;
  salesAmount: number;
}

export interface DailyPoint {
  date: string;   // ISO local date e.g. "2026-02-15"
  amount: number;
}

export interface CommercialDashboardSummary {
  from: string;
  to: string;
  salesCount: number;
  salesTotalAmount: number;
  avgSaleValue: number;
  depositsCount: number;
  depositsTotalAmount: number;
  salesByProject: SalesByProjectRow[];
  salesByAgent: SalesByAgentRow[];
  inventoryByStatus: Record<string, number>;
  inventoryByType: Record<string, number>;
  salesAmountByDay: DailyPoint[];
  depositsAmountByDay: DailyPoint[];
  conversionDepositToSaleRate: number | null;
  avgDaysDepositToSale: number | null;
}

export interface SalesTableRow {
  id: string;
  signedAt: string;
  projectName: string;
  propertyRef: string;
  buyerName: string;
  agentEmail: string;
  amount: number;
}

export interface CommercialDashboardSalesResponse {
  totalCount: number;
  totalAmount: number;
  page: number;
  pageSize: number;
  totalPages: number;
  sales: SalesTableRow[];
}

export interface DashboardParams {
  from?: string;
  to?: string;
  projectId?: string;
  agentId?: string;
}
