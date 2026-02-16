import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  auth = inject(AuthService);

  get isAdmin(): boolean {
    return this.auth.user?.role === 'ROLE_ADMIN';
  }

  logout(): void {
    this.auth.logout();
  }
}
