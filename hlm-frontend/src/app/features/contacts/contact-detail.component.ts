import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ContactService } from './contact.service';
import { Contact } from '../../core/models/contact.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-contact-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './contact-detail.component.html',
  styleUrl: './contact-detail.component.css',
})
export class ContactDetailComponent implements OnInit {
  private svc = inject(ContactService);
  private route = inject(ActivatedRoute);

  contact: Contact | null = null;
  loading = true;
  error = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getById(id).subscribe({
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
}
