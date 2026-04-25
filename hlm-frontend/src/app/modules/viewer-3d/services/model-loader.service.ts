import { Injectable } from '@angular/core';
import * as THREE from 'three';
import { GLTFLoader, GLTF } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DRACOLoader }       from 'three/examples/jsm/loaders/DRACOLoader.js';
import { Observable } from 'rxjs';
import { LoadProgress } from '../models/project-3d-model.model';

export type LoadEvent =
  | { type: 'progress'; progress: LoadProgress }
  | { type: 'loaded';   gltf: GLTF };

/**
 * Loads GLB models via GLTFLoader + DRACOLoader.
 * Draco decoder WASM files are served from /assets/draco/ (copied from node_modules at build time).
 */
@Injectable({ providedIn: 'root' })
export class ModelLoaderService {

  private readonly loader: GLTFLoader;

  constructor() {
    const draco = new DRACOLoader();
    // Draco decoder files copied from node_modules/three/examples/jsm/libs/draco/gltf/ by angular.json
    draco.setDecoderPath('/assets/draco/');
    draco.setDecoderConfig({ type: 'js' });

    this.loader = new GLTFLoader();
    this.loader.setDRACOLoader(draco);
  }

  /**
   * Loads a GLB file from the given URL.
   * Emits `progress` events while downloading, then a final `loaded` event with the GLTF result.
   * Falls back to plain GLTFLoader if the model is not Draco-compressed.
   */
  load(url: string): Observable<LoadEvent> {
    return new Observable(observer => {
      this.loader.load(
        url,
        (gltf) => {
          observer.next({ type: 'loaded', gltf });
          observer.complete();
        },
        (xhr) => {
          const loaded = xhr.loaded;
          const total  = xhr.total;
          observer.next({
            type: 'progress',
            progress: { loaded, total, ratio: total > 0 ? loaded / total : 0 },
          });
        },
        (err) => observer.error(err)
      );
    });
  }

  /**
   * Walks the loaded GLTF scene and returns all Mesh objects.
   * Assigns the mesh name as userData.meshId for raycasting lookups.
   */
  extractMeshes(gltf: GLTF): THREE.Mesh[] {
    const meshes: THREE.Mesh[] = [];
    gltf.scene.traverse(obj => {
      if ((obj as THREE.Mesh).isMesh) {
        const mesh = obj as THREE.Mesh;
        mesh.userData['meshId'] = mesh.name;
        meshes.push(mesh);
      }
    });
    return meshes;
  }
}
