import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TrancheService } from '../tranche.service';

@Component({
  selector: 'app-project-create-wizard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './project-create-wizard.component.html',
  styleUrl: './project-create-wizard.component.scss',
})
export class ProjectCreateWizardComponent {
  private fb     = inject(FormBuilder);
  private svc    = inject(TrancheService);
  private router = inject(Router);

  currentStep = 1;
  readonly TOTAL_STEPS = 5;
  submitting  = false;
  error       = '';

  // expose Math to template
  readonly Math = Math;

  readonly PROJECT_TYPES = [
    { value: 'IMMEUBLES', label: 'Projet Immeubles',
      hint: 'Appartements, Duplex, Commerces — plusieurs bâtiments par tranche' },
    { value: 'VILLAS',    label: 'Projet Villas',
      hint: 'Maisons individuelles avec terrain, option piscine / jardin' },
  ];

  readonly PROPERTY_TYPES = [
    { value: 'APPARTEMENT', label: 'Appartement' },
    { value: 'STUDIO',      label: 'Studio' },
    { value: 'T2',          label: 'T2' },
    { value: 'T3',          label: 'T3' },
    { value: 'DUPLEX',      label: 'Duplex' },
    { value: 'COMMERCE',    label: 'Local commercial' },
    { value: 'VILLA',       label: 'Villa' },
  ];

  get isVillaProject(): boolean {
    return this.step1.get('projectType')?.value === 'VILLAS';
  }

  readonly ORIENTATIONS = [
    'SUD', 'NORD', 'EST', 'OUEST',
    'SUD-OUEST', 'SUD-EST', 'NORD-OUEST', 'NORD-EST',
  ];

  readonly NAMING_OPTIONS = [
    { value: 'LETTRE',  label: 'Lettres (A, B, C…)',   hint: 'Standard — recommandé' },
    { value: 'CHIFFRE', label: 'Chiffres (1, 2, 3…)',  hint: 'Alternative simple' },
    { value: 'CUSTOM',  label: 'Noms personnalisés',   hint: 'Le Jasmin, La Lavande…' },
  ];

  readonly REF_PATTERNS = [
    { value: 'BUILDING_FLOOR_UNIT', label: 'Bâtiment + Étage + N° (A101, B203)', hint: 'Multi-bâtiments' },
    { value: 'FLOOR_UNIT',          label: 'Étage + N° (101, 203)',              hint: 'Mono-bâtiment' },
    { value: 'SEQUENTIAL',          label: 'Séquentiel (APT-001…)',              hint: 'Numérotation continue' },
  ];

  readonly STATUT_LABELS: Record<string, string> = {
    EN_PREPARATION:       'En préparation',
    EN_COMMERCIALISATION: 'En commercialisation',
    EN_TRAVAUX:           'En travaux',
    ACHEVEE:              'Achevée',
    LIVREE:               'Livrée',
  };

  // ── Step 1: Project info ───────────────────────────────────────────────────
  step1 = this.fb.group({
    projectType:        ['IMMEUBLES', Validators.required],
    projectNom:         ['', Validators.required],
    projectDescription: [''],
    projectAdresse:     [''],
    projectVille:       ['', Validators.required],
    projectCodePostal:  [''],
    // Professional fields
    maitreOuvrage:                    [''],
    dateOuvertureCommercialisation:   [''],
    tvaTaux:                          [null as number | null],
    surfaceTerrainM2:                 [null as number | null],
    prixMoyenM2Cible:                 [null as number | null],
  });

  // ── Step 2: Tranches ───────────────────────────────────────────────────────
  step2 = this.fb.group({
    tranches: this.fb.array([this.createTrancheGroup(1)]),
  });

  // ── Step 3: Building naming + buildings per tranche ────────────────────────
  step3 = this.fb.group({
    buildingNaming:     ['LETTRE'],
    buildingPrefix:     ['Bâtiment'],
    unitRefPattern:     ['BUILDING_FLOOR_UNIT'],
    unitPrefix:         [''],
    rdcLabel:           ['RDC'],
    includeParking:     [false],
    parkingPrefix:      ['P'],
    parkingUnderground: [true],
  });

  // ── Accessors ──────────────────────────────────────────────────────────────
  get tranches(): FormArray { return this.step2.get('tranches') as FormArray; }

  createTrancheGroup(numero: number): FormGroup {
    return this.fb.group({
      numero:              [numero],
      nom:                 [''],
      dateLivraisonPrevue: ['', Validators.required],
      dateDebutTravaux:    [''],
      permisConstruireRef: [''],
      buildings:           this.fb.array([]),
    });
  }

  addTranche(): void {
    if (this.tranches.length >= 5) return;
    this.tranches.push(this.createTrancheGroup(this.tranches.length + 1));
  }

  removeTranche(i: number): void {
    if (this.tranches.length <= 1) return;
    this.tranches.removeAt(i);
    this.tranches.controls.forEach((t, idx) => t.get('numero')?.setValue(idx + 1));
  }

  getTrancheBuildings(ti: number): FormArray {
    return this.tranches.at(ti).get('buildings') as FormArray;
  }

