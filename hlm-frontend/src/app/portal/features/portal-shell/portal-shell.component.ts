import { Component, inject, OnInit, signal } from '@angular/core';

import { RouterModule } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { PortalAuthService } from '../../core/portal-auth.service';
import { PortalTenantInfo } from '../../../core/models/portal.model';
import { LanguageSwitcherComponent } from '../../../core/components/language-switcher.component';

@Component({
  selector: 'app-portal-shell',
  standalone: true,
  imports: [RouterModule, TranslatePipe, LanguageSwitcherComponent],
  templateUrl: './portal-shell.component.html',
  styleUrl: './portal-shell.component.css',
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
