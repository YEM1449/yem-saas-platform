import { Component, inject, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { CommercialDashboardService } from './commercial-dashboard.service';
import {
  CommercialDashboardSalesResponse,
} from '../../core/models/commercial-dashboard.model';

@Component({
  selector: 'app-commercial-dashboard-sales',
  standalone: true,
  imports: [RouterLink, DatePipe, TranslatePipe],
  template: `
    <div class="sales-page">
      <div class="sales-header">
        <a routerLink="/app/dashboard/commercial">{{ 'dashboard.commercialSales.back' | translate }}</a>
        <h2>{{ 'dashboard.commercialSales.title' | translate }}</h2>
      </div>

      @if (loading) { <p>{{ 'dashboard.commercialSales.loading' | translate }}</p> }
      @if (error)   { <p class="error">{{ error }}</p> }

      @if (!loading && !error && data) {
        <p class="summary-line">
          {{ 'dashboard.commercialSales.summaryLine' | translate:{ count: data.totalCount, amount: formatAmount(data.totalAmount) } }}
        </p>

        <table>
          <thead>
            <tr>
              <th>{{ 'dashboard.commercialSales.thDateCompromis' | translate }}</th>
              <th>{{ 'dashboard.commercialSales.thProjet' | translate }}</th>
              <th>{{ 'dashboard.commercialSales.thBien' | translate }}</th>
              <th>{{ 'dashboard.commercialSales.thAcquereur' | translate }}</th>
              <th>{{ 'dashboard.commercialSales.thAgent' | translate }}</th>
              <th class="num">{{ 'dashboard.commercialSales.thPrixVente' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of data.sales; track row.id) {
              <tr>
                <td>{{ row.signedAt | date:'dd/MM/yyyy' }}</td>
                <td>{{ row.projectName || '—' }}</td>
                <td>{{ row.propertyRef || '—' }}</td>
                <td>{{ row.buyerName || '—' }}</td>
                <td>{{ row.agentEmail || '—' }}</td>
                <td class="num">{{ formatAmount(row.amount) }}</td>
              </tr>
            }
          </tbody>
        </table>

        @if (data.totalPages > 1) {
          <div class="pagination">
            <button [disabled]="currentPage === 0" (click)="goTo(currentPage - 1)">{{ 'dashboard.commercialSales.prev' | translate }}</button>
            <span>{{ 'dashboard.commercialSales.pageInfo' | translate:{ page: currentPage + 1, total: data.totalPages } }}</span>
            <button [disabled]="currentPage >= data.totalPages - 1" (click)="goTo(currentPage + 1)">{{ 'dashboard.commercialSales.next' | translate }}</button>
          </div>
        }
      }

      @if (!loading && !error && !data) {
        <p>{{ 'dashboard.commercialSales.empty' | translate }}</p>
      }
    </div>
  `,
  styles: [`
    .sales-page { padding: 1.5rem; max-width: 1100px; }
    .sales-header { display: flex; align-items: center; gap: 1.5rem; margin-bottom: 1rem; }
    .sales-header h2 { margin: 0; }
    .summary-line { margin-bottom: 1rem; }
    table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
    th { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 2px solid #ddd; color: #555; }
    td { padding: 0.35rem 0.6rem; border-bottom: 1px solid #f0f0f0; }
    td.num { text-align: right; }
    .pagination { display: flex; gap: 1rem; align-items: center; margin-top: 1rem; }
    .error { color: #c62828; }
  `],
})
export class CommercialDashboardSalesComponent implements OnInit {
  private svc = inject(CommercialDashboardService);

  data: CommercialDashboardSalesResponse | null = null;
  currentPage = 0;
  loading = false;
  error   = '';

  ngOnInit(): void {
    this.load();
  }

  goTo(page: number): void {
    this.currentPage = page;
    this.load();
  }

  private load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.getSales({ page: this.currentPage, size: 20 }).subscribe({
      next:  data => { this.data = data; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? `Error (${err.status})`;
      },
    });
  }

  formatAmount(v: number): string {
    return new Intl.NumberFormat('fr-MA', { maximumFractionDigits: 0 }).format(v) + ' MAD';
  }
}
