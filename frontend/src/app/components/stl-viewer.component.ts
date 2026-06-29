import { Component, Input, ViewChild, ElementRef, AfterViewInit, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
// @ts-ignore
import * as THREE from 'three';
// @ts-ignore
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

@Component({
  selector: 'app-stl-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stl-viewer-wrapper" [class.fullscreen]="isFullscreen()">
      <div class="stl-viewer-container">
        <div class="viewer-top-bar">
          <span class="file-name">{{ extractFileName(stlUrl) }}</span>
          <div class="viewer-controls">
            <button class="control-btn" (click)="resetView()" title="Resetuj widok">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
                <path d="M3 3v5h5"></path>
              </svg>
            </button>
            <button class="control-btn" (click)="toggleFullscreen()" title="Pełny ekran">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"></path>
              </svg>
            </button>
            <a class="control-btn" [href]="stlUrl" download title="Pobierz">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"></path>
              </svg>
            </a>
          </div>
        </div>
        <canvas #canvas class="stl-canvas"></canvas>
        @if (loading()) {
          <div class="stl-loading">
            <div class="spinner"></div>
            <p>Ładowanie modelu 3D...</p>
          </div>
        }
        @if (error()) {
          <div class="stl-error" role="alert">
            <p>⚠️ {{ error() }}</p>
            <a [href]="stlUrl" target="_blank" class="download-fallback">Pobierz plik</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }

    .stl-viewer-wrapper {
      position: relative;
      width: 100%;
      height: 100%;
      min-height: 300px;
      border-radius: 8px;
      overflow: hidden;
    }

    .stl-viewer-wrapper.fullscreen {
      position: fixed;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      z-index: 9999;
      border-radius: 0;
    }

    .stl-viewer-container {
      display: flex;
      flex-direction: column;
      width: 100%;
      height: 100%;
      background: var(--surface-2, #f5f5f5);
      border: 1px solid var(--border, #e0e0e0);
      border-radius: 8px;
    }

    .stl-viewer-wrapper.fullscreen .stl-viewer-container {
      border-radius: 0;
      border: none;
    }

    .viewer-top-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      background: #fafafa;
      border-bottom: 1px solid #e0e0e0;
      flex-shrink: 0;
    }

    .file-name {
      font-weight: 500;
      color: #333;
      font-size: 14px;
    }

    .viewer-controls {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .control-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      background: #fff;
      border: 1px solid #ddd;
      border-radius: 6px;
      cursor: pointer;
      color: #666;
      transition: all 0.2s;
      padding: 0;
    }

    .control-btn:hover {
      background: #f0f0f0;
      color: #333;
    }

    .stl-canvas {
      display: block;
      flex: 1;
      width: 100%;
      min-height: 0;
      cursor: grab;
    }

    .stl-canvas:active {
      cursor: grabbing;
    }

    .stl-loading, .stl-error {
      position: absolute;
      top: 44px;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background: rgba(255, 255, 255, 0.95);
      gap: 1rem;
    }

    .stl-error {
      background: rgba(254, 242, 242, 0.95);
      color: #991b1b;
    }

    .stl-error p { margin: 0; font-size: 14px; }

    .download-fallback {
      display: inline-block;
      padding: 8px 16px;
      background: #2563eb;
      color: white;
      text-decoration: none;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 500;
      transition: background 0.2s;
    }

    .download-fallback:hover {
      background: #1d4ed8;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 4px solid #e0e0e0;
      border-top-color: #2563eb;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StlViewerComponent implements AfterViewInit, OnDestroy {
  @Input() stlUrl: string | null | undefined;
  @ViewChild('canvas', { static: false }) canvasRef!: ElementRef<HTMLCanvasElement>;

  loading = signal(true);
  error = signal<string | null>(null);
  isFullscreen = signal(false);

  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private controls!: OrbitControls;
  private mesh: THREE.Mesh | null = null;
  private animationId: number | null = null;
  private resizeObserver?: ResizeObserver;
  private initialCameraPos = new THREE.Vector3();
  private initialTarget = new THREE.Vector3();

  constructor(private cdr: ChangeDetectorRef) {}

  ngAfterViewInit(): void {
    if (!this.stlUrl) {
      this.error.set('Brak adresu URL do pliku STL.');
      this.loading.set(false);
      return;
    }

    this.initScene();
    this.loadModel(this.stlUrl);
    this.animate();
  }

  private initScene(): void {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth || 600;
    const height = canvas.clientHeight || 500;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xf5f5f5);

    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100000);
    this.camera.position.set(0, 0, 100);

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setSize(width, height, false);
    this.renderer.setPixelRatio(window.devicePixelRatio);

    // Lighting: key + fill + ambient for even, well-shaded look
    const key = new THREE.DirectionalLight(0xffffff, 1.1);
    key.position.set(1, 1, 1);
    this.scene.add(key);

    const fill = new THREE.DirectionalLight(0xffffff, 0.5);
    fill.position.set(-1, -0.5, -1);
    this.scene.add(fill);

    this.scene.add(new THREE.HemisphereLight(0xffffff, 0x666688, 0.6));

    // OrbitControls — smooth rotate (drag) / zoom (wheel) / pan (right-drag)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;
    this.controls.rotateSpeed = 0.9;

    // Keep canvas sized to its container
    this.resizeObserver = new ResizeObserver(() => this.onResize());
    this.resizeObserver.observe(canvas.parentElement ?? canvas);
  }

  private loadModel(url: string): void {
    // Fetch ourselves so we can surface the server's error message and avoid
    // feeding a non-STL response (e.g. an HTML page) into the parser.
    fetch(url)
      .then(async (res) => {
        if (!res.ok) {
          let msg = `Błąd serwera (${res.status}).`;
          try {
            const body = await res.json();
            if (body?.message) msg = body.message;
          } catch { /* non-JSON error body */ }
          throw new Error(msg);
        }
        return res.arrayBuffer();
      })
      .then((buffer) => {
        const loader = new STLLoader();
        const geometry: THREE.BufferGeometry = loader.parse(buffer);
        this.addMesh(geometry);
        this.loading.set(false);
        this.cdr.markForCheck();
      })
      .catch((err: unknown) => {
        console.error('STL loading error:', err);
        const msg = err instanceof Error ? err.message : 'Nie udało się załadować pliku STL.';
        this.error.set(msg);
        this.loading.set(false);
        this.cdr.markForCheck();
      });
  }

  private addMesh(geometry: THREE.BufferGeometry): void {
    geometry.computeVertexNormals();
    geometry.center();

    const material = new THREE.MeshPhongMaterial({
      color: 0x4a82ff,
      specular: 0x222222,
      shininess: 40,
      flatShading: false
    });

    this.mesh = new THREE.Mesh(geometry, material);
    // STL Z-up → rotate to a natural orientation for the camera
    this.mesh.rotation.x = -Math.PI / 2;
    this.scene.add(this.mesh);

    this.frameObject(geometry);
  }

  /** Position the camera so the whole model fits comfortably in view. */
  private frameObject(geometry: THREE.BufferGeometry): void {
    geometry.computeBoundingSphere();
    const radius = geometry.boundingSphere?.radius ?? 50;

    const fov = (this.camera.fov * Math.PI) / 180;
    const distance = (radius / Math.sin(fov / 2)) * 1.2;

    this.camera.position.set(0, 0, distance);
    this.camera.near = distance / 100;
    this.camera.far = distance * 100;
    this.camera.updateProjectionMatrix();

    this.controls.target.set(0, 0, 0);
    this.controls.update();

    this.initialCameraPos.copy(this.camera.position);
    this.initialTarget.copy(this.controls.target);
  }

  resetView(): void {
    if (!this.controls) return;
    this.camera.position.copy(this.initialCameraPos);
    this.controls.target.copy(this.initialTarget);
    this.controls.update();
  }

  private animate = (): void => {
    this.animationId = requestAnimationFrame(this.animate);
    this.controls?.update();
    this.renderer.render(this.scene, this.camera);
  };

  private onResize(): void {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    if (width === 0 || height === 0) return;

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  toggleFullscreen(): void {
    this.isFullscreen.update(v => !v);
    setTimeout(() => this.onResize(), 50);
  }

  extractFileName(url: string | null | undefined): string {
    if (!url) return 'Model 3D';
    if (url.includes('thingiverse.com')) return 'Model 3D (Thingiverse)';
    if (url.includes('/api/listings/')) return 'Model 3D';
    const last = url.split('/').pop() || 'model.stl';
    return last.split('?')[0];
  }

  ngOnDestroy(): void {
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
    }
    this.resizeObserver?.disconnect();
    this.controls?.dispose();
    this.mesh?.geometry.dispose();
    (this.mesh?.material as THREE.Material | undefined)?.dispose();
    this.renderer?.dispose();
  }
}
