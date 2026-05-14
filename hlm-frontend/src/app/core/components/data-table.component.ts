import { Component, Input, Output, EventEmitter, TemplateRef, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ColumnDef<T> {
  field?: keyof T;
  label: string;
  sortable?: boolean;
  template?: TemplateRef<any>;
  class?: string;
}

export interface SortEvent {
  field: string;
  direction: 'asc' | 'desc' | null;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="data-table-container">
      <!-- Loading State -->
      @if (loading) {
        <div class="skeleton-stack" [attr.aria-label]="loadingLabel">
          @for (i of skeletonRows; track i) {
            <span class="skeleton skeleton-row"></span>
          }
        </div>
      }

      <!-- Error State -->
      @if (error && !loading) {
        <div class="alert alert-error">{{ error }}</div>
      }

      <!-- Empty State -->
      @if (!loading && !error && (!data || data.length === 0)) {
        <div class="card">
          <div class="empty-state">
            <div class="empty-state-icon">{{ emptyIcon }}</div>
            <p class="empty-state-title">{{ emptyTitle }}</p>
            <p class="empty-state-description">{{ emptyDescription }}</p>
            @if (emptyActionLabel) {
              <button class="btn btn-primary" (click)="emptyAction.emit()">
                {{ emptyActionLabel }}
              </button>
            }
          </div>
        </div>
      }

      <!-- Data Table -->
      @if (!loading && !error && data && data.length > 0) {
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                @for (col of columns; track col.label) {
                  <th [class]="col.class || ''"
                      [class.sortable]="col.sortable"
                      (click)="col.sortable && toggleSort(col)">
                    {{ col.label }}
                    @if (col.sortable) {
                      <span class="sort-icon">{{ getSortIcon(col) }}</span>
                    }
                  </th>
                }
              </tr>
            </thead>
            <tbody>
              @for (item of paginatedData; track trackByFn($index, item)) {
                <tr>
                  @for (col of columns; track col.label) {
                    <td [class]="col.class || ''">
                      @if (col.template) {
                        <ng-container *ngTemplateOutlet="col.template; context: { $implicit: item, column: col }"></ng-container>
                      } @else {
                        {{ getValue(item, col.field) }}
                      }
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        @if (showPagination && totalPages > 1) {
          <div class="pagination">
            <button class="btn btn-sm btn-secondary"
                    [disabled]="currentPage === 1"
                    (click)="goToPage(currentPage - 1)">
              Précédent
            </button>
            <span class="pagination-info">
              Page {{ currentPage }} sur {{ totalPages }}
            </span>
            <button class="btn btn-sm btn-secondary"
                    [disabled]="currentPage === totalPages"
                    (click)="goToPage(currentPage + 1)">
              Suivant
            </button>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .data-table-container {
      width: 100%;
    }

    .table-container {
      overflow-x: auto;
      border-radius: var(--r-md);
      border: 1px solid var(--c-border);
    }

    .data-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 14px;
    }

    .data-table thead th {
      background: var(--c-bg-secondary);
      padding: var(--sp-3) var(--sp-4);
      text-align: left;
      font-weight: 600;
      color: var(--c-text-secondary);
      border-bottom: 1px solid var(--c-border);
      white-space: nowrap;
    }

    .data-table tbody td {
      padding: var(--sp-3) var(--sp-4);
      border-bottom: 1px solid var(--c-border-light);
      vertical-align: top;
    }

    .data-table tbody tr:hover {
      background: var(--c-bg-hover);
    }

    .data-table th.sortable {
      cursor: pointer;
      user-select: none;
    }

    .data-table th.sortable:hover {
      background: var(--c-bg-hover);
    }

    .sort-icon {
      margin-left: var(--sp-1);
      opacity: 0.5;
    }

    .data-table th.sortable:hover .sort-icon {
      opacity: 1;
    }

    .pagination {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: var(--sp-2);
      margin-top: var(--sp-4);
      padding: var(--sp-2);
    }

    .pagination-info {
      color: var(--c-text-secondary);
      font-size: 14px;
    }

    .skeleton-stack {
      display: flex;
      flex-direction: column;
      gap: var(--sp-2);
    }

    .skeleton-row {
      height: 48px;
      border-radius: var(--r-sm);
    }

    .empty-state {
      text-align: center;
      padding: var(--sp-8) var(--sp-4);
    }

    .empty-state-icon {
      font-size: 48px;
      margin-bottom: var(--sp-4);
    }

    .empty-state-title {
      font-size: 18px;
      font-weight: 600;
      margin-bottom: var(--sp-2);
      color: var(--c-text);
    }

    .empty-state-description {
      color: var(--c-text-secondary);
      margin-bottom: var(--sp-4);
    }
  `]
})
export class DataTableComponent<T> implements OnChanges {
  @Input() data: T[] = [];
  @Input() columns: ColumnDef<T>[] = [];
  @Input() loading = false;
  @Input() error = '';
  @Input() pageSize = 10;
  @Input() showPagination = true;
  @Input() emptyIcon = '📄';
  @Input() emptyTitle = 'Aucune donnée';
  @Input() emptyDescription = 'Aucun élément à afficher.';
  @Input() emptyActionLabel = '';
  @Input() loadingLabel = 'Chargement...';
  @Input() trackByFn: (index: number, item: T) => any = (index, item) => item;

  @Output() sort = new EventEmitter<SortEvent>();
  @Output() pageChange = new EventEmitter<number>();
  @Output() emptyAction = new EventEmitter<void>();

  currentPage = 1;
  sortField: string | null = null;
  sortDirection: 'asc' | 'desc' | null = null;

  get skeletonRows(): number[] {
    return Array(Math.min(this.pageSize, 5)).fill(0).map((_, i) => i);
  }

  get paginatedData(): T[] {
    if (!this.showPagination) return this.sortedData;
    const start = (this.currentPage - 1) * this.pageSize;
    return this.sortedData.slice(start, start + this.pageSize);
  }

  get sortedData(): T[] {
    if (!this.sortField || !this.sortDirection) return this.data;
    return [...this.data].sort((a, b) => {
      const aVal = this.getValue(a, this.sortField as keyof T);
      const bVal = this.getValue(b, this.sortField as keyof T);
      const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
      return this.sortDirection === 'asc' ? comparison : -comparison;
    });
  }

  get totalPages(): number {
    return Math.ceil(this.data.length / this.pageSize);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['data'] && this.currentPage > this.totalPages) {
      this.currentPage = Math.max(1, this.totalPages);
    }
  }

  getValue(item: T, field?: keyof T): any {
    if (!field) return '';
    return item[field];
  }

  toggleSort(col: ColumnDef<T>): void {
    if (!col.field) return;
    const field = col.field as string;
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : this.sortDirection === 'desc' ? null : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }
    this.sort.emit({ field, direction: this.sortDirection });
  }

  getSortIcon(col: ColumnDef<T>): string {
    if (!col.field || this.sortField !== col.field) return '↕️';
    return this.sortDirection === 'asc' ? '↑' : this.sortDirection === 'desc' ? '↓' : '↕️';
  }

  goToPage(page: number): void {
    this.currentPage = Math.max(1, Math.min(page, this.totalPages));
    this.pageChange.emit(this.currentPage);
  }
}