import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { PortalAuthService } from '../../core/portal-auth.service';
import { PortalTenantInfo } from '../../../core/models/portal.model';

@Component({
  selector: 'app-portal-shell',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './portal-shell.component.html',
})
export class PortalShellComponent implements OnInit {
  private auth = inject(PortalAuthService);

  tenantInfo = signal<PortalTenantInfo | null>(null);

  ngOnInit(): void {
    this.auth.getTenantInfo().subscribe({
      next: (info) => this.tenantInfo.set(info),
      error: () => {},
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
