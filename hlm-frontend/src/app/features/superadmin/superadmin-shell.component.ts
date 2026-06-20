import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-superadmin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './superadmin-shell.component.html',
  styleUrl: './superadmin-shell.component.css',
})
export class SuperadminShellComponent {
  private i18n = inject(I18nService);
  private auth = inject(AuthService);

  logout(): void {
    this.auth.logout();
  }
}
