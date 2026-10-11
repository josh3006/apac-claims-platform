import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Claim, Dashboard, SubmitClaim, User } from './models';

/**
 * Typed REST boundary for the screen. Converts one-response Observables to Promises for async/await.
 * Relative URLs use the development proxy or nginx so credentials stay on the application's origin.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  // Demo credentials stay in memory, never localStorage. Production would use OIDC.
  private authorization = '';
  setCredentials(username: string, password: string) { this.authorization = 'Basic ' + btoa(username + ':' + password); }
  clearCredentials() { this.authorization = ''; }
  private options() { return { headers: new HttpHeaders({ Authorization: this.authorization }) }; }
  me() { return firstValueFrom(this.http.get<User>('/api/me', this.options())); }
  claims() { return firstValueFrom(this.http.get<Claim[]>('/api/claims', this.options())); }
  dashboard() { return firstValueFrom(this.http.get<Dashboard>('/api/officer/dashboard', this.options())); }
  submit(claim: SubmitClaim, key: string) {
    const headers = this.options().headers.set('Idempotency-Key', key);
    return firstValueFrom(this.http.post<Claim>('/api/claims', claim, { headers }));
  }
  change(claim: Claim, action: string, message: string, settlementAmount?: number) {
    // Send the latest observed version; a concurrent/stale edit receives HTTP 409 from the backend.
    const url = action === 'information' ? `/api/claims/${claim.id}/information`
      : `/api/officer/claims/${claim.id}/${action === 'SETTLED' || action === 'REJECTED' ? 'decision' : action}`;
    const body = action === 'SETTLED' || action === 'REJECTED'
      ? { version: claim.version, status: action, message, settlementAmount: action === 'SETTLED' ? settlementAmount : null }
      : { version: claim.version, message };
    return firstValueFrom(this.http.post<Claim>(url, body, this.options()));
  }
}
