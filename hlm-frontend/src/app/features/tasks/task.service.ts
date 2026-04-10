import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Task, TaskPage, CreateTaskRequest, UpdateTaskRequest, TaskStatus } from './task.model';
import { DueTask } from '../../core/notification-polling.service';

/**
 * HTTP client for the Tasks API (`/api/tasks`).
 *
 * Note: the backend default for `GET /api/tasks` returns only tasks where
 * `assigneeId` matches the current user. Unassigned tasks are excluded unless
 * an explicit `assigneeId` filter is provided.
 */
@Injectable({ providedIn: 'root' })
export class TaskService {
  private http = inject(HttpClient);
  private base = `${environment.apiUrl}/api/tasks`;

  /** Lists tasks for the current société, optionally filtered by status or assignee. */
  list(opts?: { status?: TaskStatus; assigneeId?: string; page?: number; size?: number }): Observable<TaskPage> {
    let p = new HttpParams();
    if (opts?.status)     p = p.set('status', opts.status);
    if (opts?.assigneeId) p = p.set('assigneeId', opts.assigneeId);
    if (opts?.page != null) p = p.set('page', String(opts.page));
    if (opts?.size != null) p = p.set('size', String(opts.size));
    return this.http.get<TaskPage>(this.base, { params: p });
  }

  getById(id: string): Observable<Task> {
    return this.http.get<Task>(`${this.base}/${id}`);
  }

  create(req: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(this.base, req);
  }

  update(id: string, req: UpdateTaskRequest): Observable<Task> {
    return this.http.put<Task>(`${this.base}/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  byContact(contactId: string): Observable<Task[]> {
    return this.http.get<Task[]>(`${this.base}/by-contact/${contactId}`);
  }

  byProperty(propertyId: string): Observable<Task[]> {
    return this.http.get<Task[]>(`${this.base}/by-property/${propertyId}`);
  }

  dueNow(): Observable<DueTask[]> {
    return this.http.get<DueTask[]>(`${this.base}/due-now`);
  }
}
