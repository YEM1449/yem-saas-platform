import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

// ── Domain types ──────────────────────────────────────────────────────────────

export type TrancheStatut =
  | 'EN_PREPARATION'
  | 'EN_COMMERCIALISATION'
  | 'EN_TRAVAUX'
  | 'ACHEVEE'
  | 'LIVREE';

export interface Tranche {
  id: string;
  projectId: string;
  numero: number;
  nom: string | null;
  displayNom: string;
  statut: TrancheStatut;
  dateLivraisonPrevue: string | null;
  dateLivraisonEff: string | null;
  dateDebutTravaux: string | null;
  permisConstruireRef: string | null;
  description: string | null;
  // KPIs
  buildingsCount: number;
  unitsCount: number;
  unitesDisponibles: number;
  unitesReservees: number;
  unitesVendues: number;
  tauxCommercialisation: number;
}

// ── Generation request types (mirror backend DTOs) ────────────────────────────

export interface FloorConfig {
  floorNumber: number;
  propertyType: string;
  unitCount: number;
  surfaceMin: number | null;
  surfaceMax: number | null;
  prixBase: number | null;
  orientation: string | null;
}

export interface BuildingConfig {
  buildingOrder: number;
  customName: string | null;
  floorCount: number;
  hasRdc: boolean;
  rdcType: string;
  rdcUnitCount: number;
  hasParking: boolean;
  parkingCount: number;
  floors: FloorConfig[];
}

export interface TrancheConfig {
  numero: number;
  nom: string | null;
  dateLivraisonPrevue: string;
  dateDebutTravaux: string | null;
  permisConstruireRef: string | null;
  buildings: BuildingConfig[];
}

export interface ProjectGenerationRequest {
  projectNom: string;
  projectDescription: string | null;
  projectAdresse: string | null;
  projectVille: string;
  projectCodePostal: string | null;
  buildingNaming: string;
  buildingPrefix: string;
  unitRefPattern: string;
  unitPrefix: string | null;
  rdcLabel: string;
  includeParking: boolean;
  parkingPrefix: string;
  parkingUnderground: boolean;
  tranches: TrancheConfig[];
}

export interface BuildingSummary {
  immeubleId: string;
  nom: string;
  floorCount: number;
  unitsCount: number;
}

export interface TrancheGenerationSummary {
  trancheId: string;
  displayNom: string;
  dateLivraisonPrevue: string | null;
  buildingsCount: number;
  unitsCount: number;
  buildings: BuildingSummary[];
}

export interface ProjectGenerationResponse {
  projectId: string;
  projectNom: string;
  tranchesGenerated: number;
  buildingsGenerated: number;
  unitsGenerated: number;
  tranches: TrancheGenerationSummary[];
  unitsByType: Record<string, number>;
  valeurTotale: number;
  status: string;
  message: string;
}

// ── Service ───────────────────────────────────────────────────────────────────

@Injectable({ providedIn: 'root' })
export class TrancheService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/projects`;

  /** Bulk-generate a project with all tranches, buildings and units. */
  generate(request: ProjectGenerationRequest): Observable<ProjectGenerationResponse> {
    return this.http.post<ProjectGenerationResponse>(`${this.base}/generate`, request);
  }

  /** List tranches for a project (with KPI aggregates). */
  listByProject(projectId: string): Observable<Tranche[]> {
    return this.http.get<Tranche[]>(`${this.base}/${projectId}/tranches`);
  }

  /** Get a single tranche. */
  getById(projectId: string, trancheId: string): Observable<Tranche> {
    return this.http.get<Tranche>(`${this.base}/${projectId}/tranches/${trancheId}`);
  }

  /** Advance the tranche statut. */
  advanceStatut(projectId: string, trancheId: string, statut: TrancheStatut): Observable<Tranche> {
    return this.http.patch<Tranche>(
      `${this.base}/${projectId}/tranches/${trancheId}/statut`,
      { statut }
    );
  }
}
