import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../../core/auth/auth.service';
import { UserPickerComponent } from '../../../../shared/pickers/user-picker.component';
import { VisiteStateService, AgendaView } from '../../services/visite-state.service';
import { Visite, StatutVisite } from '../../services/visite-api.service';
import {
  casablancaDateParts, formatHeure, formatDateLong,
} from '../../services/casablanca-time';

interface DayCell {
  iso: string;          // YYYY-MM-DD (Casablanca)
  label: string;        // day-of-month
  weekday: string;      // short weekday
  inMonth: boolean;     // for month grid dimming
  isToday: boolean;
  visites: Visite[];
}

/**
 * Agenda — day/week/month calendar of visites (RG-V04, RG-V10). State lives in
 * {@link VisiteStateService} (provided here so it resets on navigation). AGENT sees only
 * their own visits (server-enforced); MANAGER/ADMIN get an agent filter. Clicking an empty
 * day opens the quick-create form pre-filled to that date.
 */
@Component({
  selector: 'app-visites-agenda',
  standalone: true,
  imports: [RouterLink, FormsModule, TranslatePipe, UserPickerComponent],
  providers: [VisiteStateService],
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.css',
})
export class AgendaComponent implements OnInit {
  readonly state = inject(VisiteStateService);
  private auth = inject(AuthService);
  private router = inject(Router);

  readonly views: AgendaView[] = ['JOUR', 'SEMAINE', 'MOIS'];
  readonly statuts: StatutVisite[] = ['PLANIFIEE', 'CONFIRMEE', 'REALISEE', 'ANNULEE', 'NO_SHOW'];

  readonly canFilterAgent = signal(false);
  readonly fmtHeure = formatHeure;

  /** Day cells for the current view, each carrying its visits. */
  readonly cells = computed<DayCell[]>(() => {
    const view = this.state.view();
    const ref = this.state.refDate();
    const todayIso = casablancaDateParts(new Date());
    const refMonth = ref.getMonth();

    const days: Date[] = view === 'JOUR' ? [ref]
      : view === 'SEMAINE' ? this.range(startOfWeek(ref), 7)
        : this.range(startOfMonthGrid(ref), 42);

    return days.map(d => {
      const iso = casablancaDateParts(d);
      return {
        iso,
        label: String(d.getDate()),
        weekday: new Intl.DateTimeFormat('fr-FR', { weekday: 'short' }).format(d),
        inMonth: d.getMonth() === refMonth,
        isToday: iso === todayIso,
        visites: this.state.visitesForDay(iso),
      };
    });
  });

  readonly periodLabel = computed(() => {
    const ref = this.state.refDate();
    if (this.state.view() === 'JOUR') return formatDateLong(ref.toISOString());
    if (this.state.view() === 'MOIS') {
      return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(ref);
    }
    const monday = startOfWeek(ref);
    return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' }).format(monday);
  });

  ngOnInit(): void {
    const role = this.auth.user?.role ?? '';
    this.canFilterAgent.set(role.includes('ADMIN') || role.includes('MANAGER'));
    this.state.reload();
  }

  onAgent(id: string | null): void { this.state.setAgent(id); }

  onStatut(value: string): void {
    this.state.setStatut(value ? (value as StatutVisite) : null);
  }

  openVisite(v: Visite): void {
    this.router.navigate(['/app/visites', v.id]);
  }

  /** Click an empty day → quick create, pre-filled to 09:00 that day (Casablanca). */
  createOn(iso: string): void {
    this.router.navigate(['/app/visites/nouvelle'], { queryParams: { date: `${iso}T09:00` } });
  }

  statutClass(v: Visite): string { return `chip-${v.statut.toLowerCase()}`; }

  private range(start: Date, count: number): Date[] {
    return Array.from({ length: count }, (_, i) => {
      const d = new Date(start); d.setDate(d.getDate() + i); return d;
    });
  }
}

function startOfWeek(d: Date): Date {
  const r = new Date(d);
  const day = (r.getDay() + 6) % 7;
  r.setDate(r.getDate() - day);
  return r;
}
function startOfMonthGrid(d: Date): Date {
  return startOfWeek(new Date(d.getFullYear(), d.getMonth(), 1));
}
