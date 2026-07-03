import { Component, ElementRef, ViewChild, AfterViewInit, OnDestroy, ChangeDetectionStrategy, signal } from '@angular/core';
// @ts-ignore
import * as THREE from 'three';

const CYCLE_SECONDS = 6;
const LAYER_COUNT = 6;
const LAYER_HEIGHT = 0.32;
const BUILD_END = CYCLE_SECONDS * 0.6;
const SHIFT_END = CYCLE_SECONDS * 0.75;
const TOP_LAYER_INDICES = [4, 5];
const ACCENT_COLOR = 0x00c8c0;

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

@Component({
  selector: 'app-failed-print-scene',
  imports: [],
  template: `
    <canvas #canvas class="failed-print-canvas" aria-hidden="true"></canvas>
    @if (sceneUnsupported()) {
      <svg class="failed-print-fallback" viewBox="0 0 200 200" aria-hidden="true">
        <rect x="70" y="150" width="60" height="14" rx="2" fill="var(--accent)" opacity="0.25"/>
        <rect x="76" y="128" width="48" height="16" rx="2" fill="var(--accent)" opacity="0.5"/>
        <path d="M80 128 C60 100, 130 90, 100 60 C80 40, 140 30, 110 10"
              stroke="var(--accent)" stroke-width="6" fill="none" stroke-linecap="round" opacity="0.7"/>
      </svg>
    }
  `,
  styles: [`
    :host {
      position: absolute;
      inset: 0;
      display: block;
    }
    .failed-print-canvas {
      display: block;
      width: 100%;
      height: 100%;
    }
    .failed-print-fallback {
      position: absolute;
      top: 50%;
      left: 50%;
      width: min(60vw, 320px);
      transform: translate(-50%, -50%);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FailedPrintSceneComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas', { static: false }) canvasRef!: ElementRef<HTMLCanvasElement>;

  sceneUnsupported = signal(false);
  motionReduced = signal(false);

  layers: THREE.Mesh[] = [];
  nozzle!: THREE.Mesh;
  spaghetti!: THREE.Mesh;

  private scene?: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private layerMaterial?: THREE.MeshStandardMaterial;
  private clock = new THREE.Clock();
  private animationId: number | null = null;
  private resizeObserver?: ResizeObserver;

  ngAfterViewInit(): void {
    this.motionReduced.set(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false);
    this.buildModel();

    try {
      this.initRenderer();
    } catch (err) {
      console.error('Failed-print scene unavailable:', err);
      this.sceneUnsupported.set(true);
      return;
    }

    if (this.motionReduced()) {
      this.updateModel(SHIFT_END + (CYCLE_SECONDS - SHIFT_END) * 0.2);
      this.renderer.render(this.scene!, this.camera);
    } else {
      this.animate();
    }
  }

  /** Pure THREE.js object-graph construction — no GL context needed, always succeeds. */
  buildModel(): void {
    this.scene = this.scene ?? new THREE.Scene();
    this.layers = [];

    this.layerMaterial = new THREE.MeshStandardMaterial({ color: ACCENT_COLOR, roughness: 0.45, metalness: 0.1 });

    for (let i = 0; i < LAYER_COUNT; i++) {
      const geometry = new THREE.CylinderGeometry(1.6, 1.6, LAYER_HEIGHT, 32);
      const layer = new THREE.Mesh(geometry, this.layerMaterial);
      layer.position.y = i * LAYER_HEIGHT;
      layer.scale.y = 0;
      this.scene.add(layer);
      this.layers.push(layer);
    }

    const nozzleMaterial = new THREE.MeshStandardMaterial({ color: ACCENT_COLOR, roughness: 0.3, metalness: 0.2 });
    this.nozzle = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.25, 0.5), nozzleMaterial);
    this.nozzle.position.y = LAYER_COUNT * LAYER_HEIGHT + 0.6;
    this.scene.add(this.nozzle);

    const top = LAYER_COUNT * LAYER_HEIGHT;
    const curve = new THREE.CatmullRomCurve3([
      new THREE.Vector3(0, top + 0.3, 0),
      new THREE.Vector3(0.3, top - 0.1, 0.15),
      new THREE.Vector3(0.15, top - 0.5, -0.2),
      new THREE.Vector3(0.5, top - 0.9, 0.1),
      new THREE.Vector3(0.1, top - 1.3, 0.3),
      new THREE.Vector3(0.4, top - 1.6, -0.1)
    ]);
    const spaghettiMaterial = new THREE.MeshBasicMaterial({ color: ACCENT_COLOR, transparent: true, opacity: 0 });
    this.spaghetti = new THREE.Mesh(new THREE.TubeGeometry(curve, 64, 0.08, 8, false), spaghettiMaterial);
    this.spaghetti.visible = false;
    this.scene.add(this.spaghetti);
  }

  /** Pure animation math — deterministic given t, no GL context needed. */
  updateModel(t: number): void {
    const cycleTime = t % CYCLE_SECONDS;
    const buildDurationPerLayer = BUILD_END / LAYER_COUNT;

    for (let i = 0; i < LAYER_COUNT; i++) {
      const layerStart = i * buildDurationPerLayer;
      this.layers[i].scale.y = clamp01((cycleTime - layerStart) / buildDurationPerLayer);
      // A mesh scaled to exactly 0 on one axis has a singular (non-invertible) normal
      // matrix, which renders as solid black under lit materials — hide it instead.
      this.layers[i].visible = this.layers[i].scale.y > 0;
    }

    const shiftProgress = clamp01((cycleTime - BUILD_END) / (SHIFT_END - BUILD_END));
    TOP_LAYER_INDICES.forEach((layerIndex, order) => {
      const magnitude = order + 1;
      this.layers[layerIndex].rotation.z = shiftProgress * 0.35 * magnitude;
      this.layers[layerIndex].position.x = shiftProgress * 0.12 * magnitude;
    });

    const unravelProgress = clamp01((cycleTime - SHIFT_END) / (CYCLE_SECONDS - SHIFT_END));
    if (cycleTime >= SHIFT_END) {
      TOP_LAYER_INDICES.forEach(layerIndex => {
        this.layers[layerIndex].scale.y = Math.max(0, 1 - unravelProgress);
        this.layers[layerIndex].visible = this.layers[layerIndex].scale.y > 0;
      });
    }

    (this.spaghetti.material as THREE.MeshBasicMaterial).opacity = unravelProgress;
    this.spaghetti.visible = unravelProgress > 0;
    this.spaghetti.rotation.y = unravelProgress * 0.6 + Math.sin(cycleTime * 2) * 0.05;

    const jitterAmplitude = 0.02 + unravelProgress * 0.10;
    this.nozzle.position.x = Math.sin(cycleTime * 9) * jitterAmplitude;
    this.nozzle.position.z = Math.cos(cycleTime * 7) * jitterAmplitude;
  }

  /** GL-dependent setup — throws in environments without WebGL (caught by the caller). */
  private initRenderer(): void {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth || window.innerWidth;
    const height = canvas.clientHeight || window.innerHeight;

    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    this.camera.position.set(0, 6.5, 13);
    this.camera.lookAt(0, -1.6, 0);

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setSize(width, height, false);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));

    const key = new THREE.DirectionalLight(0xffffff, 1.1);
    key.position.set(2, 3, 2);
    this.scene!.add(key);
    this.scene!.add(new THREE.HemisphereLight(0xffffff, 0x0a1a1a, 0.7));

    this.resizeObserver = new ResizeObserver(() => this.onResize());
    this.resizeObserver.observe(canvas.parentElement ?? canvas);
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    this.updateModel(this.clock.getElapsedTime());
    this.renderer.render(this.scene!, this.camera);
  };

  private onResize(): void {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    if (width === 0 || height === 0) return;

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);

    if (this.motionReduced()) {
      this.renderer.render(this.scene!, this.camera);
    }
  }

  ngOnDestroy(): void {
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
    }
    this.resizeObserver?.disconnect();
    this.layers.forEach(layer => layer.geometry.dispose());
    this.layerMaterial?.dispose();
    this.spaghetti?.geometry.dispose();
    (this.spaghetti?.material as THREE.Material)?.dispose();
    this.nozzle?.geometry.dispose();
    (this.nozzle?.material as THREE.Material)?.dispose();
    this.renderer?.dispose();
  }
}
