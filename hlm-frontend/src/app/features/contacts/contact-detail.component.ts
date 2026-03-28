import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ContactService } from './contact.service';
import { Contact, TimelineEvent } from '../../core/models/contact.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { DocumentListComponent } from '../documents/document-list.component';
import { ContactTasksComponent } from '../tasks/contact-tasks.component';

@Component({
  selector: 'app-contact-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, DocumentListComponent, ContactTasksComponent, TranslateModule],
  templateUrl: './contact-detail.component.html',
  styleUrl: './contact-detail.component.css',
})
export class ContactDetailComponent implements OnInit {
  private svc = inject(ContactService);
  private route = inject(ActivatedRoute);

  contact: Contact | null = null;
  loading = true;
  error = '';

  activeTab: 'details' | 'timeline' | 'documents' | 'tasks' = 'details';
  timeline: TimelineEvent[] = [];
  timelineLoading = false;
  timelineError = '';
  timelineLoaded = false;

  private contactId = '';

  ngOnInit(): void {
    this.contactId = this.route.snapshot.paramMap.get('id')!;
    this.svc.getById(this.contactId).subscribe({
      next: (data) => {
        this.contact = data;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 404) {
          this.error = 'Contact not found.';
        } else if (err.status === 401) {
          this.error = 'Session expired. Please log in again.';
        } else if (body?.message) {
          this.error = body.message;
        } else {
          this.error = `Failed to load contact (${err.status})`;
        }
      },
    });
  }

  selectTab(tab: 'details' | 'timeline' | 'documents' | 'tasks'): void {
    this.activeTab = tab;
    if (tab === 'timeline' && !this.timelineLoaded) {
      this.loadTimeline();
    }
  }

  categoryLabel(category: string): string {
    return category === 'AUDIT' ? 'Audit'
      : category === 'MESSAGE' ? 'Message'
      : category === 'NOTIFICATION' ? 'Notification'
      : 'Événement';
  }

  private loadTimeline(): void {
    this.timelineLoading = true;
    this.timelineError = '';
    this.svc.getTimeline(this.contactId).subscribe({
      next: (events) => {
        this.timeline = events;
        this.timelineLoading = false;
        this.timelineLoaded = true;
      },
      error: (err: HttpErrorResponse) => {
        this.timelineLoading = false;
        this.timelineError = `Failed to load timeline (${err.status})`;
      },
    });
  }
}
