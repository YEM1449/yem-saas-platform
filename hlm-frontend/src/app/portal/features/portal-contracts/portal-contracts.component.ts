import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { RouterModule } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { PortalContractsService } from '../../core/portal-contracts.service';
import { PortalContract } from '../../../core/models/portal.model';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-portal-contracts',
  standalone: true,
  imports: [RouterModule, DatePipe, DecimalPipe, NgClass, TranslatePipe],
  templateUrl: './portal-contracts.component.html',
  styleUrl: './portal-contracts.component.css',
})
export class PortalContractsComponent implements OnInit {
  private service = inject(PortalContractsService);
  private i18n = inject(I18nService);

  contracts = signal<PortalContract[]>([]);
  loading   = signal(true);
  error     = signal('');

  ngOnInit(): void {
    this.service.listContracts().subscribe({
      next:  (data) => { this.contracts.set(data); this.loading.set(false); },
      error: ()     => { this.error.set(this.i18n.instant('portal.contracts.loadError')); this.loading.set(false); },
    });
  }

  statusLabel(s: string): string {
    return this.i18n.instant('portal.contracts.status.' + s);
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
