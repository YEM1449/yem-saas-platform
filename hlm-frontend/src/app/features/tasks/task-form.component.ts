import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { TaskService } from './task.service';
import { Task, CreateTaskRequest, UpdateTaskRequest } from './task.model';
import { UserPickerComponent } from '../../shared/pickers/user-picker.component';
import { ContactPickerComponent } from '../../shared/pickers/contact-picker.component';
import { ErrorResponse } from '../../core/models/error-response.model';
import { environment } from '../../../environments/environment';

interface PropertySuggestion { id: string; title: string; referenceCode: string; }

@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, UserPickerComponent, ContactPickerComponent],
  templateUrl: './task-form.component.html',
  styleUrl: './task-form.component.css',
})
export class TaskFormComponent implements OnInit {
  @Input() task?: Task;
  @Input() prefillContactId?: string;
  @Input() prefillPropertyId?: string;
  @Output() saved = new EventEmitter<Task>();
  @Output() cancelled = new EventEmitter<void>();

  private svc  = inject(TaskService);
  private http = inject(HttpClient);

  title = '';
  description = '';
  dueDate = '';
  assigneeId = '';
  contactId = '';
  propertyId = '';

  // ── Property typeahead (replaces full-list dropdown for performance) ───────
  propertyQuery        = '';
  propertySuggestions: PropertySuggestion[] = [];
  propertyLoading      = false;
  propertyShowDrop     = false;
  selectedPropertyName = '';
  private propSearch$  = new Subject<string>();

  submitting = false;
  error = '';

  get isEdit(): boolean { return !!this.task; }

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
      if (this.propertyId) this.resolvePropertyName(this.propertyId);
    }

    if (!this.isEdit) {
      // Debounced typeahead — only fires when user types ≥ 2 characters
      this.propSearch$.pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(q => {
          if (!q || q.trim().length < 2) { this.propertySuggestions = []; return of([]); }
          this.propertyLoading = true;
          return this.http.get<PropertySuggestion[]>(
            `${environment.apiUrl}/api/properties`,
            { params: { status: 'ACTIVE' } }
          );
        }),
      ).subscribe({
        next: ps => {
          const q = this.propertyQuery.toLowerCase();
          this.propertySuggestions = (ps as PropertySuggestion[])
            .filter(p => p.title.toLowerCase().includes(q) || p.referenceCode.toLowerCase().includes(q))
            .slice(0, 15);
          this.propertyLoading  = false;
          this.propertyShowDrop = this.propertySuggestions.length > 0;
        },
        error: () => { this.propertyLoading = false; },
      });
    }
  }

  onPropertyInput(): void {
    if (!this.propertyQuery.trim()) { this.clearProperty(); return; }
    this.propSearch$.next(this.propertyQuery);
  }

  selectProperty(p: PropertySuggestion): void {
    this.propertyId          = p.id;
    this.selectedPropertyName = `${p.title} · ${p.referenceCode}`;
    this.propertyQuery        = this.selectedPropertyName;
    this.propertyShowDrop     = false;
    this.propertySuggestions  = [];
  }

  clearProperty(): void {
    this.propertyId           = '';
    this.selectedPropertyName = '';
    this.propertyQuery        = '';
    this.propertySuggestions  = [];
    this.propertyShowDrop     = false;
  }

  closePropertyDrop(): void {
    setTimeout(() => { this.propertyShowDrop = false; }, 150);
  }

  private resolvePropertyName(id: string): void {
    this.http.get<PropertySuggestion>(`${environment.apiUrl}/api/properties/${id}`)
      .subscribe({
        next: p => {
          this.selectedPropertyName = `${p.title} · ${p.referenceCode}`;
          this.propertyQuery        = this.selectedPropertyName;
        },
        error: () => {},
      });
  }

  onUserSelected(id: string | null): void {
    this.assigneeId = id ?? '';
  }

  onContactSelected(id: string | null): void {
    this.contactId = id ?? '';
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
        assigneeId:  this.assigneeId || undefined,
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
        assigneeId:  this.assigneeId || undefined,
        contactId:   this.contactId  || undefined,
        propertyId:  this.propertyId || undefined,
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
