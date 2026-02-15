import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationService } from './notification.service';
import { Notification } from '../../core/models/notification.model';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css',
})
export class NotificationsComponent implements OnInit {
  private svc = inject(NotificationService);

  notifications: Notification[] = [];
  loading = true;
  error = '';
  filterUnread = false;

  ngOnInit(): void {
    this.load();
  }

  toggleFilter(): void {
    this.filterUnread = !this.filterUnread;
    this.load();
  }

  markAsRead(n: Notification): void {
    if (n.read) return;
    this.svc.markRead(n.id).subscribe({
      next: (updated) => {
        if (this.filterUnread) {
          // Remove from list — it's now read and filter shows unread only
          this.notifications = this.notifications.filter((x) => x.id !== updated.id);
        } else {
          const idx = this.notifications.findIndex((x) => x.id === updated.id);
          if (idx >= 0) this.notifications[idx] = updated;
        }
      },
      error: (err: HttpErrorResponse) => {
        this.error = `Failed to mark notification as read (${err.status})`;
      },
    });
  }

  formatType(type: string): string {
    return type.replace(/_/g, ' ');
  }

  private load(): void {
    this.loading = true;
    this.error = '';
    const readFilter = this.filterUnread ? false : undefined;
    this.svc.list(readFilter).subscribe({
      next: (list) => {
        this.notifications = list;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err.status === 403) {
          this.error = 'Access denied.';
        } else {
          this.error = `Failed to load notifications (${err.status})`;
        }
      },
    });
  }
}
