import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { LoginRequest, SocieteChoice } from '../../core/models/login.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageSwitcherComponent } from '../../core/components/language-switcher.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, LanguageSwitcherComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  form: LoginRequest = { email: '', password: '' };
  loading = false;
  error = '';
  showPassword = false;

  // Multi-société selection state
  showSocieteSelection = false;
  societes: SocieteChoice[] = [];
  partialToken = '';

  onSubmit(): void {
    this.loading = true;
    this.error = '';

    this.auth.login(this.form).subscribe({
      next: (res) => {
        this.form.password = '';
        if (res.requiresSocieteSelection && res.societes) {
          this.loading = false;
          this.showSocieteSelection = true;
          this.societes = res.societes;
          this.partialToken = res.accessToken;
        } else {
          this.resolvePostLogin();
        }
      },
      error: (err) => { this.form.password = ''; this.handleError(err); },
    });
  }

  selectSociete(societeId: string): void {
    this.loading = true;
    this.error = '';
    this.auth.switchSociete(this.partialToken, societeId).subscribe({
      next: () => this.resolvePostLogin(),
      error: (err) => this.handleError(err),
    });
  }

  backToLogin(): void {
    this.showSocieteSelection = false;
    this.societes = [];
    this.partialToken = '';
    this.error = '';
  }

  private resolvePostLogin(): void {
    this.auth.me().subscribe({
      next: (user) => {
        if (user.role === 'ROLE_SUPER_ADMIN' || user.platformRole === 'SUPER_ADMIN') {
          this.router.navigateByUrl('/superadmin/societes');
        } else {
          this.router.navigateByUrl('/app/properties');
        }
      },
      error: () => this.router.navigateByUrl('/app/properties'),
    });
  }

  private handleError(err: HttpErrorResponse): void {
    this.loading = false;
    const body = err.error as ErrorResponse | null;
    if (body?.message) {
      this.error = body.message;
      if (body.fieldErrors?.length) {
        this.error += ': ' + body.fieldErrors.map((f) => `${f.field} ${f.message}`).join(', ');
      }
    } else {
      this.error = `Request failed (${err.status})`;
    }
  }
}
