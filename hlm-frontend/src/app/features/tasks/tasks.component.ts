import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { TaskService } from './task.service';
import { Task, TaskStatus } from './task.model';
import { TaskFormComponent } from './task-form.component';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule, TaskFormComponent, TranslateModule],
  templateUrl: './tasks.component.html',
  styleUrl: './tasks.component.css',
})
export class TasksComponent implements OnInit {
  private svc = inject(TaskService);

  tasks: Task[] = [];
  loading = true;
  error = '';
  success = '';

  private loadSeq = 0;

  // Filters
  filterStatus: TaskStatus | '' = '';

  // Pagination
  page = 0;
  size = 20;
  totalPages = 0;
  totalElements = 0;

  // Form
  showForm = false;
  editingTask?: Task;

  readonly statuses: { value: TaskStatus | ''; label: string }[] = [
    { value: '', label: 'Tous' },
    { value: 'OPEN', label: 'Ouvert' },
    { value: 'IN_PROGRESS', label: 'En cours' },
    { value: 'DONE', label: 'Terminé' },
    { value: 'CANCELED', label: 'Annulé' },
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const seq = ++this.loadSeq;
    this.svc.list({
      status: this.filterStatus || undefined,
      page: this.page,
      size: this.size,
    }).subscribe({
      next: (page) => {
        if (seq !== this.loadSeq) return; // Superseded by onSaved or a newer load
        this.tasks = page.content;
        this.totalPages = page.page.totalPages;
        this.totalElements = page.page.totalElements;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        if (seq !== this.loadSeq) return;
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Erreur de chargement (${err.status})`;
      },
    });
  }

  applyFilter(): void {
    this.page = 0;
    this.load();
  }

  prevPage(): void {
    if (this.page > 0) { this.page--; this.load(); }
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) { this.page++; this.load(); }
  }

  openCreate(): void {
    this.editingTask = undefined;
    this.showForm = true;
  }

  openEdit(task: Task): void {
    this.editingTask = task;
    this.showForm = true;
  }

  onSaved(task: Task): void {
    this.loadSeq++; // Invalidate any in-flight load so it won't overwrite this update
    this.loading = false;
    this.showForm = false;
    if (this.editingTask) {
      this.tasks = this.tasks.map(t => t.id === task.id ? task : t);
      this.success = 'Tâche modifiée.';
    } else {
      this.tasks = [task, ...this.tasks];
      this.totalElements++;
      this.success = 'Tâche créée.';
    }
  }

  onCancelled(): void {
    this.showForm = false;
  }

  changeStatus(task: Task, status: TaskStatus): void {
    if (task.status === status) return;
    this.svc.update(task.id, { status }).subscribe({
      next: (updated) => {
        this.tasks = this.tasks.map(t => t.id === updated.id ? updated : t);
      },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  deleteTask(task: Task): void {
    if (!confirm(`Supprimer la tâche "${task.title}" ?`)) return;
    this.svc.delete(task.id).subscribe({
      next: () => { this.tasks = this.tasks.filter(t => t.id !== task.id); this.success = 'Tâche supprimée.'; },
      error: (err: HttpErrorResponse) => {
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  isOverdue(task: Task): boolean {
    if (!task.dueDate || task.status === 'DONE' || task.status === 'CANCELED') return false;
    return new Date(task.dueDate) < new Date();
  }

  statusBadgeClass(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      OPEN:        'badge-blue',
      IN_PROGRESS: 'badge-amber',
      DONE:        'badge-green',
      CANCELED:    'badge-gray',
    };
    return 'badge ' + (map[status] ?? '');
  }

  statusLabel(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      OPEN: 'Ouvert', IN_PROGRESS: 'En cours', DONE: 'Terminé', CANCELED: 'Annulé',
    };
    return map[status] ?? status;
  }
}
