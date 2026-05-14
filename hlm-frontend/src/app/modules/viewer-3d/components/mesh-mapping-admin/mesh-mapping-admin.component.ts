import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

import { ThreeEngineService } from '../../services/three-engine.service';
import { ModelLoaderService }  from '../../services/model-loader.service';
import { Viewer3dApiService }  from '../../services/viewer-3d-api.service';
import { Project3dModel }      from '../../models/project-3d-model.model';
import { PropertyService }     from '../../../../features/properties/property.service';
import { Property }            from '../../../../core/models/property.model';
import {
  PairProposal,
  PairStrategy,
  looksLikeBuilding,
  looksLikeTranche,
  proposeAutoPairings,
} from '../../utils/mesh-normalize';

/** Local UI struct for the per-row hierarchy (immeuble/tranche) state. */
interface MeshHierarchy {
  immeubleMeshId: string | null;
  trancheMeshId:  string | null;
}

/**
 * MeshMappingAdminComponent — ADMIN-only UI to pair GLB meshes to property records.
 *
 * Features:
 *   • 3D viewer with click-to-select; mapped meshes painted green, unmapped grey,
 *     selected amber. Mesh-name list mirrors the scene on the right.
 *   • Smart auto-pair matcher (see ../../utils/mesh-normalize.ts): multi-strategy
 *     (exact-normalised, contains, suffix, trailing-number) with a per-proposal
 *     confidence score. Pops a preview modal so the admin can review/uncheck
 *     individual proposals before applying.
 *   • Per-row hierarchy pickers: each lot mapping can reference a parent building
 *     mesh (immeubleMeshId) and a parent tranche mesh (trancheMeshId). Candidates
 *     are auto-detected via name heuristics ("BAT_", "TR_", …) but admin can
 *     override and pick any mesh.
 *   • Bulk save → PUT /api/projects/{id}/3d-model/mappings replaces the full set.
 */
@Component({
  selector: 'app-mesh-mapping-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mesh-mapping-admin.component.html',
  styleUrl: './mesh-mapping-admin.component.css',
})
export class MeshMappingAdminComponent implements OnInit, OnDestroy {

  @ViewChild('canvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly route        = inject(ActivatedRoute);
  private readonly engine       = inject(ThreeEngineService);
  private readonly loader       = inject(ModelLoaderService);
  private readonly api          = inject(Viewer3dApiService);
  private readonly propertySvc  = inject(PropertyService);
  private readonly i18n         = inject(TranslateService);

  projetId = '';

  /** Phase signals. */
  readonly loading = signal(true);
  readonly error   = signal<string | null>(null);
  readonly saving  = signal(false);
  readonly saved   = signal(false);

  readonly properties = signal<Property[]>([]);
  readonly allMeshes  = signal<string[]>([]);

  /** Per-mesh property mapping draft. */
  readonly draft = signal<Map<string, string | null>>(new Map());

  /** Per-mesh hierarchy draft (immeuble + tranche parent mesh names). */
  readonly hierarchyDraft = signal<Map<string, MeshHierarchy>>(new Map());

  readonly selectedMesh = signal<string | null>(null);
  readonly hideMapped   = signal(false);
  readonly filterText   = signal('');
  readonly expandedRows = signal<Set<string>>(new Set());
  readonly isDirty      = signal(false);

  /** Auto-pair preview state. Non-null while the modal is open. */
  readonly autoPairPreview = signal<PairProposal[] | null>(null);
  /** Set of meshNames the admin has UN-checked in the preview. */
  readonly autoPairRejected = signal<Set<string>>(new Set());

  /** Snapshot for "Undo auto-pair" (saved right before the apply). */
  private autoPairSnapshot: Map<string, string | null> | null = null;
  readonly canUndoAutoPair = signal(false);

  // ── Derived signals ───────────────────────────────────────────────────────

  /**
   * Mesh names that LOOK like building containers (via name heuristic).
   * Surfaces them as preferred candidates in the per-row "parent building" picker.
   */
  readonly buildingCandidates = computed(() => this.allMeshes().filter(looksLikeBuilding));
  readonly trancheCandidates  = computed(() => this.allMeshes().filter(looksLikeTranche));

  readonly visibleRows = computed(() => {
    const all  = this.allMeshes();
    const drft = this.draft();
    const hide = this.hideMapped();
    const q    = this.filterText().trim().toLowerCase();
    return all.filter(m => {
      if (hide && drft.get(m)) return false;
      if (!q) return true;
      const propId = drft.get(m);
      const prop = propId ? this.properties().find(p => p.id === propId) : null;
      return (
        m.toLowerCase().includes(q) ||
        (prop?.referenceCode?.toLowerCase().includes(q) ?? false) ||
        (prop?.title?.toLowerCase().includes(q) ?? false)
      );
    });
  });

  readonly mappedCount   = computed(() => {
    let n = 0;
    this.draft().forEach(v => { if (v) n++; });
    return n;
  });
  readonly unmappedCount = computed(() => this.allMeshes().length - this.mappedCount());

  private subs = new Subscription();

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.projetId = this.route.snapshot.paramMap.get('projetId') ?? '';
    if (!this.projetId) {
      this.error.set('Projet introuvable.');
      this.loading.set(false);
      return;
    }
    this.bootstrap();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.engine.dispose();
  }

