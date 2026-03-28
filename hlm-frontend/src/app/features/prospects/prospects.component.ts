import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ProspectService } from './prospect.service';
import { Prospect } from '../../core/models/prospect.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-prospects',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  templateUrl: './prospects.component.html',
  styleUrl: './prospects.component.css',
})
export class ProspectsComponent implements OnInit {
  private svc = inject(ProspectService);

  prospects: Prospect[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.svc.list().subscribe({
      next: (page) => {
        this.prospects = page.content;
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
          this.error = `Failed to load prospects (${err.status})`;
        }
      },
    });
  }
}
