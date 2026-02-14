import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProspectService } from './prospect.service';
import { DepositService, CreateDepositRequest } from './deposit.service';
import { PropertyService } from '../properties/property.service';
import { Prospect, PROSPECT_STATUSES } from '../../core/models/prospect.model';
import { Deposit } from '../../core/models/deposit.model';
import { Property } from '../../core/models/property.model';
import { ErrorResponse } from '../../core/models/error-response.model';

@Component({
  selector: 'app-prospect-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './prospect-detail.component.html',
  styleUrl: './prospect-detail.component.css',
})
export class ProspectDetailComponent implements OnInit {
  private svc = inject(ProspectService);
  private depositSvc = inject(DepositService);
  private propertySvc = inject(PropertyService);
  private route = inject(ActivatedRoute);

  prospect: Prospect | null = null;
  loading = true;
  error = '';
  statusMessage = '';
  selectedStatus = '';
  updating = false;
  statuses = PROSPECT_STATUSES;

  // Deposit section
  deposits: Deposit[] = [];
  properties: Property[] = [];
  depositsLoading = false;
  depositError = '';
  depositSuccess = '';
  selectedPropertyId = '';
  depositAmount: number | null = null;
  depositNotes = '';
  creatingDeposit = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.svc.getById(id).subscribe({
      next: (data) => {
        this.prospect = data;
        this.selectedStatus = data.status;
        this.loading = false;
        this.loadDeposits();
        this.loadProperties();
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        const body = err.error as ErrorResponse | null;
        if (err.status === 404) {
          this.error = 'Prospect not found.';
        } else if (err.status === 401) {
          this.error = 'Session expired. Please log in again.';
        } else if (body?.message) {
          this.error = body.message;
        } else {
          this.error = `Failed to load prospect (${err.status})`;
        }
      },
    });
  }

  onStatusChange(): void {
    if (!this.prospect || this.selectedStatus === this.prospect.status) return;
    this.updating = true;
    this.statusMessage = '';
    this.svc.updateStatus(this.prospect.id, this.selectedStatus).subscribe({
      next: (updated) => {
        this.prospect = updated;
        this.selectedStatus = updated.status;
        this.updating = false;
        this.statusMessage = 'Status updated.';
      },
      error: (err: HttpErrorResponse) => {
        this.updating = false;
        const body = err.error as ErrorResponse | null;
        this.statusMessage = body?.message ?? `Update failed (${err.status})`;
        if (this.prospect) {
          this.selectedStatus = this.prospect.status;
        }
      },
    });
  }

  loadDeposits(): void {
    if (!this.prospect) return;
    this.depositsLoading = true;
    this.depositError = '';
    this.depositSvc.listByContact(this.prospect.id).subscribe({
      next: (data) => {
        this.deposits = data;
        this.depositsLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.depositsLoading = false;
        const body = err.error as ErrorResponse | null;
        this.depositError = body?.message ?? `Failed to load deposits (${err.status})`;
      },
    });
  }

  loadProperties(): void {
    this.propertySvc.list().subscribe({
      next: (data) => (this.properties = data),
      error: () => {},
    });
  }

  propertyTitle(propertyId: string): string {
    const p = this.properties.find((prop) => prop.id === propertyId);
    return p ? `${p.title} (${p.referenceCode})` : propertyId;
  }

  createDeposit(): void {
    if (!this.prospect || !this.selectedPropertyId || !this.depositAmount) return;

    this.creatingDeposit = true;
    this.depositError = '';
    this.depositSuccess = '';

    const req: CreateDepositRequest = {
      contactId: this.prospect.id,
      propertyId: this.selectedPropertyId,
      amount: this.depositAmount,
      notes: this.depositNotes || undefined,
    };

    this.depositSvc.create(req).subscribe({
      next: () => {
        this.creatingDeposit = false;
        this.depositSuccess = 'Deposit created successfully.';
        this.selectedPropertyId = '';
        this.depositAmount = null;
        this.depositNotes = '';
        this.loadDeposits();
        // Refresh prospect to reflect status change
        this.svc.getById(this.prospect!.id).subscribe({
          next: (updated) => {
            this.prospect = updated;
            this.selectedStatus = updated.status;
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.creatingDeposit = false;
        const body = err.error as ErrorResponse | null;
        this.depositError = body?.message ?? `Failed to create deposit (${err.status})`;
      },
    });
  }
}
