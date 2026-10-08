import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Tooltip } from 'primeng/tooltip';

/**
 * Single reusable info-icon tooltip for form field labels (FR-G-012 / UST81).
 * Wraps PrimeNG's pTooltip so hover, click and keyboard focus all reveal the
 * same content, and renders nothing when a field has no tooltip text yet.
 */
@Component({
  selector: 'app-field-tooltip',
  standalone: true,
  imports: [CommonModule, Tooltip],
  template: `
    @if (text) {
      <button type="button" class="field-tooltip-trigger"
              [pTooltip]="text" tooltipPosition="top" tooltipEvent="both" [tabindex]="0"
              [attr.aria-label]="'More information: ' + text">
        <i class="pi pi-info-circle" aria-hidden="true"></i>
      </button>
    }
  `,
  styles: [`
    .field-tooltip-trigger {
      display: inline-flex; align-items: center; justify-content: center;
      width: 16px; height: 16px; margin-left: 4px; padding: 0;
      background: none; border: none; cursor: help; color: #999; vertical-align: middle;
      &:hover, &:focus-visible { color: #2460b9; }
      &:focus-visible { outline: 2px solid #2460b9; outline-offset: 2px; border-radius: 50%; }
      i { font-size: 13px; }
    }
  `]
})
export class FieldTooltipComponent {
  @Input() text = '';
}
