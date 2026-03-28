import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { TaskService } from './task.service';
import { Task, TaskStatus } from './task.model';
import { TaskFormComponent } from './task-form.component';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-contact-tasks',
  standalone: true,
  imports: [CommonModule, TaskFormComponent, TranslateModule],
  template: `
    <div class="task-widget">
      <div class="widget-header">
        <h4>{{ 'tasks.relatedTasks' | translate }}</h4>
        <button (click)="showForm = true" class="btn-small-primary">+ {{ 'tasks.create' | translate }}</button>
      </div>
      @if (error) { <p class="error">{{ error }}</p> }
      @if (loading) { <p class="loading-text">{{ 'common.loading' | translate }}</p> }
      @if (!loading && tasks.length === 0) { <p class="empty">{{ 'tasks.noTasks' | translate }}</p> }
      @if (!loading && tasks.length > 0) {
        <ul class="task-list">
          @for (t of tasks; track t.id) {
            <li class="task-item">
              <span [class]="'badge ' + badgeClass(t.status)">{{ statusLabel(t.status) }}</span>
              <span class="task-title">{{ t.title }}</span>
              @if (t.dueDate) {
                <span class="task-due">{{ t.dueDate | date:'dd/MM/yyyy' }}</span>
              }
            </li>
          }
        </ul>
      }
      @if (showForm) {
        <app-task-form
          [prefillContactId]="contactId"
          (saved)="onSaved($event)"
          (cancelled)="showForm = false"
        />
      }
    </div>
  `,
  styles: [`
    .task-widget { margin-top: 0.5rem; }
    .widget-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
    .widget-header h4 { margin: 0; font-size: 0.875rem; font-weight: 600; color: #374151; }
    .btn-small-primary { background: #6366f1; color: #fff; border: none; padding: 2px 10px; border-radius: 4px; cursor: pointer; font-size: 0.75rem; }
    .task-list { list-style: none; padding: 0; margin: 0; }
    .task-item { display: flex; align-items: center; gap: 0.5rem; padding: 0.35rem 0; border-bottom: 1px solid #f0f0f0; font-size: 0.8rem; }
    .task-title { flex: 1; }
    .task-due { color: #9ca3af; font-size: 0.72rem; }
    .badge { display: inline-block; padding: 1px 6px; border-radius: 10px; font-size: 0.7rem; font-weight: 600; }
    .badge-blue { background: #dbeafe; color: #1d4ed8; }
    .badge-amber { background: #fef3c7; color: #92400e; }
    .badge-green { background: #dcfce7; color: #166534; }
    .badge-gray { background: #f3f4f6; color: #6b7280; }
    .error { color: #991b1b; font-size: 0.8rem; }
    .loading-text, .empty { color: #9ca3af; font-size: 0.8rem; font-style: italic; }
  `],
})
export class ContactTasksComponent implements OnInit {
  @Input({ required: true }) contactId!: string;

  private svc = inject(TaskService);
  tasks: Task[] = [];
  loading = false;
  error = '';
  showForm = false;

  ngOnInit(): void {
    this.loading = true;
    this.svc.byContact(this.contactId).subscribe({
      next: (list) => { this.tasks = list; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        this.error = body?.message ?? `Erreur (${err.status})`;
      },
    });
  }

  onSaved(task: Task): void {
    this.tasks = [...this.tasks, task];
    this.showForm = false;
  }

  badgeClass(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      OPEN: 'badge-blue', IN_PROGRESS: 'badge-amber', DONE: 'badge-green', CANCELED: 'badge-gray',
    };
    return map[status] ?? '';
  }

  statusLabel(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      OPEN: 'Ouvert', IN_PROGRESS: 'En cours', DONE: 'Terminé', CANCELED: 'Annulé',
    };
    return map[status] ?? status;
  }
}
