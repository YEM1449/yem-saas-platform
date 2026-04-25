import { Injectable } from '@angular/core';
import * as THREE from 'three';
import { Lot3dMappingEntry } from '../models/project-3d-model.model';
import { LotStatusSnapshot, LOT_STATUS_COLORS, LotDisplayStatus } from '../models/lot-3d-status.model';

/**
 * Manages the bidirectional lookup between GLB mesh names and property (lot) data,
 * and applies status-based colour coding to scene meshes.
 *
 * Design: colour updates patch only the affected meshes' material.color —
 * no full scene rebuild or dispose/recreate cycle needed.
 */
@Injectable({ providedIn: 'root' })
export class LotMappingService {

  /** meshId → mapping entry */
  private byMeshId  = new Map<string, Lot3dMappingEntry>();
  /** meshId → Three.js Mesh */
  private meshByMeshId = new Map<string, THREE.Mesh>();
  /** Original material colours preserved for highlight/reset */
  private originalColors = new Map<string, THREE.Color>();

  /**
   * Registers the mesh→lot mapping table and the actual scene meshes.
   * Call once after the GLB is loaded and the scene traversal is complete.
   */
  applyMappings(mappings: Lot3dMappingEntry[], meshes: THREE.Mesh[]): void {
    this.byMeshId.clear();
    this.meshByMeshId.clear();
    this.originalColors.clear();

    mappings.forEach(m => this.byMeshId.set(m.meshId, m));

    meshes.forEach(mesh => {
      const meshId = mesh.name;
      if (this.byMeshId.has(meshId)) {
        this.meshByMeshId.set(meshId, mesh);
        // Ensure we have a MeshStandardMaterial for colour control
        if (!Array.isArray(mesh.material)) {
          this.ensureStandardMaterial(mesh);
          const mat = mesh.material as THREE.MeshStandardMaterial;
          this.originalColors.set(meshId, mat.color.clone());
        }
      }
    });
  }

  /**
   * Updates mesh colours from a status snapshot.
   * Only touches meshes whose status or mapping exists — leaves unmapped meshes as-is.
   * No full re-render triggered — Three.js will pick up the colour on the next RAF frame.
   */
  updateColors(statuses: LotStatusSnapshot[]): void {
    const statusMap = new Map(statuses.map(s => [s.meshId, s]));

    this.meshByMeshId.forEach((mesh, meshId) => {
      const status = statusMap.get(meshId);
      const hexColor = status
        ? LOT_STATUS_COLORS[status.statut as LotDisplayStatus] ?? LOT_STATUS_COLORS.DISPONIBLE
        : LOT_STATUS_COLORS.DISPONIBLE;

      const mat = mesh.material as THREE.MeshStandardMaterial;
      mat.color.set(hexColor);
    });
  }

  /** Returns the mapping entry for a given mesh, or undefined if unmapped. */
  getMappingForMesh(mesh: THREE.Mesh): Lot3dMappingEntry | undefined {
    return this.byMeshId.get(mesh.name);
  }

  /** Returns the status snapshot for a given meshId (from the last updateColors call). */
  getStatusForMeshId(meshId: string, statuses: LotStatusSnapshot[]): LotStatusSnapshot | undefined {
    return statuses.find(s => s.meshId === meshId);
  }

  /** Highlight a mesh on hover (lighten + emissive). */
  highlight(mesh: THREE.Mesh): void {
    const mat = mesh.material as THREE.MeshStandardMaterial;
    mat.emissive?.set(0x222222);
  }

  /** Remove highlight from a mesh. */
  unhighlight(mesh: THREE.Mesh): void {
    const mat = mesh.material as THREE.MeshStandardMaterial;
    mat.emissive?.set(0x000000);
  }

  // ── private ───────────────────────────────────────────────────────────────

  private ensureStandardMaterial(mesh: THREE.Mesh): void {
    if (!(mesh.material instanceof THREE.MeshStandardMaterial)) {
      const prev = mesh.material as THREE.Material;
      mesh.material = new THREE.MeshStandardMaterial({ color: 0x888888 });
      prev.dispose();
    }
  }
}
