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
  selectedBodyId: number | null = null;
  remarks = '';

  get selectedBody(): RegulatoryBody | undefined {
    if (!this.selectedBodyId) return undefined;
    return this.regulatoryBodies().find(rb => rb.id === this.selectedBodyId);
  }

  ngOnInit() {
    this.loadRegulatoryBodies();
  }

  loadRegulatoryBodies() {
    this.loadingBodies.set(true);
    this.workflowService.getRegulatoryBodies().subscribe({
      next: (bodies) => {
        this.regulatoryBodies.set(bodies.filter(b => b.active));
        this.loadingBodies.set(false);
      },
      error: () => {
        this.loadingBodies.set(false);
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
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set(`Complaint forwarded to ${body.name}. Awareness email sent to complainant.`);
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
