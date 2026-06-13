import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { OutboxService } from './outbox.service';
import {
  MessageChannel,
  MessageStatus,
  OutboundMessage,
  SendMessageRequest,
} from '../../core/models/outbox.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-outbox',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './outbox.component.html',
  styleUrl: './outbox.component.css',
})
export class OutboxComponent implements OnInit {
  private outboxSvc = inject(OutboxService);

  // ── List state ──────────────────────────────────────────────────────────
  messages: OutboundMessage[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 20;
  filterChannel: MessageChannel | '' = '';
  filterStatus: MessageStatus | '' = '';
  listLoading = false;
  listError = '';

  // ── Compose form state ───────────────────────────────────────────────────
  showCompose = false;
  composing = false;
  composeSuccess = '';
  composeError = '';

  form: SendMessageRequest = {
    channel: 'EMAIL',
    recipient: '',
    subject: '',
    body: '',
  };

  ngOnInit(): void {
    this.load();
  }

  // ── List actions ─────────────────────────────────────────────────────────

  load(page = 0): void {
    this.listLoading = true;
    this.listError   = '';
    this.currentPage = page;

    this.outboxSvc.list({
      channel: this.filterChannel || undefined,
      status:  this.filterStatus  || undefined,
      page,
      size: this.pageSize,
    }).subscribe({
      next: (resp) => {
        this.messages      = resp.content;
        this.totalElements = resp.totalElements;
        this.listLoading   = false;
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.listError   = body?.message ?? `Failed to load messages (${err.status})`;
        this.listLoading = false;
      },
    });
  }

  applyFilters(): void { this.load(0); }
  prevPage(): void { if (this.currentPage > 0) this.load(this.currentPage - 1); }
  nextPage(): void { if ((this.currentPage + 1) * this.pageSize < this.totalElements) this.load(this.currentPage + 1); }

  get totalPages(): number { return Math.ceil(this.totalElements / this.pageSize); }

  // ── Compose actions ───────────────────────────────────────────────────────

  openCompose(): void {
    this.showCompose   = true;
    this.composeError  = '';
    this.composeSuccess = '';
    this.form = { channel: 'EMAIL', recipient: '', subject: '', body: '' };
  }

  cancelCompose(): void { this.showCompose = false; }

  submitCompose(): void {
    if (!this.form.body?.trim()) {
      this.composeError = 'Body is required.';
      return;
    }
    if (!this.form.recipient?.trim()) {
      this.composeError = 'Recipient is required.';
      return;
    }

    this.composing     = true;
    this.composeError  = '';
    this.composeSuccess = '';

    const req: SendMessageRequest = {
      channel:   this.form.channel,
      recipient: this.form.recipient?.trim() || null,
      subject:   this.form.channel === 'EMAIL' ? (this.form.subject?.trim() || null) : null,
      body:      this.form.body.trim(),
    };

    this.outboxSvc.send(req).subscribe({
      next: (resp) => {
        this.composing      = false;
        this.composeSuccess = `Message queued — ID: ${resp.messageId}`;
        this.showCompose    = false;
        this.load(0);
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.composeError = body?.message ?? `Failed to send (${err.status})`;
        this.composing    = false;
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  statusClass(status: MessageStatus): string {
    return { PENDING: 'badge-pending', SENT: 'badge-sent', FAILED: 'badge-failed' }[status] ?? '';
  }

  get isEmail(): boolean { return this.form.channel === 'EMAIL'; }
}
