import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { SocieteService } from './societe.service';
import { SocieteDetailDto, SocieteStatsDto, SocieteComplianceDto } from './societe.model';
import { SocieteMembersComponent } from './societe-members.component';
import { environment } from '../../../../environments/environment';

type Tab = 'info' | 'stats' | 'compliance' | 'membres';

@Component({
  selector: 'app-societe-detail',
  standalone: true,
  imports: [FormsModule, SocieteMembersComponent, DatePipe, DecimalPipe, TranslatePipe],
  templateUrl: './societe-detail.component.html',
  styleUrl: './societe-detail.component.css',
})
export class SocieteDetailComponent implements OnInit, OnDestroy {
  private i18n = inject(I18nService);
  private svc = inject(SocieteService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);

  societeId = '';
  detail: SocieteDetailDto | null = null;
  stats: SocieteStatsDto | null = null;
  compliance: SocieteComplianceDto | null = null;

  loading = false;
  loadingStats = false;
  loadingCompliance = false;
  logoUploading = false;
  error = '';
  success = '';

  logoSrc: string | null = null;
  private logoObjectUrl: string | null = null;

  activeTab: Tab = 'info';

  // Suspend dialog
  showSuspendDialog = false;
  suspendRaison = '';
  suspending = false;

  ngOnInit(): void {
    this.societeId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadDetail();
  }

  ngOnDestroy(): void {
    this.revokeLogo();
  }

  loadDetail(): void {
    this.loading = true;
    this.error = '';
    this.svc.getDetail(this.societeId).subscribe({
      next: (d) => {
        this.detail = d;
        this.loading = false;
        if (d.logoDownloadUrl) {
          this.fetchLogoAsBlob(d.logoDownloadUrl);
        } else {
          this.revokeLogo();
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.extractError(err);
      },
    });
  }

  setTab(tab: Tab): void {
    this.activeTab = tab;
    if (tab === 'stats' && !this.stats) this.loadStats();
    if (tab === 'compliance' && !this.compliance) this.loadCompliance();
  }

  loadStats(): void {
    this.loadingStats = true;
    this.svc.getStats(this.societeId).subscribe({
      next: (s) => { this.stats = s; this.loadingStats = false; },
      error: () => { this.loadingStats = false; },
    });
  }

  loadCompliance(): void {
    this.loadingCompliance = true;
    this.svc.getCompliance(this.societeId).subscribe({
      next: (c) => { this.compliance = c; this.loadingCompliance = false; },
      error: () => { this.loadingCompliance = false; },
    });
  }

  openSuspend(): void {
    this.suspendRaison = '';
    this.showSuspendDialog = true;
  }

  cancelSuspend(): void {
    this.showSuspendDialog = false;
    this.suspendRaison = '';
  }

  confirmSuspend(): void {
    this.suspending = true;
    this.svc.desactiver(this.societeId, this.suspendRaison).subscribe({
      next: () => {
        this.suspending = false;
        this.showSuspendDialog = false;
        this.success = this.i18n.instant('superadmin.detail.suspended');
        this.loadDetail();
      },
      error: (err: HttpErrorResponse) => {
        this.suspending = false;
        this.error = this.extractError(err);
      },
    });
  }

  reactiver(): void {
    this.error = '';
    this.success = '';
    this.svc.reactiver(this.societeId).subscribe({
      next: () => {
        this.success = this.i18n.instant('superadmin.detail.reactivated');
        this.loadDetail();
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  goEdit(): void {
    this.router.navigate(['/superadmin/societes', this.societeId, 'edit']);
  }

  goBack(): void {
    this.router.navigate(['/superadmin/societes']);
  }

  quotaBarWidth(used: number, max?: number): number {
    if (!max || max <= 0) return 0;
    return Math.min(100, Math.round((used / max) * 100));
  }

  quotaBarClass(used: number, max?: number): string {
    const pct = this.quotaBarWidth(used, max);
    if (pct >= 90) return 'bar-danger';
    if (pct >= 70) return 'bar-warning';
    return 'bar-ok';
  }

  complianceBarClass(score: number): string {
    if (score >= 80) return 'bar-ok';
    if (score >= 50) return 'bar-warning';
    return 'bar-danger';
  }

  planClass(plan: string): string {
    switch (plan) {
      case 'ENTERPRISE': return 'badge-enterprise';
      case 'PRO': return 'badge-pro';
      default: return 'badge-starter';
    }
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.logoUploading = true;
    this.svc.uploadLogo(this.societeId, file).subscribe({
      next: () => { this.logoUploading = false; this.success = this.i18n.instant('superadmin.detail.logoUpdated'); this.loadDetail(); },
      error: (err: HttpErrorResponse) => { this.logoUploading = false; this.error = this.extractError(err); },
    });
  }

  deleteLogo(): void {
    this.svc.deleteLogo(this.societeId).subscribe({
      next: () => { this.success = this.i18n.instant('superadmin.detail.logoDeleted'); this.revokeLogo(); this.loadDetail(); },
      error: (err: HttpErrorResponse) => { this.error = this.extractError(err); },
    });
  }

  private fetchLogoAsBlob(relativeUrl: string): void {
    this.http.get(`${environment.apiUrl}${relativeUrl}`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.revokeLogo();
        this.logoObjectUrl = URL.createObjectURL(blob);
        this.logoSrc = this.logoObjectUrl;
      },
      error: () => { this.logoSrc = null; },
    });
  }

  private revokeLogo(): void {
    if (this.logoObjectUrl) {
      URL.revokeObjectURL(this.logoObjectUrl);
      this.logoObjectUrl = null;
    }
    this.logoSrc = null;
  }

  private extractError(err: HttpErrorResponse): string {
    if (err.status === 401) return this.i18n.instant('superadmin.errors.session');
    if (err.status === 403) return this.i18n.instant('superadmin.errors.accessDenied');
    if (err.status === 404) return this.i18n.instant('superadmin.errors.notFoundSociete');
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
    return this.i18n.instant('superadmin.errors.generic', { status: err.status });
  }
}
