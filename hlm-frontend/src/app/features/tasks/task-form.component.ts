import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TaskService } from './task.service';
import { Task, CreateTaskRequest, UpdateTaskRequest } from './task.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './task-form.component.html',
  styleUrl: './task-form.component.css',
})
export class TaskFormComponent implements OnInit {
  @Input() task?: Task;
  @Input() prefillContactId?: string;
  @Input() prefillPropertyId?: string;
  @Output() saved = new EventEmitter<Task>();
  @Output() cancelled = new EventEmitter<void>();

  private svc = inject(TaskService);

  title = '';
  description = '';
  dueDate = '';
  assigneeId = '';
  contactId = '';
  propertyId = '';
  submitting = false;
  error = '';

  get isEdit(): boolean {
    return !!this.task;
  }

  ngOnInit(): void {
    if (this.task) {
      this.title       = this.task.title;
      this.description = this.task.description ?? '';
      this.dueDate     = this.task.dueDate ? this.task.dueDate.substring(0, 16) : '';
      this.assigneeId  = this.task.assigneeId ?? '';
      this.contactId   = this.task.contactId ?? '';
      this.propertyId  = this.task.propertyId ?? '';
    } else {
      this.contactId  = this.prefillContactId  ?? '';
      this.propertyId = this.prefillPropertyId ?? '';
    }
  }

  submit(): void {
    if (!this.title.trim()) {
      this.error = 'Le titre est obligatoire.';
      return;
    }
    this.submitting = true;
    this.error = '';

    if (this.isEdit) {
      const req: UpdateTaskRequest = {
        title:       this.title.trim(),
        description: this.description.trim() || undefined,
        dueDate:     this.dueDate || undefined,
        assigneeId:  this.assigneeId.trim() || undefined,
      };
      this.svc.update(this.task!.id, req).subscribe({
        next: (t) => { this.submitting = false; this.saved.emit(t); },
        error: (err: HttpErrorResponse) => { this.submitting = false; this.error = this.extractError(err); },
      });
    } else {
      const req: CreateTaskRequest = {
        title:       this.title.trim(),
        description: this.description.trim() || undefined,
        dueDate:     this.dueDate || undefined,
        assigneeId:  this.assigneeId.trim() || undefined,
        contactId:   this.contactId.trim()  || undefined,
        propertyId:  this.propertyId.trim() || undefined,
      };
      this.svc.create(req).subscribe({
        next: (t) => { this.submitting = false; this.saved.emit(t); },
        error: (err: HttpErrorResponse) => { this.submitting = false; this.error = this.extractError(err); },
      });
    }
  }

  cancel(): void {
    this.cancelled.emit();
  }

  private extractError(err: HttpErrorResponse): string {
    const body = err.error as ErrorResponse | null;
    return body?.message ?? `Erreur (${err.status})`;
  }
}
