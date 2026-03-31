import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ProspectService } from './prospect.service';
import { Prospect } from '../../core/models/prospect.model';
import { ErrorResponse } from '../../core/models/error-response.model';

interface PipelineColumn {
  key: string;
  label: string;
  colorVar: string;
}

@Component({
  selector: 'app-prospects',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  templateUrl: './prospects.component.html',
  styleUrl: './prospects.component.css',
})
export class ProspectsComponent implements OnInit {
  private svc = inject(ProspectService);

  allProspects: Prospect[] = [];
  loading = true;
  error = '';

  searchQuery = '';
  showLost = false;

  readonly PIPELINE_COLUMNS: PipelineColumn[] = [
    { key: 'PROSPECT',           label: 'Prospects',       colorVar: '#64748b' },
    { key: 'QUALIFIED_PROSPECT', label: 'Qualifiés',       colorVar: '#3b82f6' },
    { key: 'CLIENT',             label: 'Clients',         colorVar: '#8b5cf6' },
    { key: 'ACTIVE_CLIENT',      label: 'Clients Actifs',  colorVar: '#10b981' },
    { key: 'COMPLETED_CLIENT',   label: 'Complétés',       colorVar: '#059669' },
    { key: 'REFERRAL',           label: 'Parrains',        colorVar: '#f59e0b' },
  ];

  ngOnInit(): void {
    this.svc.list().subscribe({
      next: (page) => {
        this.allProspects = page.content;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 401) {
          this.error = 'Session expirée. Veuillez vous reconnecter.';
        } else if (err.status === 403) {
          this.error = 'Accès refusé.';
        } else {
          this.error = body?.message ?? `Erreur de chargement (${err.status})`;
        }
      },
    });
  }

  get filtered(): Prospect[] {
    const q = this.searchQuery.toLowerCase().trim();
    return this.allProspects.filter(p => {
      if (!this.showLost && p.status === 'LOST') return false;
      if (!q) return true;
      return (
        p.fullName.toLowerCase().includes(q) ||
        (p.email ?? '').toLowerCase().includes(q) ||
        (p.phone ?? '').includes(q)
      );
    });
  }

  columnProspects(key: string): Prospect[] {
    return this.filtered.filter(p => p.status === key);
  }

  get lostCount(): number {
    return this.allProspects.filter(p => p.status === 'LOST').length;
  }

  get totalCount(): number {
    return this.allProspects.length;
  }

  /** Single-character initials for the avatar chip */
  initials(p: Prospect): string {
    const f = p.firstName?.charAt(0) ?? '';
    const l = p.lastName?.charAt(0) ?? '';
    return (f + l).toUpperCase();
  }

  /** Background color for avatar — deterministic from name */
  avatarColor(p: Prospect): string {
    const colors = ['#3b82f6','#8b5cf6','#10b981','#f59e0b','#ef4444','#06b6d4','#ec4899'];
    const code = (p.firstName?.charCodeAt(0) ?? 0) + (p.lastName?.charCodeAt(0) ?? 0);
    return colors[code % colors.length];
  }
}
