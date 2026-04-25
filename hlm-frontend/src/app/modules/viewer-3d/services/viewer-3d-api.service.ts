import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Project3dModel, UploadUrlResponse } from '../models/project-3d-model.model';
import { LotStatusSnapshot } from '../models/lot-3d-status.model';

@Injectable({ providedIn: 'root' })
export class Viewer3dApiService {

  private readonly base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /** GET /api/projects/{id}/3d-model — pre-signed URL + mappings */
  getModel(projetId: string): Observable<Project3dModel> {
    return this.http.get<Project3dModel>(`${this.base}/api/projects/${projetId}/3d-model`);
  }

  /** GET /api/projects/{id}/3d-properties-status — lightweight colour snapshot */
  getStatusSnapshot(projetId: string): Observable<LotStatusSnapshot[]> {
    return this.http.get<LotStatusSnapshot[]>(
      `${this.base}/api/projects/${projetId}/3d-properties-status`
    );
  }

  /** POST /api/projects/{id}/3d-model/upload-url — request pre-signed PUT URL (step 1) */
  requestUploadUrl(projetId: string, fileName: string, fileSizeBytes: number): Observable<UploadUrlResponse> {
    return this.http.post<UploadUrlResponse>(
      `${this.base}/api/projects/${projetId}/3d-model/upload-url`,
      { fileName, fileSizeBytes, dracoCompressed: true }
    );
  }

  /** POST /api/projects/{id}/3d-model — confirm upload with the file key (step 2) */
  confirmUpload(projetId: string, glbFileKey: string): Observable<Project3dModel> {
    return this.http.post<Project3dModel>(
      `${this.base}/api/projects/${projetId}/3d-model`,
      { glbFileKey, dracoCompressed: true }
    );
  }

  /** GET /api/portal/projects/{id}/3d-model — portal read-only variant */
  getPortalModel(projetId: string): Observable<Project3dModel> {
    return this.http.get<Project3dModel>(
      `${this.base}/api/portal/projects/${projetId}/3d-model`
    );
  }

  /** GET /api/portal/projects/{id}/3d-properties-status */
  getPortalStatusSnapshot(projetId: string): Observable<LotStatusSnapshot[]> {
    return this.http.get<LotStatusSnapshot[]>(
      `${this.base}/api/portal/projects/${projetId}/3d-properties-status`
    );
  }
}
