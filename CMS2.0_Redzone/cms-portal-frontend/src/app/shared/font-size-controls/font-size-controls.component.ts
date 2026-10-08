import { Component, signal } from '@angular/core';
import { Button } from 'primeng/button';

const STORAGE_KEY = 'cms_font_size';
const MIN_SIZE = 12;
const MAX_SIZE = 18;
const DEFAULT_SIZE = 14;
const STEP = 1;

@Component({
  selector: 'app-font-size-controls',
  standalone: true,
  imports: [Button],
  template: `
    <div class="font-size-controls">
      <p-button [text]="true" severity="secondary" label="A-"
          title="Decrease font size" [disabled]="fontSize() <= ${MIN_SIZE}" (onClick)="decrease()" />
      <p-button [text]="true" severity="secondary" label="A"
          title="Reset font size (14px)" (onClick)="reset()" />
      <p-button [text]="true" severity="secondary" label="A+"
          title="Increase font size" [disabled]="fontSize() >= ${MAX_SIZE}" (onClick)="increase()" />
    </div>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
    }
    .font-size-controls {
      display: inline-flex;
      align-items: center;
      gap: 0.125rem;
    }
  `]
})
export class FontSizeControlsComponent {
  protected fontSize = signal(this.getStoredSize());

  constructor() {
    this.applySize(this.fontSize());
  }

  increase(): void {
    this.setSize(Math.min(this.fontSize() + STEP, MAX_SIZE));
  }

  decrease(): void {
    this.setSize(Math.max(this.fontSize() - STEP, MIN_SIZE));
  }

  reset(): void {
    this.setSize(DEFAULT_SIZE);
  }

  private setSize(value: number): void {
    this.fontSize.set(value);
    localStorage.setItem(STORAGE_KEY, String(value));
    this.applySize(value);
  }

  private applySize(value: number): void {
    document.documentElement.style.setProperty('--app-text-scale', `${value / DEFAULT_SIZE}`);
    // zoom-only changes don't fire ResizeObserver on ancestors, so announce it explicitly
    // for consumers (e.g. the CEPC header) that need to re-measure their rendered size.
    document.dispatchEvent(new CustomEvent('cepc-text-scale-change'));
  }

  private getStoredSize(): number {
    const stored = Number(localStorage.getItem(STORAGE_KEY));
    return stored >= MIN_SIZE && stored <= MAX_SIZE ? stored : DEFAULT_SIZE;
  }
}
