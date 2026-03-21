import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { PortalAuthService } from '../../core/portal-auth.service';

type Step = 'request' | 'sent' | 'verifying' | 'error';

@Component({
  selector: 'app-portal-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './portal-login.component.html',
})
export class PortalLoginComponent {
  private auth   = inject(PortalAuthService);
  private router = inject(Router);
  private route  = inject(ActivatedRoute);

  step    = signal<Step>('request');
  email   = '';
  societeKey = '';
  error   = signal('');
  loading = signal(false);

  constructor() {
    // Handle magic-link redirect: /portal/login?token=xxx
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.step.set('verifying');
      this.verifyToken(token);
    }

    // Pre-fill societeKey from URL param if available
    const tk = this.route.snapshot.queryParamMap.get('societe');
    if (tk) this.societeKey = tk;
  }

  requestLink(): void {
    if (!this.email || !this.societeKey) return;
    this.loading.set(true);
    this.error.set('');
    this.auth.requestLink({ email: this.email, societeKey: this.societeKey }).subscribe({
      next: () => {
        this.loading.set(false);
        this.step.set('sent');
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Unable to send magic link. Please check your email and try again.');
        this.step.set('error');
      },
    });
  }

  private verifyToken(token: string): void {
    this.auth.verifyToken(token).subscribe({
      next: () => {
        this.router.navigateByUrl('/portal/contracts');
      },
      error: () => {
        this.error.set('This link is invalid or has expired. Please request a new one.');
        this.step.set('error');
      },
    });
  }
}
