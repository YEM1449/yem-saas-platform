import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-portal-payments',
  standalone: true,
  imports: [RouterModule, TranslatePipe],
  template: `
    <div class="portal-section">
      <p>{{ 'portal.payments.movedBefore' | translate }}
         <a routerLink="/portal/ventes">{{ 'portal.nav.ventes' | translate }}</a>.</p>
    </div>
  `,
})
export class PortalPaymentsComponent {}
