import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PortalContractsService } from '../../core/portal-contracts.service';
import { PortalContract } from '../../../core/models/portal.model';

@Component({
  selector: 'app-portal-contracts',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './portal-contracts.component.html',
})
export class PortalContractsComponent implements OnInit {
  private service = inject(PortalContractsService);

  contracts = signal<PortalContract[]>([]);
  loading   = signal(true);
  error     = signal('');

  ngOnInit(): void {
    this.service.listContracts().subscribe({
      next:  (data) => { this.contracts.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Failed to load contracts.'); this.loading.set(false); },
    });
  }

  statusLabel(s: string): string {
    return { DRAFT: 'Draft', SIGNED: 'Signed', CANCELED: 'Cancelled' }[s] ?? s;
  }

  statusClass(s: string): string {
    return { DRAFT: 'badge-draft', SIGNED: 'badge-signed', CANCELED: 'badge-canceled' }[s] ?? '';
  }

  downloadPdf(contractId: string): void {
    this.service.downloadContractPdf(contractId).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href     = url;
      a.download = `contract_${contractId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
