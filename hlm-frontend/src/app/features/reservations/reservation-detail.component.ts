import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ReservationService, ReservationDetail } from './reservation.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-reservation-detail',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './reservation-detail.component.html',
  styleUrl: './reservation-detail.component.css',
})
export class ReservationDetailComponent implements OnInit {
  private svc    = inject(ReservationService);
  private auth   = inject(AuthService);
  private route  = inject(ActivatedRoute);
  private router = inject(Router);

  detail  = signal<ReservationDetail | null>(null);
  error   = signal('');
  loading = signal(true);

  cancelling = signal(false);
  cancelError = signal('');

  get canWrite(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER' || r === 'ROLE_AGENT';
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getDetail(id).subscribe({
      next: (d) => { this.detail.set(d); this.loading.set(false); },
      error: ()  => { this.error.set('Réservation introuvable.'); this.loading.set(false); },
    });
  }

  badgeClass(status: string): string {
    switch (status) {
      case 'ACTIVE':               return 'badge badge-success';
      case 'EXPIRED':              return 'badge badge-warning';
      case 'CANCELLED':            return 'badge badge-danger';
      case 'CONVERTED_TO_DEPOSIT': return 'badge badge-info';
      default:                     return 'badge';
    }
  }

  cancelReservation(): void {
    const d = this.detail();
    if (!d || !confirm('Annuler cette réservation ?')) return;
    this.cancelling.set(true);
    this.svc.cancel(d.id).subscribe({
      next: () => this.router.navigate(['/app/reservations']),
      error: () => {
        this.cancelError.set('Échec de l\'annulation. Veuillez réessayer.');
        this.cancelling.set(false);
      },
    });
  }

  goConvertToVente(): void {
    const d = this.detail();
    if (d) this.router.navigate(['/app/ventes/new'], { queryParams: { reservationId: d.id } });
  }
}
