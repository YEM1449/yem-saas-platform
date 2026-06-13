import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SocieteService } from './societe.service';
import { SocieteDetailDto } from './societe.model';

@Component({
  selector: 'app-societe-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './societe-form.component.html',
  styleUrl: './societe-form.component.css',
})
export class SocieteFormComponent implements OnInit {
  private svc = inject(SocieteService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  isEdit = false;
  societeId = '';
  loading = false;
  saving = false;
  error = '';

  // Version for optimistic locking (edit only)
  version = 0;

  // Identité
  nom = '';
  pays = '';
  nomCommercial = '';
  formeJuridique = '';

  // RGPD
  emailDpo = '';
  dpoNom = '';
  numeroCndp = '';
  numeroCnil = '';
  baseJuridiqueDefaut = '';

  // Abonnement
  planAbonnement = 'STARTER';
  maxUtilisateurs: number | null = null;
  maxBiens: number | null = null;

  // Notes
  notesInternes = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.societeId = id;
      this.loadDetail(id);
    }
  }

  private loadDetail(id: string): void {
    this.loading = true;
    this.svc.getDetail(id).subscribe({
      next: (detail: SocieteDetailDto) => {
        this.loading = false;
        this.version = detail.version;
        this.nom = detail.nom ?? '';
        this.pays = detail.pays ?? '';
        this.nomCommercial = detail.nomCommercial ?? '';
        this.formeJuridique = detail.formeJuridique ?? '';
        this.emailDpo = detail.emailDpo ?? '';
        this.dpoNom = detail.dpoNom ?? '';
        this.numeroCndp = detail.numeroCndp ?? '';
        this.numeroCnil = detail.numeroCnil ?? '';
        this.baseJuridiqueDefaut = detail.baseJuridiqueDefaut ?? '';
        this.planAbonnement = detail.planAbonnement ?? 'STARTER';
        this.maxUtilisateurs = detail.maxUtilisateurs ?? null;
        this.maxBiens = detail.maxBiens ?? null;
        this.notesInternes = detail.notesInternes ?? '';
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = this.extractError(err);
      },
    });
  }

  submit(): void {
    if (!this.nom.trim() || !this.pays.trim()) {
      this.error = 'Le nom et le pays sont obligatoires.';
      return;
    }
    this.saving = true;
    this.error = '';
    if (this.isEdit) {
      this.svc.update(this.societeId, {
        version: this.version,
        nom: this.nom.trim(),
        pays: this.pays.trim(),
        emailDpo: this.emailDpo.trim() || undefined,
        dpoNom: this.dpoNom.trim() || undefined,
        numeroCndp: this.numeroCndp.trim() || undefined,
        numeroCnil: this.numeroCnil.trim() || undefined,
        baseJuridiqueDefaut: this.baseJuridiqueDefaut.trim() || undefined,
        planAbonnement: this.planAbonnement,
        maxUtilisateurs: this.maxUtilisateurs ?? undefined,
        maxBiens: this.maxBiens ?? undefined,
        notesInternes: this.notesInternes.trim() || undefined,
      }).subscribe({
        next: (res) => {
          this.saving = false;
          this.router.navigate(['/superadmin/societes', res.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = this.extractError(err);
        },
      });
    } else {
      this.svc.create({
        nom: this.nom.trim(),
        pays: this.pays.trim(),
        emailDpo: this.emailDpo.trim() || undefined,
        planAbonnement: this.planAbonnement,
        notesInternes: this.notesInternes.trim() || undefined,
      }).subscribe({
        next: (res) => {
          this.saving = false;
          this.router.navigate(['/superadmin/societes', res.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = this.extractError(err);
        },
      });
    }
  }

  cancel(): void {
    if (this.isEdit) {
      this.router.navigate(['/superadmin/societes', this.societeId]);
    } else {
      this.router.navigate(['/superadmin/societes']);
    }
  }

  private extractError(err: HttpErrorResponse): string {
    if (err.status === 401) return 'Session expirée. Veuillez vous reconnecter.';
    if (err.status === 403) return 'Accès refusé.';
    if (err.status === 409) return 'Conflit de version. Rechargez la page.';
    const body = err.error as { message?: string } | null;
    if (body?.message) return body.message;
    return `Erreur (${err.status})`;
  }
}
