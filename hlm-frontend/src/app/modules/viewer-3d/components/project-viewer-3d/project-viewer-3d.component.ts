import {
  Component, OnInit, OnDestroy, AfterViewInit,
  ViewChild, ElementRef, ChangeDetectionStrategy, ChangeDetectorRef,
  Input, signal, computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { interval, Subscription, switchMap, takeUntil, Subject, EMPTY } from 'rxjs';
import { catchError } from 'rxjs/operators';
import * as THREE from 'three';

import { ThreeEngineService }  from '../../services/three-engine.service';
import { ModelLoaderService, LoadEvent } from '../../services/model-loader.service';
import { LotMappingService }   from '../../services/lot-mapping.service';
import { Viewer3dApiService }  from '../../services/viewer-3d-api.service';
import { LotTooltip3dComponent } from '../lot-tooltip-3d/lot-tooltip-3d.component';

import { Project3dModel, Lot3dMappingEntry } from '../../models/project-3d-model.model';
import { LotStatusSnapshot, LOT_STATUS_COLORS, LOT_STATUS_LABELS, LotDisplayStatus } from '../../models/lot-3d-status.model';

type ViewerState = 'loading' | 'loaded' | 'error';

/** Entries for the colour legend strip */
const LEGEND_ITEMS = (Object.keys(LOT_STATUS_COLORS) as LotDisplayStatus[]).map(k => ({
  status: k,
  color:  LOT_STATUS_COLORS[k],
  label:  LOT_STATUS_LABELS[k],
}));

/**
 * Feature 1 — Interactive 3D building viewer.
 *
 * Route: /app/projets/:projetId/viewer-3d
 * Portal route: /portal/projets/:projetId/viewer-3d (read-only mode)
 *
 * Lifecycle:
 *   1. Load model metadata + pre-signed URL from API
 *   2. Stream GLB (with progress bar)
 *   3. Apply mesh→lot colour coding
 *   4. Poll status every 30 s for live updates
 *   5. Hover: show LotTooltip3dComponent overlay
 *   6. Click: emit lotId for parent to open LotDetailPanel / NouvelleVente dialog
 */
@Component({
  selector: 'app-project-viewer-3d',
  standalone: true,
  imports: [CommonModule, RouterLink, LotTooltip3dComponent],
  templateUrl: './project-viewer-3d.component.html',
  styleUrl:    './project-viewer-3d.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectViewer3dComponent implements OnInit, AfterViewInit, OnDestroy {

  /** When true the component runs in read-only portal mode (no reservation trigger). */
  @Input() portalMode = false;
  /** Explicit project ID — takes priority over the route param (used when embedded in dashboard). */
  @Input() projetIdInput: string | null = null;

  @ViewChild('canvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  readonly legendItems = LEGEND_ITEMS;

  state         = signal<ViewerState>('loading');
  loadProgress  = signal(0);        // 0–100
  errorMessage  = signal('');
  legendVisible = signal(true);

  hoveredMapping = signal<Lot3dMappingEntry | null>(null);
  hoveredStatus  = signal<LotStatusSnapshot | null>(null);
  tooltipX = 0;
  tooltipY = 0;

  /** The most recent status snapshot (used by focusedLot and parent drill-down). */
  statuses: LotStatusSnapshot[] = [];

  private projetId!: string;
  private modelMeta!: Project3dModel;
  private destroy$ = new Subject<void>();
  private subs     = new Subscription();
  private focusedMesh: THREE.Mesh | null = null;
  private focusedMeshIndex = -1;
  private meshList: THREE.Mesh[] = [];

  constructor(
    public  route:      ActivatedRoute,
    private engine:     ThreeEngineService,
    private loader:     ModelLoaderService,
    private mapping:    LotMappingService,
    private api:        Viewer3dApiService,
    private cdr:        ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.projetId = this.projetIdInput ?? this.route.snapshot.params['projetId'];
    const modelObs = this.portalMode
      ? this.api.getPortalModel(this.projetId)
      : this.api.getModel(this.projetId);

    modelObs.pipe(
      catchError(err => {
        this.state.set('error');
        this.errorMessage.set('Impossible de charger le modèle 3D. Vérifiez votre connexion et réessayez.');
        this.cdr.markForCheck();
        return EMPTY;
      }),
      takeUntil(this.destroy$)
    ).subscribe(meta => {
      this.modelMeta = meta;
      // Wait until the canvas is mounted (AfterViewInit may have already run)
      if (this.canvasRef?.nativeElement) {
        this.loadGlb(meta);
      }
    });
  }

  ngAfterViewInit(): void {
    this.engine.init(this.canvasRef.nativeElement);

    // Subscribe to engine events
    this.subs.add(
      this.engine.hover$.subscribe(ev => {
        if (!ev) {
          this.hoveredMapping.set(null);
          this.hoveredStatus.set(null);
          if (this.focusedMesh) {
            this.mapping.unhighlight(this.focusedMesh);
            this.focusedMesh = null;
          }
        } else {
          const m = this.mapping.getMappingForMesh(ev.mesh);
          if (m) {
            if (this.focusedMesh && this.focusedMesh !== ev.mesh) {
              this.mapping.unhighlight(this.focusedMesh);
            }
            this.mapping.highlight(ev.mesh);
            this.focusedMesh = ev.mesh;
            this.hoveredMapping.set(m);
            this.hoveredStatus.set(
              this.statuses.find(s => s.meshId === m.meshId) ?? null
            );
            this.tooltipX = ev.screenX;
            this.tooltipY = ev.screenY;
          }
        }
        this.cdr.markForCheck();
      })
    );

    this.subs.add(
      this.engine.click$.subscribe(ev => {
        const m = this.mapping.getMappingForMesh(ev.mesh);
        if (!m) return;
        const status = this.statuses.find(s => s.meshId === m.meshId);
        this.onLotClick(m, status);
      })
    );

    // If model metadata arrived before AfterViewInit, load now
    if (this.modelMeta) {
      this.loadGlb(this.modelMeta);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.subs.unsubscribe();
    this.engine.dispose();
  }

  toggleLegend(): void {
    this.legendVisible.update(v => !v);
  }

  // ── Keyboard accessibility (Tab + Enter for lot selection) ────────────────

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Tab') {
      event.preventDefault();
      this.focusNextMesh(event.shiftKey ? -1 : 1);
    }
    if ((event.key === 'Enter' || event.key === ' ') && this.focusedMesh) {
      const m = this.mapping.getMappingForMesh(this.focusedMesh);
      if (m) {
        const status = this.statuses.find(s => s.meshId === m.meshId);
        this.onLotClick(m, status);
      }
    }
  }

  // ── private ───────────────────────────────────────────────────────────────

  private loadGlb(meta: Project3dModel): void {
    this.loader.load(meta.glbPresignedUrl).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: (ev: LoadEvent) => {
        if (ev.type === 'progress') {
          this.loadProgress.set(Math.round(ev.progress.ratio * 100));
          this.cdr.markForCheck();
        } else {
          // Model fully loaded
          this.engine.scene.add(ev.gltf.scene);
          this.meshList = this.loader.extractMeshes(ev.gltf);
          this.engine.registerMeshes(this.meshList);
          this.mapping.applyMappings(meta.mappings, this.meshList);

          // Initial colour pass
          this.fetchAndApplyStatus();
          // Poll every 30 s
          this.subs.add(
            interval(30_000).pipe(
              switchMap(() => this.fetchStatusObs()),
              takeUntil(this.destroy$)
            ).subscribe(statuses => this.applyStatuses(statuses))
          );

          this.state.set('loaded');
          this.cdr.markForCheck();
        }
      },
      error: () => {
        this.state.set('error');
        this.errorMessage.set('Échec du chargement du modèle 3D. Affichage de la liste à la place.');
        this.cdr.markForCheck();
      },
    });
  }

  private fetchStatusObs() {
    const obs = this.portalMode
      ? this.api.getPortalStatusSnapshot(this.projetId)
      : this.api.getStatusSnapshot(this.projetId);
    return obs.pipe(catchError(() => EMPTY));
  }

  private fetchAndApplyStatus(): void {
    this.fetchStatusObs().pipe(takeUntil(this.destroy$)).subscribe(s => this.applyStatuses(s));
  }

  private applyStatuses(statuses: LotStatusSnapshot[]): void {
    this.statuses = statuses;
    this.mapping.updateColors(statuses);
    this.cdr.markForCheck();
  }

  private onLotClick(m: Lot3dMappingEntry, status: LotStatusSnapshot | undefined): void {
    if (this.portalMode) return; // portal: read-only
    // Emit an event for the host to pick up. Using a CustomEvent on the host element.
    const event = new CustomEvent('lot-selected', {
      bubbles: true,
      detail: { lotId: m.lotId, lotRef: m.lotRef, statut: status?.statut },
    });
    this.canvasRef.nativeElement.dispatchEvent(event);
  }

  private focusNextMesh(direction: 1 | -1): void {
    if (!this.meshList.length) return;
    this.focusedMeshIndex = (this.focusedMeshIndex + direction + this.meshList.length) % this.meshList.length;
    const mesh = this.meshList[this.focusedMeshIndex];
    if (this.focusedMesh) this.mapping.unhighlight(this.focusedMesh);
    this.mapping.highlight(mesh);
    this.focusedMesh = mesh;
    const m = this.mapping.getMappingForMesh(mesh);
    if (m) {
      this.hoveredMapping.set(m);
      this.hoveredStatus.set(this.statuses.find(s => s.meshId === m.meshId) ?? null);
    }
    this.cdr.markForCheck();
  }
}
