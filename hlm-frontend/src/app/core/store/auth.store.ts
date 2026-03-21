import { Injectable, signal, computed } from '@angular/core';

interface AuthState {
  token: string | null;
  societeId: string | null;
  role: string | null;
  isAuthenticated: boolean;
}

/**
 * Signal-based auth state store.
 * Provides reactive, type-safe access to current authentication state.
 * Complements AuthService which manages localStorage and HTTP calls.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _state = signal<AuthState>({
    token: null,
    societeId: null,
    role: null,
    isAuthenticated: false,
  });

  readonly token = computed(() => this._state().token);
  readonly isAuthenticated = computed(() => this._state().isAuthenticated);
  readonly role = computed(() => this._state().role);
  readonly societeId = computed(() => this._state().societeId);

  setAuth(token: string, societeId: string | null, role: string): void {
    this._state.set({ token, societeId, role, isAuthenticated: true });
  }

  clearAuth(): void {
    this._state.set({ token: null, societeId: null, role: null, isAuthenticated: false });
  }
}
