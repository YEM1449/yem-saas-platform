import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface GroupSocieteRow {
  societeId: string;
  nom: string;
  unitsDisponibles: number;
  unitsReserves: number;
  unitsVendus: number;
  absorptionPct: number;
  caConfirme: number;
  caEnCours: number;
  ventesActives: number;
  ventesStallees: number;
  encaisseTotal: number;
  aEncaisser: number;
  enRetardMontant: number;
  enRetardCount: number;
  optionsActives: number;
  retractationsEnCours: number;
}

export interface GroupTotals {
  societesCount: number;
  unitsDisponibles: number;
  unitsReserves: number;
  unitsVendus: number;
  absorptionPct: number;
  caConfirme: number;
  caEnCours: number;
  ventesActives: number;
  ventesStallees: number;
  encaisseTotal: number;
  aEncaisser: number;
  enRetardMontant: number;
  enRetardCount: number;
  optionsActives: number;
  retractationsEnCours: number;
}

export interface GroupDashboard {
  totals: GroupTotals;
  societes: GroupSocieteRow[];
}

@Injectable({ providedIn: 'root' })
export class GroupDashboardService {
  private http = inject(HttpClient);

  getDashboard(): Observable<GroupDashboard> {
    return this.http.get<GroupDashboard>(`${environment.apiUrl}/api/groupe/dashboard`);
  }
}
