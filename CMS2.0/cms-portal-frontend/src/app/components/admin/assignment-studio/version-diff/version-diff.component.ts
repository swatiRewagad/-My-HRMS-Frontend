import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-version-diff',
  standalone: true,
  imports: [CommonModule],
  template: `<div class="placeholder">Version Diff — Phase 3</div>`,
  styles: [`.placeholder { padding: 2rem; text-align: center; color: var(--text-color-secondary); }`]
})
export class VersionDiffComponent {}