  // ── Bootstrap: fetch model + properties + load GLB ────────────────────────

  private bootstrap(): void {
    this.subs.add(
      this.api.getModel(this.projetId).subscribe({
        next: (model) => this.onModelLoaded(model),
        error: (err) => {
          if (err?.status === 404) {
            this.error.set('NO_MODEL');
          } else {
            this.error.set(err?.error?.message ?? 'Erreur de chargement du modèle 3D.');
          }
          this.loading.set(false);
        },
      })
    );

    this.subs.add(
      this.propertySvc.list({ projectId: this.projetId }).subscribe({
        next: (list) => this.properties.set(list),
        error: () => {/* surfaced via the model error */},
      })
    );
  }

  private onModelLoaded(model: Project3dModel): void {
    // Seed both drafts from the server response
    const propDraft = new Map<string, string | null>();
    const hierDraft = new Map<string, MeshHierarchy>();
    model.mappings.forEach(m => {
      propDraft.set(m.meshId, m.lotId);
      hierDraft.set(m.meshId, {
        immeubleMeshId: m.immeubleMeshId ?? null,
        trancheMeshId:  m.trancheMeshId  ?? null,
      });
    });
    this.draft.set(propDraft);
    this.hierarchyDraft.set(hierDraft);

    this.engine.init(this.canvasRef.nativeElement);

    this.subs.add(
      this.engine.click$.subscribe(evt => this.onMeshClick(evt.mesh.name))
    );

    this.subs.add(
      this.loader.load(model.glbPresignedUrl).subscribe({
        next: (evt) => {
          if (evt.type === 'loaded') {
            const meshes = this.loader.extractMeshes(evt.gltf);
            this.engine.scene.add(evt.gltf.scene);
            this.engine.registerMeshes(meshes);
            this.allMeshes.set(meshes.map(m => m.name).filter(Boolean));
            this.paintAllMeshes();
            this.loading.set(false);
          }
        },
        error: () => {
          this.error.set('Impossible de charger le fichier GLB.');
          this.loading.set(false);
        },
      })
    );
  }

  // ── Painting ───────────────────────────────────────────────────────────────

  private paintAllMeshes(): void {
    const drft = this.draft();
    const sel  = this.selectedMesh();
    this.allMeshes().forEach(name => {
      if (name === sel)         this.engine.paintMesh(name, 0xf59e0b); // amber = selected
      else if (drft.get(name))  this.engine.paintMesh(name, 0x16a34a); // green = mapped
      else                      this.engine.paintMesh(name, 0xcbd5e1); // grey = unmapped
    });
  }

  // ── Interactions ───────────────────────────────────────────────────────────

  onMeshClick(meshName: string): void {
    if (!this.allMeshes().includes(meshName)) return;
    this.selectedMesh.set(meshName);
    this.paintAllMeshes();
    queueMicrotask(() => {
      document.getElementById(`row-${this.cssEscape(meshName)}`)
              ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    });
  }

