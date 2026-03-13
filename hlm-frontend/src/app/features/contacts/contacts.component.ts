import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ContactService } from './contact.service';
import { Contact } from '../../core/models/contact.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { AuthService } from '../../core/auth/auth.service';

interface CreateContactForm {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  notes: string;
}

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './contacts.component.html',
  styleUrl: './contacts.component.css',
})
export class ContactsComponent implements OnInit {
  private svc  = inject(ContactService);
  private auth = inject(AuthService);

  contacts: Contact[] = [];
  loading = true;
  error   = '';

  /** Live search query */
  searchQuery = '';

  /** Modal state */
  showModal   = false;
  submitting  = false;
  submitError = '';

  form: CreateContactForm = {
    firstName: '', lastName: '', email: '', phone: '', notes: '',
  };

  /** Managers and admins can create contacts */
  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  /** Contacts filtered by the live search query */
  get filtered(): Contact[] {
    const q = this.searchQuery.toLowerCase().trim();
    if (!q) return this.contacts;
    return this.contacts.filter(c =>
      c.fullName.toLowerCase().includes(q) ||
      (c.email  ?? '').toLowerCase().includes(q) ||
      (c.phone  ?? '').includes(q)
    );
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.list().subscribe({
      next: (page) => { this.contacts = page.content; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if      (err.status === 401) this.error = 'Session expired. Please log in again.';
        else if (err.status === 403) this.error = 'Access denied.';
        else                          this.error = body?.message ?? `Failed to load contacts (${err.status})`;
      },
    });
  }

  openModal(): void {
    this.form = { firstName: '', lastName: '', email: '', phone: '', notes: '' };
    this.submitError = '';
    this.showModal   = true;
  }

  closeModal(): void {
    if (this.submitting) return;
    this.showModal = false;
  }

  submitCreate(): void {
    if (!this.form.firstName.trim() || !this.form.lastName.trim()) {
      this.submitError = 'First name and last name are required.';
      return;
    }
    this.submitting  = true;
    this.submitError = '';

    this.svc.create({
      contactType: 'PROSPECT',
      firstName:   this.form.firstName.trim(),
      lastName:    this.form.lastName.trim(),
      email:       this.form.email.trim()  || null,
      phone:       this.form.phone.trim()  || null,
      notes:       this.form.notes.trim()  || null,
    }).subscribe({
      next: (created) => {
        this.submitting = false;
        this.contacts   = [created, ...this.contacts];
        this.showModal  = false;
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        const body = err.error as ErrorResponse | null;
        this.submitError = body?.message ?? `Failed to create contact (${err.status})`;
      },
    });
  }

  /** Build badge CSS class from any status/type string value */
  badgeClass(value: string): string {
    return 'badge badge-' + value.toLowerCase().replace(/_/g, '-');
  }
}
