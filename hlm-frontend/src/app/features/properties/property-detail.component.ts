import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { PropertyService } from './property.service';
import { Property, PropertyMedia } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentListComponent } from '../documents/document-list.component';

@Component({
  selector: 'app-property-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DocumentListComponent, TranslateModule],
  templateUrl: './property-detail.component.html',
  styleUrl: './property-detail.component.css',
})
export class PropertyDetailComponent implements OnInit {
  private svc = inject(PropertyService);
  private route = inject(ActivatedRoute);
  private auth = inject(AuthService);

  property: Property | null = null;
  loading = true;
  error = '';

  media: PropertyMedia[] = [];
  mediaLoading = false;
  mediaError = '';

  uploadLoading = false;
  uploadError = '';
  deleteError = '';

  statusChanging = false;
  statusError = '';
  statusSuccess = '';

  private propertyId = '';

  get canManageMedia(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  get isAdmin(): boolean {
    return this.auth.user?.role === 'ROLE_ADMIN';
  }

  /**
   * Statuses reachable by manual admin editorial action.
   * RESERVED and SOLD are set exclusively by the reservation/contract workflow —
   * they do NOT appear here. To release a RESERVED property, cancel the reservation.
   */
  get allowedStatusTransitions(): { value: string; label: string }[] {
    if (!this.property) return [];
    const current = this.property.status;
    const all: Record<string, { value: string; label: string }[]> = {
      DRAFT:      [{ value: 'ACTIVE',     label: 'Publier (Actif)' },
                   { value: 'ARCHIVED',   label: 'Archiver' }],
      ACTIVE:     [{ value: 'DRAFT',      label: 'Repasser en brouillon' },
                   { value: 'WITHDRAWN',  label: 'Retirer du marché' }],
      RESERVED:   [],
      WITHDRAWN:  [{ value: 'ACTIVE',     label: 'Re-publier (Actif)' },
                   { value: 'ARCHIVED',   label: 'Archiver' }],
      SOLD:       [],
      ARCHIVED:   [{ value: 'DRAFT',      label: 'Remettre en brouillon' }],
    };
    return all[current] ?? [];
  }

  changeStatus(newStatus: string): void {
    if (!this.property || !newStatus || this.statusChanging) return;
    this.statusChanging = true;
    this.statusError = '';
    this.statusSuccess = '';

    this.svc.setStatus(this.property.id, newStatus).subscribe({
      next: (updated) => {
        this.property = updated;
        this.statusChanging = false;
        this.statusSuccess = `Statut mis à jour : ${newStatus}`;
        setTimeout(() => { this.statusSuccess = ''; }, 3000);
      },
      error: (err: HttpErrorResponse) => {
        this.statusChanging = false;
        const body = err.error as ErrorResponse | null;
        this.statusError = body?.message ?? `Erreur lors du changement de statut (${err.status})`;
      },
    });
  }

  ngOnInit(): void {
    this.propertyId = this.route.snapshot.paramMap.get('id')!;
    this.svc.getById(this.propertyId).subscribe({
      next: (p) => {
        this.property = p;
        this.loading = false;
        this.loadMedia();
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = err.status === 404
          ? 'Property not found.'
          : (body?.message ?? `Failed to load property (${err.status})`);
      },
    });
  }

  loadMedia(): void {
    this.mediaLoading = true;
    this.mediaError = '';
    this.svc.listMedia(this.propertyId).subscribe({
      next: (items) => {
        this.media = items;
        this.mediaLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.mediaLoading = false;
        this.mediaError = `Failed to load media (${err.status})`;
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploadLoading = true;
    this.uploadError = '';
    this.svc.uploadMedia(this.propertyId, file).subscribe({
      next: (media) => {
        this.media = [...this.media, media];
        this.uploadLoading = false;
        input.value = '';
      },
      error: (err: HttpErrorResponse) => {
        this.uploadLoading = false;
        const body = err.error as ErrorResponse | null;
        this.uploadError = body?.message ?? `Upload failed (${err.status})`;
        input.value = '';
      },
    });
  }

  deleteMedia(mediaId: string): void {
    this.deleteError = '';
    this.svc.deleteMedia(mediaId).subscribe({
      next: () => {
        this.media = this.media.filter(m => m.id !== mediaId);
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.deleteError = body?.message ?? `Delete failed (${err.status})`;
      },
    });
  }

  downloadUrl(mediaId: string): string {
    return this.svc.downloadMediaUrl(mediaId);
  }

  isImage(contentType: string): boolean {
    return contentType.startsWith('image/');
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      DRAFT: 'Brouillon', ACTIVE: 'Actif', RESERVED: 'Réservé',
      SOLD: 'Vendu', WITHDRAWN: 'Retiré', ARCHIVED: 'Archivé',
    };
    return labels[status] ?? status;
  }

  typeLabel(type: string): string {
    const labels: Record<string, string> = {
      VILLA: 'Villa', APPARTEMENT: 'Appartement', STUDIO: 'Studio',
      T2: 'T2', T3: 'T3', DUPLEX: 'Duplex', COMMERCE: 'Commerce',
      LOCAL: 'Local commercial', TERRAIN: 'Terrain',
    };
    return labels[type] ?? type;
  }
}
