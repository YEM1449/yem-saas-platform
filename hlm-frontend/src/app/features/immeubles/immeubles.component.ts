import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ImmeubleService, Immeuble, CreateImmeubleRequest } from './immeuble.service';
import { ProjectService } from '../projects/project.service';
import { Project } from '../../core/models/project.model';
import { AuthService } from '../../core/auth/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-immeubles',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './immeubles.component.html',
})
export class ImmeublesComponent implements OnInit {
  private svc = inject(ImmeubleService);
  private projectSvc = inject(ProjectService);
  private auth = inject(AuthService);

  immeubles: Immeuble[] = [];
  projects: Project[] = [];
  loading = true;
  error = '';

  filterProjectId = '';

  showModal = false;
  submitting = false;
  submitError = '';

  form: CreateImmeubleRequest = { projectId: '', nom: '', adresse: null, nbEtages: null, description: null };

  get canCreate(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.loadList();
    this.projectSvc.list(true).subscribe({
      next: (data) => this.projects = data,
    });
  }

  loadList(): void {
    this.loading = true;
    this.error = '';
    this.svc.list(this.filterProjectId || undefined).subscribe({
      next: (data) => { this.immeubles = data; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = `Failed to load buildings (${err.status})`;
      },
    });
  }

  onFilterChange(): void { this.loadList(); }

  openModal(): void {
    this.form = { projectId: '', nom: '', adresse: null, nbEtages: null, description: null };
    this.submitError = '';
    this.showModal = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
  }

  submitCreate(): void {
    if (!this.form.projectId || !this.form.nom.trim()) {
      this.submitError = 'Le projet et le nom sont obligatoires.';
      return;
    }
    this.submitting = true;
    this.submitError = '';
    this.svc.create(this.form).subscribe({
      next: (created) => {
        this.submitting = false;
        this.immeubles = [created, ...this.immeubles];
        this.showModal = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.submitError = err.error?.message ?? `Failed to create (${err.status})`;
      },
    });
  }
}
