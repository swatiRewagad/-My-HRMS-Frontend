import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService } from '../../../services/rbio-workflow.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-rbio-adjudication',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-adjudication.component.html',
  styleUrl: './rbio-adjudication.component.scss'
})
export class RbioAdjudicationComponent {
  @Input() complaint: any = null;

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);
  private rbioWorkflow = inject(RbioWorkflowService);

  // State
  activeForm = signal<'NOTICE' | 'IMPLEAD' | 'AWARD' | 'REJECT' | null>(null);

  // UST544-546, UST767: Impleading enhancement
  impleadNodalRecordCreating = signal(false);
  closureValidationError = signal('');
  incompleteImpleadedEntities = signal<string[]>([]);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // 13-1 Notice form
  noticeContent = '';
  noticeTargetParty = '';

  // Implead Party form
  impleadPartyName = '';
  impleadPartyType = '';

  // Award form
  awardCompensationType: 'CONSEQUENTIAL_LOSS' | 'TIME_HARASSMENT' = 'CONSEQUENTIAL_LOSS';
  awardAmount = '';
  awardSummary = '';

  // Reject form
  rejectionGrounds = '';

  // Compensation caps
  readonly CONSEQUENTIAL_LOSS_CAP = 3000000; // 30 Lakh
  readonly TIME_HARASSMENT_CAP = 300000; // 3 Lakh

  get caseSummary(): string {
    return this.complaint?.description || this.complaint?.subject || 'No case summary available.';
  }

  get hearingDates(): string[] {
    return this.complaint?.hearingDates || [];
  }

  get partiesInvolved(): { role: string; name: string }[] {
    const parties: { role: string; name: string }[] = [];
    if (this.complaint?.complainantName) {
      parties.push({ role: 'Complainant', name: this.complaint.complainantName });
    }
    if (this.complaint?.entityName) {
      parties.push({ role: 'Regulated Entity', name: this.complaint.entityName });
    }
    return parties;
  }

  get impleadedParties(): { name: string; type: string }[] {
    return this.complaint?.impleadedParties || [];
  }

  get currentCap(): number {
    return this.awardCompensationType === 'CONSEQUENTIAL_LOSS'
      ? this.CONSEQUENTIAL_LOSS_CAP
      : this.TIME_HARASSMENT_CAP;
  }

  get awardAmountNumeric(): number {
    return parseFloat(this.awardAmount) || 0;
  }

  get awardPercentOfCap(): number {
    if (!this.awardAmountNumeric) return 0;
    return (this.awardAmountNumeric / this.currentCap) * 100;
  }

  get awardExceedsCap(): boolean {
    return this.awardAmountNumeric > this.currentCap;
  }

  get awardApproachesCap(): boolean {
    return this.awardPercentOfCap >= 80 && !this.awardExceedsCap;
  }

  get capLabel(): string {
    return this.awardCompensationType === 'CONSEQUENTIAL_LOSS'
      ? '30,00,000 (Consequential Loss)'
      : '3,00,000 (Time/Harassment)';
  }

  openForm(form: 'NOTICE' | 'IMPLEAD' | 'AWARD' | 'REJECT') {
    this.activeForm.set(form);
    this.resultMessage.set('');
  }

  cancelForm() {
    this.activeForm.set(null);
  }

  submitNotice() {
    if (!this.noticeContent.trim() || !this.noticeTargetParty.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      // Canonical server code is ISSUE_NOTICE_13_1; this transposed spelling is accepted as a
      // declared alias (RbioLadderActions.ISSUE_13_1_NOTICE_ALIAS) so both spellings resolve to the
      // same effect. Left as-is deliberately: a statutory notice is not worth risking on a rename.
      action: 'ISSUE_13_1_NOTICE',
      remarks: this.noticeContent,
      actor: this.auth.currentUser()?.username || '',
      targetParty: this.noticeTargetParty
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: (res) => {
        this.processing.set(false);
        // A refused action returns 200 with success:false, so the envelope must be checked.
        // Reporting success on a refusal is how the wrong action code stayed hidden.
        if (res?.success === false) {
          this.resultSuccess.set(false);
          this.resultMessage.set(res?.message || 'Failed to issue notice.');
          return;
        }
        this.resultSuccess.set(true);
        this.resultMessage.set('13-1 Notice issued successfully.');
        this.activeForm.set(null);
        this.noticeContent = '';
        this.noticeTargetParty = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to issue notice.');
        this.processing.set(false);
      }
    });
  }

  submitImplead() {
    if (!this.impleadPartyName.trim() || !this.impleadPartyType.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const actor = this.auth.currentUser()?.username || '';
    const body = {
      action: 'IMPLEAD_PARTY',
      remarks: `Impleading party: ${this.impleadPartyName} (${this.impleadPartyType})`,
      actor,
      // Canonical server param is partyName; impleadPartyName is accepted as a declared alias
      // (RbioWorkflowService.PARAM_ALIASES). Both are sent so the request satisfies the contract
      // whichever name the server checks first.
      partyName: this.impleadPartyName,
      impleadPartyName: this.impleadPartyName,
      partyType: this.impleadPartyType,
      impleadPartyType: this.impleadPartyType
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: (res) => {
        if (res?.success === false) {
          this.resultSuccess.set(false);
          this.resultMessage.set(res?.message || 'Failed to implead party.');
          this.processing.set(false);
          return;
        }

        const impleadedName = this.impleadPartyName;

        // UST544-546: Create NO/PNO record for impleaded entity.
        this.impleadNodalRecordCreating.set(true);
        this.rbioWorkflow.createImpleadNodalRecord(complaintNumber, {
          impleadPartyName: impleadedName,
          impleadPartyType: this.impleadPartyType,
          actor
        }).subscribe({
          next: (recordRes: any) => {
            this.impleadNodalRecordCreating.set(false);
            this.processing.set(false);
            this.activeForm.set(null);
            this.impleadPartyName = '';
            this.impleadPartyType = '';
            if (recordRes?.success === false) {
              this.resultSuccess.set(false);
              this.resultMessage.set(
                `Party "${impleadedName}" impleaded, but the NO/PNO record was NOT created: `
                + (recordRes?.message || 'record creation refused.'));
              return;
            }
            this.resultSuccess.set(true);
            this.resultMessage.set(`Party "${impleadedName}" impleaded successfully. NO/PNO record created.`);
          },
          error: () => {
            // The implead itself succeeded, but the NO/PNO record did not. This is reported as a
            // FAILURE rather than "pending": the previous wording set resultSuccess(true) on a 404,
            // so a missing endpoint looked like a completed record and nobody noticed the record was
            // never written. An officer must know the entity has no nodal record to correspond with.
            this.impleadNodalRecordCreating.set(false);
            this.processing.set(false);
            this.activeForm.set(null);
            this.impleadPartyName = '';
            this.impleadPartyType = '';
            this.resultSuccess.set(false);
            this.resultMessage.set(
              `Party "${impleadedName}" impleaded, but the NO/PNO record was NOT created. `
              + 'Create it manually before issuing correspondence.');
          }
        });
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to implead party.');
        this.processing.set(false);
      }
    });
  }

  submitAward() {
    if (!this.awardSummary.trim() || this.awardExceedsCap) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body: any = {
      action: 'ADJUDICATION_AWARD',
      remarks: this.awardSummary,
      actor: this.auth.currentUser()?.username || '',
      compensationType: this.awardCompensationType,
      compensationAmount: this.awardAmountNumeric
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: (res) => {
        this.processing.set(false);
        // A refused award returns 200 with success:false. Reporting success here would tell an
        // officer a citizen's compensation had been awarded when no award was recorded.
        if (res?.success === false) {
          this.resultSuccess.set(false);
          this.resultMessage.set(res?.message || 'Failed to pass award.');
          return;
        }
        this.resultSuccess.set(true);
        this.resultMessage.set('Adjudication award passed successfully.');
        this.activeForm.set(null);
        this.awardAmount = '';
        this.awardSummary = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to pass award.');
        this.processing.set(false);
      }
    });
  }

  submitReject() {
    if (!this.rejectionGrounds.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      action: 'REJECT',
      remarks: this.rejectionGrounds,
      actor: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Complaint rejected with detailed grounds.');
        this.processing.set(false);
        this.activeForm.set(null);
        this.rejectionGrounds = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to reject complaint.');
        this.processing.set(false);
      }
    });
  }

  // UST767: Validate all impleaded entities have complete data before closure/award
  validateImpleadedBeforeAward(): boolean {
    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    if (!complaintNumber) return true;

    this.rbioWorkflow.validateImpleadedEntities(complaintNumber).subscribe({
      next: (result) => {
        if (!result.valid) {
          this.closureValidationError.set(
            `Incomplete data for impleaded entities: ${result.incomplete.join(', ')}. Please ensure all impleaded entities have complete records before passing award.`
          );
          this.incompleteImpleadedEntities.set(result.incomplete);
        } else {
          this.closureValidationError.set('');
          this.incompleteImpleadedEntities.set([]);
        }
      },
      error: () => {}
    });
    return this.closureValidationError() === '';
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }
}
