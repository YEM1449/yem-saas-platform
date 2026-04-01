import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PortalSessionStore {
  private authenticated = false;

  isAuthenticated(): boolean {
    return this.authenticated;
  }

  markAuthenticated(): void {
    this.authenticated = true;
  }

  clear(): void {
    this.authenticated = false;
  }
}
