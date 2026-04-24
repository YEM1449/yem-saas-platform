import { Injectable, computed, signal } from '@angular/core';

export interface HlmDialogRef {
  close: () => void;
}

/**
 * Lightweight dialog manager.
 * On mobile (<600px) the global CSS automatically turns .modal-box into a
 * bottom sheet — no extra config needed here.
 *
 * Usage:
 *   // open
 *   const ref = this.dialog.open('my-dialog-id');
 *   // close from inside the dialog
 *   ref.close();
 *   // or check in template: *ngIf="dialog.isOpen('my-dialog-id')"
 */
@Injectable({ providedIn: 'root' })
export class HlmDialogService {
  private readonly _stack = signal<string[]>([]);

  /** True if at least one dialog is open. */
  readonly hasOpenDialog = computed(() => this._stack().length > 0);

  open(id: string): HlmDialogRef {
    this._stack.update(s => [...s, id]);
    return { close: () => this.close(id) };
  }

  close(id: string): void {
    this._stack.update(s => s.filter(x => x !== id));
  }

  isOpen(id: string): boolean {
    return this._stack().includes(id);
  }

  /** Whether the viewport is currently in mobile (bottom-sheet) mode. */
  get isMobileSheet(): boolean {
    return typeof window !== 'undefined' && window.innerWidth < 600;
  }
}