  createBuildingGroup(globalOrder: number): FormGroup {
    return this.fb.group({
      buildingOrder: [globalOrder],
      customName:    [''],
      floorCount:    [5, [Validators.required, Validators.min(1), Validators.max(30)]],
      hasRdc:        [true],
      rdcType:       ['COMMERCE'],
      rdcUnitCount:  [1],
      hasParking:    [false],
      parkingCount:  [0],
      floors:        this.fb.array([]),
    });
  }

  addBuilding(ti: number): void {
    const globalOrder = this.totalBuildingCount;
    if (globalOrder >= 26) return;
    this.getTrancheBuildings(ti).push(this.createBuildingGroup(globalOrder));
  }

  removeBuilding(ti: number, bi: number): void {
    this.getTrancheBuildings(ti).removeAt(bi);
    this.recomputeGlobalOrders();
  }

  recomputeGlobalOrders(): void {
    let order = 0;
    this.tranches.controls.forEach(t => {
      (t.get('buildings') as FormArray).controls.forEach(b => {
        b.get('buildingOrder')?.setValue(order++);
      });
    });
  }

  get totalBuildingCount(): number {
    return this.tranches.controls.reduce(
      (sum, t) => sum + (t.get('buildings') as FormArray).length, 0
    );
  }

  getFloors(ti: number, bi: number): FormArray {
    return this.getTrancheBuildings(ti).at(bi).get('floors') as FormArray;
  }

  /** Called when moving Step 3 → Step 4: auto-populate floor forms. */
  buildFloorForms(): void {
    const isVilla = this.isVillaProject;
    this.tranches.controls.forEach((t, ti) => {
      (t.get('buildings') as FormArray).controls.forEach((b, bi) => {
        const floorsArray = this.getFloors(ti, bi);
        floorsArray.clear();
        if (isVilla) {
          // One "row" per building representing identical villas
          floorsArray.push(this.createFloorGroup(0, 'VILLA', b.get('rdcUnitCount')?.value ?? 1));
        } else {
          const floorCount = b.get('floorCount')?.value ?? 5;
          const hasRdc     = b.get('hasRdc')?.value;
          const rdcType    = b.get('rdcType')?.value;
          const rdcCount   = b.get('rdcUnitCount')?.value ?? 1;
          if (hasRdc && rdcType && rdcType !== 'NONE') {
            floorsArray.push(this.createFloorGroup(0, rdcType === 'MIXTE' ? 'COMMERCE' : rdcType, rdcCount));
          }
          for (let f = 1; f <= floorCount; f++) {
            floorsArray.push(this.createFloorGroup(f, 'APPARTEMENT', 4));
          }
        }
      });
    });
  }

  createFloorGroup(floor: number, type: string, count: number): FormGroup {
    return this.fb.group({
      floorNumber:  [floor],
      propertyType: [type],
      unitCount:    [count, [Validators.required, Validators.min(1), Validators.max(20)]],
      surfaceMin:   [null as number | null],
      surfaceMax:   [null as number | null],
      prixBase:     [null as number | null],
      orientation:  ['SUD'],
      landAreaSqm:  [null as number | null],
      bedrooms:     [null as number | null],
      bathrooms:    [null as number | null],
      hasPool:      [false],
      hasGarden:    [false],
    });
  }

  getBuildingLabel(order: number, customName?: string | null): string {
    const naming = this.step3.get('buildingNaming')?.value;
    if (naming === 'CHIFFRE') return String(order + 1);
    if (naming === 'CUSTOM' && customName) return customName;
    return String.fromCharCode(65 + order);
  }

  getFloorLabel(floor: number): string {
    const rdcLabel = this.step3.get('rdcLabel')?.value || 'RDC';
    if (floor === -1) return 'Sous-sol';
    if (floor === 0)  return rdcLabel;
    if (floor === 1)  return '1er étage';
    return `${floor}ème étage`;
  }

  // ── Preview for Step 5 ─────────────────────────────────────────────────────
  get preview() {
    const prefix = this.step3.get('buildingPrefix')?.value ?? 'Bâtiment';
    const trancheList: any[] = [];
    const typeCount: Record<string, number> = {};
    let totalUnits = 0;
    let totalValue = 0;

    this.tranches.controls.forEach((t, ti) => {
      const bList: any[] = [];
      let trancheUnits = 0;

      (t.get('buildings') as FormArray).controls.forEach((b, bi) => {
        const order  = b.get('buildingOrder')?.value ?? bi;
        const custom = b.get('customName')?.value;
        const label  = this.getBuildingLabel(order, custom);
        const nom    = `${prefix} ${label}`;
        const floorList: any[] = [];
        let bUnits = 0;

        const parkCount = b.get('parkingCount')?.value ?? 0;
        if (b.get('hasParking')?.value && parkCount > 0) {
          floorList.push({ label: 'Sous-sol', type: 'PARKING', count: parkCount });
          typeCount['PARKING'] = (typeCount['PARKING'] ?? 0) + parkCount;
          bUnits += parkCount;
        }

        (b.get('floors') as FormArray).controls.forEach(f => {
          const fn    = f.get('floorNumber')?.value;
          const type  = f.get('propertyType')?.value;
          const count = f.get('unitCount')?.value ?? 0;
          const prix  = f.get('prixBase')?.value ?? 0;
          floorList.push({ label: this.getFloorLabel(fn), type, count });
          typeCount[type] = (typeCount[type] ?? 0) + count;
          bUnits += count;
          totalValue += count * prix;
        });

        trancheUnits += bUnits;
        bList.push({ nom, floors: floorList, unitCount: bUnits });
      });

      totalUnits += trancheUnits;
      trancheList.push({
        numero: t.get('numero')?.value,
        nom: t.get('nom')?.value || `Tranche ${t.get('numero')?.value}`,
        date: t.get('dateLivraisonPrevue')?.value,
        buildings: bList,
        unitCount: trancheUnits,
      });
    });

    return {
      tranches: trancheList,
      totals: {
        trancheCount:   trancheList.length,
        buildingCount:  this.totalBuildingCount,
        unitCount:      totalUnits,
        byType:         typeCount,
        estimatedValue: totalValue,
      },
    };
  }

