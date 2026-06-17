import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../../core/auth/auth.service';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { ContactPickerComponent } from '../../../../shared/pickers/contact-picker.component';
import { UserPickerComponent } from '../../../../shared/pickers/user-picker.component';
import { ProjectService } from '../../../../features/projects/project.service';
import { Project } from '../../../../core/models/project.model';
import { PropertyService } from '../../../../features/properties/property.service';
import { Property } from '../../../../core/models/property.model';
import { VisiteApiService, TypeVisite, CreateVisiteRequest, UpdateVisiteRequest } from '../../services/visite-api.service';
import { casablancaWallToInstant, instantToCasablancaWall } from '../../services/casablanca-time';

/**
 * Quick prise-de-RDV form (RG-V01, KARIM: < 20 s). Doubles as the edit form (route :id/modifier).
 * Contact and agent are set only at creation (the backend update contract keeps them fixed).
 * Conflict (409, RG-V05) surfaces a clear message; MANAGER/ADMIN can re-submit with override.
 */
@Component({
  selector: 'app-visite-form',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe, ContactPickerComponent, UserPickerComponent],
  templateUrl: './visite-form.component.html',
  styleUrl: './visite-form.component.css',
})
export class VisiteFormComponent implements OnInit {
  private api = inject(VisiteApiService);
  private projectSvc = inject(ProjectService);
  private propertySvc = inject(PropertyService);
  private auth = inject(AuthService);
  private i18n = inject(I18nService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  readonly types: TypeVisite[] = ['SUR_SITE', 'AGENCE', 'VISIO', 'TELEPHONIQUE'];
  readonly isManager = signal(false);
  readonly editId = signal<string | null>(null);
  readonly contactNom = signal<string | null>(null);

  projects = signal<Project[]>([]);
  properties = signal<Property[]>([]);

  // Form model
  contactId: string | null = null;
  initialContactId: string | null = null;   // prefill from contact detail (queryParam)
  agentId: string | null = null;
  projectId = '';
  propertyId = '';
  dateHeure = '';                 // datetime-local (Casablanca wall-clock)
  dureeMinutes = 30;
  type: TypeVisite = 'SUR_SITE';
  lieu = '';
  override = false;

  saving = signal(false);
  error = signal('');
  conflict = signal(false);

  ngOnInit(): void {
    const role = this.auth.user?.role ?? '';
    this.isManager.set(role.includes('ADMIN') || role.includes('MANAGER'));
    this.projectSvc.list(true).subscribe({ next: ps => this.projects.set(ps), error: () => {} });

    const id = this.route.snapshot.paramMap.get('id');
    const prefillDate = this.route.snapshot.queryParamMap.get('date');
    if (prefillDate) this.dateHeure = prefillDate;
    const prefillContact = this.route.snapshot.queryParamMap.get('contactId');
    if (prefillContact) { this.initialContactId = prefillContact; this.contactId = prefillContact; }

    if (id) {
      this.editId.set(id);
      this.api.get(id).subscribe({
        next: v => {
          this.contactId = v.contactId;
          this.contactNom.set(v.contactNom);
          this.projectId = v.projectId ?? '';
          this.propertyId = v.propertyId ?? '';
          this.dateHeure = instantToCasablancaWall(v.dateHeure);
          this.dureeMinutes = v.dureeMinutes;
          this.type = v.type;
          this.lieu = v.lieu ?? '';
          if (this.projectId) this.loadProperties();
        },
        error: () => this.error.set(this.i18n.instant('visites.detail.loadError')),
      });
    }
  }

  onProject(): void {
    this.propertyId = '';
    this.properties.set([]);
    if (this.projectId) this.loadProperties();
  }

  private loadProperties(): void {
    this.propertySvc.list({ projectId: this.projectId })
      .subscribe({ next: ps => this.properties.set(ps), error: () => {} });
  }

  propertyLabel(p: Property): string {
    return `${p.referenceCode} — ${p.title}`;
  }

  get canSubmit(): boolean {
    return !!this.dateHeure && (this.isEdit || !!this.contactId);
  }

  get isEdit(): boolean { return this.editId() !== null; }

  submit(): void {
    if (!this.canSubmit) {
      this.error.set(this.i18n.instant(this.contactId ? 'visites.form.dateRequired' : 'visites.form.contactRequired'));
      return;
    }
    this.error.set('');
    this.conflict.set(false);
    this.saving.set(true);
    const dateHeure = casablancaWallToInstant(this.dateHeure);

    const done = {
      next: (v: { id: string }) => this.router.navigate(['/app/visites', v.id]),
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        if (err.status === 409) {
          this.conflict.set(true);
          this.error.set(this.i18n.instant('visites.form.conflictError'));
        } else {
          this.error.set((err.error as { message?: string })?.message
            ?? this.i18n.instant('visites.form.genericError', { status: err.status }));
        }
      },
    };

    if (this.isEdit) {
      const req: UpdateVisiteRequest = {
        propertyId: this.propertyId || null,
        projectId: this.projectId || null,
        dateHeure, dureeMinutes: this.dureeMinutes, type: this.type,
        lieu: this.lieu || null, override: this.override,
      };
      this.api.update(this.editId()!, req).subscribe(done);
    } else {
      const req: CreateVisiteRequest = {
        contactId: this.contactId!,
        propertyId: this.propertyId || null,
        projectId: this.projectId || null,
        agentId: this.agentId || null,
        dateHeure, dureeMinutes: this.dureeMinutes, type: this.type,
        lieu: this.lieu || null, override: this.override,
      };
      this.api.create(req).subscribe(done);
    }
  }
}
