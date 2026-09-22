import { Component, Input, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService, LegalCase } from '../../../services/rbio-workflow.service';

@Component({
  selector: 'app-rbio-legal-case',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-legal-case.component.html',
  styleUrl: './rbio-legal-case.component.scss'
})
export class RbioLegalCaseComponent implements OnInit {
  @Input() complaint: any = null;

  private auth = inject(KeycloakAuthService);
  private workflowService = inject(RbioWorkflowService);

  // State
  legalCase = signal<LegalCase | null>(null);
  loading = signal(false);
  editing = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // Form fields
  caseNumber = '';
  courtName = '';
  caseStatus = '';
  filingDate = '';
  nextHearingDate = '';
  remarks = '';

  get isOmbudsmanAdmin(): boolean {
    return this.auth.hasRole('RBIO_ADMIN') || this.auth.hasRole('RBIO_OMBUDSMAN_ADMIN');
  }

  get isReadOnly(): boolean {
    return !this.isOmbudsmanAdmin;
  }

  ngOnInit() {
    this.loadLegalCase();
  }

  loadLegalCase() {
    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    if (!complaintId) return;
    this.loading.set(true);
    this.workflowService.getLegalCase(complaintId).subscribe({
      next: (lc) => {
        this.legalCase.set(lc);
        if (lc) {
          this.populateForm(lc);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  private populateForm(lc: LegalCase) {
    this.caseNumber = lc.caseNumber || '';
    this.courtName = lc.courtName || '';
    this.caseStatus = lc.caseStatus || '';
    this.filingDate = lc.filingDate || '';
    this.nextHearingDate = lc.nextHearingDate || '';
    this.remarks = lc.remarks || '';
  }

  startEditing() {
    if (!this.isOmbudsmanAdmin) return;
    this.editing.set(true);
    this.resultMessage.set('');
  }

  cancelEditing() {
    this.editing.set(false);
    const lc = this.legalCase();
    if (lc) {
      this.populateForm(lc);
    } else {
      this.resetForm();
    }
  }

  saveLegalCase() {
    if (!this.caseNumber.trim() || !this.courtName.trim()) return;
    this.processing.set(true);

    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const actor = this.auth.currentUser()?.username || '';
    const payload: Partial<LegalCase> = {
      complaintId,
      caseNumber: this.caseNumber,
      courtName: this.courtName,
      caseStatus: this.caseStatus,
      filingDate: this.filingDate,
      nextHearingDate: this.nextHearingDate,
      remarks: this.remarks,
      updatedBy: actor
    };

    const existingCase = this.legalCase();
    const request$ = existingCase?.id
      ? this.workflowService.updateLegalCase(complaintId, payload)
      : this.workflowService.saveLegalCase(complaintId, payload);

    request$.subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Legal case details saved successfully.');
        this.processing.set(false);
        this.editing.set(false);
        this.loadLegalCase();
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to save legal case details.');
        this.processing.set(false);
      }
    });
  }

  private resetForm() {
    this.caseNumber = '';
    this.courtName = '';
    this.caseStatus = '';
    this.filingDate = '';
    this.nextHearingDate = '';
    this.remarks = '';
  }
}
