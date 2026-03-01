import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentService } from './payment.service';
import {
  PaymentScheduleResponse,
  TrancheResponse,
  PaymentCallResponse,
  PaymentResponse,
  RecordPaymentRequest,
} from '../../core/models/payment.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-payment-schedule',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-schedule.component.html',
  styleUrls: ['./payment-schedule.component.css'],
})
export class PaymentScheduleComponent implements OnInit {
  private route   = inject(ActivatedRoute);
  private svc     = inject(PaymentService);
  auth            = inject(AuthService);

  contractId = '';
  schedule: PaymentScheduleResponse | null = null;
  error: string | null = null;
  loading = true;

  // Expanded payments panel: trancheId → calls + payments
  expandedTranche: string | null = null;
  callsForTranche: Record<string, PaymentCallResponse[]> = {};
  paymentsForCall: Record<string, PaymentResponse[]> = {};

  // Record payment modal
  activeCallId: string | null = null;
  paymentForm: RecordPaymentRequest = {
    amountReceived: 0,
    receivedAt: new Date().toISOString().substring(0, 10),
    method: 'BANK_TRANSFER',
  };
  paymentError: string | null = null;

  get canWrite(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.contractId = this.route.snapshot.paramMap.get('contractId') ?? '';
    this.loadSchedule();
  }

  loadSchedule(): void {
    this.loading = true;
    this.svc.getSchedule(this.contractId).subscribe({
      next: s => { this.schedule = s; this.loading = false; },
      error: err => {
        this.error = err.status === 404
          ? 'Aucun échéancier trouvé pour ce contrat.'
          : 'Erreur lors du chargement de l\'échéancier.';
        this.loading = false;
      },
    });
  }

  issueCall(tranche: TrancheResponse): void {
    this.svc.issueCall(this.contractId, tranche.id).subscribe({
      next: () => this.loadSchedule(),
      error: err => alert('Erreur: ' + (err.error?.message ?? 'Impossible d\'émettre l\'appel de fonds')),
    });
  }

  downloadPdf(callId: string, callNumber: number): void {
    this.svc.downloadCallPdf(callId).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href     = url;
      a.download = `appel-de-fonds-${callNumber}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  openPaymentModal(callId: string): void {
    this.activeCallId  = callId;
    this.paymentError  = null;
    this.paymentForm   = {
      amountReceived: 0,
      receivedAt: new Date().toISOString().substring(0, 10),
      method: 'BANK_TRANSFER',
    };
  }

  closePaymentModal(): void {
    this.activeCallId = null;
  }

  submitPayment(): void {
    if (!this.activeCallId) return;
    this.svc.recordPayment(this.activeCallId, this.paymentForm).subscribe({
      next: () => { this.closePaymentModal(); this.loadSchedule(); },
      error: err => {
        this.paymentError = err.error?.message ?? 'Erreur lors de l\'enregistrement du paiement.';
      },
    });
  }

  trancheStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      PLANNED: 'Planifié', ISSUED: 'Émis', PARTIALLY_PAID: 'Partiel',
      PAID: 'Payé', OVERDUE: 'En retard',
    };
    return labels[status] ?? status;
  }
}
