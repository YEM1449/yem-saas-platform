import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
  imports: [CommonModule, RouterLink, DocumentListComponent, TranslateModule],
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

  private propertyId = '';

  get canManageMedia(): boolean {
    const role = this.auth.user?.role;
    return role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER';
  }

  get isAdmin(): boolean {
    return this.auth.user?.role === 'ROLE_ADMIN';
  }

  ngOnInit(): void {
    this.propertyId = this.route.snapshot.paramMap.get('id')!;
    this.svc.list().subscribe({
      next: (list) => {
        this.property = list.find(p => p.id === this.propertyId) ?? null;
        this.loading = false;
        if (this.property) {
          this.loadMedia();
        } else {
          this.error = 'Property not found.';
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Failed to load property (${err.status})`;
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
}
