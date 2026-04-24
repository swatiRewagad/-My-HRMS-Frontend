import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WizardStateService } from '../../services/wizard-state.service';
import { MODE_OPTIONS } from '../../data/services.data';
import { ModeOption } from '../../models/realm.model';

@Component({
  selector: 'app-mode-selection',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mode-selection.component.html',
  styleUrl: './mode-selection.component.scss',
})
export class ModeSelectionComponent {
  readonly wizard = inject(WizardStateService);
  readonly modeOptions = MODE_OPTIONS;

  get selectedMode() {
    return this.wizard.selectedMode();
  }

  selectMode(mode: ModeOption['value']): void {
    this.wizard.selectedMode.set(mode);
  }
}
