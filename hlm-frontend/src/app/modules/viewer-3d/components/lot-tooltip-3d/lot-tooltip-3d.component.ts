import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, inject} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../../../core/i18n/i18n.service';
import { DecimalPipe } from '@angular/common';
import { LotStatusSnapshot, LOT_STATUS_COLORS, LOT_STATUS_LABELS, LotDisplayStatus } from '../../models/lot-3d-status.model';
import { Lot3dMappingEntry } from '../../models/project-3d-model.model';

@Component({
  selector: 'app-lot-tooltip-3d',
  standalone: true,
  imports: [DecimalPipe, TranslatePipe],
  templateUrl: './lot-tooltip-3d.component.html',
  styleUrl: './lot-tooltip-3d.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LotTooltip3dComponent {
  private i18n = inject(I18nService);
  @Input() mapping!: Lot3dMappingEntry;
  @Input() status!:  LotStatusSnapshot;
  @Input() x = 0;
  @Input() y = 0;
  /** When true the tooltip is touch-pinned and shows a close (×) button. */
  @Input() pinned = false;
  @Output() close = new EventEmitter<void>();

  get statusColor(): string {
    return LOT_STATUS_COLORS[this.status?.statut as LotDisplayStatus] ?? '#6B7280';
  }

  get statusLabel(): string {
    return this.status?.statut ? this.i18n.instant('viewer3d.status.' + this.status.statut) : '—';
  }

  get formattedPrice(): string {
    if (!this.status?.prix) return '—';
    return new Intl.NumberFormat('fr-MA', { style: 'currency', currency: 'MAD', maximumFractionDigits: 0 })
      .format(this.status.prix);
  }
}
