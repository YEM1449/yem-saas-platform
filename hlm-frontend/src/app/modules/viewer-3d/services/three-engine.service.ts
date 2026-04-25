import { Injectable, NgZone, OnDestroy } from '@angular/core';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { Subject } from 'rxjs';

export interface PointerEvent3D {
  mesh:    THREE.Mesh;
  screenX: number;
  screenY: number;
}

/**
 * Singleton Three.js engine.
 * Owns the WebGLRenderer, scene, camera, OrbitControls and the RAF loop.
 * All Three.js work runs outside Angular's zone to avoid unnecessary CD cycles.
 */
@Injectable({ providedIn: 'root' })
export class ThreeEngineService implements OnDestroy {

  scene!:    THREE.Scene;
  camera!:   THREE.PerspectiveCamera;
  renderer!: THREE.WebGLRenderer;
  controls!: OrbitControls;

  /** Fires on each pointer-move with the first intersected Mesh (or null). */
  readonly hover$ = new Subject<PointerEvent3D | null>();
  /** Fires when the user clicks a Mesh. */
  readonly click$ = new Subject<PointerEvent3D>();

  private rafId       = 0;
  private canvas!:    HTMLCanvasElement;
  private raycaster   = new THREE.Raycaster();
  private pointer     = new THREE.Vector2();
  private meshes:     THREE.Mesh[] = [];
  private resizeObs!: ResizeObserver;
  private boundPointerMove!: (e: MouseEvent) => void;
  private boundClick!:       (e: MouseEvent) => void;
  private boundVisChange!:   () => void;

  constructor(private zone: NgZone) {}

  /** Attach engine to a canvas element. Call once when the component mounts. */
  init(canvas: HTMLCanvasElement): void {
    this.canvas = canvas;

    // ── Renderer ─────────────────────────────────────────────────────────────
    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha:     true,
      powerPreference: 'high-performance',
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
    this.renderer.setSize(canvas.clientWidth, canvas.clientHeight);

    // ── Scene + Camera ────────────────────────────────────────────────────────
    this.scene  = new THREE.Scene();
    this.scene.background = new THREE.Color(0xF8FAFC);

    this.camera = new THREE.PerspectiveCamera(45, canvas.clientWidth / canvas.clientHeight, 0.1, 1000);
    this.camera.position.set(0, 50, 100);

    // ── Lights ────────────────────────────────────────────────────────────────
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.6));
    const dir = new THREE.DirectionalLight(0xffffff, 0.8);
    dir.position.set(50, 100, 50);
    dir.castShadow = false; // disabled — too expensive on mobile
    this.scene.add(dir);

    // ── Controls ──────────────────────────────────────────────────────────────
    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping  = true;
    this.controls.dampingFactor  = 0.05;
    this.controls.screenSpacePanning = true;

    // ── Events ────────────────────────────────────────────────────────────────
    this.boundPointerMove = (e) => this.onPointerMove(e);
    this.boundClick       = (e) => this.onCanvasClick(e);
    this.boundVisChange   = ()  => this.onVisibilityChange();
    canvas.addEventListener('pointermove', this.boundPointerMove);
    canvas.addEventListener('click',       this.boundClick);
    document.addEventListener('visibilitychange', this.boundVisChange);

    // ── Resize ────────────────────────────────────────────────────────────────
    this.resizeObs = new ResizeObserver(() => this.onResize());
    this.resizeObs.observe(canvas.parentElement ?? canvas);

    // ── RAF (outside Angular zone to avoid CD on every frame) ─────────────────
    this.zone.runOutsideAngular(() => this.startLoop());
  }

  /** Register meshes that should participate in raycasting. */
  registerMeshes(meshes: THREE.Mesh[]): void {
    this.meshes = meshes;
  }

  /** Cleanly disposes everything — call from ngOnDestroy of the host component. */
  dispose(): void {
    cancelAnimationFrame(this.rafId);
    this.canvas?.removeEventListener('pointermove', this.boundPointerMove);
    this.canvas?.removeEventListener('click',       this.boundClick);
    document.removeEventListener('visibilitychange', this.boundVisChange);
    this.resizeObs?.disconnect();
    this.controls?.dispose();

    // Traverse scene and dispose geometries + materials
    this.scene?.traverse(obj => {
      if ((obj as THREE.Mesh).isMesh) {
        const mesh = obj as THREE.Mesh;
        mesh.geometry?.dispose();
        const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
        mats.forEach(m => (m as THREE.Material).dispose());
      }
    });

    this.renderer?.dispose();
    this.hover$.complete();
    this.click$.complete();
  }

  ngOnDestroy(): void {
    this.dispose();
  }

  // ── private ───────────────────────────────────────────────────────────────

  private startLoop(): void {
    const tick = () => {
      this.rafId = requestAnimationFrame(tick);
      this.controls?.update();
      this.renderer?.render(this.scene, this.camera);
    };
    tick();
  }

  private onResize(): void {
    const w = this.canvas.clientWidth;
    const h = this.canvas.clientHeight;
    if (w === 0 || h === 0) return;
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h);
  }

  private onPointerMove(e: MouseEvent): void {
    const rect = this.canvas.getBoundingClientRect();
    this.pointer.x =  ((e.clientX - rect.left)  / rect.width)  * 2 - 1;
    this.pointer.y = -((e.clientY - rect.top)   / rect.height) * 2 + 1;

    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hits = this.raycaster.intersectObjects(this.meshes, false);

    this.zone.run(() => {
      if (hits.length > 0) {
        this.hover$.next({ mesh: hits[0].object as THREE.Mesh, screenX: e.clientX, screenY: e.clientY });
      } else {
        this.hover$.next(null);
      }
    });
  }

  private onCanvasClick(e: MouseEvent): void {
    const rect = this.canvas.getBoundingClientRect();
    this.pointer.x =  ((e.clientX - rect.left)  / rect.width)  * 2 - 1;
    this.pointer.y = -((e.clientY - rect.top)   / rect.height) * 2 + 1;

    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hits = this.raycaster.intersectObjects(this.meshes, false);

    if (hits.length > 0) {
      this.zone.run(() =>
        this.click$.next({ mesh: hits[0].object as THREE.Mesh, screenX: e.clientX, screenY: e.clientY })
      );
    }
  }

  private onVisibilityChange(): void {
    if (document.hidden) {
      cancelAnimationFrame(this.rafId);
    } else {
      this.zone.runOutsideAngular(() => this.startLoop());
    }
  }
}
