import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { PortalAuthService } from '../../core/portal-auth.service';

type VerifyState = 'verifying' | 'error';

@Component({
  selector: 'app-portal-verify',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './portal-verify.component.html',
  styleUrl: './portal-verify.component.css',
})
export class PortalVerifyComponent implements OnInit {
  private auth      = inject(PortalAuthService);
  private route     = inject(ActivatedRoute);
  private router    = inject(Router);
  private translate = inject(TranslateService);

  state = signal<VerifyState>('verifying');
  error = signal('');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.state.set('error');
      this.error.set(this.translate.instant('portal.verify.errorNoToken'));
      return;
    }

    this.auth.verifyToken(token).subscribe({
      next: () => {
        this.router.navigateByUrl('/portal');
      },
      error: () => {
        this.state.set('error');
        this.error.set(this.translate.instant('portal.verify.errorExpired'));
      },
    });
  }

  goToLogin(): void {
    this.router.navigateByUrl('/portal/login');
  }
}
