import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { NotificationPollingService, DueTask } from '../notification-polling.service';

@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [CommonModule, DatePipe],
  template: `
    <div class="toast-stack" aria-live="polite">
      @for (task of tasks.slice(0, 3); track task.id) {
        <div class="toast toast--due">
          <div class="toast-body">
            <span class="toast-icon">⏰</span>
            <div class="toast-content">
              <p class="toast-title">{{ task.title }}</p>
              <p class="toast-date">Échéance : {{ task.dueDate | date:'dd/MM/yyyy HH:mm' }}</p>
            </div>
          </div>
          <div class="toast-actions">
            <button class="btn btn-sm btn-primary toast-btn"
                    (click)="markDone(task)">
              Marquer fait
            </button>
            <button class="btn btn-sm btn-ghost toast-btn"
                    (click)="dismiss(task.id)"
                    aria-label="Fermer">
              ✕
            </button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-stack {
      position: fixed;
      bottom: 20px;
      right: 20px;
      z-index: 9000;
      display: flex;
      flex-direction: column-reverse;
      gap: 10px;
      max-width: 340px;
      pointer-events: none;
    }

    .toast {
      background: #fff;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.14);
      padding: 14px 16px 10px;
      border-left: 4px solid #f59e0b;
      animation: toast-in 0.2s ease;
      pointer-events: all;
    }

    @keyframes toast-in {
      from { opacity: 0; transform: translateY(12px); }
      to   { opacity: 1; transform: translateY(0);    }
    }

    .toast-body {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      margin-bottom: 10px;
    }

    .toast-icon { font-size: 20px; flex-shrink: 0; }

    .toast-content { flex: 1; }

    .toast-title {
      font-size: 14px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 2px;
    }

    .toast-date {
      font-size: 12px;
      color: #94a3b8;
      margin: 0;
    }

    .toast-actions {
      display: flex;
      justify-content: flex-end;
      gap: 6px;
    }

    .toast-btn { font-size: 12px; padding: 4px 10px; }
  `],
})
export class NotificationToastComponent implements OnInit, OnDestroy {
  private polling = inject(NotificationPollingService);
  private sub!: Subscription;

  tasks: DueTask[] = [];

  ngOnInit(): void {
    this.sub = this.polling.tasks$.subscribe(t => (this.tasks = t));
  }

  ngOnDestroy(): void { this.sub.unsubscribe(); }

  markDone(task: DueTask): void { this.polling.markDone(task.id); }
  dismiss(id: string): void     { this.polling.dismiss(id); }
}
