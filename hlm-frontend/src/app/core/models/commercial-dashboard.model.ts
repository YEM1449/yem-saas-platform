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

export interface DiscountByAgentRow {
  agentId: string;
  agentEmail: string;
  avgDiscountPercent: number;
  salesCount: number;
}

export interface ProspectSourceRow {
  source: string;
  count: number;
  convertedCount: number;
  conversionRate: number | null;
}

export interface DailyPoint {
  date: string;   // ISO local date e.g. "2026-02-15"
  amount: number;
}

export interface CommercialDashboardSummary {
  from: string;
  to: string;
  /** Server timestamp when this DTO was assembled (ISO datetime string). */
  asOf: string;
  salesCount: number;
  salesTotalAmount: number;
  avgSaleValue: number;
  depositsCount: number;
  depositsTotalAmount: number;
  /** Current open reservations (PENDING + CONFIRMED), not date-filtered. */
  activeReservationsCount: number;
  activeReservationsTotalAmount: number;
  /** Average age of open reservations in days; null when none. */
  avgReservationAgeDays: number | null;
  /** Contacts with status PROSPECT or QUALIFIED_PROSPECT (tenant-wide). */
  activeProspectsCount: number;
  salesByProject: SalesByProjectRow[];
  salesByAgent: SalesByAgentRow[];
  inventoryByStatus: Record<string, number>;
  inventoryByType: Record<string, number>;
  salesAmountByDay: DailyPoint[];
  depositsAmountByDay: DailyPoint[];
  conversionDepositToSaleRate: number | null;
  avgDaysDepositToSale: number | null;
  /** F3.2 — null when no contracts have listPrice set */
  avgDiscountPercent: number | null;
  maxDiscountPercent: number | null;
  discountByAgent: DiscountByAgentRow[];
  /** F3.4 — prospects grouped by source */
  prospectsBySource: ProspectSourceRow[];
  /** Count of ACTIVE property_reservation records (lightweight holds). */
  propertyHoldsCount: number;
  /** ACTIVE property holds expiring within the next 48 h. */
  propertyHoldsExpiringSoon: number;
  /** Active pipeline: statut → count (COMPROMIS, FINANCEMENT, ACTE_NOTARIE). */
  ventesParStatut: Record<string, number>;
  /** Total prixVente of non-terminal ventes (committed CA). */
  caActivePipeline: number;
  /** Taux d'absorption = SOLD / (SOLD+ACTIVE+RESERVED) ×100. Null when no stock. */
  tauxAbsorption: number | null;
  /** Count of ACTIVE + RESERVED + SOLD properties. */
  stockCommercialise: number;
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
