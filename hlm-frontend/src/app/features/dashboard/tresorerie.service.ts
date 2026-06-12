import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AppelEnRetard {
  venteId: string;
  venteRef: string;
  acquereur: string | null;
  libelle: string;
  montant: number;
  dateEcheance: string;
  joursRetard: number;
}

export interface MoisPrevision {
  annee: number;
  mois: number;
  libelle: string;
  montant: number;
}

export interface TresorerieDashboard {
  encaisseTotal: number;
  aEncaisser: number;
  previsionnel6Mois: number;
  enRetardMontant: number;
  enRetardCount: number;
  optionsActives: number;
  retractationsEnCours: number;
  accordsExpirant15j: number;
  appelsEnRetard: AppelEnRetard[];
  previsionnelParMois: MoisPrevision[];
}

@Injectable({ providedIn: 'root' })
export class TresorerieService {
  private http = inject(HttpClient);

  getTresorerie(): Observable<TresorerieDashboard> {
    return this.http.get<TresorerieDashboard>(`${environment.apiUrl}/api/dashboard/tresorerie`);
  }
}
