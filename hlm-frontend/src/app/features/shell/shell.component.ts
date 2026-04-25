import { Component, inject, OnInit, OnDestroy, HostListener } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { KeepAliveService } from '../../core/keep-alive.service';
import { SocieteService } from '../superadmin/societes/societe.service';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageSwitcherComponent } from '../../core/components/language-switcher.component';
import { NotificationPollingService } from '../../core/notification-polling.service';
import { NotificationToastComponent } from '../../core/components/notification-toast.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, TranslateModule, LanguageSwitcherComponent, NotificationToastComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent implements OnInit, OnDestroy {
  auth = inject(AuthService);
  private router = inject(Router);
  private keepAlive = inject(KeepAliveService);
  private societeSvc = inject(SocieteService);
  private polling = inject(NotificationPollingService);

  sidebarOpen  = false;
  railExpanded = false;

  ngOnInit(): void {
    this.railExpanded = localStorage.getItem('rail_expanded') === '1';
    this.keepAlive.start();
    this.polling.start();
  }

  ngOnDestroy(): void {
    this.keepAlive.stop();
    this.polling.stop();
  }

  @HostListener('window:resize')
  onResize(): void {
    if (window.innerWidth >= 960) this.sidebarOpen = false;
  }

  toggleSidebar(): void { this.sidebarOpen = !this.sidebarOpen; }
  closeSidebar(): void  { this.sidebarOpen = false; }

  toggleRail(): void {
    this.railExpanded = !this.railExpanded;
    localStorage.setItem('rail_expanded', this.railExpanded ? '1' : '0');
  }

  get isAdmin(): boolean {
    return this.auth.user?.role === 'ROLE_ADMIN';
  }

  get isAdminOrManager(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }

  // ── Impersonation ──────────────────────────────────────────────────
  // isImpersonating is derived from /auth/me (isImpersonating claim in the httpOnly cookie)
  get isImpersonating(): boolean {
    return this.auth.user?.isImpersonating === true;
  }

  get impersonationTarget(): string {
    return this.auth.user?.impersonationTargetEmail ?? '';
  }

  endImpersonation(): void {
    // Ask the backend to re-issue the original SUPER_ADMIN cookie
    this.societeSvc.endImpersonation().subscribe({
      complete: () => {
        // Clear the cached /auth/me so the next guard re-fetches as SUPER_ADMIN
        this.auth.clearCachedUser();
        this.router.navigateByUrl('/superadmin/societes');
      },
      error: () => {
        // Even if the call fails, navigate away (the impersonation cookie will expire)
        this.auth.clearCachedUser();
        this.router.navigateByUrl('/superadmin/societes');
      },
    });
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
