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
  styleUrl: './portal-contracts.component.css',
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
    return ({ PENDING: 'En attente', GENERATED: 'Généré', SIGNED: 'Signé' } as Record<string, string>)[s] ?? s;
  }

  statusClass(s: string): string {
    return ({ PENDING: 'badge-draft', GENERATED: 'badge-generated', SIGNED: 'badge-signed' } as Record<string, string>)[s] ?? '';
  }

  downloadDoc(venteId: string, docId: string): void {
    this.service.downloadContractPdf(venteId, docId).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href     = url;
      a.download = `contrat_${venteId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
