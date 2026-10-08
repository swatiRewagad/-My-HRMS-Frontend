import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LegalCase, LegalCaseFormShape } from '../right-side-panel.types';
import { TranslateOrPipe } from '../../../../pipes/translate-or.pipe';

@Component({
  selector: 'app-right-side-panel-legal-case',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateOrPipe],
  templateUrl: './legal-case.component.html',
  styleUrl: './legal-case.component.scss',
})
export class LegalCaseComponent implements OnChanges {
  @Input() legalCase: LegalCase | null = null;
  @Input() loadingLegalCase = false;
  @Input() showLegalCaseDialog = false;
  @Input() legalCaseSaving = false;
  @Input() legalCaseError = '';
  @Input() legalCaseForm!: LegalCaseFormShape;
  @Input() legalCaseRegionOptions: { officeCode: string; officeName: string; officeType: string }[] = [];

  @Output() close = new EventEmitter<void>();
  @Output() openEditor = new EventEmitter<void>();
  @Output() closeDialog = new EventEmitter<void>();
  @Output() legalCaseFormChange = new EventEmitter<LegalCaseFormShape>();
  @Output() save = new EventEmitter<void>();

  localForm!: LegalCaseFormShape;

  get todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['showLegalCaseDialog'] && this.showLegalCaseDialog) {
      this.localForm = { ...this.legalCaseForm };
    }
  }

  onFieldChange(): void {
    this.legalCaseFormChange.emit({ ...this.localForm });
  }
}
