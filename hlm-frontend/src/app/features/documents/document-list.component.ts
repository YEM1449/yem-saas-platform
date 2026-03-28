import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { DocumentService } from './document.service';
import { DocumentResponse, DocumentEntityType } from './document.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-document-list',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './document-list.component.html',
  styleUrl: './document-list.component.css',
})
export class DocumentListComponent implements OnInit {
  @Input({ required: true }) entityType!: DocumentEntityType;
  @Input({ required: true }) entityId!: string;

  private svc = inject(DocumentService);

  documents: DocumentResponse[] = [];
  loading = false;
  error = '';
  uploading = false;
  uploadError = '';
  deleteError = '';
  description = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.list(this.entityType, this.entityId).subscribe({
      next: (docs) => { this.documents = docs; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Erreur de chargement (${err.status})`;
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploading = true;
    this.uploadError = '';
    this.svc.upload(this.entityType, this.entityId, file, this.description.trim() || undefined).subscribe({
      next: (doc) => {
        this.documents = [...this.documents, doc];
        this.uploading = false;
        this.description = '';
        input.value = '';
      },
      error: (err: HttpErrorResponse) => {
        this.uploading = false;
        const body = err.error as ErrorResponse | null;
        this.uploadError = body?.message ?? `Erreur upload (${err.status})`;
        input.value = '';
      },
    });
  }

  download(doc: DocumentResponse): void {
    window.open(this.svc.downloadUrl(doc.id), '_blank');
  }

  deleteDoc(doc: DocumentResponse): void {
    if (!confirm(`Supprimer "${doc.fileName}" ?`)) return;
    this.deleteError = '';
    this.svc.delete(doc.id).subscribe({
      next: () => { this.documents = this.documents.filter(d => d.id !== doc.id); },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.deleteError = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  fileIcon(mimeType: string): string {
    if (mimeType.startsWith('image/')) return '🖼';
    if (mimeType === 'application/pdf') return '📄';
    return '📎';
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  }
}