  focusMesh(meshName: string): void {
    this.selectedMesh.set(meshName);
    this.engine.focusOnMesh(meshName);
    this.paintAllMeshes();
  }

  toggleExpand(meshName: string): void {
    const next = new Set(this.expandedRows());
    if (next.has(meshName)) next.delete(meshName);
    else                    next.add(meshName);
    this.expandedRows.set(next);
  }

  setMapping(meshName: string, propertyId: string | null): void {
    const next = new Map(this.draft());
    next.set(meshName, propertyId || null);
    this.draft.set(next);
    this.isDirty.set(true);
    this.paintAllMeshes();
  }

  setHierarchy(
    meshName: string,
    field: 'immeubleMeshId' | 'trancheMeshId',
    value: string | null,
  ): void {
    const next = new Map(this.hierarchyDraft());
    const prev = next.get(meshName) ?? { immeubleMeshId: null, trancheMeshId: null };
    next.set(meshName, { ...prev, [field]: value || null });
    this.hierarchyDraft.set(next);
    this.isDirty.set(true);
  }

  getHierarchy(meshName: string): MeshHierarchy {
    return this.hierarchyDraft().get(meshName) ?? { immeubleMeshId: null, trancheMeshId: null };
  }

  clearAll(): void {
    if (!confirm(this.i18n.instant('viewer3d.clearAllConfirm'))) return;
    const next = new Map<string, string | null>();
    this.allMeshes().forEach(m => next.set(m, null));
    this.draft.set(next);
    this.hierarchyDraft.set(new Map());
    this.isDirty.set(true);
    this.paintAllMeshes();
  }

  // ── Auto-pair (smart) ──────────────────────────────────────────────────────

  /**
   * Runs the multi-strategy matcher and opens the preview modal.
   * The admin reviews every proposal individually before applying.
   */
  runAutoPair(): void {
    const unpaired = this.allMeshes().filter(m => !this.draft().get(m));
    const usedIds  = new Set<string>();
    this.draft().forEach(v => { if (v) usedIds.add(v); });
    const candidates = this.properties().filter(p => !usedIds.has(p.id));

    const proposals = proposeAutoPairings(
      unpaired,
      candidates.map(p => ({ id: p.id, referenceCode: p.referenceCode, title: p.title })),
    );

    if (proposals.length === 0) {
      alert(this.i18n.instant('viewer3d.autoPairNone'));
      return;
    }

    this.autoPairPreview.set(proposals);
    this.autoPairRejected.set(new Set());
  }

  toggleProposal(meshName: string): void {
    const next = new Set(this.autoPairRejected());
    if (next.has(meshName)) next.delete(meshName);
    else                    next.add(meshName);
    this.autoPairRejected.set(next);
  }

  /** Apply only the proposals the admin has NOT unchecked. */
  applyAutoPair(): void {
    const proposals = this.autoPairPreview();
    if (!proposals) return;

    // Snapshot the current state so the admin can undo
    this.autoPairSnapshot = new Map(this.draft());

    const next = new Map(this.draft());
    const rejected = this.autoPairRejected();
    let applied = 0;
    proposals.forEach(p => {
      if (rejected.has(p.meshName)) return;
      next.set(p.meshName, p.propertyId);
      applied++;
    });

    if (applied > 0) {
      this.draft.set(next);
      this.isDirty.set(true);
      this.canUndoAutoPair.set(true);
      this.paintAllMeshes();
    }
    this.autoPairPreview.set(null);
    this.autoPairRejected.set(new Set());
  }

  cancelAutoPair(): void {
    this.autoPairPreview.set(null);
    this.autoPairRejected.set(new Set());
  }

  undoAutoPair(): void {
    if (!this.autoPairSnapshot) return;
    this.draft.set(new Map(this.autoPairSnapshot));
    this.autoPairSnapshot = null;
    this.canUndoAutoPair.set(false);
    this.paintAllMeshes();
  }

