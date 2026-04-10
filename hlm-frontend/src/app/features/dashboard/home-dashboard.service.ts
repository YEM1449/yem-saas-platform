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
  // Tâches
  openTasksCount: number;
  overdueTasksCount: number;
  tasksDueTodayCount: number;
  // Widgets
  recentVentes: RecentVenteRow[];
  urgentTasks: UrgentTaskRow[];
}

export interface RecentVenteRow {
  id: string;
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

@Injectable({ providedIn: 'root' })
export class HomeDashboardService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/dashboard/home`;

  getSnapshot(): Observable<HomeDashboard> {
    return this.http.get<HomeDashboard>(this.base);
  }
}
