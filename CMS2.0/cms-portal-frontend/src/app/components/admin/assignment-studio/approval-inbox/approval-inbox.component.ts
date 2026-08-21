import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AssignmentStudioService } from '../../../../services/assignment-studio.service';

@Component({
  selector: 'app-approval-inbox',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './approval-inbox.component.html',
  styleUrl: './approval-inbox.component.scss'
})
export class ApprovalInboxComponent implements OnInit {

  pendingVersions = signal<any[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  remarksMap: Record<number, string> = {};
  actionInProgress = signal<number | null>(null);

  constructor(
    private studioService: AssignmentStudioService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadPending();
  }

  loadPending() {
    this.loading.set(true);
    this.studioService.getPendingApprovals().subscribe({
      next: (versions) => {
        this.pendingVersions.set(versions);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load pending approvals');
      }
    });
  }

  approve(version: any) {
    this.actionInProgress.set(version.id);
    const remarks = this.remarksMap[version.id] || '';
    this.studioService.approve(version.ruleSetId, version.id, remarks).subscribe({
      next: () => {
        this.successMessage.set(`Version ${version.versionNo} approved`);
        this.actionInProgress.set(null);
        this.loadPending();
      },
      error: (err) => {
        this.actionInProgress.set(null);
        this.errorMessage.set(err.error?.message || 'Approve failed');
      }
    });
  }

  reject(version: any) {
    const remarks = this.remarksMap[version.id] || '';
    if (!remarks.trim()) {
      this.errorMessage.set('Rejection requires remarks');
      return;
    }
    this.actionInProgress.set(version.id);
    this.studioService.reject(version.ruleSetId, version.id, remarks).subscribe({
      next: () => {
        this.successMessage.set(`Version ${version.versionNo} rejected — returned to DRAFT`);
        this.actionInProgress.set(null);
        this.loadPending();
      },
      error: (err) => {
        this.actionInProgress.set(null);
        this.errorMessage.set(err.error?.message || 'Reject failed');
      }
    });
  }

  openEditor(version: any) {
    this.router.navigate([
      '/admin/assignment-studio', version.ruleSetId, 'versions', version.id, 'edit'
    ]);
  }

  clearMessages() {
    this.errorMessage.set('');
    this.successMessage.set('');
  }
}
