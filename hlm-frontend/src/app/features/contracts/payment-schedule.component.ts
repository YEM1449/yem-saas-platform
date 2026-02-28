import { Component, inject, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PaymentScheduleService } from './payment-schedule.service';
import {
  AddPaymentRequest,
  CreateScheduleItemRequest,
  PaymentScheduleItem,
  SchedulePayment,
  SendScheduleItemRequest,
} from '../../core/models/payment-schedule.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-payment-schedule',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './payment-schedule.component.html',
  styleUrl: './payment-schedule.component.css',
})
export class PaymentScheduleComponent implements OnChanges {
  @Input({ required: true }) contractId!: string;
  /** Buyer contact ID — passed through for outbox resolution */
  @Input() buyerContactId: string | null = null;

  private svc  = inject(PaymentScheduleService);
  private auth = inject(AuthService);

  items: PaymentScheduleItem[] = [];
  loading = false;
  error   = '';

  // ── Create form ──────────────────────────────────────────────────────────
  showCreateForm = false;
  newLabel  = '';
  newAmount = 0;
  newDueDate = '';
  newNotes  = '';
  createError = '';

  // ── Add payment modal ────────────────────────────────────────────────────
  paymentItemId: string | null = null;
  paymentItemLabel = '';
  paymentAmount = 0;
  paymentDate   = new Date().toISOString().slice(0, 10);
  paymentChannel = '';
  paymentRef    = '';
  paymentNotes  = '';
  paymentError  = '';
  payments: SchedulePayment[] = [];

  // ── Send modal ───────────────────────────────────────────────────────────
  sendItemId: string | null = null;
  sendEmail = true;
  sendSms   = false;
  sendError = '';
  sendSuccess = '';

  // ── Action feedback ──────────────────────────────────────────────────────
  actionError   = '';
  actionSuccess = '';

  get canWrite(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['contractId'] && this.contractId) {
      this.loadItems();
    }
  }

  loadItems(): void {
    this.loading = true;
    this.error = '';
    this.svc.list(this.contractId).subscribe({
      next: items => { this.items = items; this.loading = false; },
      error: (e: HttpErrorResponse) => {
        this.error = e.error?.message ?? 'Failed to load schedule';
        this.loading = false;
      },
    });
  }

  // ── Create ───────────────────────────────────────────────────────────────

  submitCreate(): void {
    this.createError = '';
    const req: CreateScheduleItemRequest = {
      label:   this.newLabel.trim(),
      amount:  this.newAmount,
      dueDate: this.newDueDate,
      notes:   this.newNotes.trim() || undefined,
    };
    this.svc.create(this.contractId, req).subscribe({
      next: item => {
        this.items = [...this.items, item];
        this.showCreateForm = false;
        this.resetCreateForm();
      },
      error: (e: HttpErrorResponse) => {
        this.createError = e.error?.message ?? 'Create failed';
      },
    });
  }

  resetCreateForm(): void {
    this.newLabel = '';
    this.newAmount = 0;
    this.newDueDate = '';
    this.newNotes = '';
  }

  // ── Issue ────────────────────────────────────────────────────────────────

  issue(item: PaymentScheduleItem): void {
    this.clearFeedback();
    this.svc.issue(item.id).subscribe({
      next: updated => this.replaceItem(updated),
      error: (e: HttpErrorResponse) => {
        this.actionError = e.error?.message ?? 'Issue failed';
      },
    });
  }

  // ── Send ─────────────────────────────────────────────────────────────────

  openSendModal(item: PaymentScheduleItem): void {
    this.sendItemId    = item.id;
    this.sendError     = '';
    this.sendSuccess   = '';
  }

  submitSend(): void {
    if (!this.sendItemId) return;
    this.sendError = '';
    const req: SendScheduleItemRequest = {
      contactId: this.buyerContactId ?? undefined,
      sendEmail: this.sendEmail,
      sendSms:   this.sendSms,
    };
    this.svc.send(this.sendItemId, req).subscribe({
      next: updated => {
        this.replaceItem(updated);
        this.sendSuccess = 'Notification envoyée.';
        this.sendItemId = null;
      },
      error: (e: HttpErrorResponse) => {
        this.sendError = e.error?.message ?? 'Send failed';
      },
    });
  }

  // ── Cancel ───────────────────────────────────────────────────────────────

  cancelItem(item: PaymentScheduleItem): void {
    if (!confirm('Annuler cet appel de fonds ?')) return;
    this.clearFeedback();
    this.svc.cancel(item.id).subscribe({
      next: updated => this.replaceItem(updated),
      error: (e: HttpErrorResponse) => {
        this.actionError = e.error?.message ?? 'Cancel failed';
      },
    });
  }

  // ── Delete ───────────────────────────────────────────────────────────────

  deleteItem(item: PaymentScheduleItem): void {
    if (!confirm('Supprimer cet appel de fonds ?')) return;
    this.clearFeedback();
    this.svc.delete(item.id).subscribe({
      next: () => { this.items = this.items.filter(i => i.id !== item.id); },
      error: (e: HttpErrorResponse) => {
        this.actionError = e.error?.message ?? 'Delete failed';
      },
    });
  }

  // ── Download PDF ─────────────────────────────────────────────────────────

  downloadPdf(item: PaymentScheduleItem): void {
    this.svc.downloadPdf(item.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a   = document.createElement('a');
        a.href    = url;
        a.download = `appel-de-fonds-${item.sequence}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => { this.actionError = 'PDF download failed'; },
    });
  }

  // ── Add payment modal ────────────────────────────────────────────────────

  openPaymentModal(item: PaymentScheduleItem): void {
    this.paymentItemId    = item.id;
    this.paymentItemLabel = item.label;
    this.paymentAmount    = 0;
    this.paymentDate      = new Date().toISOString().slice(0, 10);
    this.paymentChannel   = '';
    this.paymentRef       = '';
    this.paymentNotes     = '';
    this.paymentError     = '';
    this.payments         = [];
    this.svc.listPayments(item.id).subscribe({
      next: p => { this.payments = p; },
    });
  }

  submitPayment(): void {
    if (!this.paymentItemId) return;
    this.paymentError = '';
    const req: AddPaymentRequest = {
      amount:           this.paymentAmount,
      paidAt:           this.paymentDate,
      channel:          this.paymentChannel.trim() || undefined,
      paymentReference: this.paymentRef.trim()     || undefined,
      notes:            this.paymentNotes.trim()   || undefined,
    };
    this.svc.addPayment(this.paymentItemId, req).subscribe({
      next: payment => {
        this.payments = [...this.payments, payment];
        this.paymentAmount = 0;
        this.paymentRef    = '';
        this.paymentNotes  = '';
        // Reload items to reflect updated status/remaining
        this.loadItems();
      },
      error: (e: HttpErrorResponse) => {
        this.paymentError = e.error?.message ?? 'Payment failed';
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private replaceItem(updated: PaymentScheduleItem): void {
    this.items = this.items.map(i => i.id === updated.id ? updated : i);
  }

  private clearFeedback(): void {
    this.actionError   = '';
    this.actionSuccess = '';
  }

  statusClass(status: string): string {
    return {
      DRAFT:    'badge-draft',
      ISSUED:   'badge-issued',
      SENT:     'badge-sent',
      OVERDUE:  'badge-overdue',
      PAID:     'badge-paid',
      CANCELED: 'badge-canceled',
    }[status] ?? '';
  }
}
