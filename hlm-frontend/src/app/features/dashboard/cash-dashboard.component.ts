import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { PaymentScheduleService } from '../contracts/payment-schedule.service';
import { CashDashboardResponse } from '../../core/models/payment-schedule.model';

@Component({
  selector: 'app-cash-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './cash-dashboard.component.html',
  styleUrl: './cash-dashboard.component.css',
})
export class CashDashboardComponent implements OnInit {
  private svc = inject(PaymentScheduleService);

  data: CashDashboardResponse | null = null;
  loading = false;
  error   = '';

  // Default window: current calendar month
  from = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
  to   = new Date().toISOString().slice(0, 10);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.getCashDashboard(this.from, this.to).subscribe({
      next: d => { this.data = d; this.loading = false; },
      error: () => { this.error = 'Failed to load cash dashboard'; this.loading = false; },
    });
  }
}
