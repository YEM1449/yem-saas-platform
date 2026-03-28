import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TemplateSummary, TemplateSourceResponse, TemplateType } from './template.model';

@Injectable({ providedIn: 'root' })
export class TemplateService {
  private http = inject(HttpClient);
  private base = '/api/templates';

  list(): Observable<TemplateSummary[]> {
    return this.http.get<TemplateSummary[]>(this.base);
  }

  getSource(type: TemplateType): Observable<TemplateSourceResponse> {
    return this.http.get<TemplateSourceResponse>(`${this.base}/${type}/source`);
  }

  upsert(type: TemplateType, htmlContent: string): Observable<TemplateSummary> {
    return this.http.put<TemplateSummary>(`${this.base}/${type}`, { htmlContent });
  }

  delete(type: TemplateType): Observable<void> {
    return this.http.delete<void>(`${this.base}/${type}`);
  }

  previewUrl(type: TemplateType): string {
    return `${this.base}/${type}/preview`;
  }
}
