import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ProjectService } from './project.service';
import { Project } from '../../core/models/project.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './projects.component.html',
  styleUrl: './projects.component.css',
})
export class ProjectsComponent implements OnInit {
  private svc = inject(ProjectService);

  projects: Project[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.svc.list().subscribe({
      next: (data) => {
        this.projects = data;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 401) {
          this.error = 'Session expired. Please log in again.';
        } else if (err.status === 403) {
          this.error = 'Access denied.';
        } else if (body?.message) {
          this.error = body.message;
        } else {
          this.error = `Failed to load projects (${err.status})`;
        }
      },
    });
  }
}
