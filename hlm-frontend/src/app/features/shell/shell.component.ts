import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  auth = inject(AuthService);

  get isAdmin(): boolean {
    return this.auth.user?.role === 'ROLE_ADMIN';
  }

  get isAdminOrManager(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  /** Avatar monogram — first character of the userId (or role label) */
  get userInitial(): string {
    const role = this.userRoleLabel;
    return role.charAt(0).toUpperCase();
  }

  /** Short user identifier shown in the sidebar footer */
  get userLabel(): string {
    const id = this.auth.user?.userId ?? '';
    // Show first 8 chars of the UUID for brevity
    return id ? id.substring(0, 8).toUpperCase() : 'User';
  }

  get userRoleLabel(): string {
    return (this.auth.user?.role ?? '').replace('ROLE_', '');
  }

  logout(): void {
    this.auth.logout();
  }
}
