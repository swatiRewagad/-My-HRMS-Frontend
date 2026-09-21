import { Component, Input, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService, RegulatoryBody } from '../../../services/rbio-workflow.service';

@Component({
  selector: 'app-rbio-forward-regulatory',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-forward-regulatory.component.html',
  styleUrl: './rbio-forward-regulatory.component.scss'
})
export class RbioForwardRegulatoryComponent implements OnInit {
  @Input() complaint: any = null;

  private auth = inject(KeycloakAuthService);
  private workflowService = inject(RbioWorkflowService);

  // State
  showForm = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);
  regulatoryBodies = signal<RegulatoryBody[]>([]);
  loadingBodies = signal(false);

  // Form fields
  // number | string: the server sends the id as a String, and a <select> binds strings anyway. Typed
  // `number` it compared unequal to every option and selectedBody() never resolved.
  selectedBodyId: number | string | null = null;
  remarks = '';

  get selectedBody(): RegulatoryBody | undefined {
    if (!this.selectedBodyId) return undefined;
    return this.regulatoryBodies().find(rb => rb.id === this.selectedBodyId);
  }

  ngOnInit() {
    this.loadRegulatoryBodies();
  }

  /**
   * The validated master list (UST766).
   *
   * <p>The master now exists. Until this batch {@code getRegulatoryBodies()} called an endpoint no controller
   * implemented, and a {@code catchError(() => of([]))} turned the 404 into a permanently empty dropdown —
   * while this same screen told the officer that "only bodies from the validated master list can be
   * selected". The server returns only bodies whose contact email is VERIFIED, so an empty list means none
   * has been verified yet. That is surfaced rather than left looking like a loading glitch.
   */
  loadRegulatoryBodies() {
    this.loadingBodies.set(true);
    this.workflowService.getRegulatoryBodies().subscribe({
      next: (bodies) => {
        // No `active` filter: the server returns only active, forwardable bodies and no longer sends that
        // field, so filtering on it would silently empty the list now that the endpoint answers.
        this.regulatoryBodies.set(bodies);
        this.loadingBodies.set(false);
        if (bodies.length === 0) {
          this.resultSuccess.set(false);
          this.resultMessage.set(
            'No regulatory body has a verified contact email, so a referral cannot be made yet.');
        }
      },
      error: () => {
        // Fails closed AND says so: an empty dropdown with no message is indistinguishable from a master
        // that legitimately has no entries.
        this.regulatoryBodies.set([]);
        this.loadingBodies.set(false);
        this.resultSuccess.set(false);
        this.resultMessage.set('The regulatory body list could not be loaded. Please retry.');
      }
    });
  }

  openForm() {
    this.showForm.set(true);
    this.resultMessage.set('');
  }

  cancelForm() {
    this.showForm.set(false);
    this.selectedBodyId = null;
    this.remarks = '';
  }

  submitForward() {
    if (!this.selectedBodyId || !this.remarks.trim()) return;
    const body = this.selectedBody;
    if (!body) return;

    this.processing.set(true);
    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const actor = this.auth.currentUser()?.username || '';

    this.workflowService.forwardToRegulatoryBody(complaintId, {
      regulatoryBodyId: body.id,
      regulatoryBodyName: body.name,
      remarks: this.remarks,
      actor
    }).subscribe({
      next: (res: any) => {
        // A refusal answers HTTP 200 with success:false on this path, so the flag must be checked. An
        // unverified body is refused server-side (UST766) and would otherwise have read as a success.
        if (res && res.success === false) {
          this.resultSuccess.set(false);
          this.resultMessage.set(res.message || 'Failed to forward complaint.');
          this.processing.set(false);
          return;
        }
        this.resultSuccess.set(true);
        // States what the server actually did. This previously asserted "Awareness email sent to
        // complainant" unconditionally, and no such email existed anywhere in the backend — the complainant
        // was never told their complaint had left RBI's jurisdiction while the file recorded that they had.
        // The email is now QUEUED through the communication outbox, which is a durable obligation rather
        // than a completed send, so the wording says queued.
        this.resultMessage.set(
          `Complaint forwarded to ${body.name}. An awareness email has been queued for the complainant.`);
        this.processing.set(false);
        this.showForm.set(false);
        this.selectedBodyId = null;
        this.remarks = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to forward complaint.');
        this.processing.set(false);
      }
    });
  }
}
