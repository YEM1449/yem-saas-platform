import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-portal-payments',
  standalone: true,
  imports: [RouterModule],
  template: `
    <div class="portal-section">
      <p>L'échéancier de paiement est désormais disponible dans l'onglet
         <a routerLink="/portal/ventes">Mes Ventes</a>.</p>
    </div>
  `,
})
export class PortalPaymentsComponent {}