  /** Localised, short label for a proposal's match strategy. */
  strategyLabel(s: PairStrategy): string {
    switch (s) {
      case 'exact':    return this.i18n.instant('viewer3d.matchExact');
      case 'contains': return this.i18n.instant('viewer3d.matchContains');
      case 'suffix':   return this.i18n.instant('viewer3d.matchSuffix');
      case 'trailing': return this.i18n.instant('viewer3d.matchTrailing');
      default:         return s;
    }
  }

  strategyClass(s: PairStrategy): string {
    return `match-strategy match-${s}`;
  }

  // ── Save ───────────────────────────────────────────────────────────────────

  save(): void {
    this.saving.set(true);
    this.saved.set(false);

    const payload: Array<{
      meshId:           string;
      propertyId:       string;
      immeubleMeshId?:  string | null;
      trancheMeshId?:   string | null;
    }> = [];

    this.draft().forEach((propertyId, meshId) => {
      if (!propertyId) return;
      const hier = this.getHierarchy(meshId);
      payload.push({
        meshId,
        propertyId,
        immeubleMeshId: hier.immeubleMeshId,
        trancheMeshId:  hier.trancheMeshId,
      });
    });

    this.subs.add(
      this.api.updateMappings(this.projetId, payload).subscribe({
        next: () => {
          this.saving.set(false);
          this.saved.set(true);
          this.isDirty.set(false);
          this.autoPairSnapshot = null;
          this.canUndoAutoPair.set(false);
          setTimeout(() => this.saved.set(false), 2500);
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err?.error?.message ?? 'Erreur lors de l\'enregistrement.');
        },
      })
    );
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  propertyLabel(id: string | null | undefined): string {
    if (!id) return '';
    const p = this.properties().find(x => x.id === id);
    if (!p) return id.substring(0, 8);
    return `${p.referenceCode} — ${p.title}`;
  }

  /**
   * Properties available to pair to `meshName`:
   * unused properties + the one currently paired to this mesh (so it stays selectable).
   */
  availableForMesh(meshName: string): Property[] {
    const current = this.draft().get(meshName) ?? null;
    const usedElsewhere = new Set<string>();
    this.draft().forEach((propId, mesh) => {
      if (propId && mesh !== meshName) usedElsewhere.add(propId);
    });
    return this.properties().filter(p => !usedElsewhere.has(p.id) || p.id === current);
  }

  /**
   * For the per-row hierarchy picker — every mesh is technically a valid parent
   * (including the row's own mesh, though picking yourself is silly, so we
   * filter it out).  Building/tranche candidates surface first for usability.
   */
  parentCandidates(meshName: string, kind: 'building' | 'tranche'): string[] {
    const all = this.allMeshes().filter(m => m !== meshName);
    const heuristics = kind === 'building' ? this.buildingCandidates() : this.trancheCandidates();
    const heurSet = new Set(heuristics);
    // Heuristic matches first, then everything else
    return [...heuristics, ...all.filter(m => !heurSet.has(m))];
  }

  isHeuristicCandidate(meshName: string, kind: 'building' | 'tranche'): boolean {
    return kind === 'building' ? looksLikeBuilding(meshName) : looksLikeTranche(meshName);
  }

  proposalForMesh(meshName: string): PairProposal | undefined {
    return this.autoPairPreview()?.find(p => p.meshName === meshName);
  }

  isProposalAccepted(meshName: string): boolean {
    return !this.autoPairRejected().has(meshName);
  }

  acceptedProposalCount(): number {
    const proposals = this.autoPairPreview();
    if (!proposals) return 0;
    const rejected = this.autoPairRejected();
    return proposals.filter(p => !rejected.has(p.meshName)).length;
  }

  private cssEscape(s: string): string {
    return typeof CSS !== 'undefined' && CSS.escape ? CSS.escape(s) : s.replace(/[^a-z0-9_-]/gi, '_');
  }

  @HostListener('window:beforeunload', ['$event'])
  warnOnLeave(e: BeforeUnloadEvent): void {
    if (this.isDirty()) {
      e.preventDefault();
      e.returnValue = '';
    }
  }
}
