import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ContactService } from './contact.service';
import { Contact } from '../../core/models/contact.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-contacts',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './contacts.component.html',
  styleUrl: './contacts.component.css',
})
export class ContactsComponent implements OnInit {
  private svc = inject(ContactService);

  contacts: Contact[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.svc.list().subscribe({
      next: (page) => {
        this.contacts = page.content;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 401) {
          this.error = 'Session expired. Please log in again.';
        } else if (err.status === 403) {
          this.error = 'Access denied. Your role does not permit viewing contacts.';
        } else if (body?.message) {
          this.error = body.message;
        } else {
          this.error = `Failed to load contacts (${err.status})`;
        }
      },
    });
  }
}
