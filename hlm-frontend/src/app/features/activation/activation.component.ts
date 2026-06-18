import { Component, inject, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';

import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { ActivationRequest, InvitationDetails } from '../../core/models/login.model';

type PageState = 'loading' | 'form' | 'invalid' | 'done';

@Component({
  selector: 'app-activation',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './activation.component.html',
  styleUrl: './activation.component.css',
})
export class ActivationComponent implements OnInit {
  private i18n = inject(I18nService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  state: PageState = 'loading';
  token = '';
  invitation: InvitationDetails | null = null;
  error = '';

  form: ActivationRequest = {
    motDePasse: '',
    confirmationMotDePasse: '',
    consentementCgu: false,
    consentementCguVersion: '1.0',
  };
  submitting = false;

  get pwHasLength():  boolean { return this.form.motDePasse.length >= 12; }
  get pwHasUpper():   boolean { return /[A-Z]/.test(this.form.motDePasse); }
  get pwHasLower():   boolean { return /[a-z]/.test(this.form.motDePasse); }
  get pwHasDigit():   boolean { return /[0-9]/.test(this.form.motDePasse); }
  get pwHasSpecial(): boolean { return /[^a-zA-Z0-9]/.test(this.form.motDePasse); }

  get passwordStrength(): 0 | 1 | 2 | 3 | 4 {
    const score = [this.pwHasLength, this.pwHasUpper, this.pwHasLower,
                   this.pwHasDigit, this.pwHasSpecial].filter(Boolean).length;
    return Math.min(4, score) as 0 | 1 | 2 | 3 | 4;
  }

  get passwordStrengthLabel(): string {
    return ['', 'Faible', 'Moyen', 'Fort', 'Très fort'][this.passwordStrength];
  }

  get passwordStrengthClass(): string {
    return ['', 'strength-weak', 'strength-fair', 'strength-good', 'strength-strong'][this.passwordStrength];
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.state = 'invalid';
      this.error = this.i18n.instant('activation.invalidLink');
      return;
    }
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { token: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.auth.validateInvitation(this.token).subscribe({
      next: (details) => {
        this.invitation = details;
        this.state = 'form';
      },
      error: (err: HttpErrorResponse) => {
        this.state = 'invalid';
        this.error = err.status === 404 || err.status === 409
          ? 'Ce lien d\'activation est invalide ou a déjà été utilisé.'
          : err.error?.message ?? this.i18n.instant('activation.expiredLink');
      },
    });
  }

  onSubmit(): void {
    if (this.form.motDePasse !== this.form.confirmationMotDePasse) {
      this.error = this.i18n.instant('activation.pwMismatch');
      return;
    }
    if (!this.form.consentementCgu) {
      this.error = this.i18n.instant('activation.mustAcceptCgu');
      return;
    }
    this.submitting = true;
    this.error = '';
    this.auth.activateAccount(this.token, this.form).subscribe({
      next: () => {
        this.state = 'done';
        setTimeout(() => this.router.navigateByUrl('/login'), 2000);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        this.error = err.error?.message ?? this.i18n.instant('activation.genericError', { status: err.status });
      },
    });
  }
}
