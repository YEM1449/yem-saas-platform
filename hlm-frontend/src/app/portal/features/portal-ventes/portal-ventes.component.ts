import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { PortalVentesService } from '../../core/portal-ventes.service';
import { Vente, EcheanceStatut, VenteDocument } from '../../../features/ventes/vente.service';
import { PipelineStepperComponent } from '../../../features/ventes/pipeline-stepper.component';

@Component({
  selector: 'app-portal-ventes',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, PipelineStepperComponent, TranslateModule],
  templateUrl: './portal-ventes.component.html',
  styleUrl: './portal-ventes.component.css',
})
export class PortalVentesComponent implements OnInit {
  private svc = inject(PortalVentesService);

  ventes  = signal<Vente[]>([]);
  loading = signal(true);
  error   = signal('');

  /** Per-vente upload state */
  uploading  = signal<Record<string, boolean>>({});
  uploadErr  = signal<Record<string, string>>({});
  legalAck   = signal<Record<string, boolean>>({});
  uploadFile: Record<string, File | null> = {};

  ngOnInit(): void {
    this.svc.list().subscribe({
      next:  (data) => { this.ventes.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Erreur lors du chargement.'); this.loading.set(false); },
    });
  }

  echLabel(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'En attente', PAYEE: 'Payée', EN_RETARD: 'En retard' }[s] ?? s;
  }

  echClass(s: EcheanceStatut): string {
    return { EN_ATTENTE: 'badge-info', PAYEE: 'badge-success', EN_RETARD: 'badge-error' }[s] ?? '';
  }

  paidTotal(v: Vente): number {
    return v.echeances.filter(e => e.statut === 'PAYEE').reduce((sum, e) => sum + e.montant, 0);
  }

  remainingTotal(v: Vente): number {
    return v.echeances.filter(e => e.statut !== 'PAYEE').reduce((sum, e) => sum + e.montant, 0);
  }

  contractDocFor(v: Vente): VenteDocument | undefined {
    return v.documents.find(d => d.documentType === 'CONTRAT_GENERE');
  }

  hasSignedContract(v: Vente): boolean {
    return v.documents.some(d => d.documentType === 'CONTRAT_SIGNE_CLIENT');
  }

  onFileSelected(key: string, e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0];
    this.uploadFile = { ...this.uploadFile, [key]: f ?? null };
  }

  toggleLegalAck(venteId: string): void {
    this.legalAck.update(m => ({ ...m, [venteId]: !m[venteId] }));
  }

  downloadDoc(venteId: string, docId: string, fileName: string): void {
    this.svc.downloadDocument(venteId, docId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {},
    });
  }

  uploadContractSigned(v: Vente): void {
    const file = this.uploadFile[v.id];
    if (!file || !this.legalAck()[v.id]) return;

    this.uploading.update(u => ({ ...u, [v.id]: true }));
    this.uploadErr.update(e => ({ ...e, [v.id]: '' }));

    this.svc.uploadDocument(v.id, file, 'CONTRAT_SIGNE_CLIENT').subscribe({
      next: () => {
        this.uploading.update(u => ({ ...u, [v.id]: false }));
        this.uploadFile = { ...this.uploadFile, [v.id]: null };
        // Reload to reflect new document in the list
        this.svc.list().subscribe(data => this.ventes.set(data));
      },
      error: () => {
        this.uploading.update(u => ({ ...u, [v.id]: false }));
        this.uploadErr.update(e => ({ ...e, [v.id]: 'Erreur lors du dépôt. Veuillez réessayer.' }));
      },
    });
  }

  uploadGeneralDoc(v: Vente, docType: string): void {
    const key = `${v.id}_${docType}`;
    const file = this.uploadFile[key];
    if (!file) return;

    this.uploading.update(u => ({ ...u, [key]: true }));

    this.svc.uploadDocument(v.id, file, docType).subscribe({
      next: () => {
        this.uploading.update(u => ({ ...u, [key]: false }));
        this.uploadFile = { ...this.uploadFile, [key]: null };
        this.svc.list().subscribe(data => this.ventes.set(data));
      },
      error: () => this.uploading.update(u => ({ ...u, [key]: false })),
    });
  }
}
