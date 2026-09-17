import { Component, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';

const STORAGE_KEY = 'cms_font_scale';
const MIN_SCALE = 80;
const MAX_SCALE = 140;
const STEP = 10;
const DEFAULT_SCALE = 100;

@Component({
  selector: 'app-font-size-controls',
  standalone: true,
  imports: [ButtonModule],
  template: `
    <div class="font-size-controls">
      <button pButton [text]="true" severity="secondary" label="A-"
          title="Decrease font size" [disabled]="scale() <= ${MIN_SCALE}" (click)="decrease()"></button>
      <button pButton [text]="true" severity="secondary" label="A"
          title="Reset font size" (click)="reset()"></button>
      <button pButton [text]="true" severity="secondary" label="A+"
          title="Increase font size" [disabled]="scale() >= ${MAX_SCALE}" (click)="increase()"></button>
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
    .font-size-controls :host ::ng-deep .p-button {
      padding: 0.25rem 0.5rem;
      font-weight: 600;
      min-width: 2rem;
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