  typeEntries(byType: Record<string, number>) {
    return Object.entries(byType);
  }

  // ── Navigation ─────────────────────────────────────────────────────────────
  next(): void {
    if (this.currentStep === 3) this.buildFloorForms();
    if (this.currentStep < this.TOTAL_STEPS) this.currentStep++;
  }

  prev(): void {
    if (this.currentStep > 1) this.currentStep--;
  }

  canGoNext(): boolean {
    switch (this.currentStep) {
      case 1: return this.step1.valid;
      case 2: return this.tranches.valid && this.tranches.length > 0;
      case 3: return this.totalBuildingCount > 0;
      default: return true;
    }
  }

  // ── Submit ─────────────────────────────────────────────────────────────────
  generate(): void {
    if (this.submitting) return;
    this.submitting = true;
    this.error      = '';

    const s1 = this.step1.value;
    const s3 = this.step3.value;

    const request = {
      projectNom:         s1.projectNom,
      projectDescription: s1.projectDescription || null,
      projectAdresse:     s1.projectAdresse || null,
      projectVille:       s1.projectVille,
      projectCodePostal:  s1.projectCodePostal || null,
      maitreOuvrage:                  s1.maitreOuvrage || null,
      dateOuvertureCommercialisation: s1.dateOuvertureCommercialisation || null,
      tvaTaux:                        s1.tvaTaux ?? null,
      surfaceTerrainM2:               s1.surfaceTerrainM2 ?? null,
      prixMoyenM2Cible:               s1.prixMoyenM2Cible ?? null,
      buildingNaming:     s3.buildingNaming,
      buildingPrefix:     s3.buildingPrefix,
      unitRefPattern:     s3.unitRefPattern,
      unitPrefix:         s3.unitPrefix || null,
      rdcLabel:           s3.rdcLabel,
      includeParking:     s3.includeParking,
      parkingPrefix:      s3.parkingPrefix,
      parkingUnderground: s3.parkingUnderground,
      tranches: this.tranches.controls.map(t => ({
        numero:              t.get('numero')?.value,
        nom:                 t.get('nom')?.value || null,
        dateLivraisonPrevue: t.get('dateLivraisonPrevue')?.value,
        dateDebutTravaux:    t.get('dateDebutTravaux')?.value || null,
        permisConstruireRef: t.get('permisConstruireRef')?.value || null,
        buildings: (t.get('buildings') as FormArray).controls.map(b => ({
          buildingOrder: b.get('buildingOrder')?.value,
          customName:    b.get('customName')?.value || null,
          floorCount:    b.get('floorCount')?.value,
          hasRdc:        b.get('hasRdc')?.value,
          rdcType:       b.get('rdcType')?.value,
          rdcUnitCount:  b.get('rdcUnitCount')?.value,
          hasParking:    b.get('hasParking')?.value,
          parkingCount:  b.get('parkingCount')?.value,
          floors: (b.get('floors') as FormArray).controls.map(f => ({
            floorNumber:  f.get('floorNumber')?.value,
            propertyType: f.get('propertyType')?.value,
            unitCount:    f.get('unitCount')?.value,
            surfaceMin:   f.get('surfaceMin')?.value,
            surfaceMax:   f.get('surfaceMax')?.value,
            prixBase:     f.get('prixBase')?.value,
            orientation:  f.get('orientation')?.value,
            landAreaSqm:  f.get('landAreaSqm')?.value ?? null,
            bedrooms:     f.get('bedrooms')?.value   ?? null,
            bathrooms:    f.get('bathrooms')?.value  ?? null,
            hasPool:      f.get('hasPool')?.value    ?? false,
            hasGarden:    f.get('hasGarden')?.value  ?? false,
          })),
        })),
      })),
    };

    this.svc.generate(request as any).subscribe({
      next: res => {
        this.submitting = false;
        this.router.navigate(['/app/projects', res.projectId],
          { queryParams: { generated: 'true' } });
      },
      error: err => {
        this.submitting = false;
        this.error = err?.error?.message ?? 'Une erreur est survenue.';
      },
    });
  }
}
