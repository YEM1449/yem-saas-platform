import {
  Component, Input, Output, EventEmitter,
  signal, inject, ChangeDetectionStrategy,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { DecimalPipe } from '@angular/common';
import { Viewer3dApiService } from '../../services/viewer-3d-api.service';
import { Project3dModel } from '../../models/project-3d-model.model';
import { firstValueFrom } from 'rxjs';

type UploadStep = 'idle' | 'requesting' | 'uploading' | 'confirming' | 'done' | 'error';

@Component({
  selector: 'app-model-upload-admin',
  standalone: true,
  imports: [DecimalPipe, TranslatePipe],
  templateUrl: './model-upload-admin.component.html',
  styleUrl: './model-upload-admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModelUploadAdminComponent {
  private i18n = inject(I18nService);
  @Input({ required: true }) projetId!: string;
  /** Emitted after a successful upload+confirm cycle. */
  @Output() uploaded = new EventEmitter<Project3dModel>();

  private readonly api = inject(Viewer3dApiService);

  readonly step       = signal<UploadStep>('idle');
  readonly progress   = signal(0);
  readonly errorMsg   = signal('');
  readonly selectedFile = signal<File | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0] ?? null;
    if (!file) return;

    if (!file.name.endsWith('.glb')) {
      this.errorMsg.set(this.i18n.instant('viewer3d.upload.errGlbOnly'));
      this.step.set('error');
      return;
    }
    if (file.size > 52_428_800) {
      this.errorMsg.set(this.i18n.instant('viewer3d.upload.errMaxSize'));
      this.step.set('error');
      return;
    }

    this.selectedFile.set(file);
    this.errorMsg.set('');
    this.step.set('idle');
  }

  async startUpload(): Promise<void> {
    const file = this.selectedFile();
    if (!file) return;

    try {
      // Step 1 — request pre-signed PUT URL
      this.step.set('requesting');
      const { uploadUrl, fileKey } = await firstValueFrom(
        this.api.requestUploadUrl(this.projetId, file.name, file.size)
      );

      // Step 2 — upload directly to R2 via XHR (for progress events)
      this.step.set('uploading');
      await this.putToR2(uploadUrl, file);

      // Step 3 — confirm metadata in backend
      this.step.set('confirming');
      const model = await firstValueFrom(this.api.confirmUpload(this.projetId, fileKey));

      this.step.set('done');
      this.uploaded.emit(model);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Erreur inconnue.';
      this.errorMsg.set(msg);
      this.step.set('error');
    }
  }

  reset(): void {
    this.step.set('idle');
    this.progress.set(0);
    this.errorMsg.set('');
    this.selectedFile.set(null);
  }

  private putToR2(url: string, file: File): Promise<void> {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('PUT', url);
      xhr.setRequestHeader('Content-Type', 'model/gltf-binary');

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          this.progress.set(Math.round((e.loaded / e.total) * 100));
        }
      };

      xhr.onload  = () => (xhr.status >= 200 && xhr.status < 300)
        ? resolve()
        : reject(new Error(`Upload R2 échoué : ${xhr.status}`));
      xhr.onerror = () => reject(new Error(this.i18n.instant('viewer3d.upload.errNetwork')));
      xhr.send(file);
    });
  }
}
