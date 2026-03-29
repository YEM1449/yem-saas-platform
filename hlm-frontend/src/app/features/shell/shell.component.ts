import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { KeepAliveService } from '../../core/keep-alive.service';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageSwitcherComponent } from '../../core/components/language-switcher.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, TranslateModule, LanguageSwitcherComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  private router = inject(Router);
  private keepAlive = inject(KeepAliveService);

  ngOnInit(): void { this.keepAlive.start(); }
  ngOnDestroy(): void { this.keepAlive.stop(); }

  get isAdmin(): boolean {
    return this.auth.user?.role === 'ROLE_ADMIN';
  }

  get isAdminOrManager(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  // ── Impersonation ──────────────────────────────────────────────────
  get isImpersonating(): boolean {
    return localStorage.getItem('hlm_impersonation_active') === 'true';
  }

  get impersonationTarget(): string {
    return localStorage.getItem('hlm_impersonation_target') ?? '';
  }

  endImpersonation(): void {
    const original = localStorage.getItem('hlm_superadmin_original_token');
    if (original) {
      localStorage.setItem('hlm_access_token', original);
    }
    localStorage.removeItem('hlm_impersonation_active');
    localStorage.removeItem('hlm_impersonation_target');
    localStorage.removeItem('hlm_impersonation_societe');
    localStorage.removeItem('hlm_superadmin_original_token');
    this.router.navigateByUrl('/superadmin/societes');
  }

  /** Avatar monogram — first character of the userId (or role label) */
  get userInitial(): string {
    const role = this.userRoleLabel;
    return role.charAt(0).toUpperCase();
  }

  /** Short user identifier shown in the sidebar footer */
  get userLabel(): string {
    const id = this.auth.user?.userId ?? '';
    return id ? id.substring(0, 8).toUpperCase() : 'User';
  }

  get userRoleLabel(): string {
    return (this.auth.user?.role ?? '').replace('ROLE_', '');
  }

  get societeLogoUrl(): string | null {
    return this.auth.user?.societeLogoUrl ?? null;
  }

  logout(): void {
    this.auth.logout();
  }
}
