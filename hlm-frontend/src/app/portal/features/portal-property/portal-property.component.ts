import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { PortalContractsService } from '../../core/portal-contracts.service';
import { PortalProperty } from '../../../core/models/portal.model';

@Component({
  selector: 'app-portal-property',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './portal-property.component.html',
})
export class PortalPropertyComponent implements OnInit {
  private service = inject(PortalContractsService);
  private route   = inject(ActivatedRoute);

  property = signal<PortalProperty | null>(null);
  loading  = signal(true);
  error    = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.service.getProperty(id).subscribe({
      next:  (data) => { this.property.set(data); this.loading.set(false); },
      error: ()     => { this.error.set('Property not found.'); this.loading.set(false); },
    });
  }
}
