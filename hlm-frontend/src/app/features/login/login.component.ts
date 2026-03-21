import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { LoginRequest } from '../../core/models/login.model';
import { ErrorResponse } from '../../core/models/error-response.model';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  form: LoginRequest = { email: '', password: '' };
  loading = false;
  error = '';

  onSubmit(): void {
    this.loading = true;
    this.error = '';

    this.auth.login(this.form).subscribe({
      next: () => this.router.navigateByUrl('/app/properties'),
      error: (err) => this.handleError(err),
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
