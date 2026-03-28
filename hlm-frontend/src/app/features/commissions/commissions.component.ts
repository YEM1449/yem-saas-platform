import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { CommissionService } from './commission.service';
import { CommissionDTO, CommissionRuleRequest, CommissionRuleResponse } from '../../core/models/commission.model';

@Component({
  selector: 'app-commissions',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './commissions.component.html',
})
export class CommissionsComponent implements OnInit {
  private svc  = inject(CommissionService);
  private auth = inject(AuthService);

  commissions: CommissionDTO[] = [];
  rules: CommissionRuleResponse[] = [];

  loading = false;
  error   = '';

  // Filters (admin/manager only)
  agentId  = '';
  fromDate = '';
  toDate   = '';

  // Rule form
  showRuleForm    = false;
  editingRuleId: string | null = null;
  ruleForm: CommissionRuleRequest = { ratePercent: 0, effectiveFrom: '' };
  ruleError = '';

  get isAdmin(): boolean    { return this.auth.user?.role === 'ROLE_ADMIN'; }
  get isAdminOrManager(): boolean {
    const r = this.auth.user?.role;
    return r === 'ROLE_ADMIN' || r === 'ROLE_MANAGER';
  }
  get isAgent(): boolean { return this.auth.user?.role === 'ROLE_AGENT'; }

  ngOnInit(): void {
    this.load();
    if (this.isAdmin) this.loadRules();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    const obs = this.isAgent
      ? this.svc.getMyCommissions(this.fromDate || undefined, this.toDate || undefined)
      : this.svc.getAllCommissions(this.agentId || undefined, this.fromDate || undefined, this.toDate || undefined);

    obs.subscribe({
      next: data => { this.commissions = data; this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.error?.message ?? `Failed to load (${err.status})`;
      },
    });
  }

  loadRules(): void {
    this.svc.listRules().subscribe({ next: r => (this.rules = r) });
  }

  get totalCommission(): number {
    return this.commissions.reduce((s, c) => s + c.commissionAmount, 0);
  }

  formatAmount(v: number): string {
    return new Intl.NumberFormat('fr-MA', { style: 'decimal', maximumFractionDigits: 0 }).format(v) + ' MAD';
  }

  openNewRule(): void {
    this.editingRuleId = null;
    this.ruleForm = { ratePercent: 0, effectiveFrom: '' };
    this.showRuleForm = true;
    this.ruleError = '';
  }

  editRule(r: CommissionRuleResponse): void {
    this.editingRuleId = r.id;
    this.ruleForm = {
      projectId: r.projectId ?? undefined,
      ratePercent: r.ratePercent,
      fixedAmount: r.fixedAmount ?? undefined,
      effectiveFrom: r.effectiveFrom,
      effectiveTo: r.effectiveTo ?? undefined,
    };
    this.showRuleForm = true;
    this.ruleError = '';
  }

  saveRule(): void {
    const obs = this.editingRuleId
      ? this.svc.updateRule(this.editingRuleId, this.ruleForm)
      : this.svc.createRule(this.ruleForm);
    obs.subscribe({
      next: () => { this.showRuleForm = false; this.loadRules(); },
      error: (err: HttpErrorResponse) => { this.ruleError = err.error?.message ?? 'Save failed'; },
    });
  }

  deleteRule(id: string): void {
    if (!confirm('Delete this commission rule?')) return;
    this.svc.deleteRule(id).subscribe({ next: () => this.loadRules() });
  }
}
