import { Injectable, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Subscription, interval } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DueTask {
  id: string;
  title: string;
  dueDate: string;
}

/** Polls GET /api/tasks/due-now every 60 s and exposes the result as an observable. */
@Injectable({ providedIn: 'root' })
export class NotificationPollingService implements OnDestroy {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/tasks`;

  private _tasks = new BehaviorSubject<DueTask[]>([]);
  readonly tasks$ = this._tasks.asObservable();

  private sub: Subscription | null = null;

  /** Call once when the user is authenticated (e.g. in ShellComponent.ngOnInit). */
  start(): void {
    if (this.sub) return;
    this.poll(); // immediate first load
    this.sub = interval(60_000)
      .pipe(switchMap(() => this.fetch()))
      .subscribe(tasks => this._tasks.next(tasks));
  }

  stop(): void {
    this.sub?.unsubscribe();
    this.sub = null;
    this._tasks.next([]);
  }

  dismiss(taskId: string): void {
    this._tasks.next(this._tasks.value.filter(t => t.id !== taskId));
  }

  markDone(taskId: string): void {
    this.http.put(`${this.base}/${taskId}`, { status: 'DONE' })
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.dismiss(taskId));
  }

  private poll(): void {
    this.fetch().subscribe(tasks => this._tasks.next(tasks));
  }

  private fetch() {
    return this.http.get<DueTask[]>(`${this.base}/due-now`)
      .pipe(catchError(() => of([])));
  }

  ngOnDestroy(): void { this.stop(); }
}
