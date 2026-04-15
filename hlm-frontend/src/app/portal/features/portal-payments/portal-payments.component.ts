import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PortalContractsService } from '../../core/portal-contracts.service';
import { PaymentScheduleItem } from '../../../core/models/payment-schedule.model';

@Component({
  selector: 'app-portal-payments',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './portal-payments.component.html',
  styleUrl: './portal-payments.component.css',
})
export class PortalPaymentsComponent implements OnInit {
  private service = inject(PortalContractsService);
  private route   = inject(ActivatedRoute);

  items       = signal<PaymentScheduleItem[]>([]);
  contractId  = signal('');
  loading     = signal(true);
  error       = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('contractId') ?? '';
    this.contractId.set(id);
    this.service.getPaymentSchedule(id).subscribe({
      next:  (data) => { this.items.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Payment schedule not available.'); this.loading.set(false); },
    });
  }

  totalAmount(items: PaymentScheduleItem[]): number {
    return items.reduce((sum, i) => sum + i.amount, 0);
  }

  totalPaid(items: PaymentScheduleItem[]): number {
    return items.reduce((sum, i) => sum + i.amountPaid, 0);
  }

  itemStatusLabel(s: string): string {
    const map: Record<string, string> = {
      DRAFT: 'Brouillon', ISSUED: 'Émis', SENT: 'Envoyé',
      OVERDUE: 'En retard', PAID: 'Payé', CANCELED: 'Annulé',
    };
    return map[s] ?? s;
  }

  itemStatusClass(s: string): string {
    const map: Record<string, string> = {
      DRAFT: 'badge-planned', ISSUED: 'badge-issued', SENT: 'badge-issued',
      OVERDUE: 'badge-overdue', PAID: 'badge-paid', CANCELED: 'badge-planned',
    };
    return map[s] ?? '';
  }
}
