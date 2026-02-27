import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProspectService } from './prospect.service';
import { ContactInterestService } from './contact-interest.service';
import { DepositService, CreateDepositRequest } from './deposit.service';
import { PropertyService } from '../properties/property.service';
import { Prospect, PROSPECT_STATUSES } from '../../core/models/prospect.model';
import { ContactInterest } from '../../core/models/contact-interest.model';
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
  private interestSvc = inject(ContactInterestService);
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

  interests: ContactInterest[] = [];
  properties: Property[] = [];
  interestsLoading = false;
  interestError = '';
  selectedPropertyId = '';
  addingInterest = false;
  removingPropertyId = '';

  deposits: Deposit[] = [];
  depositsLoading = false;
  depositError = '';
  depositSuccess = '';
  depositPropertyId = '';
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
        this.loadInterests();
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

  loadInterests(): void {
    if (!this.prospect) return;
    this.interestsLoading = true;
    this.interestError = '';
    this.interestSvc.list(this.prospect.id).subscribe({
      next: (data) => {
        this.interests = data;
        this.interestsLoading = false;
      },
      error: (err: HttpErrorResponse) => {
        this.interestsLoading = false;
        const body = err.error as ErrorResponse | null;
        this.interestError = body?.message ?? `Failed to load interests (${err.status})`;
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

  get availableProperties(): Property[] {
    const interestedIds = new Set(this.interests.map((i) => i.propertyId));
    return this.properties.filter((p) => !interestedIds.has(p.id));
  }

  get activeProperties(): Property[] {
    return this.properties.filter((p) => p.status === 'ACTIVE');
  }

  addInterest(): void {
    if (!this.prospect || !this.selectedPropertyId) return;
    this.addingInterest = true;
    this.interestError = '';
    this.interestSvc.add(this.prospect.id, this.selectedPropertyId).subscribe({
      next: () => {
        this.selectedPropertyId = '';
        this.addingInterest = false;
        this.loadInterests();
      },
      error: (err: HttpErrorResponse) => {
        this.addingInterest = false;
        const body = err.error as ErrorResponse | null;
        this.interestError = body?.message ?? `Failed to add interest (${err.status})`;
      },
    });
  }

  removeInterest(propertyId: string): void {
    if (!this.prospect) return;
    this.removingPropertyId = propertyId;
    this.interestError = '';
    this.interestSvc.remove(this.prospect.id, propertyId).subscribe({
      next: () => {
        this.removingPropertyId = '';
        this.loadInterests();
      },
      error: (err: HttpErrorResponse) => {
        this.removingPropertyId = '';
        const body = err.error as ErrorResponse | null;
        this.interestError = body?.message ?? `Failed to remove interest (${err.status})`;
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

  downloadReservationPdf(d: Deposit): void {
    this.depositSvc.downloadReservationPdf(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `reservation_${d.reference}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.depositError = 'Failed to download PDF.';
      },
    });
  }

  createDeposit(): void {
    if (!this.prospect || !this.depositPropertyId || !this.depositAmount) return;
    this.creatingDeposit = true;
    this.depositError = '';
    this.depositSuccess = '';

    const req: CreateDepositRequest = {
      contactId: this.prospect.id,
      propertyId: this.depositPropertyId,
      amount: this.depositAmount,
      notes: this.depositNotes || undefined,
    };

    this.depositSvc.create(req).subscribe({
      next: () => {
        this.creatingDeposit = false;
        this.depositSuccess = 'Deposit created successfully.';
        this.depositPropertyId = '';
        this.depositAmount = null;
        this.depositNotes = '';
        this.loadDeposits();
        this.loadProperties();
        // Refresh prospect to reflect status/type change from workflow
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
        if (err.status === 409) {
          this.depositError = 'Ce bien est déjà réservé ou un acompte existe déjà.';
          this.loadProperties();
        } else {
          this.depositError = body?.message ?? `Failed to create deposit (${err.status})`;
        }
      },
    });
  }
}
