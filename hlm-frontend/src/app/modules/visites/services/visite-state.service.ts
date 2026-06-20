import { Injectable, computed, inject, signal } from '@angular/core';
import { VisiteApiService, Visite, StatutVisite } from './visite-api.service';
import { casablancaDayStart, casablancaDayEnd, casablancaDateParts } from './casablanca-time';

export type AgendaView = 'JOUR' | 'SEMAINE' | 'MOIS';

/**
 * Holds the agenda's reactive state (view, reference day, filters, loaded visits) as signals.
 * The window [from,to] sent to the API is derived from the view + reference day in Casablanca
 * time (RG-V10). One instance per agenda component (not root) so navigating away resets it.
 */
@Injectable()
export class VisiteStateService {
  private api = inject(VisiteApiService);

  readonly view = signal<AgendaView>('SEMAINE');
  readonly refDate = signal<Date>(new Date());
  readonly agentId = signal<string | null>(null);
  readonly statut = signal<StatutVisite | null>(null);

  readonly visites = signal<Visite[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  /** Casablanca window [from,to) covering the current view, as UTC instant ISO strings. */
  readonly window = computed<{ from: string; to: string }>(() => {
    const ref = this.refDate();
    switch (this.view()) {
      case 'JOUR':
        return { from: casablancaDayStart(ref), to: casablancaDayEnd(ref) };
      case 'SEMAINE': {
        const monday = startOfWeek(ref);
        const sunday = addDays(monday, 6);
        return { from: casablancaDayStart(monday), to: casablancaDayEnd(sunday) };
      }
      case 'MOIS': {
        const first = startOfMonthGrid(ref);
        const last = addDays(first, 41); // 6-week grid
        return { from: casablancaDayStart(first), to: casablancaDayEnd(last) };
      }
    }
  });

  setView(v: AgendaView): void { this.view.set(v); this.reload(); }
  setRef(d: Date): void { this.refDate.set(d); this.reload(); }
  setAgent(id: string | null): void { this.agentId.set(id); this.reload(); }
  setStatut(s: StatutVisite | null): void { this.statut.set(s); this.reload(); }

  today(): void { this.setRef(new Date()); }

  prev(): void { this.shift(-1); }
  next(): void { this.shift(1); }

  private shift(dir: number): void {
    const ref = this.refDate();
    switch (this.view()) {
      case 'JOUR': this.setRef(addDays(ref, dir)); break;
      case 'SEMAINE': this.setRef(addDays(ref, 7 * dir)); break;
      case 'MOIS': this.setRef(addMonths(ref, dir)); break;
    }
  }

  reload(): void {
    const w = this.window();
    this.loading.set(true);
    this.error.set('');
    this.api.agenda({ from: w.from, to: w.to, agentId: this.agentId(), statut: this.statut() })
      .subscribe({
        next: (rows) => { this.visites.set(rows); this.loading.set(false); },
        error: () => { this.error.set('load'); this.loading.set(false); },
      });
  }

  /** Visits whose Casablanca calendar day equals `YYYY-MM-DD`. */
  visitesForDay(isoDay: string): Visite[] {
    return this.visites()
      .filter(v => casablancaDateParts(new Date(v.dateHeure)) === isoDay)
      .sort((a, b) => a.dateHeure.localeCompare(b.dateHeure));
  }
}

// ── date arithmetic (calendar, not tz-sensitive: used only to pick reference days) ──
function addDays(d: Date, n: number): Date { const r = new Date(d); r.setDate(r.getDate() + n); return r; }
function addMonths(d: Date, n: number): Date { const r = new Date(d); r.setMonth(r.getMonth() + n); return r; }
function startOfWeek(d: Date): Date {
  const r = new Date(d);
  const day = (r.getDay() + 6) % 7; // Monday = 0
  return addDays(r, -day);
}
function startOfMonthGrid(d: Date): Date {
  const first = new Date(d.getFullYear(), d.getMonth(), 1);
  return startOfWeek(first);
}
