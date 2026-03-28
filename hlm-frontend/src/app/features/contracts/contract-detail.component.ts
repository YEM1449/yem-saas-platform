import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ContractService } from './contract.service';
import { ContractResponse } from '../../core/models/contract.model';
import { PaymentScheduleComponent } from './payment-schedule.component';
import { DocumentListComponent } from '../documents/document-list.component';

type Tab = 'info' | 'payments' | 'documents';

@Component({
  selector: 'app-contract-detail',
  standalone: true,
  imports: [CommonModule, PaymentScheduleComponent, DocumentListComponent, TranslateModule],
  templateUrl: './contract-detail.component.html',
  styleUrl: './contract-detail.component.css',
})
export class ContractDetailComponent implements OnInit {
  private route       = inject(ActivatedRoute);
  private contractSvc = inject(ContractService);

  contract: ContractResponse | null = null;
  loading = true;
  error   = '';
  activeTab: Tab = 'info';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.contractSvc.getById(id).subscribe({
      next: c => { this.contract = c; this.loading = false; },
      error: (e: HttpErrorResponse) => {
        this.error = e.error?.message ?? 'Contract not found';
        this.loading = false;
      },
    });
  }

  switchTab(tab: Tab): void {
    this.activeTab = tab;
  }
}
