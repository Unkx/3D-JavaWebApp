import { ElementRef } from '@angular/core';
// @ts-ignore
import * as THREE from 'three';
import { FailedPrintSceneComponent } from './failed-print-scene.component';

describe('FailedPrintSceneComponent', () => {
  function createBuilt(): FailedPrintSceneComponent {
    const component = new FailedPrintSceneComponent();
    component.buildModel();
    return component;
  }

  describe('buildModel()', () => {
    it('creates 6 layers, a nozzle, and a hidden spaghetti strand', () => {
      const component = createBuilt();
      expect(component.layers.length).toBe(6);
      expect(component.nozzle).toBeDefined();
      expect(component.spaghetti.visible).toBe(false);
    });
  });

  describe('updateModel()', () => {
    it('keeps all layers collapsed at the very start of the cycle', () => {
      const component = createBuilt();
      component.updateModel(0);
      component.layers.forEach(layer => expect(layer.scale.y).toBe(0));
    });

    it('builds the lowest layers up before the top layers start', () => {
      const component = createBuilt();
      component.updateModel(1.8); // halfway through the 3.6s build phase
      expect(component.layers[0].scale.y).toBe(1);
      expect(component.layers[5].scale.y).toBeLessThan(1);
    });

    it('tilts the top layers during the shift phase without shrinking them yet', () => {
      const component = createBuilt();
      component.updateModel(4.1); // inside the 3.6s-4.5s shift window
      expect(component.layers[5].rotation.z).toBeGreaterThan(0);
      expect(component.layers[5].scale.y).toBe(1);
    });

    it('shrinks the top layers and reveals the spaghetti strand during unravel', () => {
      const component = createBuilt();
      component.updateModel(5.5); // inside the 4.5s-6s unravel window
      expect(component.layers[5].scale.y).toBeLessThan(1);
      expect(component.spaghetti.visible).toBe(true);
      expect((component.spaghetti.material as THREE.MeshBasicMaterial).opacity).toBeGreaterThan(0);
    });

    it('wraps cleanly back to a collapsed state at the start of the next cycle', () => {
      const component = createBuilt();
      component.updateModel(6); // one full 6s cycle later == the t=0 state
      component.layers.forEach(layer => expect(layer.scale.y).toBe(0));
    });
  });

  describe('ngAfterViewInit() without a WebGL context', () => {
    it('falls back to sceneUnsupported instead of throwing', () => {
      const component = new FailedPrintSceneComponent();
      component.canvasRef = { nativeElement: document.createElement('canvas') } as ElementRef<HTMLCanvasElement>;

      // jsdom canvases have no real WebGL context, so THREE.WebGLRenderer throws
      // inside ngAfterViewInit's try/catch — this exercises the real fallback path.
      expect(() => component.ngAfterViewInit()).not.toThrow();
      expect(component.sceneUnsupported()).toBe(true);
    });
  });
});
