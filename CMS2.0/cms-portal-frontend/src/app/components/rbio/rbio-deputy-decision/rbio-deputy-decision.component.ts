import { Component, Input, Output, EventEmitter, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService } from '../../../services/rbio-workflow.service';

@Component({
  selector: 'app-rbio-deputy-decision',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-deputy-decision.component.html',
  styleUrl: './rbio-deputy-decision.component.scss'
})
export class RbioDeputyDecisionComponent {
  @Input() complaint: any = null;
  @Output() decisionSubmitted = new EventEmitter<{ action: string; result: any }>();

  private auth = inject(KeycloakAuthService);
  private workflowService = inject(RbioWorkflowService);

  // State
  showForm = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // Form fields
  maintainability: 'MAINTAINABLE' | 'NON_MAINTAINABLE' = 'MAINTAINABLE';
  drbioDecision: 'FACILITATION' | 'REJECTION' = 'FACILITATION';
  remarks = '';

  // Auto-reassign state
  showReassignError = signal(false);
  reassignErrorMessage = signal('');

  get isDeputyOmbudsman(): boolean {
    return this.auth.hasRole('RBIO_DEPUTY_OMBUDSMAN');
  }

  get canShowAction(): boolean {
    if (!this.isDeputyOmbudsman) return false;
    const status = (this.complaint?.status || '').toLowerCase();
    return ['deputy_review', 'in_progress', 'assigned', 'pending_deputy_decision'].includes(status);
  }

  openForm() {
    this.showForm.set(true);
    this.resultMessage.set('');
  }

  cancelForm() {
    this.showForm.set(false);
    this.remarks = '';
  }

  submitDecision() {
    if (!this.remarks.trim()) return;
    this.processing.set(true);

    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const actor = this.auth.currentUser()?.username || '';

    this.workflowService.submitDeputyDecision(complaintId, {
      maintainability: this.maintainability,
      decision: this.drbioDecision,
      remarks: this.remarks,
      actor
    }).subscribe({
      next: (res) => {
        this.resultSuccess.set(true);
        if (this.maintainability === 'MAINTAINABLE') {
          this.resultMessage.set('Decision recorded. Complaint will proceed to facilitation.');
        } else {
          this.resultMessage.set('Non-Maintainable decision recorded. Complaint rejected.');
        }
        this.processing.set(false);
        this.showForm.set(false);
        this.decisionSubmitted.emit({ action: 'DEPUTY_OMBUDSMAN_DECISION', result: res });
        this.remarks = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to record decision.');
        this.processing.set(false);
      }
    });
  }

  onMaintainabilityChange() {
    if (this.maintainability === 'NON_MAINTAINABLE') {
      this.drbioDecision = 'REJECTION';
    } else {
      this.drbioDecision = 'FACILITATION';
    }
  }
}
