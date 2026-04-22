import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReportStatut = 'COMPROMIS' | 'FINANCEMENT' | 'ACTE_NOTARIE' | 'LIVRE' | 'ANNULE';

export interface ReportFilters {
  from?: string;
  to?: string;
  statut?: ReportStatut | null;
}

@Injectable({ providedIn: 'root' })
export class ReportExportService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/reports`;

  downloadVentesPdf(filters: ReportFilters): Observable<Blob> {
    return this.http.get(`${this.base}/ventes/pdf`, {
      params: this.toParams(filters),
      responseType: 'blob',
    });
  }

  downloadVentesCsv(filters: ReportFilters): Observable<Blob> {
    return this.http.get(`${this.base}/ventes/csv`, {
      params: this.toParams(filters),
      responseType: 'blob',
    });
  }

  downloadAgentsPdf(filters: ReportFilters): Observable<Blob> {
    return this.http.get(`${this.base}/agents/pdf`, {
      params: this.toParams(filters),
      responseType: 'blob',
    });
  }

  downloadAgentsCsv(filters: ReportFilters): Observable<Blob> {
    return this.http.get(`${this.base}/agents/csv`, {
      params: this.toParams(filters),
      responseType: 'blob',
    });
  }

  private toParams(f: ReportFilters): HttpParams {
    let p = new HttpParams();
    if (f.from)   p = p.set('from', f.from);
    if (f.to)     p = p.set('to', f.to);
    if (f.statut) p = p.set('statut', f.statut);
    return p;
  }

  triggerDownload(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a   = document.createElement('a');
    a.href     = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
