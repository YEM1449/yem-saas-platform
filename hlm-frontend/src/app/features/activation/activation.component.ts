import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { ActivationRequest, InvitationDetails } from '../../core/models/login.model';

type PageState = 'loading' | 'form' | 'invalid' | 'done';

@Component({
  selector: 'app-activation',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './activation.component.html',
  styleUrl: './activation.component.css',
})
export class ActivationComponent implements OnInit {
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

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.state = 'invalid';
      this.error = 'Lien d\'activation invalide.';
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
          : err.error?.message ?? 'Ce lien d\'activation a expiré ou est invalide.';
      },
    });
  }

  onSubmit(): void {
    if (this.form.motDePasse !== this.form.confirmationMotDePasse) {
      this.error = 'Les mots de passe ne correspondent pas.';
      return;
    }
    if (!this.form.consentementCgu) {
      this.error = 'Vous devez accepter les conditions générales d\'utilisation.';
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
        this.error = err.error?.message ?? `Erreur (${err.status})`;
      },
    });
  }
}
