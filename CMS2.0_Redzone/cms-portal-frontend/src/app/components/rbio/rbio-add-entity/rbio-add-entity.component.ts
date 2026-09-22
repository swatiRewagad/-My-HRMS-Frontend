import { Component, Input, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService, AdditionalEntity } from '../../../services/rbio-workflow.service';

@Component({
  selector: 'app-rbio-add-entity',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-add-entity.component.html',
  styleUrl: './rbio-add-entity.component.scss'
})
export class RbioAddEntityComponent implements OnInit {
  @Input() complaint: any = null;

  private auth = inject(KeycloakAuthService);
  private workflowService = inject(RbioWorkflowService);

  readonly MAX_ADDITIONAL_ENTITIES = 6;

  // State
  showForm = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);
  additionalEntities = signal<AdditionalEntity[]>([]);
  loading = signal(false);

  // Form fields
  entityName = '';
  entityBranch = '';
  entityType = '';
  entityCategory = '';

  get canAddEntity(): boolean {
    const roles = this.auth.getRoles();
    const allowedRoles = ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN'];
    return roles.some(r => allowedRoles.includes(r));
  }

  get hasReachedMax(): boolean {
    return this.additionalEntities().length >= this.MAX_ADDITIONAL_ENTITIES;
  }

  get isAssessmentStage(): boolean {
    const status = (this.complaint?.status || '').toLowerCase();
    return ['assigned', 'in_progress', 'assessment', 'deputy_review', 'reviewer_review'].includes(status);
  }

  get showComponent(): boolean {
    return this.canAddEntity && this.isAssessmentStage;
  }

  ngOnInit() {
    if (this.complaint?.complaintId || this.complaint?.complaintNumber) {
      this.loadEntities();
    }
  }

  loadEntities() {
    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    if (!complaintId) return;
    this.loading.set(true);
    this.workflowService.getAdditionalEntities(complaintId).subscribe({
      next: (entities) => {
        this.additionalEntities.set(entities);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  openForm() {
    if (this.hasReachedMax) {
      this.resultSuccess.set(false);
      this.resultMessage.set(`Maximum of ${this.MAX_ADDITIONAL_ENTITIES} additional entities reached.`);
      return;
    }
    this.showForm.set(true);
    this.resultMessage.set('');
  }

  cancelForm() {
    this.showForm.set(false);
    this.resetFormFields();
  }

  submitEntity() {
    if (!this.entityName.trim() || !this.entityType.trim()) return;
    if (this.hasReachedMax) return;

    this.processing.set(true);
    const complaintId = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const actor = this.auth.currentUser()?.username || '';

    this.workflowService.addEntity(complaintId, {
      complaintId,
      entityName: this.entityName,
      entityBranch: this.entityBranch,
      entityType: this.entityType,
      entityCategory: this.entityCategory,
      createdBy: actor
    }).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set(`Entity "${this.entityName}" added. NO record created.`);
        this.processing.set(false);
        this.showForm.set(false);
        this.resetFormFields();
        this.loadEntities();
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to add entity.');
        this.processing.set(false);
      }
    });
  }

  private resetFormFields() {
    this.entityName = '';
    this.entityBranch = '';
    this.entityType = '';
    this.entityCategory = '';
  }
}
