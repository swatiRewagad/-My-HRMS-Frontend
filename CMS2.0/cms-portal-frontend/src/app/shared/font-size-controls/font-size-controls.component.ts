import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

const STORAGE_KEY = 'cms_font_scale';
const MIN_SCALE = 80;
const MAX_SCALE = 140;
const STEP = 10;
const DEFAULT_SCALE = 100;

@Component({
  selector: 'app-font-size-controls',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="font-size-controls">
      <button type="button" class="fsc-btn" title="Decrease font size" (click)="decrease()">A-</button>
      <button type="button" class="fsc-btn fsc-mid" title="Reset font size" (click)="reset()">A</button>
      <button type="button" class="fsc-btn" title="Increase font size" (click)="increase()">A+</button>
    </div>
  `,
  styles: [`
    .font-size-controls {
      display: inline-flex;
      align-items: center;
      gap: 2px;
    }
    .fsc-btn {
      border: 1px solid #d1d5db;
      background: #fff;
      color: #374151;
      border-radius: 4px;
      padding: 4px 8px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      line-height: 1;
    }
    .fsc-btn:hover {
      border-color: #2563eb;
      color: #2563eb;
    }
    .fsc-mid {
      font-size: 13px;
    }
  `]
})
export class FontSizeControlsComponent {
  protected scale = signal(this.getStoredScale());

  constructor() {
    this.applyScale(this.scale());
  }

  increase(): void {
    this.setScale(Math.min(this.scale() + STEP, MAX_SCALE));
  }

  decrease(): void {
    this.setScale(Math.max(this.scale() - STEP, MIN_SCALE));
  }

  reset(): void {
    this.setScale(DEFAULT_SCALE);
  }

  private setScale(value: number): void {
    this.scale.set(value);
    localStorage.setItem(STORAGE_KEY, String(value));
    this.applyScale(value);
  }

  private applyScale(value: number): void {
    document.body.style.zoom = `${value}%`;
  }

  private getStoredScale(): number {
    const stored = Number(localStorage.getItem(STORAGE_KEY));
    return stored >= MIN_SCALE && stored <= MAX_SCALE ? stored : DEFAULT_SCALE;
  }
}
