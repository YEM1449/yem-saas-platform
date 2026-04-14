import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { PortalAuthService } from '../../core/portal-auth.service';

type VerifyState = 'verifying' | 'error';

@Component({
  selector: 'app-portal-verify',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './portal-verify.component.html',
  styleUrl: './portal-verify.component.css',
})
export class PortalVerifyComponent implements OnInit {
  private auth   = inject(PortalAuthService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  state = signal<VerifyState>('verifying');
  error = signal('');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.state.set('error');
      this.error.set('Lien invalide — aucun jeton trouvé dans l\'URL.');
      return;
    }

    this.auth.verifyToken(token).subscribe({
      next: () => {
        this.router.navigateByUrl('/portal');
      },
      error: () => {
        this.state.set('error');
        this.error.set('Ce lien est invalide ou a expiré. Veuillez en demander un nouveau.');
      },
    });
  }

  goToLogin(): void {
    this.router.navigateByUrl('/portal/login');
  }
}
