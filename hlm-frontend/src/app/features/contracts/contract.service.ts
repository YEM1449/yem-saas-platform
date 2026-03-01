import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContractResponse, SaleContractStatus } from '../../core/models/contract.model';

export interface ListContractsParams {
  status?: SaleContractStatus;
  projectId?: string;
  agentId?: string;
}

@Injectable({ providedIn: 'root' })
export class ContractService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/contracts`;

  list(params: ListContractsParams = {}): Observable<ContractResponse[]> {
    let p = new HttpParams();
    for (const [k, v] of Object.entries(params)) {
      if (v != null && v !== '') p = p.set(k, String(v));
    }
    return this.http.get<ContractResponse[]>(this.base, { params: p });
  }

  getById(contractId: string): Observable<ContractResponse> {
    return this.http.get<ContractResponse>(`${this.base}/${contractId}`);
  }

  downloadPdf(contractId: string): Observable<Blob> {
    return this.http.get(
      `${this.base}/${contractId}/documents/contract.pdf`,
      { responseType: 'blob' }
    );
  }
}
