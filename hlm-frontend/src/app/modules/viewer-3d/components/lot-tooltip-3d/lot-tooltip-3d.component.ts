import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LotStatusSnapshot, LOT_STATUS_COLORS, LOT_STATUS_LABELS, LotDisplayStatus } from '../../models/lot-3d-status.model';
import { Lot3dMappingEntry } from '../../models/project-3d-model.model';

@Component({
  selector: 'app-lot-tooltip-3d',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lot-tooltip-3d.component.html',
  styleUrl: './lot-tooltip-3d.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LotTooltip3dComponent {
  @Input() mapping!: Lot3dMappingEntry;
  @Input() status!:  LotStatusSnapshot;
  @Input() x = 0;
  @Input() y = 0;

  get statusColor(): string {
    return LOT_STATUS_COLORS[this.status?.statut as LotDisplayStatus] ?? '#6B7280';
  }

  get statusLabel(): string {
    return LOT_STATUS_LABELS[this.status?.statut as LotDisplayStatus] ?? '—';
  }

  get formattedPrice(): string {
    if (!this.status?.prix) return '—';
    return new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 })
      .format(this.status.prix);
  }
}
