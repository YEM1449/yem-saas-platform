import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ImmeubleService, Immeuble } from '../../immeubles/immeuble.service';
import { PropertyService } from '../../properties/property.service';
import { TrancheService, Tranche } from '../tranche.service';
import { Property } from '../../../core/models/property.model';

interface FloorRow {
  number: number;
  label: string;
  units: Property[];
}

interface StatusStats {
  disponible: number;
  reserve: number;
  vendu: number;
  brouillon: number;
  retire: number;
  total: number;
  absorption: number;
}

@Component({
  selector: 'app-building-view',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './building-view.component.html',
  styleUrl: './building-view.component.css',
})
export class BuildingViewComponent implements OnInit {
  @Input() projectId!: string;

  private trancheSvc = inject(TrancheService);
  private immSvc    = inject(ImmeubleService);
  private propSvc   = inject(PropertyService);

  tranches: Tranche[]   = [];
  trancheIdx            = 0;
  allImmeubles: Immeuble[]      = [];
  trancheImmeubles: Immeuble[]  = [];
  selectedImmeuble: Immeuble | null = null;

  floors: FloorRow[] = [];
  stats: StatusStats = { disponible: 0, reserve: 0, vendu: 0, brouillon: 0, retire: 0, total: 0, absorption: 0 };

  selectedProperty: Property | null = null;
  statusFilter: string | null = null;

  loadingTranches = true;
  loadingBuilding = false;
  error = '';

  get selectedTranche(): Tranche | null {
    return this.tranches[this.trancheIdx] ?? null;
  }

  get filteredFloors(): FloorRow[] {
    if (!this.statusFilter) return this.floors;
    return this.floors
      .map(f => ({ ...f, units: f.units.filter(u => u.status === this.statusFilter) }))
      .filter(f => f.units.length > 0);
  }

  ngOnInit(): void {
    forkJoin({
      tranches:  this.trancheSvc.listByProject(this.projectId),
      immeubles: this.immSvc.list(this.projectId),
    }).subscribe({
      next: ({ tranches, immeubles }) => {
        this.tranches     = tranches;
        this.allImmeubles = immeubles;
        this.loadingTranches = false;
        this.selectTranche(0);
      },
      error: () => {
        this.loadingTranches = false;
        this.error = 'Impossible de charger les bâtiments.';
      },
    });
  }

  selectTranche(idx: number): void {
    this.trancheIdx      = idx;
    this.selectedProperty = null;
    this.statusFilter    = null;
    const t = this.tranches[idx];
    if (t) {
      const filtered = this.allImmeubles.filter(imm => imm.trancheId === t.id);
      this.trancheImmeubles = filtered.length ? filtered : this.allImmeubles;
    } else {
      this.trancheImmeubles = this.allImmeubles;
    }
    if (this.trancheImmeubles.length) {
      this.selectImmeuble(this.trancheImmeubles[0]);
    } else {
      this.selectedImmeuble = null;
      this.floors = [];
      this.stats = { disponible: 0, reserve: 0, vendu: 0, brouillon: 0, retire: 0, total: 0, absorption: 0 };
    }
  }

  prevTranche(): void { if (this.trancheIdx > 0) this.selectTranche(this.trancheIdx - 1); }
  nextTranche(): void { if (this.trancheIdx < this.tranches.length - 1) this.selectTranche(this.trancheIdx + 1); }

  selectImmeuble(imm: Immeuble): void {
    this.selectedImmeuble = imm;
    this.selectedProperty = null;
    this.statusFilter     = null;
    this.loadingBuilding  = true;
    this.propSvc.list({ immeubleId: imm.id }).subscribe({
      next: (props) => {
        this.buildFloors(props);
        this.loadingBuilding = false;
      },
      error: () => { this.loadingBuilding = false; },
    });
  }

  private buildFloors(props: Property[]): void {
    const d   = props.filter(p => p.status === 'ACTIVE').length;
    const r   = props.filter(p => p.status === 'RESERVED').length;
    const v   = props.filter(p => p.status === 'SOLD').length;
    const b   = props.filter(p => p.status === 'DRAFT').length;
    const ret = props.filter(p => p.status === 'WITHDRAWN' || p.status === 'ARCHIVED').length;
    const total = props.length;
    const stock = total - b;
    this.stats = {
      disponible: d, reserve: r, vendu: v, brouillon: b, retire: ret, total,
      absorption: stock > 0 ? Math.round((v + r) / stock * 100) : 0,
    };

    const byFloor = new Map<number, Property[]>();
    props.forEach(p => {
      const n = p.floorNumber ?? -1;
      if (!byFloor.has(n)) byFloor.set(n, []);
      byFloor.get(n)!.push(p);
    });

    this.floors = Array.from(byFloor.entries())
      .sort(([a], [b]) => b - a)
      .map(([num, units]) => ({
        number: num,
        label:  num < 0 ? 'Non défini' : num === 0 ? 'RDC' : `ÉTAGE ${num}`,
        units:  units.sort((a, b) => (a.referenceCode ?? '').localeCompare(b.referenceCode ?? '')),
      }));
  }

  selectProperty(p: Property): void {
    this.selectedProperty = this.selectedProperty?.id === p.id ? null : p;
  }

  toggleFilter(status: string): void {
    this.statusFilter = this.statusFilter === status ? null : status;
  }

  statusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':    return 'status-active';
      case 'DRAFT':     return 'status-draft';
      case 'RESERVED':  return 'status-reserved';
      case 'SOLD':      return 'status-sold';
      case 'WITHDRAWN':
      case 'ARCHIVED':  return 'status-withdrawn';
      default:          return 'status-draft';
    }
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      ACTIVE: 'Disponible', DRAFT: 'Brouillon', RESERVED: 'Réservé',
      SOLD: 'Vendu', WITHDRAWN: 'Retiré', ARCHIVED: 'Archivé',
    };
    return map[status] ?? status;
  }

  typeLabel(type: string): string {
    const map: Record<string, string> = {
      APPARTEMENT: 'Appartement', VILLA: 'Villa', PARKING: 'Parking',
      DUPLEX: 'Duplex', STUDIO: 'Studio', T2: 'T2', T3: 'T3',
      LOCAL_COMMERCIAL: 'Local commercial', LOT: 'Lot', TERRAIN_VIERGE: 'Terrain',
    };
    return map[type] ?? type;
  }

  formatPrice(value: number | null): string {
    if (!value) return '--';
    if (value >= 1_000_000) {
      const m = value / 1_000_000;
      return (Math.round(m * 10) / 10).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' M';
    }
    if (value >= 1_000) return Math.round(value / 1_000) + ' K';
    return value.toString();
  }

  pricePerSqm(p: Property): string {
    if (!p.price || !p.surfaceAreaSqm) return '--';
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })
      .format(Math.round(p.price / p.surfaceAreaSqm));
  }

  pipelineStep(status: string): number {
    switch (status) {
      case 'RESERVED': return 1;
      case 'SOLD':     return 2;
      default:         return 0;
    }
  }

  floorBadgeLabel(p: Property): string {
    if (p.floorNumber === null || p.floorNumber === undefined) return '--';
    return p.floorNumber === 0 ? 'RDC' : String(p.floorNumber);
  }
}
