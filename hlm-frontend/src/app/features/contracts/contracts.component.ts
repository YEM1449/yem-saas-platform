import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ContractService, ListContractsParams } from './contract.service';
import { ContractResponse, SaleContractStatus } from '../../core/models/contract.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { OutboxService } from '../outbox/outbox.service';
import { MessageChannel } from '../../core/models/outbox.model';

@Component({
  selector: 'app-contracts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contracts.component.html',
  styleUrl: './contracts.component.css',
})
export class ContractsComponent implements OnInit {
  private contractSvc = inject(ContractService);
  private outboxSvc  = inject(OutboxService);

  contracts: ContractResponse[] = [];
  loading = false;
  error = '';

  filterStatus: SaleContractStatus | '' = '';
  readonly statuses: SaleContractStatus[] = ['DRAFT', 'SIGNED', 'CANCELED'];

  sendingId: string | null = null;
  sendSuccess = '';
  sendError   = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const params: ListContractsParams = {};
    if (this.filterStatus) params.status = this.filterStatus;

    this.contractSvc.list(params).subscribe({
      next: (data) => {
        this.contracts = data;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Failed to load contracts (${err.status})`;
      },
    });
  }

  downloadPdf(c: ContractResponse): void {
    this.contractSvc.downloadPdf(c.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `contract_${c.id.substring(0, 8).toUpperCase()}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.error = 'Failed to download PDF.';
      },
    });
  }

  sendMessage(c: ContractResponse, channel: MessageChannel): void {
    this.sendingId = c.id;
    this.sendSuccess = '';
    this.sendError   = '';
    const ref = c.id.substring(0, 8).toUpperCase();
    this.outboxSvc.send({
      channel,
      contactId: c.buyerContactId,
      subject: channel === 'EMAIL' ? `Contrat ${ref}` : null,
      body: `Bonjour, voici les informations relatives à votre contrat ${ref}.`,
      correlationType: 'CONTRACT',
      correlationId: c.id,
    }).subscribe({
      next: () => {
        this.sendingId = null;
        this.sendSuccess = `Message envoyé (${channel}) pour le contrat ${ref}.`;
      },
      error: (err: HttpErrorResponse) => {
        this.sendingId = null;
        const body = err.error as ErrorResponse | null;
        this.sendError = body?.message ?? `Échec d'envoi (${err.status})`;
      },
    });
  }
}
