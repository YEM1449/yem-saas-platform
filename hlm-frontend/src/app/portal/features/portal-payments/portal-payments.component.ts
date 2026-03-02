import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PortalContractsService } from '../../core/portal-contracts.service';
import { PortalPaymentSchedule, PortalTranche } from '../../../core/models/portal.model';

@Component({
  selector: 'app-portal-payments',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './portal-payments.component.html',
})
export class PortalPaymentsComponent implements OnInit {
  private service = inject(PortalContractsService);
  private route   = inject(ActivatedRoute);

  schedule    = signal<PortalPaymentSchedule | null>(null);
  contractId  = signal('');
  loading     = signal(true);
  error       = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('contractId') ?? '';
    this.contractId.set(id);
    this.service.getPaymentSchedule(id).subscribe({
      next:  (data) => { this.schedule.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Payment schedule not available.'); this.loading.set(false); },
    });
  }

  trancheStatusLabel(s: string): string {
    const map: Record<string, string> = {
      PLANNED: 'Planned', ISSUED: 'Issued',
      PARTIALLY_PAID: 'Partial', PAID: 'Paid', OVERDUE: 'Overdue',
    };
    return map[s] ?? s;
  }

  trancheStatusClass(s: string): string {
    const map: Record<string, string> = {
      PLANNED: 'badge-planned', ISSUED: 'badge-issued',
      PARTIALLY_PAID: 'badge-partial', PAID: 'badge-paid', OVERDUE: 'badge-overdue',
    };
    return map[s] ?? '';
  }

  totalPaid(tranches: PortalTranche[]): number {
    return tranches
      .filter(t => t.status === 'PAID')
      .reduce((sum, t) => sum + t.amount, 0);
  }

  totalAmount(tranches: PortalTranche[]): number {
    return tranches.reduce((sum, t) => sum + t.amount, 0);
  }
}
