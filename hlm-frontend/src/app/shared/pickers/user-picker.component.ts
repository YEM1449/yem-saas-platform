import {
  Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject, debounceTime, distinctUntilChanged, takeUntil, switchMap, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UserSuggestion {
  id: string;
  displayName: string;
  email: string;
}

/**
 * Inline user typeahead picker.
 * Emits the selected user's UUID via `userSelected` — never shows UUIDs in the UI.
 *
 * Usage:
 *   <app-user-picker
 *     [placeholder]="'Assigner à…'"
 *     [initialId]="existingAssigneeId"
 *     (userSelected)="assigneeId = $event" />
 */
@Component({
  selector: 'app-user-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-picker.component.html',
  styleUrl: './user-picker.component.css',
})
export class UserPickerComponent implements OnInit, OnDestroy {
  @Input() placeholder = 'Rechercher un collaborateur…';
  /** Pre-fill from a known UUID (e.g. editing an existing task). */
  @Input() initialId?: string;
  @Input() disabled = false;
  /** Emits the selected user's UUID, or null when cleared. */
  @Output() userSelected = new EventEmitter<string | null>();

  private http = inject(HttpClient);
  private destroy$ = new Subject<void>();
  private search$ = new Subject<string>();

  query = '';
  suggestions: UserSuggestion[] = [];
  showDropdown = false;
  loading = false;
  selectedId: string | null = null;
  selectedName = '';

  ngOnInit(): void {
    // Resolve initial UUID to display name
    if (this.initialId) {
      this.selectedId = this.initialId;
      this.resolveInitial(this.initialId);
    }

    this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.trim().length < 1) { this.suggestions = []; return of([]); }
        this.loading = true;
        return this.http.get<UserSuggestion[]>(
          `${environment.apiUrl}/api/users/suggest`,
          { params: { q: q.trim() } }
        );
      }),
      takeUntil(this.destroy$),
    ).subscribe({
      next: results => { this.suggestions = results; this.loading = false; this.showDropdown = results.length > 0; },
      error: () => { this.loading = false; },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onInput(): void {
    if (!this.query.trim()) { this.clear(); return; }
    this.search$.next(this.query);
    this.showDropdown = true;
  }

  select(u: UserSuggestion): void {
    this.selectedId   = u.id;
    this.selectedName = u.displayName;
    this.query        = u.displayName;
    this.showDropdown = false;
    this.suggestions  = [];
    this.userSelected.emit(u.id);
  }

  clear(): void {
    this.selectedId   = null;
    this.selectedName = '';
    this.query        = '';
    this.suggestions  = [];
    this.showDropdown = false;
    this.userSelected.emit(null);
  }

  closeDropdown(): void {
    // Delay so click on option registers first
    setTimeout(() => { this.showDropdown = false; }, 150);
  }

  private resolveInitial(id: string): void {
    // Ask the suggest endpoint with no query and find by id in result,
    // OR show a short placeholder until the user next interacts.
    // Fetching all is fine since the list is société-scoped and typically small.
    this.http.get<UserSuggestion[]>(`${environment.apiUrl}/api/users/suggest`)
      .subscribe({
        next: all => {
          const match = all.find(u => u.id === id);
          if (match) { this.selectedName = match.displayName; this.query = match.displayName; }
        },
        error: () => {},
      });
  }
}
