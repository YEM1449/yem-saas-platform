import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface HomeDashboard {
  asOf: string;
  // Pipeline ventes
  activeVentesCount: number;
  caActivePipeline: number;
  ventesParStatut: Record<string, number>;
  // Tendance mensuelle
  caSigneMoisCourant: number;
  caSigneMoisPrecedent: number;
  caLivre: number;
  // Échéancier pulse
  echeancesA30JoursMontant: number;
  echeancesEnRetardMontant: number;
  echeancesEnRetardCount: number;
  // Inventory
  biensDraftCount: number;
  biensActifsCount: number;
  biensReservesCount: number;
  biensVendusCount: number;
  tauxAbsorption: number | null;
  stockCommercialise: number;
  // Entrée pipeline
  activeProspectsCount: number;
  activeReservationsCount: number;
  reservationsExpirantBientot: number;
  // Alertes
  ventesStalleesCount: number;
  // Tâches
  openTasksCount: number;
  overdueTasksCount: number;
  tasksDueTodayCount: number;
  // Owner KPIs
  cancellationRate90d: number | null;
  avgTicketLivre: number;
  conversionRate30d: number | null;
  encaisseMoisCourant: number;
  topAgents: AgentLeaderboardRow[];
  // Executive view (Wave 13)
  caYtd: number;
  caSameMonthLastYear: number;
  caYoYPct: number | null;
  monthsOfSupply: number | null;
  salesVelocityPerWeek: number;
  winRate90d: number | null;
  dsoRolling90d: number | null;
  collectionEfficiency90d: number | null;
  caMensuelCible: number | null;
  ventesMensuelCible: number | null;
  quotaAttainmentMtdPct: number | null;
  ventesSigneesMoisCourantCount: number;
  quotaVentesAttainmentMtdPct: number | null;
  upcomingDeliveries: UpcomingDeliveryRow[];
  // Trend & project/tranche/immeuble breakdown
  monthlyTrend: MonthlyTrendPoint[];
  projectBreakdown: ProjectBreakdownRow[];
  trancheBreakdown: TrancheBreakdownRow[];
  immeubleBreakdown: ImmeubleBreakdownRow[];
  // Widgets
  recentVentes: RecentVenteRow[];
  urgentTasks: UrgentTaskRow[];
}

export interface MonthlyTrendPoint {
  yearMonth: string;
  label: string;
  caSigne: number;
}

export interface ProjectBreakdownRow {
  projectId: string | null;
  projectName: string;
  totalCA: number;
  ventesCount: number;
}

export interface TrancheBreakdownRow {
  trancheId: string | null;
  trancheLabel: string;
  projectId: string | null;
  projectName: string;
  totalCA: number;
  ventesCount: number;
}

export interface ImmeubleBreakdownRow {
  immeubleId: string | null;
  immeubleNom: string;
  projectId: string | null;
  projectName: string;
  totalCA: number;
  ventesCount: number;
}

export interface UpcomingDeliveryRow {
  trancheId: string;
  trancheLabel: string;
  projectId: string;
  projectName: string;
  dateLivraisonPrevue: string;
  daysUntilDelivery: number;
  totalUnits: number;
  soldUnits: number;
}

export interface AgentLeaderboardRow {
  agentId: string;
  agentName: string;
  totalCA: number;
  ventesCount: number;
}

export interface RecentVenteRow {
  id: string;
  venteRef: string | null;
  contactFullName: string | null;
  statut: string;
  prixVente: number | null;
  createdAt: string;
}

export interface UrgentTaskRow {
  id: string;
  title: string;
  status: string;
  dueDate: string;
  contactId: string | null;
}

export interface ShareholderKpi {
  portfolioValueAtMarket: number;
  soldValue: number;
  projectedCompletionExposure: number;
  concentrationByProject: ProjectConcentrationRow[];
  generatedAt: string;
}

export interface ProjectConcentrationRow {
  projectId: string;
  projectName: string;
  unitCount: number;
  pctOfPortfolio: number;
}

export interface ProjectDirectorKpi {
  projects: ProjectProgressRow[];
  generatedAt: string;
}

export interface ProjectProgressRow {
  projectId: string;
  projectName: string;
  totalUnits: number;
  soldUnits: number;
  reservedUnits: number;
  availableUnits: number;
  soldPct: number;
  reservedPct: number;
  deliveryPlanned: string | null;
  deliveryOnTrack: boolean;
}

@Injectable({ providedIn: 'root' })
export class HomeDashboardService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/dashboard/home`;

  getSnapshot(): Observable<HomeDashboard> {
    return this.http.get<HomeDashboard>(this.base);
  }

  getShareholderKpis(): Observable<ShareholderKpi> {
    return this.http.get<ShareholderKpi>(`${this.base}/shareholder`);
  }

  getProjectDirectorKpis(): Observable<ProjectDirectorKpi> {
    return this.http.get<ProjectDirectorKpi>(`${this.base}/project-director`);
  }
}
