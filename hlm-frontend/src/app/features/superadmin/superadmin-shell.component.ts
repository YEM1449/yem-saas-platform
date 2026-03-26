import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TranslateModule } from '@ngx-translate/core';
import { LanguageSwitcherComponent } from '../../core/components/language-switcher.component';

@Component({
  selector: 'app-superadmin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslateModule, LanguageSwitcherComponent],
  templateUrl: './superadmin-shell.component.html',
  styleUrl: './superadmin-shell.component.css',
})
export class SuperadminShellComponent {
  private auth = inject(AuthService);

  logout(): void {
    this.auth.logout();
  }
}
