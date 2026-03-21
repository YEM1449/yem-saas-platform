import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from './project.service';
import { Project } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.css',
})
export class ProjectsComponent implements OnInit {
  private svc  = inject(ProjectService);
  private auth = inject(AuthService);

  projects: Project[] = [];
  loading = true;
  error   = '';

  /** Modal state */
  showModal   = false;
  submitting  = false;
  submitError = '';

  form = { name: '', description: '' };

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.list().subscribe({
      next: (data) => { this.projects = data; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if      (err.status === 401) this.error = 'Session expired. Please log in again.';
        else if (err.status === 403) this.error = 'Access denied.';
        else                          this.error = body?.message ?? `Failed to load projects (${err.status})`;
      },
    });
  }

  openModal(): void {
    this.form        = { name: '', description: '' };
    this.submitError = '';
    this.showModal   = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
  }

  submitCreate(): void {
    if (!this.form.name.trim()) {
      this.submitError = 'Project name is required.';
      return;
    }
    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      name:        this.form.name.trim(),
      description: this.form.description.trim() || undefined,
    }).subscribe({
      next: (created) => {
        this.submitting = false;
        this.projects   = [created, ...this.projects];
        this.showModal  = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        const body = err.error as ErrorResponse | null;
        this.submitError = body?.message ?? `Failed to create project (${err.status})`;
      },
    });
  }
}
