import { Component, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from './api.service';
import { Claim, Dashboard, SubmitClaim, User } from './models';

@Component({ selector: 'app-root', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './app.component.html' })
export class AppComponent implements OnDestroy {
  private api = inject(ApiService);
  user: User | null = null;
  username = 'claimant'; password = '';
  claims: Claim[] = []; selected: Claim | null = null; dashboard: Dashboard | null = null;
  error = ''; notice = ''; busy = false; filter = ''; message = ''; settlementAmount = 0;
  readonly markets = ['MY', 'SG', 'HK', 'TH', 'ID', 'VN'];
  readonly statuses = ['SUBMITTED', 'UNDER_REVIEW', 'INFORMATION_REQUIRED', 'SETTLED', 'REJECTED'];
  draft: SubmitClaim = this.emptyDraft();
  private timer?: ReturnType<typeof setInterval>;
  private refreshing = false;
  get visibleClaims() { return this.claims.filter(c => !this.filter || c.status === this.filter); }
  private emptyDraft(): SubmitClaim { return { type: 'MOTOR', market: 'MY', incidentDate: '', description: '', estimatedAmount: 0 }; }

  async login() {
    await this.run(async () => {
      this.api.setCredentials(this.username, this.password);
      this.user = await this.api.me(); this.password = '';
      await this.refresh();
      // Polling keeps the UI understandable and makes changes visible across two browser windows.
      this.timer = setInterval(() => { if (!this.busy) void this.refresh().catch(e => this.showError(e)); }, 10000);
    });
  }
  logout() {
    clearInterval(this.timer); this.api.clearCredentials(); this.user = null;
    this.claims = []; this.selected = null; this.dashboard = null; this.message = ''; this.error = ''; this.notice = '';
  }
  ngOnDestroy() { clearInterval(this.timer); }
  async refresh() {
    if (this.refreshing || !this.user) return;
    this.refreshing = true;
    try {
      this.claims = await this.api.claims();
      if (this.selected) this.selected = this.claims.find(c => c.id === this.selected?.id) ?? null;
      if (this.user.role === 'OFFICER') this.dashboard = await this.api.dashboard();
    } finally { this.refreshing = false; }
  }
  async manualRefresh() { await this.run(() => this.refresh()); }
  select(claim: Claim) { this.selected = claim; this.message = ''; this.settlementAmount = claim.estimatedAmount; this.notice = ''; }
  async submit() {
    await this.run(async () => {
      const claim = await this.api.submit(this.draft); this.draft = this.emptyDraft();
      await this.refresh(); this.select(claim); this.notice = 'Claim submitted. Keep the reference below.';
    });
  }
  async act(action: string) {
    if (!this.selected) return;
    const claim = this.selected;
    await this.run(async () => {
      await this.api.change(claim, action, action === 'assign' ? 'Picked up for assessment' : this.message, this.settlementAmount);
      this.message = ''; await this.refresh(); this.notice = 'Claim updated.';
    });
  }
  private async run(work: () => Promise<void>) {
    if (this.busy) return;
    this.busy = true; this.error = ''; this.notice = '';
    try { await work(); } catch (e) { this.showError(e); } finally { this.busy = false; }
  }
  private showError(error: unknown) {
    this.error = error instanceof HttpErrorResponse
      ? error.error?.message ?? (error.status === 401 ? 'Incorrect username or password.' : error.status === 403 ? 'You do not have permission for this action.' : 'Cannot complete request. Check the backend and refresh.')
      : 'Something went wrong. Please refresh.';
  }
}
