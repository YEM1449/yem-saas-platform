import {
  Component, Input, Output, EventEmitter, OnInit, OnDestroy, inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject, debounceTime, distinctUntilChanged, takeUntil, switchMap, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ContactSuggestion {
  id: string;
  displayName: string;
  phone: string | null;
  email: string | null;
}

/**
 * Inline contact typeahead picker.
 * Searches contacts by name, phone or email. Emits UUID via `contactSelected`.
 *
 * Usage:
 *   <app-contact-picker
 *     [placeholder]="'Lier un contact…'"
 *     [initialId]="existingContactId"
 *     (contactSelected)="contactId = $event" />
 */
@Component({
  selector: 'app-contact-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contact-picker.component.html',
  styleUrl: './contact-picker.component.css',
})
export class ContactPickerComponent implements OnInit, OnDestroy {
  @Input() placeholder = 'Rechercher un contact…';
  @Input() initialId?: string;
  @Input() disabled = false;
  @Output() contactSelected = new EventEmitter<string | null>();

  private http = inject(HttpClient);
  private destroy$ = new Subject<void>();
  private search$ = new Subject<string>();

  query = '';
  suggestions: ContactSuggestion[] = [];
  showDropdown = false;
  loading = false;
  selectedId: string | null = null;

  ngOnInit(): void {
    if (this.initialId) {
      this.selectedId = this.initialId;
      this.resolveInitial(this.initialId);
    }

    this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        if (!q || q.trim().length < 1) {
          this.suggestions = [];
          return of({ content: [] as Array<{ id: string; fullName: string; phone: string|null; email: string|null }> });
        }
        this.loading = true;
        return this.http.get<{ content: Array<{ id: string; fullName: string; phone: string|null; email: string|null }> }>(
          `${environment.apiUrl}/api/contacts`,
          { params: { q: q.trim(), size: '10' } }
        );
      }),
      takeUntil(this.destroy$),
    ).subscribe({
      next: page => {
        this.suggestions = (page.content ?? []).map(c => ({
          id: c.id,
          displayName: c.fullName,
          phone: c.phone,
          email: c.email,
        }));
        this.loading = false;
        this.showDropdown = this.suggestions.length > 0;
      },
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

  select(c: ContactSuggestion): void {
    this.selectedId  = c.id;
    this.query       = c.displayName;
    this.showDropdown = false;
    this.suggestions  = [];
    this.contactSelected.emit(c.id);
  }

  clear(): void {
    this.selectedId  = null;
    this.query       = '';
    this.suggestions = [];
    this.showDropdown = false;
    this.contactSelected.emit(null);
  }

  closeDropdown(): void {
    setTimeout(() => { this.showDropdown = false; }, 150);
  }

  hint(c: ContactSuggestion): string {
    const parts: string[] = [];
    if (c.phone) parts.push(c.phone);
    if (c.email) parts.push(c.email);
    return parts.join(' · ');
  }

  private resolveInitial(id: string): void {
    this.http.get<{ firstName: string; lastName: string; fullName: string }>(
      `${environment.apiUrl}/api/contacts/${id}`
    ).subscribe({
      next: c => { this.query = c.fullName; },
      error: () => {},
    });
  }
}
