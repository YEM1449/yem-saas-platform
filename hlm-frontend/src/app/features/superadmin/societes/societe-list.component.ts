import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SocieteService } from './societe.service';
import { SocieteDto } from './societe.model';

@Component({
  selector: 'app-societe-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './societe-list.component.html',
  styleUrl: './societe-list.component.css',
})
export class SocieteListComponent implements OnInit {
  private svc = inject(SocieteService);
  private router = inject(Router);

  societes: SocieteDto[] = [];
  loading = false;
  error = '';
  success = '';

  // Filters
  search = '';
  pays = '';
  planAbonnement = '';
  actifFilter = '';

  // Pagination
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 20;

  // Suspend dialog
  showSuspendDialog = false;
  suspendTarget: SocieteDto | null = null;
  suspendRaison = '';
  suspending = false;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const filter: { search?: string; pays?: string; planAbonnement?: string; actif?: boolean } = {};
    if (this.search.trim()) filter.search = this.search.trim();
    if (this.pays) filter.pays = this.pays;
    if (this.planAbonnement) filter.planAbonnement = this.planAbonnement;
    if (this.actifFilter !== '') filter.actif = this.actifFilter === 'true';
    this.svc.list(filter, { page: this.currentPage, size: this.pageSize }).subscribe({
      next: (page) => {
        this.societes = page.content;
        this.totalPages = page.totalPages;
        this.totalElements = page.totalElements;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.extractError(err);
      },
    });
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.load();
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.load();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.load();
    }
  }

  openSuspend(s: SocieteDto): void {
    this.suspendTarget = s;
    this.suspendRaison = '';
    this.showSuspendDialog = true;
  }

  cancelSuspend(): void {
    this.showSuspendDialog = false;
    this.suspendTarget = null;
    this.suspendRaison = '';
  }

  confirmSuspend(): void {
    if (!this.suspendTarget) return;
    this.suspending = true;
    this.svc.desactiver(this.suspendTarget.id, this.suspendRaison).subscribe({
      next: () => {
        this.suspending = false;
        this.showSuspendDialog = false;
        this.success = `Société "${this.suspendTarget!.nom}" suspendue.`;
        this.suspendTarget = null;
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.suspending = false;
        this.error = this.extractError(err);
      },
    });
  }

  reactiver(s: SocieteDto): void {
    this.error = '';
    this.success = '';
    this.svc.reactiver(s.id).subscribe({
      next: () => {
        this.success = `Société "${s.nom}" réactivée.`;
        this.load();
      },
      error: (err: HttpErrorResponse) => {
        this.error = this.extractError(err);
      },
    });
  }

  viewDetail(s: SocieteDto): void {
    this.router.navigate(['/superadmin/societes', s.id]);
  }

  scoreClass(score: number): string {
    if (score >= 80) return 'score-good';
    if (score >= 50) return 'score-medium';
    return 'score-bad';
  }

  planClass(plan: string): string {
    switch (plan) {
      case 'ENTERPRISE': return 'badge-enterprise';
      case 'PRO': return 'badge-pro';
      default: return 'badge-starter';
    }
  }

  private extractError(err: HttpErrorResponse): string {
    if (err.status === 401) return 'Session expirée. Veuillez vous reconnecter.';
    if (err.status === 403) return 'Accès refusé.';
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
    return `Erreur (${err.status})`;
  }
}
